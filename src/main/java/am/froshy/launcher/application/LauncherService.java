package am.froshy.launcher.application;

import am.froshy.launcher.config.LauncherConfig;
import am.froshy.launcher.domain.DownloadStatus;
import am.froshy.launcher.domain.LaunchRequest;
import am.froshy.launcher.domain.LaunchResult;
import am.froshy.launcher.domain.MinecraftProfile;
import am.froshy.launcher.domain.ModpackManifest;
import am.froshy.launcher.domain.PreparedLaunchStatus;
import am.froshy.launcher.infrastructure.ProfileStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public final class LauncherService {

    private static final int MAX_OUTPUT_LINES = 2000;

    private final LauncherConfig config;
    private final ProfileStore profileStore;
    private final MinecraftVersionDownloader versionDownloader;
    private final ModpackInstaller modpackInstaller;
    private volatile ModpackCompatibilityMode modpackCompatibilityMode;

    private final Map<String, MinecraftProfile> profiles         = new ConcurrentHashMap<>();
    private final Map<String, DownloadStatus>   downloads        = new ConcurrentHashMap<>();
    private final Map<String, String>           activeVersionDls = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> downloadTasks  = new ConcurrentHashMap<>();
    private final Map<String, PreparedLaunchStatus> preparedLaunches = new ConcurrentHashMap<>();

    // Procesos de juego activos
    private final Map<String, Process>       activeProcesses = new ConcurrentHashMap<>();
    private final Map<String, List<String>>  processOutputs  = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public LauncherService(LauncherConfig config, ProfileStore profileStore) {
        this(config, profileStore, new MojangVersionDownloader(), new ModpackInstaller(),
                ModpackCompatibilityMode.fromEnvironment(System.getenv("FROSHY_MODPACK_COMPAT")));
    }

    public LauncherService(LauncherConfig config, ProfileStore profileStore,
                           MinecraftVersionDownloader versionDownloader) {
        this(config, profileStore, versionDownloader, new ModpackInstaller(),
                ModpackCompatibilityMode.fromEnvironment(System.getenv("FROSHY_MODPACK_COMPAT")));
    }

    public LauncherService(LauncherConfig config, ProfileStore profileStore,
                           MinecraftVersionDownloader versionDownloader,
                           ModpackInstaller modpackInstaller) {
        this(config, profileStore, versionDownloader, modpackInstaller,
                ModpackCompatibilityMode.fromEnvironment(System.getenv("FROSHY_MODPACK_COMPAT")));
    }

    public LauncherService(LauncherConfig config, ProfileStore profileStore,
                           MinecraftVersionDownloader versionDownloader,
                           ModpackInstaller modpackInstaller,
                           ModpackCompatibilityMode modpackCompatibilityMode) {
        this.config            = config;
        this.profileStore      = profileStore;
        this.versionDownloader = versionDownloader;
        this.modpackInstaller  = modpackInstaller;
        this.modpackCompatibilityMode = modpackCompatibilityMode == null
                ? ModpackCompatibilityMode.BOTH
                : modpackCompatibilityMode;
        profileStore.load().forEach(p -> profiles.put(p.id(), p));
    }

    // ── Perfiles ─────────────────────────────────────────────────────────

    public List<MinecraftProfile> listProfiles() {
        return new ArrayList<>(profiles.values());
    }

    public MinecraftProfile createProfile(MinecraftProfile profile) {
        if (profiles.containsKey(profile.id()))
            throw new IllegalArgumentException("Ya existe un perfil con id: " + profile.id());
        ensureProfileInstanceDirectory(profile);
        profiles.put(profile.id(), profile);
        profileStore.save(profiles.values());
        return profile;
    }

    public MinecraftProfile updateProfile(String existingId, MinecraftProfile changes) {
        if (existingId == null || existingId.isBlank()) {
            throw new IllegalArgumentException("existingId es obligatorio");
        }
        if (!profiles.containsKey(existingId)) {
            throw new IllegalArgumentException("Perfil no encontrado: " + existingId);
        }

        // En modo edicion, la ID del formulario se ignora y se conserva la original.
        MinecraftProfile updated = new MinecraftProfile(
                existingId,
                changes.displayName(),
                changes.javaPath(),
                changes.gameVersion(),
                changes.jvmArgs(),
                changes.gameArgs(),
                changes.loaderType(),
                changes.loaderVersion(),
                changes.modpackPath()
        );

        ensureProfileInstanceDirectory(updated);
        profiles.put(existingId, updated);
        profileStore.save(profiles.values());
        return updated;
    }

    // ── Lanzamiento ───────────────────────────────────────────────────────

    public LaunchResult launch(LaunchRequest request) {
        MinecraftProfile profile = Optional.ofNullable(profiles.get(request.profileId()))
                .orElseThrow(() -> new IllegalArgumentException("Perfil no encontrado: " + request.profileId()));
        Path instanceDir = profileGameDirectory(profile);

        String effectiveVersion;
        try {
            effectiveVersion = resolveLaunchPlan(profile).effectiveVersionId();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer el modpack del perfil: " + ex.getMessage(), ex);
        }

        return launchWithVersion(request, profile, effectiveVersion, instanceDir);
    }

    private LaunchResult launchWithVersion(LaunchRequest request, MinecraftProfile profile,
                                           String effectiveVersion, Path gameDir) {

        // Extraer username de los gameArgs del perfil (--username <valor>)
        String username = extractUsername(profile);

        // Construir instalación (classpath, mainClass, args resueltos)
        VersionInstallation install;
        try {
            install = versionDownloader.buildInstallation(
                    effectiveVersion, gameDir, username);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Version " + effectiveVersion + " no preparada: " + ex.getMessage(), ex);
        }

        // Construir comando completo
        List<String> cmd = new ArrayList<>();
        cmd.add(profile.javaPath().isBlank() ? "java" : profile.javaPath());
        // JVM args del perfil (memoria, etc.) van ANTES de los del sistema
        cmd.addAll(profile.jvmArgs());
        // JVM args de la instalación (classpath, library.path, etc.)
        cmd.addAll(install.jvmArguments());
        // Main class
        cmd.add(install.mainClass());
        // Game arguments
        cmd.addAll(install.gameArguments());
        // Demo mode
        if (request.demoMode()) cmd.add("--demo");

        String launchId  = UUID.randomUUID().toString();
        String cmdLine   = String.join(" ", cmd);

        try {
            Files.createDirectories(gameDir);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(gameDir.toFile());
            pb.redirectErrorStream(true);   // stderr → stdout

            Process process = pb.start();
            activeProcesses.put(launchId, process);

            // Hilo lector de output
            startOutputReader(launchId, process);

            return new LaunchResult(launchId, profile.id(), cmdLine, Instant.now(), "STARTED");

        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo iniciar Minecraft: " + ex.getMessage(), ex);
        }
    }

    /** Prepara la version del perfil y la lanza en un solo flujo. */
    public LaunchResult prepareAndLaunch(LaunchRequest request) {
        MinecraftProfile profile = Optional.ofNullable(profiles.get(request.profileId()))
                .orElseThrow(() -> new IllegalArgumentException("Perfil no encontrado: " + request.profileId()));
        Path instanceDir = profileGameDirectory(profile);

        try {
            LaunchPlan plan = prepareProfile(profile, instanceDir, (p, m) -> {});
            return launchWithVersion(request, profile, plan.effectiveVersionId(), instanceDir);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("No se pudo preparar el lanzamiento: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo preparar el lanzamiento: " + ex.getMessage(), ex);
        }
    }

    public PreparedLaunchStatus startPreparedLaunch(LaunchRequest request) {
        MinecraftProfile profile = Optional.ofNullable(profiles.get(request.profileId()))
                .orElseThrow(() -> new IllegalArgumentException("Perfil no encontrado: " + request.profileId()));

        String operationId = "pl-" + UUID.randomUUID();
        PreparedLaunchStatus queued = new PreparedLaunchStatus(
                operationId,
                profile.id(),
                profile.gameVersion(),
                "QUEUED",
                0,
                "En cola..."
        );
        preparedLaunches.put(operationId, queued);

        scheduler.execute(() -> runPreparedLaunch(operationId, request, profile));
        return queued;
    }

    public Optional<PreparedLaunchStatus> getPreparedLaunchStatus(String operationId) {
        return Optional.ofNullable(preparedLaunches.get(operationId));
    }

    // ── Output del proceso ────────────────────────────────────────────────

    /** Devuelve líneas nuevas desde {@code fromIndex} y el estado del proceso. */
    public Map<String, Object> getGameOutput(String launchId, int fromIndex) {
        List<String> buffer = processOutputs.get(launchId);
        boolean alive = isGameAlive(launchId);

        if (buffer == null) {
            return Map.of("lines", List.of(), "total", 0, "alive", alive);
        }
        synchronized (buffer) {
            int total = buffer.size();
            List<String> newLines = (fromIndex < total)
                    ? List.copyOf(buffer.subList(fromIndex, total))
                    : List.of();
            return Map.of("lines", newLines, "total", total, "alive", alive);
        }
    }

    public boolean isGameAlive(String launchId) {
        Process p = activeProcesses.get(launchId);
        return p != null && p.isAlive();
    }

    private void startOutputReader(String launchId, Process process) {
        LinkedList<String> buffer = new LinkedList<>();
        processOutputs.put(launchId, buffer);

        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    synchronized (buffer) {
                        buffer.add(line);
                        if (buffer.size() > MAX_OUTPUT_LINES)
                            buffer.removeFirst();
                    }
                }
            } catch (IOException ignored) {}
            // Proceso terminado
            activeProcesses.remove(launchId);
        }, "game-reader-" + launchId.substring(0, 8));
        t.setDaemon(true);
        t.start();
    }

    // ── Descarga / preparación de versión ────────────────────────────────

    public DownloadStatus prepareVersion(String gameVersion) {
        if (gameVersion == null || gameVersion.isBlank())
            throw new IllegalArgumentException("version es obligatoria");

        String target = "minecraft-" + gameVersion;

        // ¿Ya instalada completamente?
        if (isVersionInstalled(gameVersion)) {
            String id = "ready-" + UUID.randomUUID();
            DownloadStatus done = new DownloadStatus(id, target, "DONE", 100);
            downloads.put(id, done);
            return done;
        }

        // ¿Ya hay una descarga activa?
        String existingId = activeVersionDls.get(gameVersion);
        if (existingId != null) {
            DownloadStatus existing = downloads.get(existingId);
            if (existing != null && !isTerminal(existing.state())) return existing;
        }

        String downloadId = "dl-" + UUID.randomUUID();
        downloads.put(downloadId, new DownloadStatus(downloadId, target, "QUEUED", 0));
        activeVersionDls.put(gameVersion, downloadId);

        scheduler.execute(() -> {
            try {
                versionDownloader.downloadVersion(gameVersion, config.gameDirectory(), (prog, msg) -> {
                    int norm = Math.max(0, Math.min(prog, 100));
                    String state = norm >= 100 ? "DONE" : "IN_PROGRESS";
                    String message = msg != null ? msg : "";
                    downloads.put(downloadId, new DownloadStatus(downloadId, target, state, norm, message));
                });
                downloads.put(downloadId, new DownloadStatus(downloadId, target, "DONE", 100, "Instalacion completada."));
            } catch (Exception ex) {
                downloads.put(downloadId, new DownloadStatus(downloadId, target, "FAILED", 0,
                        "Error: " + ex.getMessage()));
            } finally {
                activeVersionDls.remove(gameVersion, downloadId);
            }
        });

        return downloads.get(downloadId);
    }

    public Optional<DownloadStatus> getDownloadStatus(String id) {
        return Optional.ofNullable(downloads.get(id));
    }

    public DownloadStatus startDownload(String target) {
        if (target == null || target.isBlank())
            throw new IllegalArgumentException("target es obligatorio");
        String id = UUID.randomUUID().toString();
        downloads.put(id, new DownloadStatus(id, target, "QUEUED", 0));
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            DownloadStatus cur = downloads.get(id);
            if (cur == null || "DONE".equals(cur.state())) return;
            int next = Math.min(100, cur.progress() + 20);
            downloads.put(id, new DownloadStatus(id, target,
                    next >= 100 ? "DONE" : "IN_PROGRESS", next));
            if (next >= 100) {
                ScheduledFuture<?> t = downloadTasks.remove(id);
                if (t != null) t.cancel(false);
            }
        }, 200, 300, TimeUnit.MILLISECONDS);
        downloadTasks.put(id, task);
        return downloads.get(id);
    }

    // ── Salud y shutdown ─────────────────────────────────────────────────

    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "apiPort", config.internalApiPort(),
                "profiles", profiles.size(),
                "modpackCompatibility", modpackCompatibilityMode.name(),
                "timestamp", Instant.now().toString()
        );
    }

    public ModpackCompatibilityMode getModpackCompatibilityMode() {
        return modpackCompatibilityMode;
    }

    public ModpackCompatibilityMode setModpackCompatibilityMode(ModpackCompatibilityMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("mode es obligatorio");
        }
        this.modpackCompatibilityMode = mode;
        return this.modpackCompatibilityMode;
    }

    public String getProfileInstancePath(String profileId) {
        MinecraftProfile profile = Optional.ofNullable(profiles.get(profileId))
                .orElseThrow(() -> new IllegalArgumentException("Perfil no encontrado: " + profileId));
        return ensureProfileInstanceDirectory(profile).toAbsolutePath().toString();
    }

    public void shutdown() {
        downloadTasks.values().forEach(t -> t.cancel(false));
        scheduler.shutdownNow();
        activeProcesses.values().forEach(Process::destroy);
    }

    // ── Utilidades ────────────────────────────────────────────────────────

    private boolean isVersionInstalled(String version) {
        Path dir  = config.gameDirectory().resolve("versions").resolve(version);
        Path jar  = dir.resolve(version + ".jar");
        Path json = dir.resolve(version + ".json");
        try {
            return Files.exists(json) && Files.exists(jar) && Files.size(jar) > 10_000;
        } catch (IOException e) { return false; }
    }

    private boolean isTerminal(String state) {
        return "DONE".equals(state) || "FAILED".equals(state);
    }

    private String extractUsername(MinecraftProfile profile) {
        List<String> args = profile.gameArgs();
        for (int i = 0; i < args.size() - 1; i++) {
            if ("--username".equals(args.get(i))) return args.get(i + 1);
        }
        // Fallback: usar el nombre del perfil sin espacios
        return profile.displayName().replaceAll("\\s+", "_");
    }

    private void runPreparedLaunch(String operationId, LaunchRequest request, MinecraftProfile profile) {
        try {
            LaunchPlan basePlan = resolveLaunchPlan(profile);
            Path instanceDir = profileGameDirectory(profile);
            preparedLaunches.put(operationId, new PreparedLaunchStatus(
                    operationId,
                    profile.id(),
                    basePlan.minecraftVersion(),
                    "PREPARING",
                    1,
                    "Verificando instalacion de Minecraft " + basePlan.minecraftVersion() + "..."
            ));

            versionDownloader.downloadVersion(basePlan.minecraftVersion(), instanceDir, (progress, message) -> {
                int mappedProgress = Math.max(1, Math.min(60, progress * 60 / 100));
                String safeMessage = (message == null || message.isBlank())
                        ? "Preparando archivos de Minecraft..."
                        : message;
                preparedLaunches.put(operationId, new PreparedLaunchStatus(
                        operationId,
                        profile.id(),
                        basePlan.minecraftVersion(),
                        "PREPARING",
                        mappedProgress,
                        safeMessage
                ));
            });

            LaunchPlan readyPlan = prepareLoadersAndModpack(operationId, profile, basePlan, instanceDir);

            preparedLaunches.put(operationId, new PreparedLaunchStatus(
                    operationId,
                    profile.id(),
                    readyPlan.effectiveVersionId(),
                    "STARTING",
                    95,
                    "Iniciando Minecraft..."
            ));

            LaunchResult result = launchWithVersion(request, profile, readyPlan.effectiveVersionId(), instanceDir);

            preparedLaunches.put(operationId, new PreparedLaunchStatus(
                    operationId,
                    profile.id(),
                    readyPlan.effectiveVersionId(),
                    "DONE",
                    100,
                    "Minecraft iniciado.",
                    result.launchId()
            ));
        } catch (Exception ex) {
            preparedLaunches.put(operationId, new PreparedLaunchStatus(
                    operationId,
                    profile.id(),
                    profile.gameVersion(),
                    "FAILED",
                    0,
                    "Error: " + ex.getMessage()
            ));
        }
    }

    private LaunchPlan prepareLoadersAndModpack(String operationId, MinecraftProfile profile,
                                                LaunchPlan basePlan, Path gameDir)
            throws IOException, InterruptedException {
        String effectiveVersion = basePlan.minecraftVersion();

        if (basePlan.hasLoader()) {
            preparedLaunches.put(operationId, new PreparedLaunchStatus(
                    operationId,
                    profile.id(),
                    basePlan.minecraftVersion(),
                    "PREPARING",
                    62,
                    "Instalando loader " + basePlan.loaderType() + " " + basePlan.loaderVersion() + "..."
            ));

            effectiveVersion = modpackInstaller.installLoader(
                    basePlan.loaderType(),
                    basePlan.minecraftVersion(),
                    basePlan.loaderVersion(),
                    gameDir,
                    (progress, message) -> preparedLaunches.put(operationId, new PreparedLaunchStatus(
                            operationId,
                            profile.id(),
                            basePlan.minecraftVersion(),
                            "PREPARING",
                            60 + Math.max(0, Math.min(progress, 100)) * 25 / 100,
                            message == null || message.isBlank() ? "Instalando loader..." : message
                    ))
            );
        }

        if (basePlan.hasModpack()) {
            Path modpackPath = basePlan.modpackPath();
            ModpackManifest manifest = parseAndValidateModpack(modpackPath);
            String launchVersion = effectiveVersion;
            modpackInstaller.installModpackFiles(modpackPath, manifest, gameDir, (progress, message) ->
                    preparedLaunches.put(operationId, new PreparedLaunchStatus(
                            operationId,
                            profile.id(),
                            launchVersion,
                            "PREPARING",
                            85 + Math.max(0, Math.min(progress, 100)) * 10 / 100,
                            message == null || message.isBlank() ? "Instalando modpack..." : message
                    ))
            );
        }

        return new LaunchPlan(
                basePlan.minecraftVersion(),
                basePlan.loaderType(),
                basePlan.loaderVersion(),
                basePlan.modpackPath(),
                effectiveVersion
        );
    }

    private LaunchPlan prepareProfile(MinecraftProfile profile, Path gameDir, BiConsumer<Integer, String> progress)
            throws IOException, InterruptedException {
        LaunchPlan plan = resolveLaunchPlan(profile);

        progress.accept(0, "Preparando Minecraft " + plan.minecraftVersion() + "...");
        versionDownloader.downloadVersion(plan.minecraftVersion(), gameDir, progress);

        String effectiveVersion = plan.minecraftVersion();
        if (plan.hasLoader()) {
            effectiveVersion = modpackInstaller.installLoader(
                    plan.loaderType(),
                    plan.minecraftVersion(),
                    plan.loaderVersion(),
                    gameDir,
                    progress
            );
        }

        if (plan.hasModpack()) {
            ModpackManifest manifest = parseAndValidateModpack(plan.modpackPath());
            modpackInstaller.installModpackFiles(plan.modpackPath(), manifest, gameDir, progress);
        }

        return new LaunchPlan(plan.minecraftVersion(), plan.loaderType(), plan.loaderVersion(), plan.modpackPath(), effectiveVersion);
    }

    private LaunchPlan resolveLaunchPlan(MinecraftProfile profile) throws IOException {
        String minecraftVersion = profile.gameVersion();
        String loaderType = profile.loaderType();
        String loaderVersion = profile.loaderVersion();
        Path modpackPath = null;

        if (profile.hasModpack()) {
            modpackPath = Path.of(profile.modpackPath());
            if (!Files.exists(modpackPath)) {
                throw new IOException("No existe el modpack en ruta: " + modpackPath);
            }

            ModpackManifest manifest = parseAndValidateModpack(modpackPath);
            if (manifest.minecraftVersion() != null && !manifest.minecraftVersion().isBlank()) {
                minecraftVersion = manifest.minecraftVersion();
            }
            if (manifest.loaderType() != null && !manifest.loaderType().isBlank()) {
                loaderType = manifest.loaderType();
            }
            if (manifest.loaderVersion() != null && !manifest.loaderVersion().isBlank()) {
                loaderVersion = manifest.loaderVersion();
            }
        }

        String effectiveVersion = ModpackInstaller.computeEffectiveVersionId(
                minecraftVersion,
                loaderType,
                loaderVersion
        );

        return new LaunchPlan(minecraftVersion, loaderType, loaderVersion, modpackPath, effectiveVersion);
    }

    private ModpackManifest parseAndValidateModpack(Path modpackPath) throws IOException {
        ModpackManifest manifest = modpackInstaller.parseModpack(modpackPath);
        if (!modpackCompatibilityMode.isAllowed(manifest.source())) {
            throw new IOException("Formato de modpack no permitido por compatibilidad actual ("
                    + modpackCompatibilityMode + "): " + manifest.source());
        }
        return manifest;
    }

    private Path profileGameDirectory(MinecraftProfile profile) {
        String safeId = profile.id().replaceAll("[^a-zA-Z0-9._-]", "_");
        return config.gameDirectory().resolve("instances").resolve(safeId);
    }

    private Path ensureProfileInstanceDirectory(MinecraftProfile profile) {
        Path instanceDir = profileGameDirectory(profile);
        try {
            Files.createDirectories(instanceDir);
            return instanceDir;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo preparar la instancia del perfil " + profile.id(), ex);
        }
    }

    private record LaunchPlan(
            String minecraftVersion,
            String loaderType,
            String loaderVersion,
            Path modpackPath,
            String effectiveVersionId
    ) {
        private boolean hasLoader() {
            return loaderType != null && !loaderType.isBlank() && !"VANILLA".equalsIgnoreCase(loaderType);
        }

        private boolean hasModpack() {
            return modpackPath != null;
        }
    }
}
