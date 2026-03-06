package am.froshy.launcher.api.internal;

import am.froshy.launcher.domain.DownloadStatus;
import am.froshy.launcher.domain.LaunchRequest;
import am.froshy.launcher.domain.LaunchResult;
import am.froshy.launcher.domain.LauncherUpdateStatus;
import am.froshy.launcher.domain.MinecraftProfile;
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

    public LaunchResult launch(LaunchRequest request) {
        return send("launch", "POST", request, new TypeReference<>() {});
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
