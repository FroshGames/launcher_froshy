package am.froshy.launcher.application;

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
import java.util.function.IntConsumer;

public final class MojangVersionDownloader implements MinecraftVersionDownloader {
    private static final URI VERSION_MANIFEST_URI = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final int MAX_RETRIES = 3;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MojangVersionDownloader() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void downloadClientJar(String version, Path destination, IntConsumer progressConsumer) throws IOException, InterruptedException {
        progressConsumer.accept(10);
        JsonNode manifest = withRetry("manifest", () -> getJson(VERSION_MANIFEST_URI));

        String versionMetadataUrl = null;
        for (JsonNode node : manifest.path("versions")) {
            if (version.equals(node.path("id").asText())) {
                versionMetadataUrl = node.path("url").asText();
                break;
            }
        }

        if (versionMetadataUrl == null || versionMetadataUrl.isBlank()) {
            throw new IllegalArgumentException("Version de Minecraft no encontrada: " + version);
        }

        progressConsumer.accept(35);
        URI metadataUri = URI.create(versionMetadataUrl);
        JsonNode versionMetadata = withRetry("metadata " + version, () -> getJson(metadataUri));
        JsonNode clientNode = versionMetadata.path("downloads").path("client");
        String clientJarUrl = clientNode.path("url").asText();

        if (clientJarUrl == null || clientJarUrl.isBlank()) {
            throw new IOException("No se encontro URL de descarga client para la version " + version);
        }

        progressConsumer.accept(55);
        withRetryVoid("jar " + version, () -> downloadToFile(URI.create(clientJarUrl), destination, progressConsumer));
        progressConsumer.accept(100);
    }

    private JsonNode getJson(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "FroshyLauncher/1.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException("Fallo HTTP " + response.statusCode() + " al consultar " + uri);
        }

        return objectMapper.readTree(response.body());
    }

    private void downloadToFile(URI uri, Path destination, IntConsumer progressConsumer) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", "FroshyLauncher/1.0")
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 300) {
            throw new IOException("Fallo HTTP " + response.statusCode() + " al descargar " + uri);
        }

        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);

        Files.createDirectories(destination.getParent());
        Path tempFile = destination.resolveSibling(destination.getFileName() + ".part");

        try (InputStream input = response.body();
             OutputStream output = Files.newOutputStream(tempFile,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING,
                     StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[8192];
            long totalRead = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                totalRead += read;

                if (contentLength > 0) {
                    int progress = 60 + (int) Math.min(35, (totalRead * 35) / contentLength);
                    progressConsumer.accept(progress);
                }
            }
        }

        long size = Files.size(tempFile);
        if (size <= 0) {
            Files.deleteIfExists(tempFile);
            throw new IOException("La descarga de " + uri + " finalizo sin contenido");
        }

        Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private <T> T withRetry(String operation, RetryableSupplier<T> supplier) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return supplier.get();
            } catch (IOException ex) {
                last = ex;
                if (attempt == MAX_RETRIES) {
                    break;
                }
                sleepBackoff(attempt);
            }
        }

        throw new IOException("Fallo la descarga de " + operation + " tras " + MAX_RETRIES + " intentos", last);
    }

    private void withRetryVoid(String operation, RetryableRunnable runnable) throws IOException, InterruptedException {
        withRetry(operation, () -> {
            runnable.run();
            return null;
        });
    }

    private void sleepBackoff(int attempt) throws InterruptedException {
        long millis = 500L * attempt;
        Thread.sleep(millis);
    }

    @FunctionalInterface
    private interface RetryableSupplier<T> {
        T get() throws IOException, InterruptedException;
    }

    @FunctionalInterface
    private interface RetryableRunnable {
        void run() throws IOException, InterruptedException;
    }
}
