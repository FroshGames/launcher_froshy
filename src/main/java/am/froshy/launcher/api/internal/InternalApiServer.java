package am.froshy.launcher.api.internal;

import am.froshy.launcher.application.LauncherService;
import am.froshy.launcher.application.LauncherUpdateService;
import am.froshy.launcher.domain.LaunchRequest;
import am.froshy.launcher.domain.MinecraftProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public final class InternalApiServer {
    private final HttpServer server;
    private final LauncherService launcherService;
    private final LauncherUpdateService updateService;
    private final ObjectMapper objectMapper;

    public InternalApiServer(int port, LauncherService launcherService, LauncherUpdateService updateService) {
        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo levantar la API interna", ex);
        }

        this.launcherService = launcherService;
        this.updateService = updateService;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.server.setExecutor(Executors.newCachedThreadPool());
        createContexts();
    }

    public void start() {
        server.start();
    }

    public void stop() {
        launcherService.shutdown();
        server.stop(0);
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private void createContexts() {
        server.createContext("/internal/v1/health", exchange -> withErrorHandling(exchange, this::handleHealth));
        server.createContext("/internal/v1/profiles", exchange -> withErrorHandling(exchange, this::handleProfiles));
        server.createContext("/internal/v1/launch", exchange -> withErrorHandling(exchange, this::handleLaunch));
        server.createContext("/internal/v1/downloads", exchange -> withErrorHandling(exchange, this::handleDownloads));
        server.createContext("/internal/v1/updates/check", exchange -> withErrorHandling(exchange, this::handleUpdateCheck));
        server.createContext("/internal/v1/versions/prepare", exchange -> withErrorHandling(exchange, this::handlePrepareVersion));
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange, List.of("GET"));
            return;
        }
        sendJson(exchange, 200, launcherService.health());
    }

    private void handleProfiles(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            sendJson(exchange, 200, launcherService.listProfiles());
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            MinecraftProfile profile = readBody(exchange, MinecraftProfile.class);
            MinecraftProfile created = launcherService.createProfile(profile);
            sendJson(exchange, 201, created);
            return;
        }

        sendMethodNotAllowed(exchange, List.of("GET", "POST"));
    }

    private void handleLaunch(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange, List.of("POST"));
            return;
        }

        LaunchRequest request = readBody(exchange, LaunchRequest.class);
        sendJson(exchange, 202, launcherService.launch(request));
    }

    private void handleDownloads(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String fullPath = exchange.getRequestURI().getPath();
        String basePath = "/internal/v1/downloads";

        if ("POST".equalsIgnoreCase(method) && basePath.equals(fullPath)) {
            Map<String, String> request = readBody(exchange, new TypeReference<>() {});
            sendJson(exchange, 202, launcherService.startDownload(request.get("target")));
            return;
        }

        if ("GET".equalsIgnoreCase(method) && fullPath.startsWith(basePath + "/")) {
            String id = fullPath.substring((basePath + "/").length());
            launcherService.getDownloadStatus(id)
                    .ifPresentOrElse(
                            status -> sendJsonUnchecked(exchange, 200, status),
                            () -> sendJsonUnchecked(exchange, 404, Map.of("error", "Descarga no encontrada"))
                    );
            return;
        }

        sendMethodNotAllowed(exchange, List.of("POST", "GET"));
    }

    private void handleUpdateCheck(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange, List.of("GET"));
            return;
        }
        sendJson(exchange, 200, updateService.checkForUpdates());
    }

    private void handlePrepareVersion(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange, List.of("POST"));
            return;
        }

        Map<String, String> request = readBody(exchange, new TypeReference<>() {});
        String version = request.get("version");
        sendJson(exchange, 202, launcherService.prepareVersion(version));
    }

    private <T> T readBody(HttpExchange exchange, Class<T> type) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            return objectMapper.readValue(body, type);
        }
    }

    private <T> T readBody(HttpExchange exchange, TypeReference<T> typeReference) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            return objectMapper.readValue(body, typeReference);
        }
    }

    private void withErrorHandling(HttpExchange exchange, ExchangeHandler handler) throws IOException {
        try {
            handler.handle(exchange);
        } catch (IllegalArgumentException ex) {
            sendJson(exchange, 400, Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            sendJson(exchange, 500, Map.of("error", "Error interno", "detail", ex.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private void sendMethodNotAllowed(HttpExchange exchange, List<String> allowedMethods) throws IOException {
        exchange.getResponseHeaders().set("Allow", String.join(", ", allowedMethods));
        sendJson(exchange, 405, Map.of("error", "Metodo no permitido"));
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
        byte[] payload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, payload.length);
        exchange.getResponseBody().write(payload);
    }

    private void sendJsonUnchecked(HttpExchange exchange, int statusCode, Object body) {
        try {
            sendJson(exchange, statusCode, body);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
