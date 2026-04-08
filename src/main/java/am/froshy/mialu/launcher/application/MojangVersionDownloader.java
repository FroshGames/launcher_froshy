package am.froshy.mialu.launcher.application;

import am.froshy.mialu.launcher.config.LauncherVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Encargado de traer al equipo todas las dependencias necesarias de Mojang para que el juego arranque,
 * incluyendo: Metadata Versions (JSONs), archivo principal Client.JAR, Bibliotecas nativas (Libraries) y Activos de Sonido/Modelos (Assets).
 * Utiliza descarga en concurrencia masiva (Thread Pooling) para agilizar este proceso.
 */
public final class MojangVersionDownloader implements MinecraftVersionDownloader {

    private static final URI VERSION_MANIFEST_URI =
            URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final String MOJANG_PROFILE_API = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String ASSETS_BASE = "https://resources.download.minecraft.net/";
    private static final int MAX_RETRIES = 5;
    private static final int LIB_DOWNLOAD_THREADS = 16;
    private static final int ASSET_DOWNLOAD_THREADS = 32;

    // OS detection ──────────────────────────────────────────────────────────
    private static final String OS;
    static {
        String name = System.getProperty("os.name", "").toLowerCase();
        if      (name.contains("win"))                     OS = "windows";
        else if (name.contains("mac") || name.contains("darwin")) OS = "osx";
        else                                               OS = "linux";
    }

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final PremiumUuidResolver premiumUuidResolver;

    public MojangVersionDownloader() {
        this(null);
    }

    MojangVersionDownloader(PremiumUuidResolver premiumUuidResolver) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        this.mapper = new ObjectMapper();
        this.premiumUuidResolver = premiumUuidResolver != null
                ? premiumUuidResolver
                : this::resolvePremiumUuidFromMojang;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  downloadVersion — descarga todo lo necesario
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void downloadVersion(String version, Path gameDir, BiConsumer<Integer, String> progress)
            throws IOException, InterruptedException {

        // ── Fase 1: manifest + version JSON (0→5 %) ──────────────────────
        progress.accept(0, "Conectando con servidores de Mojang...");
        JsonNode manifest = withRetry("manifest", () -> getJson(VERSION_MANIFEST_URI));
        String versionUrl = findVersionUrl(manifest, version);
        progress.accept(2, "Obteniendo metadatos de version " + version + "...");

        Path versionDir  = gameDir.resolve("versions").resolve(version);
        Path versionJson = versionDir.resolve(version + ".json");
        Files.createDirectories(versionDir);

        JsonNode meta;
        if (Files.exists(versionJson)) {
            meta = mapper.readTree(versionJson.toFile());
        } else {
            meta = withRetry("meta-" + version, () -> getJson(URI.create(versionUrl)));
            mapper.writeValue(versionJson.toFile(), meta);
        }
        progress.accept(5, "Metadatos obtenidos. Descargando JAR del cliente...");

        // ── Fase 2: client JAR (5→35 %) ─────────────────────────────────
        Path clientJar = versionDir.resolve(version + ".jar");
        JsonNode clientNode = meta.path("downloads").path("client");
        String jarUrl = clientNode.path("url").asText();
        if (jarUrl.isBlank()) {
            throw new IOException("Metadatos incompletos para client.jar de " + version);
        }
        long jarSize = clientNode.path("size").asLong(-1L);
        String jarSha1 = textOrNull(clientNode.path("sha1"));
        if (!isValidFile(clientJar, Math.max(1000, jarSize), jarSha1)) {
            withRetryVoid("client-jar", () ->
                    downloadWithProgress(URI.create(jarUrl), clientJar,
                            p -> progress.accept(5 + p * 30 / 100, "Descargando JAR del cliente... " + (5 + p * 30 / 100) + "%"),
                            jarSha1, jarSize));
        }
        progress.accept(35, "JAR del cliente listo. Descargando librerias...");

        // ── Fase 3: librerías (35→68 %) ──────────────────────────────────
        JsonNode libs = meta.path("libraries");
        Path nativesDir = versionDir.resolve("natives");
        Files.createDirectories(nativesDir);
        downloadLibraries(libs, gameDir, nativesDir, progress, 35, 68);

        // ── Fase 4: asset index (68→72 %) ────────────────────────────────
        progress.accept(68, "Descargando indice de assets...");
        String assetIndexId  = meta.path("assetIndex").path("id").asText("legacy");
        String assetIndexUrl = meta.path("assetIndex").path("url").asText();
        Path indexFile = gameDir.resolve("assets").resolve("indexes")
                .resolve(assetIndexId + ".json");
        Files.createDirectories(indexFile.getParent());
        if (!Files.exists(indexFile)) {
            JsonNode indexData = withRetry("asset-index", () -> getJson(URI.create(assetIndexUrl)));
            mapper.writeValue(indexFile.toFile(), indexData);
        }
        progress.accept(72, "Indice de assets listo. Descargando recursos del juego...");

        // ── Fase 5: asset objects (72→100 %) ─────────────────────────────
        JsonNode indexData = mapper.readTree(indexFile.toFile());
        downloadAssets(indexData, gameDir, progress, 72, 100);

        progress.accept(100, "Descarga completada. Listo para jugar!");
    }

