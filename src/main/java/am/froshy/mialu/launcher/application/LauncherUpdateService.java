package am.froshy.mialu.launcher.application;

import am.froshy.mialu.launcher.config.LauncherSettings;
import am.froshy.mialu.launcher.domain.LauncherUpdateStatus;
import am.froshy.mialu.launcher.infrastructure.SettingsStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class LauncherUpdateService {
    private static final String DEFAULT_RELEASE_API_URL = "https://api.github.com/repos/FroshGames/launcher_froshy/releases/latest";
    private static final String USER_AGENT = "Launcher_Mialu-Updater/1.0";

    private final String currentVersion;
    private final String releaseApiUrl;
    private final Path updatesDirectory;
    private final SettingsStore settingsStore;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LauncherUpdateService(String currentVersion, String metadataUrl) {
        this(currentVersion, metadataUrl,
                Path.of(System.getProperty("java.io.tmpdir"), "launcher-mialu-updates"),
                new SettingsStore(
                        Path.of(System.getProperty("java.io.tmpdir"), "launcher-mialu-settings.json"),
                        new ObjectMapper().findAndRegisterModules()
                ));
    }

    public LauncherUpdateService(String currentVersion, String releaseApiUrl, Path updatesDirectory, SettingsStore settingsStore) {
        this.currentVersion = currentVersion;
        this.releaseApiUrl = releaseApiUrl == null || releaseApiUrl.isBlank() ? DEFAULT_RELEASE_API_URL : releaseApiUrl;
        this.updatesDirectory = updatesDirectory;
        this.settingsStore = settingsStore;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public LauncherUpdateStatus checkForUpdates() {
        return checkForUpdates(true);
    }

    public LauncherUpdateStatus checkForUpdates(boolean manualTrigger) {
        LauncherSettings settings = settingsStore.load();
        if (!manualTrigger && !shouldRunWeeklyCheck(settings.lastUpdateCheckAt())) {
            return new LauncherUpdateStatus(
                    "SKIPPED",
                    currentVersion,
                    safe(settings.lastDownloadedVersion(), currentVersion),
                    "",
                    "La comprobación automática semanal aún no toca.",
                    formatInstant(settings.lastUpdateCheckAt()),
                    safe(settings.lastDownloadedAssetName(), ""),
                    safe(settings.lastDownloadedFile(), "")
            );
        }

        Instant checkedAt = Instant.now();
        settings = settings.withLastUpdateCheckAt(checkedAt);
        settingsStore.save(settings);

        try {
            JsonNode release = getJson(URI.create(releaseApiUrl));
            String latestVersion = normalizeVersion(extractLatestVersion(release));
            String normalizedCurrent = normalizeVersion(currentVersion);
            String notes = release.path("body").asText("Sin notas");
            ReleaseAsset asset = selectBestAsset(release.path("assets"));

            if (compareVersions(normalizedCurrent, latestVersion) >= 0) {
                return new LauncherUpdateStatus(
                        "UP_TO_DATE",
                        currentVersion,
                        latestVersion,
                        asset == null ? "" : asset.downloadUrl(),
                        notes,
                        formatInstant(checkedAt),
                        safe(settings.lastDownloadedAssetName(), ""),
                        safe(settings.lastDownloadedFile(), "")
                );
            }

            if (asset == null) {
                return new LauncherUpdateStatus(
                        "UPDATE_AVAILABLE",
                        currentVersion,
                        latestVersion,
                        "",
                        "Hay una release nueva, pero no se encontró un package descargable compatible.",
                        formatInstant(checkedAt),
                        safe(settings.lastDownloadedAssetName(), ""),
                        safe(settings.lastDownloadedFile(), "")
                );
            }

            Path downloaded = downloadAssetIfNeeded(asset, latestVersion);
            LauncherSettings updatedSettings = settings
                    .withLastUpdateCheckAt(checkedAt)
                    .withDownloadedUpdate(latestVersion, asset.name(), downloaded.toAbsolutePath().toString());
            settingsStore.save(updatedSettings);

            return new LauncherUpdateStatus(
                    "UPDATE_DOWNLOADED",
                    currentVersion,
                    latestVersion,
                    asset.downloadUrl(),
                    notes,
                    formatInstant(checkedAt),
                    asset.name(),
                    downloaded.toAbsolutePath().toString()
            );
        } catch (Exception ex) {
            return new LauncherUpdateStatus(
                    "CHECK_FAILED",
                    currentVersion,
                    currentVersion,
                    "",
                    ex.getMessage(),
                    formatInstant(checkedAt),
                    safe(settings.lastDownloadedAssetName(), ""),
                    safe(settings.lastDownloadedFile(), "")
            );
        }
    }

    private boolean shouldRunWeeklyCheck(Instant lastCheckAt) {
        if (lastCheckAt == null) {
            return true;
        }
        return lastCheckAt.plus(7, ChronoUnit.DAYS).isBefore(Instant.now());
    }

    private JsonNode getJson(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " al consultar updates");
        }
        return objectMapper.readTree(response.body());
    }

    private String extractLatestVersion(JsonNode release) {
        String tag = release.path("tag_name").asText("").trim();
        if (!tag.isBlank()) return tag;
        String name = release.path("name").asText("").trim();
        if (!name.isBlank()) return name;
        return currentVersion;
    }

    private ReleaseAsset selectBestAsset(JsonNode assetsNode) {
        if (assetsNode == null || !assetsNode.isArray() || assetsNode.isEmpty()) {
            return null;
        }
        List<ReleaseAsset> assets = new ArrayList<>();
        for (JsonNode assetNode : assetsNode) {
            String name = assetNode.path("name").asText("");
            String downloadUrl = assetNode.path("browser_download_url").asText("");
            if (name.isBlank() || downloadUrl.isBlank()) continue;
            assets.add(new ReleaseAsset(name, downloadUrl));
        }
        if (assets.isEmpty()) return null;

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return assets.stream()
                .sorted(Comparator.comparingInt(asset -> assetPriority(asset, os)))
                .findFirst()
                .orElse(null);
    }

    private int assetPriority(ReleaseAsset asset, String os) {
        String name = asset.name().toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            if (name.contains("launcher_mialu") && name.endsWith("win64.zip")) return 0;
            if (name.endsWith(".zip")) return 1;
        } else if (os.contains("mac") || os.contains("nix") || os.contains("nux")) {
            if (name.contains("launcher_mialu") && name.endsWith(".tar.gz")) return 0;
            if (name.endsWith(".tar.gz")) return 1;
        }
        if (name.contains("launcher_mialu") && name.endsWith(".jar")) return 2;
        if (name.endsWith(".jar")) return 3;
        return 10;
    }

    private Path downloadAssetIfNeeded(ReleaseAsset asset, String latestVersion) throws IOException, InterruptedException {
        Files.createDirectories(updatesDirectory);
        String safeVersion = latestVersion.replaceAll("[^A-Za-z0-9._-]", "_");
        Path destination = updatesDirectory.resolve(safeVersion + "-" + asset.name());
        if (Files.exists(destination) && Files.size(destination) > 0) {
            return destination;
        }

        Path tmp = destination.resolveSibling(destination.getFileName() + ".part");
        HttpRequest request = HttpRequest.newBuilder(URI.create(asset.downloadUrl()))
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " al descargar " + asset.name());
        }
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            in.transferTo(out);
        }
        Files.move(tmp, destination, StandardCopyOption.REPLACE_EXISTING);
        return destination;
    }

    private String normalizeVersion(String version) {
        if (version == null || version.isBlank()) return "0";
        return version.trim().replaceFirst("^[Vv]", "");
    }

    private int compareVersions(String current, String latest) {
        String[] left = current.replace("-SNAPSHOT", ".-1").split("[._-]");
        String[] right = latest.replace("-SNAPSHOT", ".-1").split("[._-]");
        int len = Math.max(left.length, right.length);
        for (int i = 0; i < len; i++) {
            String a = i < left.length ? left[i] : "0";
            String b = i < right.length ? right[i] : "0";
            int cmp;
            if (a.matches("-?\\d+") && b.matches("-?\\d+")) {
                cmp = Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } else {
                cmp = a.compareToIgnoreCase(b);
            }
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record ReleaseAsset(String name, String downloadUrl) {}
}



