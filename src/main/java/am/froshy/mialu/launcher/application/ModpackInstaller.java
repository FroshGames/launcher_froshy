package am.froshy.mialu.launcher.application;

import am.froshy.mialu.launcher.domain.ModpackFile;
import am.froshy.mialu.launcher.domain.ModpackManifest;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    private static final String MODPACK_STATE_FILE = ".froshy-modpack-state.json";
    private static final List<String> LEGACY_MANAGED_PATHS = List.of(
            "mods",
            "resourcepacks",
            "texturepacks",
            "shaderpacks",
            "config",
            "defaultconfigs",
            "kubejs",
            "datapacks",
            "patchouli_books",
            "options.txt",
            "optionsof.txt",
            "optionsshaders.txt",
            "servers.dat"
    );
    private static final Set<String> CLIENT_CONTENT_ROOTS = Set.of(
            "mods",
            "config",
            "defaultconfigs",
            "resourcepacks",
            "texturepacks",
            "shaderpacks",
            "kubejs",
            "datapacks",
            "patchouli_books",
            "options.txt",
            "optionsof.txt",
            "optionsshaders.txt",
            "servers.dat"
    );
    private static final Set<String> RESERVED_ARCHIVE_ROOTS = Set.of(
            "overrides",
            "client-overrides",
            "minecraft",
            ".minecraft"
    );

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

        progress.accept(0, "Preparando la instancia del perfil...");
        Files.createDirectories(gameDir);

        Set<String> managedPaths = collectManagedPaths(zipFile, manifest);

        progress.accept(2, "Limpiando archivos previos del modpack...");
        purgeManagedContent(gameDir, managedPaths);

        progress.accept(8, "Extrayendo overrides y contenido del modpack...");
        extractOverrides(zipFile, gameDir);

        List<ModpackFile> files = manifest.files();
        if ("CURSEFORGE".equals(manifest.source())) {
            progress.accept(12, "Resolviendo URLs de archivos desde CurseForge...");
            files = resolveCurseForgeUrls(files, progress);
        }

        int total = files.size();
        if (total == 0) {
            writeInstalledState(gameDir, managedPaths);
            progress.accept(100, "Modpack instalado: " + manifest.name());
            return;
        }

        AtomicInteger ready = new AtomicInteger(0);
        List<DownloadItem> tasks = new ArrayList<>();
        for (ModpackFile file : files) {
            if (file.downloadUrl() == null || file.downloadUrl().isBlank()
                    || file.downloadUrl().startsWith("curse://")) {
                ready.incrementAndGet();
                continue;
            }
            Path dest = resolveGamePath(gameDir, file.path());
            if (isValidFile(dest, file.size())) { ready.incrementAndGet(); continue; }
            tasks.add(new DownloadItem(file.downloadUrl(), dest, file.sha1(), file.sha512(), file.size()));
        }

        int preDone = ready.get();
        int initPct = 10 + preDone * 85 / Math.max(total, 1);
        progress.accept(initPct, "Archivos del modpack: " + preDone + "/" + total + " ya presentes.");

        downloadMods(tasks, total, preDone, progress);
        writeInstalledState(gameDir, managedPaths);
        progress.accept(100, "Modpack instalado: " + manifest.name());
    }

    private Set<String> collectManagedPaths(Path zipFile, ModpackManifest manifest) throws IOException {
        Set<String> managed = new LinkedHashSet<>();
        String archiveRootPrefix = detectArchiveRootPrefix(zipFile);
        for (ModpackFile file : manifest.files()) {
            String normalized = normalizeRelativePath(file.path());
            if (!normalized.isBlank()) {
                managed.add(normalized);
            }
        }

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String relative = resolvePackContentRelativePath(entry.getName(), archiveRootPrefix);
                if (relative != null && !entry.isDirectory()) {
                    String normalized = normalizeRelativePath(relative);
                    if (!normalized.isBlank()) {
                        managed.add(normalized);
                    }
                }
                zip.closeEntry();
            }
        }
        return managed;
    }

    private void purgeManagedContent(Path gameDir, Set<String> nextManagedPaths) throws IOException {
        Set<String> pathsToDelete = new LinkedHashSet<>(loadInstalledState(gameDir));
        if (pathsToDelete.isEmpty()) {
            pathsToDelete.addAll(LEGACY_MANAGED_PATHS);
        }
        pathsToDelete.addAll(nextManagedPaths);

        for (String relativePath : pathsToDelete) {
            deleteRecursively(resolveGamePath(gameDir, relativePath));
        }
        Files.deleteIfExists(gameDir.resolve(MODPACK_STATE_FILE));
    }

    private void extractOverrides(Path zipFile, Path gameDir) throws IOException {
        String archiveRootPrefix = detectArchiveRootPrefix(zipFile);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String relative = resolvePackContentRelativePath(entry.getName(), archiveRootPrefix);
                if (relative != null && !entry.isDirectory()) {
                    Path dest = resolveGamePath(gameDir, relative);
                    if (dest.getParent() != null) {
                        Files.createDirectories(dest.getParent());
                    }
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

    private String resolvePackContentRelativePath(String entryName, String archiveRootPrefix) {
        String normalized = stripArchiveRootPrefix(normalizeZipEntryName(entryName), archiveRootPrefix);
        String lower = normalized.toLowerCase();

        if (isMetadataEntry(lower)) {
            return null;
        }

        for (String marker : List.of("overrides/", "client-overrides/")) {
            if (lower.startsWith(marker)) {
                return stripLeadingMinecraftRoot(normalized.substring(marker.length()));
            }
            int nested = lower.indexOf("/" + marker);
            if (nested >= 0) {
                return stripLeadingMinecraftRoot(normalized.substring(nested + marker.length() + 1));
            }
        }

        for (String marker : List.of("minecraft/", ".minecraft/")) {
            if (lower.startsWith(marker)) {
                return normalized.substring(marker.length());
            }
            int nested = lower.indexOf("/" + marker);
            if (nested >= 0) {
                return normalized.substring(nested + marker.length() + 1);
            }
        }

        // Para Modrinth/CurseForge importamos todo el contenido empaquetado
        // (excepto metadata) para incluir config, texturas, shaders y carpetas custom.
        return normalized.isBlank() ? null : normalized;
    }

    private String stripLeadingMinecraftRoot(String path) {
        String current = path == null ? "" : path;
        String lower = current.toLowerCase();
        if (lower.startsWith("minecraft/")) {
            return current.substring("minecraft/".length());
        }
        if (lower.startsWith(".minecraft/")) {
            return current.substring(".minecraft/".length());
        }
        return current;
    }

    private String detectArchiveRootPrefix(Path zipFile) throws IOException {
        Set<String> roots = new LinkedHashSet<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String normalized = normalizeZipEntryName(entry.getName()).trim();
                if (normalized.isBlank()) {
                    zip.closeEntry();
                    continue;
                }
                if (normalized.startsWith("__MACOSX/")) {
                    zip.closeEntry();
                    continue;
                }
                int slash = normalized.indexOf('/');
                if (slash < 0) {
                    return "";
                }
                roots.add(normalized.substring(0, slash + 1));
                if (roots.size() > 1) {
                    return "";
                }
                zip.closeEntry();
            }
        }

        if (roots.isEmpty()) {
            return "";
        }
        String candidate = roots.iterator().next();
        if (candidate.isBlank()) {
            return "";
        }
        String name = candidate.substring(0, candidate.length() - 1).toLowerCase();
        if (CLIENT_CONTENT_ROOTS.contains(name) || RESERVED_ARCHIVE_ROOTS.contains(name)) {
            return "";
        }
        return candidate;
    }

    private String stripArchiveRootPrefix(String entryName, String archiveRootPrefix) {
        if (archiveRootPrefix == null || archiveRootPrefix.isBlank()) {
            return entryName;
        }
        if (entryName.startsWith(archiveRootPrefix)) {
            return entryName.substring(archiveRootPrefix.length());
        }
        return entryName;
    }

    private boolean isClientContentPath(String lowerPath) {
        for (String root : CLIENT_CONTENT_ROOTS) {
            if (lowerPath.equals(root) || lowerPath.startsWith(root + "/")) {
                return true;
            }
        }
        return false;
    }

    private boolean isMetadataEntry(String lowerPath) {
        return lowerPath.equals("modrinth.index.json")
                || lowerPath.endsWith("/modrinth.index.json")
                || lowerPath.equals("manifest.json")
                || lowerPath.endsWith("/manifest.json")
                || lowerPath.equals("minecraftinstance.json")
                || lowerPath.endsWith("/minecraftinstance.json")
                || lowerPath.equals("instance.cfg")
                || lowerPath.endsWith("/instance.cfg");
    }

    private Path resolveGamePath(Path gameDir, String relativePath) throws IOException {
        String normalizedRelative = normalizeRelativePath(relativePath);
        Path normalizedGameDir = gameDir.toAbsolutePath().normalize();
        Path resolved = normalizedGameDir.resolve(normalizedRelative.replace("/", File.separator)).normalize();
        if (!resolved.startsWith(normalizedGameDir)) {
            throw new IOException("Ruta fuera de la instancia no permitida: " + relativePath);
        }
        return resolved;
    }

    private String normalizeRelativePath(String relativePath) throws IOException {
        if (relativePath == null) {
            throw new IOException("El modpack contiene una ruta nula");
        }

        String normalized = relativePath.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.matches("^[A-Za-z]:.*")) {
            throw new IOException("Ruta absoluta no permitida en modpack: " + relativePath);
        }

        Path safePath = Path.of(normalized).normalize();
        String result = safePath.toString().replace('\\', '/');
        if (safePath.isAbsolute() || result.equals("..") || result.startsWith("../") || result.isBlank()) {
            throw new IOException("Ruta relativa no valida en modpack: " + relativePath);
        }
        return result;
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
                if (task.dest().getParent() != null) {
                    Files.createDirectories(task.dest().getParent());
                }
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
                progress.accept(pct, "Archivos del modpack: " + current + "/" + totalMods + " (" + pct + "%)");
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
                getJavaExecutable(), "-jar", installer.toAbsolutePath().toString(),
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
                getJavaExecutable(), "-jar", installer.toAbsolutePath().toString(),
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
        runProcess(new ProcessBuilder(getJavaExecutable(), "-jar", jar.toAbsolutePath().toString(), "--installClient")
                .redirectErrorStream(true)
                .directory(gameDir.toFile()), progress, lo, hi);
    }

    private String getJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        java.nio.file.Path exe = java.nio.file.Paths.get(javaHome, "bin", "java");
        if (!Files.exists(exe)) {
            exe = java.nio.file.Paths.get(javaHome, "bin", "java.exe");
        }
        if (Files.exists(exe)) {
            return exe.toAbsolutePath().toString();
        }
        // Fallbacks for jpackage environments
        exe = java.nio.file.Paths.get(javaHome, "java.exe");
        if (Files.exists(exe)) {
            return exe.toAbsolutePath().toString();
        }
        // Try system JAVA_HOME
        String sysJavaHome = System.getenv("JAVA_HOME");
        if (sysJavaHome != null && !sysJavaHome.trim().isEmpty()) {
            exe = java.nio.file.Paths.get(sysJavaHome, "bin", "java.exe");
            if (Files.exists(exe)) {
                return exe.toAbsolutePath().toString();
            }
        }
        
        // Final desperate fallback: java from path
        return "java";
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
                .header("User-Agent", "Launcher_Mialu/1.0")
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
                .header("User-Agent", "Launcher_Mialu/1.0")
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(resp.body());
    }

    private boolean isValidFile(Path p, long minSize) {
        try { return Files.exists(p) && (minSize <= 0 || Files.size(p) >= minSize); }
        catch (IOException e) { return false; }
    }

    private List<String> loadInstalledState(Path gameDir) {
        Path stateFile = gameDir.resolve(MODPACK_STATE_FILE);
        if (!Files.exists(stateFile)) {
            return List.of();
        }
        try {
            InstalledModpackState state = mapper.readValue(stateFile.toFile(), InstalledModpackState.class);
            if (state == null || state.managedPaths == null) {
                return List.of();
            }
            return state.managedPaths.stream().distinct().toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private void writeInstalledState(Path gameDir, Set<String> managedPaths) throws IOException {
        InstalledModpackState state = new InstalledModpackState();
        state.managedPaths.addAll(managedPaths.stream().sorted().toList());
        mapper.writerWithDefaultPrettyPrinter().writeValue(gameDir.resolve(MODPACK_STATE_FILE).toFile(), state);
    }

    private void deleteRecursively(Path target) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        if (!Files.isDirectory(target)) {
            Files.deleteIfExists(target);
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(target)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Si está bloqueado, se reintentará en la siguiente preparación.
                }
            });
        }
    }

    private static final class InstalledModpackState {
        public List<String> managedPaths = new ArrayList<>();
    }

    private record DownloadItem(String url, Path dest, String sha1, String sha512, long size) {}
}