    // ── Descarga de librerías ────────────────────────────────────────────

    private void downloadLibraries(JsonNode libs, Path gameDir, Path nativesDir,
            BiConsumer<Integer, String> progress, int lo, int hi) throws IOException, InterruptedException {
        List<DownloadTask> tasks = new ArrayList<>();
        List<Path> nativeArchives = new ArrayList<>();
        int totalUnits = 0;
        int readyUnits = 0;

        for (JsonNode lib : libs) {
            if (!evaluateRules(lib.path("rules"))) continue;

            // Artifact principal → classpath
            JsonNode artifact = lib.path("downloads").path("artifact");
            if (!artifact.isMissingNode() && artifact.has("url")) {
                totalUnits++;
                String relPath = artifact.path("path").asText();
                Path dest = gameDir.resolve("libraries").resolve(relPath);
                long size = artifact.path("size").asLong(-1L);
                String sha1 = textOrNull(artifact.path("sha1"));
                if (!isValidFile(dest, Math.max(4, size), sha1)) {
                    tasks.add(new DownloadTask("lib-" + relPath, URI.create(artifact.path("url").asText()), dest, sha1, size));
                } else {
                    readyUnits++;
                }
            }

            // Natives classifier → extraer a nativesDir
            String nativeKey = getNativeClassifier(lib);
            if (nativeKey != null) {
                JsonNode natArt = lib.path("downloads").path("classifiers").path(nativeKey);
                if (!natArt.isMissingNode() && natArt.has("url")) {
                    totalUnits++;
                    String relPath = natArt.path("path").asText();
                    Path dest = gameDir.resolve("libraries").resolve(relPath);
                    long size = natArt.path("size").asLong(-1L);
                    String sha1 = textOrNull(natArt.path("sha1"));
                    if (!isValidFile(dest, Math.max(4, size), sha1)) {
                        tasks.add(new DownloadTask("native-" + relPath, URI.create(natArt.path("url").asText()), dest, sha1, size));
                    } else {
                        readyUnits++;
                        nativeArchives.add(dest);
                    }
                }
            }
        }

        if (totalUnits == 0) {
            progress.accept(hi, "Sin librerias que descargar.");
            return;
        }

        int initPct = lo + (int) ((long) readyUnits * (hi - lo) / totalUnits);
        progress.accept(initPct, "Librerias: " + readyUnits + "/" + totalUnits + " (" + initPct + "%)");

        int initialReady = readyUnits;
        int totalCount = totalUnits;
        downloadTasks(tasks, Math.min(LIB_DOWNLOAD_THREADS, tasks.size()), (d, t) -> {
            int current = initialReady + d;
            int pct = lo + (int) ((long) current * (hi - lo) / totalCount);
            progress.accept(pct, "Librerias: " + current + "/" + totalCount + " (" + pct + "%)");
        });

        for (DownloadTask task : tasks) {
            if (task.id().startsWith("native-")) {
                nativeArchives.add(task.dest());
            }
        }
        for (Path nativeArchive : nativeArchives) {
            if (isValidFile(nativeArchive, 4)) {
                extractNatives(nativeArchive, nativesDir);
            }
        }

        progress.accept(hi, "Librerias completas.");
    }

