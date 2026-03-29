package am.froshy.mialu.launcher.application;

import am.froshy.mialu.launcher.domain.LauncherUpdateStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class LauncherUpdateService {
    private final String currentVersion;
    private final String metadataUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LauncherUpdateService(String currentVersion, String metadataUrl) {
        this.currentVersion = currentVersion;
        this.metadataUrl = metadataUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public LauncherUpdateStatus checkForUpdates() {
        if (metadataUrl == null || metadataUrl.isBlank()) {
            return new LauncherUpdateStatus("NO_CONFIG", currentVersion, currentVersion, "", "Configura MIALU_UPDATE_METADATA_URL");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(metadataUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return new LauncherUpdateStatus("CHECK_FAILED", currentVersion, currentVersion, "", "HTTP " + response.statusCode());
            }

            Map<String, String> metadata = objectMapper.readValue(response.body(), new TypeReference<>() {});
            String latestVersion = metadata.getOrDefault("version", currentVersion);
            String downloadUrl = metadata.getOrDefault("downloadUrl", "");
            String notes = metadata.getOrDefault("notes", "Sin notas");

            if (!currentVersion.equalsIgnoreCase(latestVersion)) {
                return new LauncherUpdateStatus("UPDATE_AVAILABLE", currentVersion, latestVersion, downloadUrl, notes);
            }
            return new LauncherUpdateStatus("UP_TO_DATE", currentVersion, latestVersion, downloadUrl, notes);
        } catch (Exception ex) {
            return new LauncherUpdateStatus("CHECK_FAILED", currentVersion, currentVersion, "", ex.getMessage());
        }
    }
}



