package am.froshy.launcher.application;

import am.froshy.launcher.domain.ModpackFile;
import am.froshy.launcher.domain.ModpackManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
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
 * Parsea e instala modpacks en formato Modrinth (.mrpack) y CurseForge (.zip),
 * e instala automáticamente los loaders Forge / NeoForge / Fabric / Quilt.
 */
public final class ModpackInstaller {

    private static final String CURSE_API  = "https://api.curse.tools/v1/cf";
    private static final int    MOD_THREADS = 8;
    private static final int    MAX_RETRIES = 3;

    private final HttpClient   http;
    private final ObjectMapper mapper;

    public ModpackInstaller() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        this.mapper = new ObjectMapper();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PARSEO
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Parsea un modpack (.mrpack o .zip con manifest.json CurseForge).
     * No descarga nada; sólo devuelve los metadatos y la lista de archivos.
     */
    public ModpackManifest parseModpack(Path zipFile) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = normalizeZipEntryName(entry.getName());
                if (isEntryNamed(name, "modrinth.index.json")) {
                    return parseModrinth(mapper.readTree(zip.readAllBytes()));
                }
                if (isEntryNamed(name, "manifest.json")) {
                    return parseCurseForge(mapper.readTree(zip.readAllBytes()));
                }
                zip.closeEntry();
            }
        }
        throw new IOException("Formato de modpack no reconocido. " +
                "Se esperaba modrinth.index.json (Modrinth) o manifest.json (CurseForge).");
    }

    private ModpackManifest parseModrinth(JsonNode root) {
        String name        = root.path("name").asText("Modpack");
        String packVersion = root.path("versionId").asText("1.0.0");
        JsonNode deps      = root.path("dependencies");
        String mcVersion   = deps.path("minecraft").asText("1.20.1");

        String loaderType    = "VANILLA";
        String loaderVersion = "";
        if (deps.has("fabric-loader")) { loaderType = "FABRIC";   loaderVersion = deps.path("fabric-loader").asText(); }
        else if (deps.has("forge"))    { loaderType = "FORGE";    loaderVersion = deps.path("forge").asText(); }
        else if (deps.has("neoforge")) { loaderType = "NEOFORGE"; loaderVersion = deps.path("neoforge").asText(); }
        else if (deps.has("quilt-loader")) { loaderType = "QUILT"; loaderVersion = deps.path("quilt-loader").asText(); }

        List<ModpackFile> files = new ArrayList<>();
        for (JsonNode f : root.path("files")) {
            String clientEnv = f.path("env").path("client").asText("required");
            if ("unsupported".equals(clientEnv)) continue;
            boolean optional = !"required".equals(clientEnv);

            String path = f.path("path").asText();
            String url  = f.path("downloads").isArray() && f.path("downloads").size() > 0
                    ? f.path("downloads").get(0).asText() : "";
            String sha512 = f.path("hashes").path("sha512").asText(null);
            String sha1   = f.path("hashes").path("sha1").asText(null);
            long   size   = f.path("fileSize").asLong(-1L);
            files.add(new ModpackFile(path, url, sha1, sha512, size, !optional, optional));
        }
        return new ModpackManifest(name, packVersion, mcVersion, loaderType, loaderVersion, files, "MODRINTH");
    }

    private ModpackManifest parseCurseForge(JsonNode root) {
        String name        = root.path("name").asText("Modpack");
        String packVersion = root.path("version").asText("1.0.0");
        JsonNode mc        = root.path("minecraft");
        String mcVersion   = mc.path("version").asText("1.20.1");

        String loaderType = "VANILLA", loaderVersion = "";
        for (JsonNode loader : mc.path("modLoaders")) {
            String id = loader.path("id").asText("");
            if (id.startsWith("forge-"))    { loaderType = "FORGE";    loaderVersion = id.substring(6);  break; }
            if (id.startsWith("neoforge-")) { loaderType = "NEOFORGE"; loaderVersion = id.substring(9);  break; }
            if (id.startsWith("fabric-"))   { loaderType = "FABRIC";   loaderVersion = id.substring(7);  break; }
            if (id.startsWith("quilt-"))    { loaderType = "QUILT";    loaderVersion = id.substring(6);  break; }
        }

        List<ModpackFile> files = new ArrayList<>();
        for (JsonNode f : root.path("files")) {
            int projectId = f.path("projectID").asInt();
            int fileId    = f.path("fileID").asInt();
            boolean req   = f.path("required").asBoolean(true);
            // URL marcada como cursor:// para resolver después
            files.add(new ModpackFile(
                    "mods/cf_" + projectId + "_" + fileId + ".jar",
                    "curse://" + projectId + "/" + fileId,
                    null, -1L, req));
        }
        return new ModpackManifest(name, packVersion, mcVersion, loaderType, loaderVersion, files, "CURSEFORGE");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INSTALACIÓN DE MODS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Extrae overrides y descarga todos los mods del modpack.
     *
     * @param zipFile  ruta al archivo del modpack
     * @param manifest manifiesto parseado
     * @param gameDir  directorio raíz del juego (instancia)
     * @param progress callback (0-100, mensaje)
     */
    public void installModpackFiles(Path zipFile, ModpackManifest manifest, Path gameDir,
            BiConsumer<Integer, String> progress) throws IOException, InterruptedException {

        progress.accept(0, "Limpiando mods previos para evitar conflictos...");
        purgeModsDirectory(gameDir);

        progress.accept(0, "Extrayendo archivos base del modpack...");
        extractOverrides(zipFile, gameDir, manifest.source());

        List<ModpackFile> files = manifest.files();
        if ("CURSEFORGE".equals(manifest.source())) {
            progress.accept(5, "Resolviendo URLs de mods desde CurseForge...");
            files = resolveCurseForgeUrls(files, progress);
        }

        int total = files.size();
        if (total == 0) { progress.accept(100, "Sin mods que descargar."); return; }

        AtomicInteger ready = new AtomicInteger(0);
        List<DownloadItem> tasks = new ArrayList<>();
        for (ModpackFile file : files) {
            if (file.downloadUrl() == null || file.downloadUrl().isBlank()
                    || file.downloadUrl().startsWith("curse://")) {
                ready.incrementAndGet();
                continue;
            }
            Path dest = gameDir.resolve(file.path().replace("/", File.separator));
            if (isValidFile(dest, file.size())) { ready.incrementAndGet(); continue; }
            tasks.add(new DownloadItem(file.downloadUrl(), dest, file.sha1(), file.sha512(), file.size()));
        }

        int preDone = ready.get();
        int initPct = 10 + preDone * 85 / Math.max(total, 1);
        progress.accept(initPct, "Mods: " + preDone + "/" + total + " ya presentes.");

        downloadMods(tasks, total, preDone, progress);
        progress.accept(100, "Modpack instalado: " + manifest.name());
    }

    private void purgeModsDirectory(Path gameDir) throws IOException {
        Path modsDir = gameDir.resolve("mods");
        if (!Files.isDirectory(modsDir)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(modsDir)) {
            walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // Si un archivo está bloqueado, se reintentará en el siguiente ciclo.
                        }
                    });
        }
    }

    private void extractOverrides(Path zipFile, Path gameDir, String source) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String relative = resolveOverridesRelativePath(entry.getName());
                if (relative != null && !entry.isDirectory()) {
                    if (relative.isBlank()) { zip.closeEntry(); continue; }
                    Path dest = gameDir.resolve(relative.replace("/", File.separator));
                    Files.createDirectories(dest.getParent());
                    Files.copy(zip, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private String normalizeZipEntryName(String entryName) {
        return entryName == null ? "" : entryName.replace('\\', '/');
    }

    private boolean isEntryNamed(String entryName, String expectedName) {
        String normalized = normalizeZipEntryName(entryName);
        String expected = expectedName.toLowerCase();
        String lower = normalized.toLowerCase();
        return lower.equals(expected) || lower.endsWith("/" + expected);
    }

    private String resolveOverridesRelativePath(String entryName) {
        String normalized = normalizeZipEntryName(entryName);
        String lower = normalized.toLowerCase();
        for (String marker : List.of("overrides/", "client-overrides/")) {
            if (lower.startsWith(marker)) {
                return normalized.substring(marker.length());
            }
            int nested = lower.indexOf("/" + marker);
            if (nested >= 0) {
                return normalized.substring(nested + marker.length() + 1);
            }
        }
        return null;
    }

    private List<ModpackFile> resolveCurseForgeUrls(List<ModpackFile> files,
            BiConsumer<Integer, String> progress) {
        List<ModpackFile> resolved = new ArrayList<>();
        int total = files.size(), done = 0;
        for (ModpackFile f : files) {
            if (!f.downloadUrl().startsWith("curse://")) { resolved.add(f); done++; continue; }
            String[] parts = f.downloadUrl().substring(8).split("/");
            int projectId = Integer.parseInt(parts[0]);
            int fileId    = Integer.parseInt(parts[1]);
            try {
                JsonNode info     = getJson(URI.create(CURSE_API + "/mods/" + projectId + "/files/" + fileId));
                String url        = info.path("data").path("downloadUrl").asText("");
                String fileName   = info.path("data").path("fileName").asText(projectId + "_" + fileId + ".jar");
                if (url.isBlank()) {
                    url = "https://edge.forgecdn.net/files/" + (fileId / 1000) + "/" + (fileId % 1000) + "/" + fileName;
                }
                resolved.add(new ModpackFile("mods/" + fileName, url, null, -1L, f.required()));
            } catch (Exception ex) {
                System.err.println("[modpack] No se pudo resolver " + projectId + "/" + fileId + ": " + ex.getMessage());
                // Mantiene curse:// → será saltado al descargar
                resolved.add(f);
            }
            done++;
            progress.accept(done * 5 / Math.max(total, 1),
                    "Resolviendo mods CurseForge: " + done + "/" + total);
        }
        return resolved;
    }

    private void downloadMods(List<DownloadItem> tasks, int totalMods, int preDone,
            BiConsumer<Integer, String> progress) throws IOException, InterruptedException {
        if (tasks.isEmpty()) return;

        ExecutorService pool        = Executors.newFixedThreadPool(Math.min(MOD_THREADS, tasks.size()));
        CompletionService<Void> cs  = new ExecutorCompletionService<>(pool);
        List<String> failures       = new ArrayList<>();
        AtomicInteger done          = new AtomicInteger(preDone);

        for (DownloadItem task : tasks) {
            cs.submit(() -> {
                Files.createDirectories(task.dest().getParent());
                downloadWithRetry(task.url(), task.dest());
                return null;
            });
        }

        try {
            for (int i = 0; i < tasks.size(); i++) {
                try {
                    cs.take().get();
                } catch (ExecutionException ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    failures.add(msg);
                }
                int current = done.incrementAndGet();
                int pct = 10 + current * 85 / Math.max(totalMods, 1);
                progress.accept(pct, "Mods: " + current + "/" + totalMods + " (" + pct + "%)");
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.MINUTES);
        }

        if (!failures.isEmpty()) {
            String detail = failures.stream().limit(3)
                    .collect(java.util.stream.Collectors.joining("; "));
            throw new IOException(failures.size() + " mods no se pudieron descargar: " + detail);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INSTALACIÓN DE LOADERS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Instala el loader correspondiente y devuelve el versionId de lanzamiento.
     * Si ya está instalado, devuelve directamente el versionId sin repetir.
     */
    public String installLoader(String loaderType, String mcVersion, String loaderVersion,
            Path gameDir, BiConsumer<Integer, String> progress)
            throws IOException, InterruptedException {
        return switch (loaderType.toUpperCase()) {
            case "FORGE"    -> installForge(mcVersion, loaderVersion, gameDir, progress);
            case "NEOFORGE" -> installNeoForge(mcVersion, loaderVersion, gameDir, progress);
            case "FABRIC"   -> installFabric(mcVersion, loaderVersion, gameDir, progress);
            case "QUILT"    -> installQuilt(mcVersion, loaderVersion, gameDir, progress);
            default         -> mcVersion; // VANILLA
        };
    }

    public String installForge(String mcVersion, String forgeVersion, Path gameDir,
            BiConsumer<Integer, String> progress) throws IOException, InterruptedException {
        String versionId  = mcVersion + "-forge-" + forgeVersion;
        Path   versionDir = gameDir.resolve("versions").resolve(versionId);
        if (Files.exists(versionDir.resolve(versionId + ".json"))) {
            progress.accept(100, "Forge " + forgeVersion + " ya instalado.");
            return versionId;
        }

        String url = "https://maven.minecraftforge.net/net/minecraftforge/forge/"
                + mcVersion + "-" + forgeVersion
                + "/forge-" + mcVersion + "-" + forgeVersion + "-installer.jar";
        Path installer = gameDir.resolve("installers")
                .resolve("forge-" + mcVersion + "-" + forgeVersion + "-installer.jar");
        Files.createDirectories(installer.getParent());

        if (!Files.exists(installer)) {
            progress.accept(5, "Descargando Forge installer " + forgeVersion + "...");
            downloadWithRetry(url, installer);
        }

        progress.accept(20, "Instalando Forge " + forgeVersion + "...");
        ensureLauncherProfiles(gameDir);
        runInstaller(installer, gameDir, progress, 20, 95);

        if (!Files.exists(versionDir.resolve(versionId + ".json"))) {
            throw new IOException("Forge no se instaló correctamente en " + versionDir);
        }
        progress.accept(100, "Forge " + forgeVersion + " instalado.");
        return versionId;
    }

    public String installNeoForge(String mcVersion, String neoVersion, Path gameDir,
            BiConsumer<Integer, String> progress) throws IOException, InterruptedException {
        // NeoForge 1.20.2+ → versionId: "neoforge-{ver}"
        // NeoForge 1.20.1  → versionId: "{mcVer}-forge-{ver}" (heredado de Forge)
        boolean isLegacy  = mcVersion.equals("1.20.1");
        String  versionId = isLegacy ? (mcVersion + "-forge-" + neoVersion) : ("neoforge-" + neoVersion);
        Path    versionDir = gameDir.resolve("versions").resolve(versionId);
        if (Files.exists(versionDir.resolve(versionId + ".json"))) {
            progress.accept(100, "NeoForge " + neoVersion + " ya instalado.");
            return versionId;
        }

        String url = isLegacy
                ? "https://maven.neoforged.net/releases/net/neoforged/forge/"
                        + neoVersion + "/forge-" + neoVersion + "-installer.jar"
                : "https://maven.neoforged.net/releases/net/neoforged/neoforge/"
                        + neoVersion + "/neoforge-" + neoVersion + "-installer.jar";

        Path installer = gameDir.resolve("installers").resolve("neoforge-" + neoVersion + "-installer.jar");
        Files.createDirectories(installer.getParent());
        if (!Files.exists(installer)) {
            progress.accept(5, "Descargando NeoForge installer " + neoVersion + "...");
            downloadWithRetry(url, installer);
        }

        progress.accept(20, "Instalando NeoForge " + neoVersion + "...");
        ensureLauncherProfiles(gameDir);
        runInstaller(installer, gameDir, progress, 20, 95);

        if (!Files.exists(versionDir.resolve(versionId + ".json"))) {
            throw new IOException("NeoForge no se instaló correctamente en " + versionDir);
        }
        progress.accept(100, "NeoForge " + neoVersion + " instalado.");
        return versionId;
    }

    public String installFabric(String mcVersion, String loaderVersion, Path gameDir,
            BiConsumer<Integer, String> progress) throws IOException, InterruptedException {
        String versionId  = "fabric-loader-" + loaderVersion + "-" + mcVersion;
        Path   versionDir = gameDir.resolve("versions").resolve(versionId);
        if (Files.exists(versionDir.resolve(versionId + ".json"))) {
            progress.accept(100, "Fabric " + loaderVersion + " ya instalado.");
            return versionId;
        }

        String installerVer = resolveLatestFabricInstaller();
        String url = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/"
                + installerVer + "/fabric-installer-" + installerVer + ".jar";
        Path installer = gameDir.resolve("installers").resolve("fabric-installer-" + installerVer + ".jar");
        Files.createDirectories(installer.getParent());
        if (!Files.exists(installer)) {
            progress.accept(5, "Descargando Fabric installer " + installerVer + "...");
            downloadWithRetry(url, installer);
        }

        progress.accept(20, "Instalando Fabric " + loaderVersion + "...");
        runProcess(new ProcessBuilder(
                "java", "-jar", installer.toAbsolutePath().toString(),
                "client",
                "-mcversion", mcVersion,
                "-loader", loaderVersion,
                "-dir", gameDir.toAbsolutePath().toString(),
                "-noprofile"
        ).redirectErrorStream(true).directory(gameDir.toFile()), progress, 20, 95);

        if (!Files.exists(versionDir.resolve(versionId + ".json"))) {
            throw new IOException("Fabric no se instaló correctamente en " + versionDir);
        }
        progress.accept(100, "Fabric " + loaderVersion + " instalado.");
        return versionId;
    }

    public String installQuilt(String mcVersion, String loaderVersion, Path gameDir,
            BiConsumer<Integer, String> progress) throws IOException, InterruptedException {
        String versionId  = "quilt-loader-" + loaderVersion + "-" + mcVersion;
        Path   versionDir = gameDir.resolve("versions").resolve(versionId);
        if (Files.exists(versionDir.resolve(versionId + ".json"))) {
            progress.accept(100, "Quilt " + loaderVersion + " ya instalado.");
            return versionId;
        }

        String installerVer = resolveLatestQuiltInstaller();
        String url = "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer/"
                + installerVer + "/quilt-installer-" + installerVer + ".jar";
        Path installer = gameDir.resolve("installers").resolve("quilt-installer-" + installerVer + ".jar");
        Files.createDirectories(installer.getParent());
        if (!Files.exists(installer)) {
            progress.accept(5, "Descargando Quilt installer " + installerVer + "...");
            downloadWithRetry(url, installer);
        }

        progress.accept(20, "Instalando Quilt " + loaderVersion + "...");
        runProcess(new ProcessBuilder(
                "java", "-jar", installer.toAbsolutePath().toString(),
                "install", "client", mcVersion, loaderVersion,
                "--install-dir=" + gameDir.toAbsolutePath(),
                "--no-profile"
        ).redirectErrorStream(true).directory(gameDir.toFile()), progress, 20, 95);

        if (!Files.exists(versionDir.resolve(versionId + ".json"))) {
            throw new IOException("Quilt no se instaló correctamente en " + versionDir);
        }
        progress.accept(100, "Quilt " + loaderVersion + " instalado.");
        return versionId;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  VERSIÓN EFECTIVA (sin instalar)
    // ══════════════════════════════════════════════════════════════════════

    /** Devuelve el versionId de lanzamiento dado el loader. No instala nada. */
    public static String computeEffectiveVersionId(String mcVersion, String loaderType, String loaderVersion) {
        if (loaderType == null || loaderType.isBlank() || "VANILLA".equalsIgnoreCase(loaderType)) {
            return mcVersion;
        }
        String lv = loaderVersion == null ? "" : loaderVersion.trim();
        return switch (loaderType.toUpperCase()) {
            case "FORGE"    -> mcVersion + "-forge-" + lv;
            case "NEOFORGE" -> lv.startsWith("2") ? "neoforge-" + lv : mcVersion + "-forge-" + lv;
            case "FABRIC"   -> "fabric-loader-" + lv + "-" + mcVersion;
            case "QUILT"    -> "quilt-loader-" + lv + "-" + mcVersion;
            default         -> mcVersion;
        };
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private void runInstaller(Path jar, Path gameDir, BiConsumer<Integer, String> progress,
            int lo, int hi) throws IOException, InterruptedException {
        runProcess(new ProcessBuilder("java", "-jar", jar.toAbsolutePath().toString(), "--installClient")
                .redirectErrorStream(true)
                .directory(gameDir.toFile()), progress, lo, hi);
    }

    private void runProcess(ProcessBuilder pb, BiConsumer<Integer, String> progress,
            int lo, int hi) throws IOException, InterruptedException {
        Process proc = pb.start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
                // Estima progreso por cantidad de líneas de output
                int estimated = lo + Math.min(lines.size() * 2, hi - lo);
                String msg = line.length() > 80 ? line.substring(0, 80) + "…" : line;
                progress.accept(estimated, msg);
            }
        }
        int exit = proc.waitFor();
        if (exit != 0) {
            throw new IOException("Instalador terminó con código " + exit
                    + (lines.isEmpty() ? "" : ". Último output: " + lines.get(lines.size() - 1)));
        }
    }

    private void ensureLauncherProfiles(Path gameDir) throws IOException {
        Path profiles = gameDir.resolve("launcher_profiles.json");
        if (!Files.exists(profiles)) {
            Files.createDirectories(gameDir);
            Files.writeString(profiles,
                    "{\"profiles\":{},\"selectedProfile\":\"(Default)\","
                    + "\"clientToken\":\"00000000-0000-0000-0000-000000000000\","
                    + "\"launcherVersion\":{\"name\":\"2.0.992\",\"format\":21,\"profilesFormat\":2}}");
        }
    }

    private String resolveLatestFabricInstaller() {
        try {
            JsonNode meta = getJson(URI.create("https://meta.fabricmc.net/v2/versions/installer"));
            if (meta.isArray() && meta.size() > 0) return meta.get(0).path("version").asText("1.0.1");
        } catch (Exception ignored) {}
        return "1.0.1";
    }

    private String resolveLatestQuiltInstaller() {
        try {
            JsonNode meta = getJson(URI.create(
                    "https://meta.quiltmc.org/v3/versions/installer"));
            if (meta.isArray() && meta.size() > 0) return meta.get(0).path("version").asText("0.9.1");
        } catch (Exception ignored) {}
        return "0.9.1";
    }

    private void downloadWithRetry(String url, Path dest) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                downloadFile(url, dest);
                return;
            } catch (IOException ex) {
                last = ex;
                if (attempt < MAX_RETRIES) Thread.sleep(600L * attempt);
            }
        }
        throw last;
    }

    private void downloadFile(String url, Path dest) throws IOException, InterruptedException {
        Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "FroshyLauncher/1.0")
                .GET().build();
        HttpResponse<Path> resp = http.send(req, HttpResponse.BodyHandlers.ofFile(tmp,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));
        if (resp.statusCode() >= 300) {
            Files.deleteIfExists(tmp);
            throw new IOException("HTTP " + resp.statusCode() + " al descargar " + url);
        }
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    private JsonNode getJson(URI uri) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "FroshyLauncher/1.0")
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(resp.body());
    }

    private boolean isValidFile(Path p, long minSize) {
        try { return Files.exists(p) && (minSize <= 0 || Files.size(p) >= minSize); }
        catch (IOException e) { return false; }
    }

    private record DownloadItem(String url, Path dest, String sha1, String sha512, long size) {}
}