    // ── Descarga de assets ───────────────────────────────────────────────

    private void downloadAssets(JsonNode indexData, Path gameDir,
            BiConsumer<Integer, String> progress, int lo, int hi) throws IOException, InterruptedException {
        JsonNode objects = indexData.path("objects");
        List<DownloadTask> pending = new ArrayList<>();
        objects.fields().forEachRemaining(e -> {
            String hash = e.getValue().path("hash").asText();
            String prefix = hash.substring(0, 2);
            Path dest = gameDir.resolve("assets").resolve("objects")
                    .resolve(prefix).resolve(hash);
            long size = e.getValue().path("size").asLong(-1L);
            if (!isValidFile(dest, Math.max(1, size), hash)) {
                pending.add(new DownloadTask("asset-" + hash,
                        URI.create(ASSETS_BASE + prefix + "/" + hash), dest, hash, size));
            }
        });

        int total      = objects.size();
        int preDone    = total - pending.size();
        AtomicInteger done = new AtomicInteger(preDone);
        int initPct = lo + (int)((long) preDone * (hi - lo) / Math.max(total, 1));
        progress.accept(initPct, "Assets: " + preDone + "/" + total + " ya descargados.");

        if (pending.isEmpty()) { progress.accept(hi, "Todos los assets presentes."); return; }

        downloadTasks(pending, Math.min(ASSET_DOWNLOAD_THREADS, pending.size()), (d, t) -> {
            int current = preDone + d;
            done.set(current);
            int pct = lo + (int) ((long) current * (hi - lo) / total);
            progress.accept(pct, "Assets: " + current + "/" + total + " (" + pct + "%)");
        });

        progress.accept(hi, "Assets descargados: " + total + "/" + total);
    }

    private record DownloadTask(String id, URI uri, Path dest, String sha1, long size) {}

