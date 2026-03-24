package am.froshy.launcher.application;

import am.froshy.launcher.config.LauncherConfig;
import am.froshy.launcher.domain.DownloadStatus;
import am.froshy.launcher.domain.LaunchRequest;
import am.froshy.launcher.domain.LaunchResult;
import am.froshy.launcher.domain.MinecraftProfile;
import am.froshy.launcher.infrastructure.ProfileStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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

public final class LauncherService {

    private static final int MAX_OUTPUT_LINES = 2000;

    private final LauncherConfig config;
    private final ProfileStore profileStore;
    private final MinecraftVersionDownloader versionDownloader;

    private final Map<String, MinecraftProfile> profiles         = new ConcurrentHashMap<>();
    private final Map<String, DownloadStatus>   downloads        = new ConcurrentHashMap<>();
    private final Map<String, String>           activeVersionDls = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> downloadTasks  = new ConcurrentHashMap<>();

    // Procesos de juego activos
    private final Map<String, Process>       activeProcesses = new ConcurrentHashMap<>();
    private final Map<String, List<String>>  processOutputs  = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public LauncherService(LauncherConfig config, ProfileStore profileStore) {
        this(config, profileStore, new MojangVersionDownloader());
    }

    public LauncherService(LauncherConfig config, ProfileStore profileStore,
                           MinecraftVersionDownloader versionDownloader) {
        this.config            = config;
        this.profileStore      = profileStore;
        this.versionDownloader = versionDownloader;
        profileStore.load().forEach(p -> profiles.put(p.id(), p));
    }

    // ── Perfiles ─────────────────────────────────────────────────────────

    public List<MinecraftProfile> listProfiles() {
        return new ArrayList<>(profiles.values());
    }

    public MinecraftProfile createProfile(MinecraftProfile profile) {
        if (profiles.containsKey(profile.id()))
            throw new IllegalArgumentException("Ya existe un perfil con id: " + profile.id());
        profiles.put(profile.id(), profile);
        profileStore.save(profiles.values());
        return profile;
    }

    // ── Lanzamiento ───────────────────────────────────────────────────────

    public LaunchResult launch(LaunchRequest request) {
        MinecraftProfile profile = Optional.ofNullable(profiles.get(request.profileId()))
                .orElseThrow(() -> new IllegalArgumentException("Perfil no encontrado: " + request.profileId()));

        // Extraer username de los gameArgs del perfil (--username <valor>)
        String username = extractUsername(profile);

        // Construir instalación (classpath, mainClass, args resueltos)
        VersionInstallation install;
        try {
            install = versionDownloader.buildInstallation(
                    profile.gameVersion(), config.gameDirectory(), username);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Version " + profile.gameVersion() + " no preparada: " + ex.getMessage(), ex);
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
            Files.createDirectories(config.gameDirectory());
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(config.gameDirectory().toFile());
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
        List<String> buffer = Collections.synchronizedList(new LinkedList<>());
        processOutputs.put(launchId, buffer);

        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    synchronized (buffer) {
                        buffer.add(line);
                        if (buffer.size() > MAX_OUTPUT_LINES)
                            ((LinkedList<String>) buffer).removeFirst();
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
            DownloadStatus done = new DownloadStatus(id, target, "DONE", 100, "Ya instalada");
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
        downloads.put(downloadId, new DownloadStatus(downloadId, target, "QUEUED", 0, "Esperando…"));
        activeVersionDls.put(gameVersion, downloadId);

        scheduler.execute(() -> {
            try {
                versionDownloader.downloadVersion(gameVersion, config.gameDirectory(), (prog, msg) -> {
                    int norm = Math.max(0, Math.min(prog, 100));
                    String state = norm >= 100 ? "DONE" : "IN_PROGRESS";
                    String displayMsg = (msg != null && !msg.isBlank()) ? msg : "";
                    downloads.put(downloadId, new DownloadStatus(downloadId, target, state, norm, displayMsg));
                });
                downloads.put(downloadId, new DownloadStatus(downloadId, target, "DONE", 100, "¡Instalación completa!"));
            } catch (Exception ex) {
                String errMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                downloads.put(downloadId, new DownloadStatus(downloadId, target, "FAILED", 0,
                        "Error: " + errMsg));
                System.err.println("[LauncherService] Descarga fallida para " + gameVersion + ": " + errMsg);
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
                "timestamp", Instant.now().toString()
        );
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
        Path nativesDir = dir.resolve("natives");
        try {
            if (!(Files.exists(json) && Files.exists(jar) && Files.size(jar) > 10_000)) {
                return false;
            }
            if (!Files.isDirectory(nativesDir)) {
                return false;
            }
            // Evita falsos positivos: si no hay ningun binario nativo extraido, se re-prepara.
            try (var files = Files.list(nativesDir)) {
                return files.anyMatch(p -> {
                    String n = p.getFileName().toString().toLowerCase();
                    return n.endsWith(".dll") || n.endsWith(".so") || n.endsWith(".dylib") || n.endsWith(".jnilib");
                });
            }
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
}
