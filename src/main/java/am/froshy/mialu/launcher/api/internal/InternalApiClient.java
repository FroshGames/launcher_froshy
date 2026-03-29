package am.froshy.mialu.launcher.api.internal;

import am.froshy.mialu.launcher.domain.DownloadStatus;
import am.froshy.mialu.launcher.domain.LaunchRequest;
import am.froshy.mialu.launcher.domain.LaunchResult;
import am.froshy.mialu.launcher.domain.LauncherUpdateStatus;
import am.froshy.mialu.launcher.domain.MinecraftProfile;
import am.froshy.mialu.launcher.domain.PreparedLaunchStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class InternalApiClient {
    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public InternalApiClient(URI baseUri) {
        String normalizedBase = baseUri.toString().endsWith("/") ? baseUri.toString() : baseUri + "/";
        this.baseUri = URI.create(normalizedBase);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public Map<String, Object> health() {
        return send("health", "GET", null, new TypeReference<>() {});
    }

    public List<MinecraftProfile> listProfiles() {
        return send("profiles", "GET", null, new TypeReference<>() {});
    }

    public MinecraftProfile createProfile(MinecraftProfile profile) {
        return send("profiles", "POST", profile, new TypeReference<>() {});
    }

    public MinecraftProfile updateProfile(String existingId, MinecraftProfile profile) {
        return send("profiles/" + existingId, "PUT", profile, new TypeReference<>() {});
    }

    public void deleteProfile(String profileId) {
        send("profiles/" + profileId, "DELETE", null, new TypeReference<Map<String, Object>>() {});
    }

    public String getProfileInstancePath(String profileId) {
        Map<String, Object> response = send("profiles/" + profileId + "/instance-path", "GET", null, new TypeReference<>() {});
        Object instancePath = response.get("instancePath");
        return instancePath == null ? "" : instancePath.toString();
    }

    public LaunchResult launch(LaunchRequest request) {
        return send("launch", "POST", request, new TypeReference<>() {});
    }

    public LaunchResult launchPrepared(LaunchRequest request) {
        return send("launch-prepared", "POST", request, new TypeReference<>() {});
    }

    public PreparedLaunchStatus startLaunchPreparedAsync(LaunchRequest request) {
        return send("launch-prepared/start", "POST", request, new TypeReference<>() {});
    }

    public PreparedLaunchStatus getLaunchPreparedStatus(String operationId) {
        return send("launch-prepared/" + operationId, "GET", null, new TypeReference<>() {});
    }

    public DownloadStatus startDownload(String target) {
        return send("downloads", "POST", Map.of("target", target), new TypeReference<>() {});
    }

    public DownloadStatus getDownloadStatus(String downloadId) {
        return send("downloads/" + downloadId, "GET", null, new TypeReference<>() {});
    }

    public LauncherUpdateStatus checkUpdates() {
        return send("updates/check", "GET", null, new TypeReference<>() {});
    }

    public DownloadStatus prepareVersion(String version) {
        return send("versions/prepare", "POST", Map.of("version", version), new TypeReference<>() {});
    }

    public String getModpackCompatibilityMode() {
        Map<String, Object> response = send("settings/modpack-compat", "GET", null, new TypeReference<>() {});
        Object mode = response.get("mode");
        return mode == null ? "BOTH" : mode.toString();
    }

    public String setModpackCompatibilityMode(String mode) {
        Map<String, Object> response = send("settings/modpack-compat", "POST", Map.of("mode", mode), new TypeReference<>() {});
        Object applied = response.get("mode");
        return applied == null ? "BOTH" : applied.toString();
    }

    public Map<String, Object> getGameOutput(String launchId, int fromIndex) {
        return send("launch/" + launchId + "/output?from=" + fromIndex, "GET", null, new TypeReference<>() {});
    }

    public boolean isGameAlive(String launchId) {
        Map<String, Object> res = send("launch/" + launchId + "/alive", "GET", null, new TypeReference<>() {});
        return Boolean.TRUE.equals(res.get("alive"));
    }

    private <T> T send(String path, String method, Object body, TypeReference<T> typeReference) {
        try {
            HttpRequest request = buildRequest(path, method, body);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Error API " + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), typeReference);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo parsear la respuesta", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Llamada interrumpida", ex);
        }
    }

    private HttpRequest buildRequest(String path, String method, Object body) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json");

        if ("GET".equalsIgnoreCase(method)) {
            return builder.GET().build();
        }

        byte[] payload = objectMapper.writeValueAsBytes(body);
        return builder.method(method, HttpRequest.BodyPublishers.ofByteArray(payload)).build();
    }
}