    // ═══════════════════════════════════════════════════════════════════════
    //  buildInstallation — construye los argumentos de lanzamiento
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public VersionInstallation buildInstallation(String version, Path gameDir, String username)
            throws IOException {

        ResolvedVersionData resolved = resolveVersionData(version, gameDir, new HashSet<>());
        JsonNode meta = resolved.meta();
        Path versionDir = gameDir.resolve("versions").resolve(version);

        // ── Classpath: librerías + client JAR cuando corresponda ─────────
        Set<Path> classpath = new LinkedHashSet<>();
        for (JsonNode lib : meta.path("libraries")) {
            if (!evaluateRules(lib.path("rules"))) continue;
            JsonNode artifact = lib.path("downloads").path("artifact");
            if (!artifact.isMissingNode()) {
                Path libFile = gameDir.resolve("libraries")
                        .resolve(artifact.path("path").asText());
                if (Files.exists(libFile)) classpath.add(libFile.toAbsolutePath().normalize());
            }
        }
        if (shouldIncludeClientJar(meta, gameDir)) {
            String clientJarVersion = resolved.clientJarVersion();
            Path clientJar = gameDir.resolve("versions").resolve(clientJarVersion).resolve(clientJarVersion + ".jar");
            if (!Files.exists(clientJar)) {
                clientJar = versionDir.resolve(version + ".jar");
            }
            if (Files.exists(clientJar)) {
                classpath.add(clientJar.toAbsolutePath().normalize());
            }
        }

        // ── Variables de sustitución ─────────────────────────────────────
        String mainClass     = meta.path("mainClass").asText();
        String assetIndexId  = meta.path("assetIndex").path("id").asText("legacy");
        Path   assetsDir     = gameDir.resolve("assets");
        Path   librariesDir  = gameDir.resolve("libraries");
        Path   nativesDir    = versionDir.resolve("natives");

        List<Path> classpathEntries = new ArrayList<>(classpath);

        String cpStr = classpathEntries.stream()
                .map(p -> p.toAbsolutePath().toString())
                .collect(java.util.stream.Collectors.joining(File.pathSeparator));

        UUID resolvedUuid = offlineUUID(username);
        try {
            UUID premiumUuid = premiumUuidResolver.resolve(username);
            if (premiumUuid != null) {
                resolvedUuid = premiumUuid;
            }
        } catch (Exception ignored) {
            // No bloqueamos el lanzamiento si Mojang no responde.
        }

        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name",    username);
        vars.put("version_name",        version);
        vars.put("game_directory",      gameDir.toAbsolutePath().toString());
        vars.put("assets_root",         assetsDir.toAbsolutePath().toString());
        vars.put("assets_index_name",   assetIndexId);
        vars.put("game_assets",         assetsDir.toAbsolutePath().toString());
        vars.put("auth_uuid",           resolvedUuid.toString());
        vars.put("auth_access_token",   "0");
        vars.put("auth_session",        "0");
        vars.put("clientid",            "0");
        vars.put("auth_xuid",           "0");
        vars.put("user_type",           "offline");
        vars.put("user_properties",     "{}");
        vars.put("profile_name",        username);
        vars.put("version_type",        meta.path("type").asText("release"));
        vars.put("launcher_name",       "Launcher_Mialu");
        vars.put("launcher_version",    LauncherVersion.get());
        vars.put("natives_directory",   nativesDir.toAbsolutePath().toString());
        vars.put("library_directory",   librariesDir.toAbsolutePath().toString());
        vars.put("classpath_separator", File.pathSeparator);
        vars.put("classpath",           cpStr);
        vars.put("resolution_width",    "854");
        vars.put("resolution_height",   "480");

        // ── JVM arguments ────────────────────────────────────────────────
        List<String> jvmArgs = new ArrayList<>();
        JsonNode jvmArgsNode = meta.path("arguments").path("jvm");
        if (!jvmArgsNode.isMissingNode() && jvmArgsNode.isArray()) {
            jvmArgs.addAll(processArgNodes(jvmArgsNode, vars));
        } else {
            // Formato antiguo (<1.13): argumentos mínimos
            jvmArgs.add("-Djava.library.path=" + nativesDir.toAbsolutePath());
            jvmArgs.add("-Dminecraft.launcher.brand=Launcher_Mialu");
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

        return new VersionInstallation(classpathEntries, mainClass, jvmArgs, gameArgs, nativesDir);
    }

    private boolean shouldIncludeClientJar(JsonNode meta, Path gameDir) {
        return !usesLibraryManagedMinecraftClient(meta, gameDir);
    }

    private boolean usesLibraryManagedMinecraftClient(JsonNode meta, Path gameDir) {
        if (!"cpw.mods.bootstraplauncher.BootstrapLauncher".equals(meta.path("mainClass").asText(""))) {
            return false;
        }

        JsonNode jvmArgs = meta.path("arguments").path("jvm");
        boolean forgeLikeBootstrap = jvmArgs.isArray() && processArgNodes(jvmArgs, Map.of()).stream()
                .anyMatch(arg -> arg.startsWith("-DignoreList=") && arg.contains("client-extra"));
        if (!forgeLikeBootstrap) {
            return false;
        }

        Path minecraftClientLibs = gameDir.resolve("libraries").resolve("net").resolve("minecraft").resolve("client");
        if (!Files.isDirectory(minecraftClientLibs)) {
            return false;
        }

        try (java.util.stream.Stream<Path> walk = Files.walk(minecraftClientLibs, 3)) {
            return walk
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .anyMatch(name -> name.endsWith("-srg.jar") || name.endsWith("-slim.jar") || name.endsWith("-extra.jar"));
        } catch (IOException ex) {
            return false;
        }
    }

    private ResolvedVersionData resolveVersionData(String version, Path gameDir, Set<String> visiting)
            throws IOException {
        if (!visiting.add(version)) {
            throw new IOException("Cadena de herencia circular detectada en version: " + version);
        }

        Path versionJson = gameDir.resolve("versions").resolve(version).resolve(version + ".json");
        if (!Files.exists(versionJson)) {
            throw new IOException("Version " + version + " no instalada. Falta: " + versionJson);
        }

        ObjectNode current = (ObjectNode) mapper.readTree(versionJson.toFile());
        String parentId = current.path("inheritsFrom").asText("");

        if (parentId.isBlank()) {
            visiting.remove(version);
            return new ResolvedVersionData(current, findClientJarVersion(version, gameDir, new HashSet<>()));
        }

        ResolvedVersionData parent = resolveVersionData(parentId, gameDir, visiting);
        ObjectNode merged = mergeVersionMeta((ObjectNode) parent.meta(), current);
        visiting.remove(version);
        return new ResolvedVersionData(merged, findClientJarVersion(version, gameDir, new HashSet<>()));
    }

    private String findClientJarVersion(String version, Path gameDir, Set<String> visiting) throws IOException {
        if (!visiting.add(version)) {
            throw new IOException("Cadena de herencia circular al resolver JAR cliente: " + version);
        }

        Path versionDir = gameDir.resolve("versions").resolve(version);
        Path jar = versionDir.resolve(version + ".jar");
        Path json = versionDir.resolve(version + ".json");
        if (!Files.exists(json)) {
            throw new IOException("Version " + version + " no instalada. Falta: " + json);
        }

        ObjectNode meta = (ObjectNode) mapper.readTree(json.toFile());
        if (Files.exists(jar) || meta.path("downloads").path("client").has("url")) {
            return version;
        }

        String parent = meta.path("inheritsFrom").asText("");
        if (parent.isBlank()) {
            return version;
        }

        return findClientJarVersion(parent, gameDir, visiting);
    }

    private ObjectNode mergeVersionMeta(ObjectNode parent, ObjectNode child) {
        ObjectNode merged = parent.deepCopy();

        child.fields().forEachRemaining(entry -> {
            String field = entry.getKey();
            if ("libraries".equals(field) || "arguments".equals(field)) {
                return;
            }
            merged.set(field, entry.getValue());
        });

        ArrayNode mergedLibraries = mapper.createArrayNode();
        JsonNode parentLibraries = parent.path("libraries");
        if (parentLibraries.isArray()) {
            parentLibraries.forEach(mergedLibraries::add);
        }
        JsonNode childLibraries = child.path("libraries");
        if (childLibraries.isArray()) {
            childLibraries.forEach(mergedLibraries::add);
        }
        merged.set("libraries", mergedLibraries);

        ObjectNode mergedArguments = mapper.createObjectNode();
        JsonNode parentArgs = parent.path("arguments");
        JsonNode childArgs = child.path("arguments");
        mergeArgumentArray(parentArgs, childArgs, mergedArguments, "jvm");
        mergeArgumentArray(parentArgs, childArgs, mergedArguments, "game");
        if (!mergedArguments.isEmpty()) {
            merged.set("arguments", mergedArguments);
        }

        return merged;
    }

    private void mergeArgumentArray(JsonNode parentArgs, JsonNode childArgs, ObjectNode mergedArguments, String key) {
        ArrayNode merged = mapper.createArrayNode();

        if (parentArgs.isObject() && parentArgs.path(key).isArray()) {
            parentArgs.path(key).forEach(merged::add);
        }
        if (childArgs.isObject() && childArgs.path(key).isArray()) {
            childArgs.path(key).forEach(merged::add);
        }

        if (!merged.isEmpty()) {
            mergedArguments.set(key, merged);
        }
    }

    private record ResolvedVersionData(JsonNode meta, String clientJarVersion) {}


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
                // Ignorar nodos con "features" (demo, custom resolution) excepto si los soportamos
                if (!node.path("rules").isMissingNode()
                        && node.path("rules").toString().contains("features")) {
                    continue;
                }
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
    //  Helpers de reglas OS
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
        throw new IOException("Version de Minecraft no encontrada en manifest: " + version);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Helpers de descarga HTTP
    // ═══════════════════════════════════════════════════════════════════════

    private JsonNode getJson(URI uri) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Launcher_Mialu/1.0")
                .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300)
            throw new IOException("HTTP " + res.statusCode() + " → " + uri);
        return mapper.readTree(res.body());
    }

    private void downloadWithProgress(URI uri, Path dest, java.util.function.IntConsumer progress,
            String expectedSha1, long expectedSize) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "Launcher_Mialu/1.0")
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
        verifyDownloadedFile(tmp, expectedSha1, expectedSize, uri);
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    private void downloadSimple(URI uri, Path dest, String expectedSha1, long expectedSize)
            throws IOException, InterruptedException {
        Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "Launcher_Mialu/1.0")
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
        verifyDownloadedFile(tmp, expectedSha1, expectedSize, uri);
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    private void verifyDownloadedFile(Path file, String expectedSha1, long expectedSize, URI uri) throws IOException {
        if (expectedSize > 0 && Files.size(file) != expectedSize) {
            throw new IOException("Tamano invalido para " + uri + " (esperado=" + expectedSize
                    + ", real=" + Files.size(file) + ")");
        }
        if (expectedSha1 != null && !expectedSha1.isBlank()) {
            String actual = sha1Hex(file);
            if (!expectedSha1.equalsIgnoreCase(actual)) {
                throw new IOException("SHA1 invalido para " + uri + " (esperado=" + expectedSha1
                        + ", real=" + actual + ")");
            }
        }
    }

    private void downloadTasks(List<DownloadTask> tasks, int threads,
            BiConsumer<Integer, Integer> onProgress) throws IOException, InterruptedException {
        if (tasks.isEmpty()) return;

        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, threads));
        CompletionService<Void> completion = new ExecutorCompletionService<>(pool);
        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();

        for (DownloadTask task : tasks) {
            completion.submit(() -> {
                Files.createDirectories(task.dest().getParent());
                withRetryVoid(task.id(), () -> downloadSimple(task.uri(), task.dest(), task.sha1(), task.size()));
                return null;
            });
        }

        try {
            for (int i = 1; i <= tasks.size(); i++) {
                try {
                    completion.take().get();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    failures.add(cause.getMessage());
                }
                onProgress.accept(i, tasks.size());
            }
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(60, TimeUnit.MINUTES)) {
                pool.shutdownNow();
            }
        }

        if (!failures.isEmpty()) {
            String detail = failures.stream().limit(3).collect(java.util.stream.Collectors.joining(" | "));
            throw new IOException("Descargas incompletas (" + failures.size() + " fallos): " + detail);
        }
    }

    // ── Extracción de nativos ────────────────────────────────────────────

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
                    if (!Files.exists(target)) {
                        Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                    }
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

    private boolean isValidFile(Path p, long minSize, String expectedSha1) {
        try {
            if (!Files.exists(p) || Files.size(p) < minSize) return false;
            if (expectedSha1 == null || expectedSha1.isBlank()) return true;
            return expectedSha1.equalsIgnoreCase(sha1Hex(p));
        } catch (IOException e) {
            return false;
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode()) return null;
        String value = node.asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private String sha1Hex(Path file) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 no disponible", ex);
        }

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[16_384];
            int n;
            while ((n = in.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
        }
        return toHex(md.digest());
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static UUID offlineUUID(String username) {
        return UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    private UUID resolvePremiumUuidFromMojang(String username) throws IOException, InterruptedException {
        if (username == null || !username.matches("^[A-Za-z0-9_]{3,16}$")) {
            return null;
        }

        String encodedUser = URLEncoder.encode(username, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MOJANG_PROFILE_API + encodedUser))
                .timeout(Duration.ofSeconds(4))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
            return null;
        }

        JsonNode payload = mapper.readTree(response.body());
        String rawId = payload.path("id").asText("").trim();
        if (!rawId.matches("^[0-9a-fA-F]{32}$")) {
            return null;
        }

        String hyphenated = rawId.substring(0, 8) + "-"
                + rawId.substring(8, 12) + "-"
                + rawId.substring(12, 16) + "-"
                + rawId.substring(16, 20) + "-"
                + rawId.substring(20);
        return UUID.fromString(hyphenated);
    }

    @FunctionalInterface
    interface PremiumUuidResolver {
        UUID resolve(String username) throws IOException, InterruptedException;
    }

    // ── Retry helpers ────────────────────────────────────────────────────

    private <T> T withRetry(String op, ThrowingSupplier<T> s) throws IOException, InterruptedException {
        IOException last = null;
        for (int i = 1; i <= MAX_RETRIES; i++) {
            try { return s.get(); }
            catch (IOException ex) {
                last = ex;
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



