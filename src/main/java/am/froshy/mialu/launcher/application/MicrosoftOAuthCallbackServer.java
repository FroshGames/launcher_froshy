package am.froshy.mialu.launcher.application;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Servidor temporal para manejar callbacks de OAuth de Microsoft en el puerto 3000.
 * Esto es necesario porque el Client ID público de Microsoft está registrado con
 * redirect_uri = http://localhost:3000/
 */
public final class MicrosoftOAuthCallbackServer {
    private static final int PORT = 3000;
    private final HttpServer server;
    private final BlockingQueue<OAuthCallbackData> callbacks;
    
    public MicrosoftOAuthCallbackServer() {
        this.callbacks = new LinkedBlockingQueue<>();
        try {
            this.server = HttpServer.create(new InetSocketAddress(PORT), 0);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo crear servidor de callback OAuth en puerto " + PORT, ex);
        }
        
        server.createContext("/", this::handleRequest);
        server.setExecutor(Executors.newCachedThreadPool());
    }
    
    public void start() {
        server.start();
    }
    
    public void stop() {
        server.stop(0);
    }
    
    public Optional<OAuthCallbackData> waitForCallback(long timeoutMs) {
        try {
            OAuthCallbackData data = callbacks.poll(timeoutMs, TimeUnit.MILLISECONDS);
            return Optional.ofNullable(data);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            throw new IllegalStateException("Error esperando callback de OAuth", ex);
        }
    }
    
    private void handleRequest(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendHtml(exchange, 405, "Método no permitido");
                return;
            }
            
            String rawQuery = exchange.getRequestURI().getRawQuery();
            if (rawQuery == null || rawQuery.isBlank()) {
                sendHtml(exchange, 400, "Sin parámetros de OAuth");
                return;
            }
            
            Map<String, String> params = parseQuery(rawQuery);
            
            OAuthCallbackData data = new OAuthCallbackData(
                    params.get("code"),
                    params.get("state"),
                    params.get("error"),
                    params.get("error_description")
            );
            
            callbacks.offer(data);
            sendHtml(exchange, 200, "<html><head><meta charset='utf-8'></head><body style='font-family:Segoe UI,sans-serif;background:#0f1222;color:#eaefff;padding:24px;'>"
                    + "<h2 style='color:#22bb66;'>Login completado</h2>"
                    + "<p>Autenticación exitosa. Ya puedes cerrar esta ventana y volver al launcher.</p>"
                    + "</body></html>");
        } catch (Exception ex) {
            sendHtml(exchange, 500, "Error procesando callback: " + ex.getMessage());
        } finally {
            exchange.close();
        }
    }
    
    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }
        
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            } else if (!pair.isBlank()) {
                String key = URLDecoder.decode(pair, StandardCharsets.UTF_8);
                params.put(key, "");
            }
        }
        return params;
    }
    
    private void sendHtml(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] payload = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, payload.length);
        exchange.getResponseBody().write(payload);
    }
    
    public record OAuthCallbackData(
            String code,
            String state,
            String error,
            String errorDescription
    ) {}
}


