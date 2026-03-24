package am.froshy.launcher.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MojangVersionDownloader implements MinecraftVersionDownloader {

    private static final URI VERSION_MANIFEST_URI =
            URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final String ASSETS_BASE = "https://resources.download.minecraft.net/";
    private static final int MAX_RETRIES = 3;
    private static final int MAX_INHERIT_DEPTH = 4;

    // OS detection ──────────────────────────────────────────────────────────
    private static final String OS;
    static {
        String name = System.getProperty("os.name", "").toLowerCase();
        if      (name.contains("win"))                          OS = "windows";
        else if (name.contains("mac") || name.contains("darwin")) OS = "osx";
        else                                                     OS = "linux";
    }
    /** Sufijo para detectar nativos de la plataforma actual en el artifact path / nombre. */
    private static final String NATIVE_SUFFIX = switch (OS) {
        case "windows" -> "natives-windows";
        case "osx"     -> "natives-macos";
        default        -> "natives-linux";
    };

    private final HttpClient http;
    private final ObjectMapper mapper;

    public MojangVersionDownloader() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        this.mapper = new ObjectMapper();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  downloadVersion — descarga todo lo necesario
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void downloadVersion(String version, Path gameDir, BiConsumer<Integer, String> progress)
            throws IOException, InterruptedException {

        // ── Fase 1: manifest + version JSON (0→5 %) ──────────────────────
        progress.accept(0, "Descargando manifest de versiones…");
        JsonNode manifest = withRetry("manifest", () -> getJson(VERSION_MANIFEST_URI));
        String versionUrl = findVersionUrl(manifest, version);
        progress.accept(2, "Versión " + version + " encontrada");

        Path versionDir  = gameDir.resolve("versions").resolve(version);
        Path versionJson = versionDir.resolve(version + ".json");
        Files.createDirectories(versionDir);

        JsonNode meta;
        if (Files.exists(versionJson)) {
            meta = mapper.readTree(versionJson.toFile());
        } else {
            progress.accept(3, "Descargando metadata de " + version + "…");
            meta = withRetry("meta-" + version, () -> getJson(URI.create(versionUrl)));
            mapper.writeValue(versionJson.toFile(), meta);
        }
        // Resolver herencia (inheritsFrom — usado por Forge/Fabric y algunos snapshots)
        meta = resolveInheritance(meta, manifest, gameDir, 0);
        progress.accept(5, "Metadata lista");

        // ── Fase 2: client JAR (5→35 %) ─────────────────────────────────
        Path clientJar    = versionDir.resolve(version + ".jar");
        String clientSha1 = meta.path("downloads").path("client").path("sha1").asText("");
        if (!isValidFileSha1(clientJar, clientSha1)) {
            String jarUrl = meta.path("downloads").path("client").path("url").asText();
            if (!jarUrl.isBlank()) {
                progress.accept(5, "Descargando " + version + ".jar…");
                withRetryVoid("client-jar", () ->
                        downloadWithProgress(URI.create(jarUrl), clientJar,
                                p -> progress.accept(5 + p * 30 / 100, "JAR del cliente: " + p + "%")));
            }
        } else {
            progress.accept(35, "JAR del cliente ya está al día");
        }
        progress.accept(35, "JAR del cliente ✓");

        // ── Fase 3: librerías (35→68 %) ──────────────────────────────────
        JsonNode libs = meta.path("libraries");
        Path nativesDir = versionDir.resolve("natives");
        Files.createDirectories(nativesDir);
        downloadLibraries(libs, gameDir, nativesDir, progress, 35, 68);

        // ── Fase 4: asset index (68→72 %) ────────────────────────────────
        String assetIndexId  = meta.path("assetIndex").path("id").asText("legacy");
        String assetIndexUrl = meta.path("assetIndex").path("url").asText();
        Path indexFile = gameDir.resolve("assets").resolve("indexes")
                .resolve(assetIndexId + ".json");
        Files.createDirectories(indexFile.getParent());
        if (!Files.exists(indexFile) && !assetIndexUrl.isBlank()) {
            progress.accept(68, "Descargando índice de assets (" + assetIndexId + ")…");
            JsonNode indexData = withRetry("asset-index", () -> getJson(URI.create(assetIndexUrl)));
            mapper.writeValue(indexFile.toFile(), indexData);
        }
        progress.accept(72, "Índice de assets ✓");

        // ── Fase 5: asset objects (72→100 %) ─────────────────────────────
        if (Files.exists(indexFile)) {
            JsonNode indexData = mapper.readTree(indexFile.toFile());
            downloadAssets(indexData, gameDir, progress, 72, 100);
        }

        progress.accept(100, "¡Instalación completa!");
    }

    // ── Resolución de herencia (inheritsFrom) ────────────────────────────

    /**
     * Si {@code meta} tiene un campo {@code inheritsFrom}, descarga el JSON padre
     * y mezcla los metadatos (librerías, argumentos, etc.). Recursivo hasta MAX_INHERIT_DEPTH.
     */
    private JsonNode resolveInheritance(JsonNode meta, JsonNode manifest,
                                         Path gameDir, int depth) throws IOException, InterruptedException {
        if (depth >= MAX_INHERIT_DEPTH || !meta.has("inheritsFrom")) return meta;

        String parentId  = meta.path("inheritsFrom").asText();
        String parentUrl = findVersionUrl(manifest, parentId);
        Path parentDir   = gameDir.resolve("versions").resolve(parentId);
        Path parentJson  = parentDir.resolve(parentId + ".json");
        Files.createDirectories(parentDir);

        JsonNode parent;
        if (Files.exists(parentJson)) {
            parent = mapper.readTree(parentJson.toFile());
        } else {
            parent = withRetry("meta-" + parentId, () -> getJson(URI.create(parentUrl)));
            mapper.writeValue(parentJson.toFile(), parent);
        }
        parent = resolveInheritance(parent, manifest, gameDir, depth + 1);
        return mergeMeta(parent, meta);
    }

    /** Resuelve herencia LOCALMENTE (solo lee JSONs ya descargados). */
    private JsonNode resolveInheritanceLocal(JsonNode meta, Path gameDir) throws IOException {
        if (!meta.has("inheritsFrom")) return meta;
        String parentId   = meta.path("inheritsFrom").asText();
        Path   parentJson = gameDir.resolve("versions").resolve(parentId).resolve(parentId + ".json");
        if (!Files.exists(parentJson))
            throw new IOException("Versión padre '" + parentId + "' no instalada. Ejecuta prepareVersion primero.");
        JsonNode parent = mapper.readTree(parentJson.toFile());
        parent = resolveInheritanceLocal(parent, gameDir);
        return mergeMeta(parent, meta);
    }

    /** Fusiona dos JSONs de versión: el hijo sobreescribe al padre, excepto librerías y argumentos. */
    private JsonNode mergeMeta(JsonNode parent, JsonNode child) {
        ObjectNode merged = mapper.createObjectNode();
        parent.fields().forEachRemaining(e -> merged.set(e.getKey(), e.getValue()));
        child.fields().forEachRemaining(e -> {
            String key = e.getKey();
            if (!"libraries".equals(key) && !"arguments".equals(key)
                    && !"minecraftArguments".equals(key)) {
                merged.set(key, e.getValue());
            }
        });
        // Librerías: padre primero, luego hijo
        ArrayNode libs = mapper.createArrayNode();
        parent.path("libraries").forEach(libs::add);
        child.path("libraries").forEach(libs::add);
        merged.set("libraries", libs);
        // Argumentos del juego / JVM
        if (child.has("arguments") || parent.has("arguments")) {
            ObjectNode args = mapper.createObjectNode();
            ArrayNode jvmA = mapper.createArrayNode();
            parent.path("arguments").path("jvm").forEach(jvmA::add);
            child.path("arguments").path("jvm").forEach(jvmA::add);
            ArrayNode gameA = mapper.createArrayNode();
            parent.path("arguments").path("game").forEach(gameA::add);
            child.path("arguments").path("game").forEach(gameA::add);
            args.set("jvm", jvmA);
            args.set("game", gameA);
            merged.set("arguments", args);
        } else if (child.has("minecraftArguments")) {
            merged.put("minecraftArguments",
                    parent.path("minecraftArguments").asText("")
                            + " " + child.path("minecraftArguments").asText(""));
        }
        return merged;
    }

    // ── Descarga de librerías ────────────────────────────────────────────

    private void downloadLibraries(JsonNode libs, Path gameDir, Path nativesDir,
            BiConsumer<Integer, String> progress, int lo, int hi)
            throws IOException, InterruptedException {
        int total = libs.size();
        if (total == 0) { progress.accept(hi, "Sin librerías"); return; }
        int done = 0;
        for (JsonNode lib : libs) {
            if (!evaluateRules(lib.path("rules"))) { done++; continue; }

            // ── Artifact principal → classpath ──────────────────────────
            JsonNode artifact = lib.path("downloads").path("artifact");
            if (!artifact.isMissingNode() && artifact.has("url")) {
                String relPath = artifact.path("path").asText();
                String sha1    = artifact.path("sha1").asText("");
                Path dest = gameDir.resolve("libraries").resolve(relPath);
                if (!isValidFileSha1(dest, sha1)) {
                    Files.createDirectories(dest.getParent());
                    final String url = artifact.path("url").asText();
                    String name = getLibFilename(relPath);
                    progress.accept(lo + done * (hi - lo) / total, "Lib: " + name);
                    withRetryVoid("lib-" + relPath, () ->
                            downloadSimple(URI.create(url), dest));
                }
                // ── FIX CRÍTICO: extraer nativos formato 1.17+ ──────────
                // En 1.17+ los nativos ya NO usan "classifiers" sino que son
                // artifacts separados con nombre como "org.lwjgl:lwjgl:3.3.x:natives-windows".
                // Hay que detectarlos y extraer los .dll/.so al nativesDir.
                String libName = lib.path("name").asText("");
                boolean isNativeArtifact = libName.contains(":natives-" + NATIVE_SUFFIX.replace("natives-", ""))
                        || libName.contains(":" + NATIVE_SUFFIX)
                        || relPath.contains(NATIVE_SUFFIX)
                        || relPath.contains("natives-windows")
                        || relPath.contains("natives-linux")
                        || relPath.contains("natives-macos")
                        || relPath.contains("natives-osx");
                if (isNativeArtifact && isValidFile(dest, 4)) {
                    progress.accept(lo + done * (hi - lo) / total,
                            "Extrayendo nativos: " + getLibFilename(relPath));
                    extractNatives(dest, nativesDir);
                }
            }

            // ── Natives classifier (formato pre-1.17) → extraer a nativesDir ──
            String nativeKey = getNativeClassifier(lib);
            if (nativeKey != null) {
                JsonNode natArt = lib.path("downloads").path("classifiers").path(nativeKey);
                if (!natArt.isMissingNode() && natArt.has("url")) {
                    String relPath = natArt.path("path").asText();
                    String sha1    = natArt.path("sha1").asText("");
                    Path dest = gameDir.resolve("libraries").resolve(relPath);
                    if (!isValidFileSha1(dest, sha1)) {
                        Files.createDirectories(dest.getParent());
                        final String url = natArt.path("url").asText();
                        progress.accept(lo + done * (hi - lo) / total,
                                "Nativos: " + getLibFilename(relPath));
                        withRetryVoid("native-" + relPath, () ->
                                downloadSimple(URI.create(url), dest));
                    }
                    if (isValidFile(dest, 4)) {
                        progress.accept(lo + done * (hi - lo) / total,
                                "Extrayendo: " + getLibFilename(relPath));
                        extractNatives(dest, nativesDir);
                    }
                }
            }

            done++;
            progress.accept(lo + done * (hi - lo) / total, "");
        }
    }

    // ── Descarga de assets ───────────────────────────────────────────────

    private void downloadAssets(JsonNode indexData, Path gameDir,
            BiConsumer<Integer, String> progress, int lo, int hi)
            throws InterruptedException {
        JsonNode objects = indexData.path("objects");
        List<AssetTask> pending = new ArrayList<>();
        objects.fields().forEachRemaining(e -> {
            String hash   = e.getValue().path("hash").asText();
            String prefix = hash.substring(0, 2);
            Path dest = gameDir.resolve("assets").resolve("objects")
                    .resolve(prefix).resolve(hash);
            if (!Files.exists(dest)) {
                pending.add(new AssetTask(
                        URI.create(ASSETS_BASE + prefix + "/" + hash), dest));
            }
        });

        int total   = objects.size();
        int preDone = total - pending.size();
        AtomicInteger done = new AtomicInteger(preDone);
        progress.accept(lo + (int)((long) preDone * (hi - lo) / Math.max(total, 1)),
                "Assets: " + preDone + "/" + total + " listos");

        if (pending.isEmpty()) { progress.accept(hi, "Assets ✓"); return; }

        progress.accept(lo, "Descargando assets: " + pending.size() + " pendientes…");
        int threads = Math.min(8, pending.size());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (AssetTask task : pending) {
            pool.submit(() -> {
                try {
                    Files.createDirectories(task.dest().getParent());
                    downloadSimple(task.uri(), task.dest());
                } catch (Exception ex) {
                    System.err.println("[assets] warn: " + ex.getMessage());
                }
                int d = done.incrementAndGet();
                if (d % 50 == 0 || d == total) {
                    progress.accept(lo + (int)((long) d * (hi - lo) / total),
                            "Assets: " + d + "/" + total);
                }
            });
        }
        pool.shutdown();
        if (!pool.awaitTermination(60, TimeUnit.MINUTES)) {
            pool.shutdownNow();
        }
        progress.accept(hi, "Assets ✓");
    }

    private record AssetTask(URI uri, Path dest) {}

    // ═══════════════════════════════════════════════════════════════════════
    //  buildInstallation — construye los argumentos de lanzamiento
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public VersionInstallation buildInstallation(String version, Path gameDir, String username)
            throws IOException {

        Path versionDir  = gameDir.resolve("versions").resolve(version);
        Path versionJson = versionDir.resolve(version + ".json");
        if (!Files.exists(versionJson)) {
            throw new IOException("Versión " + version + " no instalada. Ejecuta prepareVersion primero.");
        }

        JsonNode meta = mapper.readTree(versionJson.toFile());
        // Resolver herencia localmente
        meta = resolveInheritanceLocal(meta, gameDir);

        // ── Classpath: librerías + client JAR ────────────────────────────
        List<Path> classpath = new ArrayList<>();
        for (JsonNode lib : meta.path("libraries")) {
            if (!evaluateRules(lib.path("rules"))) continue;
            JsonNode artifact = lib.path("downloads").path("artifact");
            if (!artifact.isMissingNode()) {
                Path libFile = gameDir.resolve("libraries")
                        .resolve(artifact.path("path").asText());
                if (Files.exists(libFile)) classpath.add(libFile);
            }
        }
        Path clientJar = versionDir.resolve(version + ".jar");
        if (Files.exists(clientJar)) classpath.add(clientJar);

        // ── Variables de sustitución ─────────────────────────────────────
        String mainClass    = meta.path("mainClass").asText();
        String assetIndexId = meta.path("assetIndex").path("id").asText("legacy");
        Path   assetsDir    = gameDir.resolve("assets");
        Path   nativesDir   = versionDir.resolve("natives");

        String cpStr = classpath.stream()
                .map(p -> p.toAbsolutePath().toString())
                .collect(java.util.stream.Collectors.joining(File.pathSeparator));

        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name",  username);
        vars.put("version_name",      version);
        vars.put("game_directory",    gameDir.toAbsolutePath().toString());
        vars.put("assets_root",       assetsDir.toAbsolutePath().toString());
        vars.put("assets_index_name", assetIndexId);
        vars.put("auth_uuid",         offlineUUID(username).toString());
        vars.put("auth_access_token", "0");
        vars.put("clientid",          "0");
        vars.put("auth_xuid",         "0");
        vars.put("user_type",         "offline");
        vars.put("version_type",      meta.path("type").asText("release"));
        vars.put("launcher_name",     "FroshyLauncher");
        vars.put("launcher_version",  "1.0");
        vars.put("natives_directory", nativesDir.toAbsolutePath().toString());
        vars.put("classpath",         cpStr);
        vars.put("resolution_width",  "854");
        vars.put("resolution_height", "480");

        // ── JVM arguments ────────────────────────────────────────────────
        List<String> jvmArgs = new ArrayList<>();
        JsonNode jvmArgsNode = meta.path("arguments").path("jvm");
        if (!jvmArgsNode.isMissingNode() && jvmArgsNode.isArray()) {
            jvmArgs.addAll(processArgNodes(jvmArgsNode, vars));
        } else {
            // Formato antiguo (<1.13)
            jvmArgs.add("-Djava.library.path=" + nativesDir.toAbsolutePath());
            jvmArgs.add("-Dminecraft.launcher.brand=FroshyLauncher");
            jvmArgs.add("-Dminecraft.launcher.version=1.0");
            jvmArgs.add("-cp");
            jvmArgs.add(cpStr);
        }

        // ── Game arguments ───────────────────────────────────────────────
        List<String> gameArgs = new ArrayList<>();
        JsonNode gameArgsNode = meta.path("arguments").path("game");
        if (!gameArgsNode.isMissingNode() && gameArgsNode.isArray()) {
            gameArgs.addAll(processArgNodes(gameArgsNode, vars));
        } else if (meta.has("minecraftArguments")) {
            for (String tok : meta.path("minecraftArguments").asText().split(" ")) {
                if (!tok.isBlank()) gameArgs.add(substitute(tok, vars));
            }
        }

        return new VersionInstallation(classpath, mainClass, jvmArgs, gameArgs, nativesDir);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Helpers de argumentos
    // ═══════════════════════════════════════════════════════════════════════

    private List<String> processArgNodes(JsonNode nodes, Map<String, String> vars) {
        List<String> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            if (node.isTextual()) {
                result.add(substitute(node.asText(), vars));
            } else if (node.isObject()) {
                JsonNode rules = node.path("rules");
                // Ignorar nodos con "features" (demo mode, custom resolution) salvo que se soporten
                if (!rules.isMissingNode() && rules.toString().contains("features")) continue;
                if (evaluateRules(rules)) {
                    JsonNode val = node.path("value");
                    if (val.isTextual()) {
                        result.add(substitute(val.asText(), vars));
                    } else if (val.isArray()) {
                        val.forEach(v -> result.add(substitute(v.asText(), vars)));
                    }
                }
            }
        }
        return result;
    }

    private String substitute(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace("${" + e.getKey() + "}", e.getValue());
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Helpers de reglas OS / arch
    // ═══════════════════════════════════════════════════════════════════════

    private boolean evaluateRules(JsonNode rules) {
        if (rules == null || rules.isMissingNode() || rules.isEmpty()) return true;
        boolean allowed = false;
        for (JsonNode rule : rules) {
            String action = rule.path("action").asText("allow");
            boolean match = true;
            JsonNode os = rule.path("os");
            if (!os.isMissingNode()) {
                String reqOs = os.path("name").asText("");
                if (!reqOs.isEmpty() && !reqOs.equals(OS)) match = false;
                // Verificar arquitectura si se especifica
                String reqArch = os.path("arch").asText("");
                if (!reqArch.isEmpty()) {
                    String sysArch = System.getProperty("os.arch", "").toLowerCase();
                    boolean is64 = sysArch.contains("64") || sysArch.contains("aarch64");
                    if ("x86".equals(reqArch) && is64) match = false;
                }
            }
            // Ignorar reglas de "features" (se manejan por separado)
            if (!rule.path("features").isMissingNode()) match = false;
            if (match) allowed = "allow".equals(action);
        }
        return allowed;
    }

    private String getNativeClassifier(JsonNode lib) {
        JsonNode natives = lib.path("natives");
        if (natives.isMissingNode()) return null;
        JsonNode cls = natives.path(OS);
        if (cls.isMissingNode()) return null;
        String arch = System.getProperty("os.arch", "").contains("64") ? "64" : "32";
        return cls.asText().replace("${arch}", arch);
    }

    private String findVersionUrl(JsonNode manifest, String version) throws IOException {
        for (JsonNode v : manifest.path("versions")) {
            if (version.equals(v.path("id").asText())) {
                return v.path("url").asText();
            }
        }
        throw new IOException("Versión de Minecraft no encontrada en el manifest: " + version);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Verificación SHA-1
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Verifica que el archivo exista y coincida con el SHA-1 esperado.
     * Si {@code expectedSha1} está vacío, solo verifica que el archivo tenga un tamaño mínimo.
     */
    private boolean isValidFileSha1(Path file, String expectedSha1) {
        if (!Files.exists(file)) return false;
        if (expectedSha1 == null || expectedSha1.isBlank()) {
            try { return Files.size(file) >= 1000; } catch (IOException e) { return false; }
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) md.update(buf, 0, n);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(40);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString().equalsIgnoreCase(expectedSha1);
        } catch (Exception e) {
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Helpers de descarga HTTP
    // ═══════════════════════════════════════════════════════════════════════

    private JsonNode getJson(URI uri) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "FroshyLauncher/1.0")
                .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300)
            throw new IOException("HTTP " + res.statusCode() + " → " + uri);
        return mapper.readTree(res.body());
    }

    private void downloadWithProgress(URI uri, Path dest, IntConsumer progress)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "FroshyLauncher/1.0")
                .GET().build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() >= 300)
            throw new IOException("HTTP " + res.statusCode() + " → " + uri);

        long total = res.headers().firstValueAsLong("Content-Length").orElse(-1L);
        Path tmp   = dest.resolveSibling(dest.getFileName() + ".part");
        Files.createDirectories(dest.getParent());

        try (InputStream in  = res.body();
             OutputStream out = Files.newOutputStream(tmp,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buf = new byte[16384];
            long read = 0; int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                read += n;
                if (total > 0) progress.accept((int)(read * 100 / total));
            }
        }
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    private void downloadSimple(URI uri, Path dest) throws IOException, InterruptedException {
        Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "FroshyLauncher/1.0")
                .GET().build();
        HttpResponse<Path> res = http.send(req,
                HttpResponse.BodyHandlers.ofFile(tmp,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING));
        if (res.statusCode() >= 300) {
            Files.deleteIfExists(tmp);
            throw new IOException("HTTP " + res.statusCode() + " → " + uri);
        }
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    // ── Extracción de nativos ────────────────────────────────────────────

    /**
     * Extrae archivos de biblioteca nativa (.dll, .so, .dylib, .jnilib) de un JAR
     * al directorio {@code nativesDir}. Siempre sobreescribe para evitar DLLs corruptos.
     */
    private void extractNatives(Path nativeJar, Path nativesDir) throws IOException {
        Files.createDirectories(nativesDir);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(nativeJar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("META-INF/")) { zip.closeEntry(); continue; }
                if (name.endsWith(".dll") || name.endsWith(".so")
                        || name.endsWith(".dylib") || name.endsWith(".jnilib")) {
                    Path target = nativesDir.resolve(Path.of(name).getFileName());
                    // Siempre sobreescribir — evita DLLs corruptos de descargas previas
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    // ── Utilidades ───────────────────────────────────────────────────────

    private boolean isValidFile(Path p, long minSize) {
        try { return Files.exists(p) && Files.size(p) >= minSize; }
        catch (IOException e) { return false; }
    }

    /** Extrae el nombre de archivo final de un path de librería Maven. */
    private String getLibFilename(String relPath) {
        int idx = relPath.lastIndexOf('/');
        return idx >= 0 ? relPath.substring(idx + 1) : relPath;
    }

    private static UUID offlineUUID(String username) {
        return UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    // ── Retry helpers ────────────────────────────────────────────────────

    private <T> T withRetry(String op, ThrowingSupplier<T> s) throws IOException, InterruptedException {
        IOException last = null;
        for (int i = 1; i <= MAX_RETRIES; i++) {
            try { return s.get(); }
            catch (IOException ex) {
                last = ex;
                System.err.println("[retry] " + op + " intento " + i + "/" + MAX_RETRIES + ": " + ex.getMessage());
                if (i < MAX_RETRIES) Thread.sleep(500L * i);
            }
        }
        throw new IOException("Falló " + op + " tras " + MAX_RETRIES + " intentos", last);
    }

    private void withRetryVoid(String op, ThrowingRunnable r) throws IOException, InterruptedException {
        withRetry(op, () -> { r.run(); return null; });
    }

    @FunctionalInterface private interface ThrowingSupplier<T> {
        T get() throws IOException, InterruptedException;
    }
    @FunctionalInterface private interface ThrowingRunnable {
        void run() throws IOException, InterruptedException;
    }
}
