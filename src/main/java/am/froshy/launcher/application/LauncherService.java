package am.froshy.launcher.application;

import am.froshy.launcher.config.LauncherConfig;
import am.froshy.launcher.domain.DownloadStatus;
import am.froshy.launcher.domain.LaunchRequest;
import am.froshy.launcher.domain.LaunchResult;
import am.froshy.launcher.domain.MinecraftProfile;
import am.froshy.launcher.infrastructure.ProfileStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
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
    private final LauncherConfig config;
    private final ProfileStore profileStore;
    private final MinecraftVersionDownloader versionDownloader;
    private final Map<String, MinecraftProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, DownloadStatus> downloads = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> downloadTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, String> activeVersionDownloads = new ConcurrentHashMap<>();

    public LauncherService(LauncherConfig config, ProfileStore profileStore) {
        this(config, profileStore, new MojangVersionDownloader());
    }

    public LauncherService(LauncherConfig config, ProfileStore profileStore, MinecraftVersionDownloader versionDownloader) {
        this.config = config;
        this.profileStore = profileStore;
        this.versionDownloader = versionDownloader;
        profileStore.load().forEach(profile -> profiles.put(profile.id(), profile));
    }

    public List<MinecraftProfile> listProfiles() {
        return new ArrayList<>(profiles.values());
    }

    public MinecraftProfile createProfile(MinecraftProfile profile) {
        if (profiles.containsKey(profile.id())) {
            throw new IllegalArgumentException("Ya existe un perfil con id: " + profile.id());
        }
        profiles.put(profile.id(), profile);
        profileStore.save(profiles.values());
        return profile;
    }

    public LaunchResult launch(LaunchRequest request) {
        MinecraftProfile profile = Optional.ofNullable(profiles.get(request.profileId()))
                .orElseThrow(() -> new IllegalArgumentException("Perfil no encontrado: " + request.profileId()));

        ensureVersionDownloaded(profile.gameVersion());

        String launchId = UUID.randomUUID().toString();
        String commandLine = buildCommand(profile, request.demoMode());
        return new LaunchResult(launchId, profile.id(), commandLine, Instant.now(), "STARTED");
    }

    public DownloadStatus startDownload(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("target es obligatorio");
        }

        String downloadId = UUID.randomUUID().toString();
        downloads.put(downloadId, new DownloadStatus(downloadId, target, "QUEUED", 0));

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            DownloadStatus current = downloads.get(downloadId);
            if (current == null || "DONE".equals(current.state())) {
                return;
            }

            int nextProgress = Math.min(100, current.progress() + 20);
            String nextState = nextProgress >= 100 ? "DONE" : "IN_PROGRESS";
            downloads.put(downloadId, new DownloadStatus(downloadId, target, nextState, nextProgress));

            if (nextProgress >= 100) {
                ScheduledFuture<?> currentTask = downloadTasks.remove(downloadId);
                if (currentTask != null) {
                    currentTask.cancel(false);
                }
            }
        }, 200, 300, TimeUnit.MILLISECONDS);

        downloadTasks.put(downloadId, task);
        return downloads.get(downloadId);
    }

    public Optional<DownloadStatus> getDownloadStatus(String id) {
        return Optional.ofNullable(downloads.get(id));
    }

    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "apiPort", config.internalApiPort(),
                "profiles", profiles.size(),
                "timestamp", Instant.now().toString()
        );
    }

    public void shutdown() {
        downloadTasks.values().forEach(task -> task.cancel(false));
        scheduler.shutdownNow();
    }

    public DownloadStatus prepareVersion(String gameVersion) {
        if (gameVersion == null || gameVersion.isBlank()) {
            throw new IllegalArgumentException("version es obligatoria");
        }

        Path versionJar = resolveVersionJar(gameVersion);
        String target = "minecraft-" + gameVersion;

        if (isUsableVersionJar(versionJar)) {
            String readyId = "ready-" + UUID.randomUUID();
            DownloadStatus status = new DownloadStatus(readyId, target, "DONE", 100);
            downloads.put(readyId, status);
            return status;
        }

        String existingId = activeVersionDownloads.get(gameVersion);
        if (existingId != null) {
            DownloadStatus existing = downloads.get(existingId);
            if (existing != null && !isTerminal(existing.state())) {
                return existing;
            }
        }

        String downloadId = "auto-" + UUID.randomUUID();
        DownloadStatus queued = new DownloadStatus(downloadId, target, "QUEUED", 0);
        downloads.put(downloadId, queued);
        activeVersionDownloads.put(gameVersion, downloadId);

        scheduler.execute(() -> {
            try {
                performVersionDownload(gameVersion, downloadId, target);
            } catch (Exception ignored) {
                // El detalle del error ya queda reflejado como FAILED en downloads.
            } finally {
                activeVersionDownloads.remove(gameVersion, downloadId);
            }
        });

        return queued;
    }

    private void ensureVersionDownloaded(String gameVersion) {
        Path versionJar = resolveVersionJar(gameVersion);
        if (isUsableVersionJar(versionJar)) {
            return;
        }

        String downloadId = "auto-" + UUID.randomUUID();
        String target = "minecraft-" + gameVersion;
        performVersionDownload(gameVersion, downloadId, target);
    }

    private void performVersionDownload(String gameVersion, String downloadId, String target) {
        Path versionJar = resolveVersionJar(gameVersion);

        try {
            Files.createDirectories(versionJar.getParent());
            downloads.put(downloadId, new DownloadStatus(downloadId, target, "QUEUED", 0));

            versionDownloader.downloadClientJar(gameVersion, versionJar, progress -> {
                int normalized = Math.max(0, Math.min(progress, 100));
                String state = normalized >= 100 ? "DONE" : "IN_PROGRESS";
                downloads.put(downloadId, new DownloadStatus(downloadId, target, state, normalized));
            });

            if (!isUsableVersionJar(versionJar)) {
                downloads.put(downloadId, new DownloadStatus(downloadId, target, "FAILED", 0));
                throw new IllegalStateException("La version descargada no es valida: " + gameVersion);
            }

            downloads.put(downloadId, new DownloadStatus(downloadId, target, "DONE", 100));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            downloads.put(downloadId, new DownloadStatus(downloadId, target, "FAILED", 0));
            throw new IllegalStateException("Descarga interrumpida para version " + gameVersion, ex);
        } catch (IOException | RuntimeException ex) {
            downloads.put(downloadId, new DownloadStatus(downloadId, target, "FAILED", 0));
            throw new IllegalStateException("No se pudo preparar la version " + gameVersion + ": " + ex.getMessage(), ex);
        }
    }

    private boolean isTerminal(String state) {
        return "DONE".equals(state) || "FAILED".equals(state);
    }

    private boolean isUsableVersionJar(Path versionJar) {
        if (!Files.exists(versionJar)) {
            return false;
        }

        try {
            if (Files.size(versionJar) < 4) {
                return false;
            }

            try (InputStream input = Files.newInputStream(versionJar)) {
                byte[] header = input.readNBytes(4);
                return header.length == 4
                        && header[0] == 'P'
                        && header[1] == 'K'
                        && header[2] == 3
                        && header[3] == 4;
            }
        } catch (IOException ex) {
            return false;
        }
    }

    private Path resolveVersionJar(String gameVersion) {
        return config.gameDirectory()
                .resolve("versions")
                .resolve("minecraft-" + gameVersion + ".jar");
    }

    private String buildCommand(MinecraftProfile profile, boolean demoMode) {
        String jvmArgs = String.join(" ", profile.jvmArgs());
        String gameArgs = String.join(" ", profile.gameArgs());
        String demoArg = demoMode ? "--demo" : "";
        String gameJarPath = resolveVersionJar(profile.gameVersion()).toAbsolutePath().toString();

        return "%s %s -jar \"%s\" --gameDir \"%s\" %s %s".formatted(
                profile.javaPath(),
                jvmArgs,
                gameJarPath,
                config.gameDirectory().toAbsolutePath(),
                demoArg,
                gameArgs
        ).trim().replaceAll("\\s+", " ");
    }
}
