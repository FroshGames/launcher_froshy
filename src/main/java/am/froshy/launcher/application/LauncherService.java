package am.froshy.launcher.application;

import am.froshy.launcher.config.LauncherConfig;
import am.froshy.launcher.domain.DownloadStatus;
import am.froshy.launcher.domain.LaunchRequest;
import am.froshy.launcher.domain.LaunchResult;
import am.froshy.launcher.domain.MinecraftProfile;
import am.froshy.launcher.infrastructure.ProfileStore;

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
    private final Map<String, MinecraftProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, DownloadStatus> downloads = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> downloadTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public LauncherService(LauncherConfig config, ProfileStore profileStore) {
        this.config = config;
        this.profileStore = profileStore;
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

    private String buildCommand(MinecraftProfile profile, boolean demoMode) {
        String jvmArgs = String.join(" ", profile.jvmArgs());
        String gameArgs = String.join(" ", profile.gameArgs());
        String demoArg = demoMode ? "--demo" : "";

        return "%s %s -jar minecraft-%s.jar --gameDir %s %s %s".formatted(
                profile.javaPath(),
                jvmArgs,
                profile.gameVersion(),
                config.gameDirectory(),
                demoArg,
                gameArgs
        ).trim().replaceAll("\\s+", " ");
    }
}

