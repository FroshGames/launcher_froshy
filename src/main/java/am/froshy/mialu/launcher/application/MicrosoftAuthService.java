package am.froshy.mialu.launcher.application;

import am.froshy.mialu.launcher.config.LauncherConfig;
import am.froshy.mialu.launcher.domain.MicrosoftBrowserLogin;
import am.froshy.mialu.launcher.domain.MicrosoftDeviceCode;
import am.froshy.mialu.launcher.domain.MicrosoftSessionStatus;
import am.froshy.mialu.launcher.infrastructure.MicrosoftAuthStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class MicrosoftAuthService {

    private static final URI AUTHORIZE_URI = URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize");
    private static final URI DEVICE_CODE_URI = URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode");
    private static final URI TOKEN_URI = URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token");
    private static final URI XBL_AUTH_URI = URI.create("https://user.auth.xboxlive.com/user/authenticate");
    private static final URI XSTS_AUTH_URI = URI.create("https://xsts.auth.xboxlive.com/xsts/authorize");
    private static final URI MC_LOGIN_URI = URI.create("https://api.minecraftservices.com/authentication/login_with_xbox");
    private static final URI MC_PROFILE_URI = URI.create("https://api.minecraftservices.com/minecraft/profile");

    // Cliente publico con redirect loopback para login OAuth normal en apps de escritorio.
    private static final String DEFAULT_CLIENT_ID = "04b07795-8ddb-461a-bbee-02f9e1bf7b46";
    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final long LOGIN_EXPIRY_SECONDS = 300;

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final MicrosoftAuthStore store;
    private final String clientId;
    private final String redirectUri;

    private final Map<String, PendingBrowserLogin> pendingByState = new ConcurrentHashMap<>();
    private final Map<String, PendingBrowserLogin> pendingByOperation = new ConcurrentHashMap<>();

    private volatile MicrosoftAuthStore.StoredMicrosoftSession session;
    private volatile MicrosoftOAuthCallbackServer callbackServer;

    public MicrosoftAuthService(LauncherConfig config, MicrosoftAuthStore store) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.mapper = new ObjectMapper().findAndRegisterModules();
        this.store = store;
        this.clientId = readClientId();
        this.session = store.load().orElse(null);

        String fallbackRedirect = "http://localhost:" + config.internalApiPort() + "/";
        String resolvedRedirect = fallbackRedirect;

        // Con client_id público intentamos loopback en 3000; si falla, usamos el callback interno.
        if (DEFAULT_CLIENT_ID.equals(clientId)) {
            try {
                this.callbackServer = new MicrosoftOAuthCallbackServer();
                this.callbackServer.start();
                resolvedRedirect = "http://localhost:3000/";
            } catch (Exception ex) {
                System.err.println("Advertencia: Puerto 3000 no disponible. Se usara callback interno en puerto "
                        + config.internalApiPort() + ".");
            }
        }

        this.redirectUri = resolvedRedirect;
    }

    private String buildRedirectUri(LauncherConfig config) {
        return "http://localhost:" + config.internalApiPort() + "/";
    }

    private String readClientId() {
        String preferred = System.getenv("MIALU_MS_CLIENT_ID");
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        String legacy = System.getenv("FROSHY_MS_CLIENT_ID");
        if (legacy != null && !legacy.isBlank()) {
            return legacy.trim();
        }
        String jvmProp = System.getProperty("mialu.ms.clientId", "");
        if (!jvmProp.isBlank()) {
            return jvmProp.trim();
        }
        return DEFAULT_CLIENT_ID;
    }

    public MicrosoftBrowserLogin startBrowserLogin() {
        ensureConfigured();
        cleanupExpiredPending();

        String operationId = "msa-" + UUID.randomUUID();
        String state = randomToken(24);
        String codeVerifier = randomToken(64);
        String codeChallenge = pkceChallenge(codeVerifier);
        Instant expiresAt = Instant.now().plusSeconds(LOGIN_EXPIRY_SECONDS);

        String authorizationUrl = buildAuthorizationUrl(state, codeChallenge);
        PendingBrowserLogin pending = new PendingBrowserLogin(operationId, state, codeVerifier, expiresAt);
        pendingByState.put(state, pending);
        pendingByOperation.put(operationId, pending);

        return new MicrosoftBrowserLogin(operationId, authorizationUrl, expiresAt);
    }

    public MicrosoftSessionStatus completeBrowserLogin(String operationId) {
        PendingBrowserLogin pending = pendingByOperation.get(operationId);
        if (pending == null) {
            throw new IllegalArgumentException("Operacion de login no encontrada o expirada");
        }

        long timeoutMs = Math.max(1000L, Duration.between(Instant.now(), pending.expiresAt()).toMillis());
        
        // Si estamos usando el servidor de callback en puerto 3000, esperar allí
        if (callbackServer != null && DEFAULT_CLIENT_ID.equals(clientId)) {
            try {
                Optional<MicrosoftOAuthCallbackServer.OAuthCallbackData> callback = callbackServer.waitForCallback(timeoutMs);
                if (callback.isEmpty()) {
                    throw new IllegalStateException("No se recibio callback OAuth en localhost. "
                            + "Verifica que el navegador no bloquee la redireccion.");
                }

                MicrosoftOAuthCallbackServer.OAuthCallbackData data = callback.get();
                return processBrowserLoginData(pending, data);
            } catch (Exception ex) {
                pending.future().completeExceptionally(ex);
                throw new IllegalStateException(ex.getMessage(), ex);
            } finally {
                clearPending(pending);
            }
        }
        
        // Fallback: usar el flujo normal esperando en el futuro
        try {
            return pending.future().get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException ex) {
            pending.future().completeExceptionally(new IllegalStateException("Tiempo de espera agotado en login Microsoft"));
            throw new IllegalStateException("Tiempo de espera agotado en login Microsoft");
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            throw new IllegalStateException(cause.getMessage(), cause);
        } finally {
            clearPending(pending);
        }
    }
    
    private MicrosoftSessionStatus processBrowserLoginData(PendingBrowserLogin pending, MicrosoftOAuthCallbackServer.OAuthCallbackData data) {
        if (data.state() == null || !data.state().equals(pending.state())) {
            pending.future().completeExceptionally(new IllegalStateException("Estado OAuth invalido (state mismatch)"));
            throw new IllegalStateException("Estado OAuth invalido (state mismatch)");
        }
        if (data.error() != null && !data.error().isBlank()) {
            String detail = (data.errorDescription() == null || data.errorDescription().isBlank()) ? data.error() : data.errorDescription();
            pending.future().completeExceptionally(new IllegalStateException("Login Microsoft cancelado: " + detail));
            throw new IllegalStateException("Login Microsoft cancelado: " + detail);
        }
        if (data.code() == null || data.code().isBlank()) {
            pending.future().completeExceptionally(new IllegalStateException("Microsoft no devolvio codigo de autorizacion"));
            throw new IllegalStateException("Microsoft no devolvio codigo de autorizacion");
        }
        
        try {
            JsonNode token = postForm(TOKEN_FLOW.TOKEN, Map.of(
                    "grant_type", "authorization_code",
                    "client_id", clientId,
                    "code", data.code(),
                    "redirect_uri", redirectUri,
                    "code_verifier", pending.codeVerifier(),
                    "scope", SCOPE
            ));
            if (token.has("error")) {
                throw new IllegalStateException("Microsoft auth error: " + token.path("error").asText("unknown"));
            }

            String msAccessToken = token.path("access_token").asText("");
            String refreshToken = token.path("refresh_token").asText("");
            if (msAccessToken.isBlank()) {
                throw new IllegalStateException("No se recibio access_token de Microsoft");
            }

            MicrosoftSessionStatus status = exchangeAndPersist(refreshToken, msAccessToken);
            pending.future().complete(status);
            return status;
        } catch (Exception ex) {
            pending.future().completeExceptionally(ex);
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    public String handleBrowserCallback(String state, String code, String error, String errorDescription) {
        PendingBrowserLogin pending = pendingByState.get(state);
        if (pending == null || pending.expiresAt().isBefore(Instant.now())) {
            return callbackHtml(false, "La sesion de login expiro. Vuelve a intentarlo desde el launcher.");
        }

        if (error != null && !error.isBlank()) {
            String detail = (errorDescription == null || errorDescription.isBlank()) ? error : errorDescription;
            pending.future().completeExceptionally(new IllegalStateException("Login Microsoft cancelado: " + detail));
            return callbackHtml(false, "Inicio de sesion cancelado: " + detail);
        }
        if (code == null || code.isBlank()) {
            pending.future().completeExceptionally(new IllegalStateException("Microsoft no devolvio codigo de autorizacion"));
            return callbackHtml(false, "Microsoft no devolvio codigo de autorizacion.");
        }

        try {
            JsonNode token = postForm(TOKEN_FLOW.TOKEN, Map.of(
                    "grant_type", "authorization_code",
                    "client_id", clientId,
                    "code", code,
                    "redirect_uri", redirectUri,
                    "code_verifier", pending.codeVerifier(),
                    "scope", SCOPE
            ));
            if (token.has("error")) {
                throw new IllegalStateException("Microsoft auth error: " + token.path("error").asText("unknown"));
            }

            String msAccessToken = token.path("access_token").asText("");
            String refreshToken = token.path("refresh_token").asText("");
            if (msAccessToken.isBlank()) {
                throw new IllegalStateException("No se recibio access_token de Microsoft");
            }

            MicrosoftSessionStatus status = exchangeAndPersist(refreshToken, msAccessToken);
            pending.future().complete(status);
            return callbackHtml(true, "Login completado. Ya puedes volver al launcher.");
        } catch (Exception ex) {
            pending.future().completeExceptionally(ex);
            return callbackHtml(false, "Error en login Microsoft: " + ex.getMessage());
        }
    }

    public MicrosoftDeviceCode startDeviceLogin() {
        throw new UnsupportedOperationException("Flujo por codigo deshabilitado. Usa login normal de navegador.");
    }

    public MicrosoftSessionStatus completeDeviceLogin(String deviceCode) {
        throw new UnsupportedOperationException("Flujo por codigo deshabilitado. Usa login normal de navegador.");
    }

    public MicrosoftSessionStatus getSessionStatus() {
        if (!isConfigured()) {
            return MicrosoftSessionStatus.disconnected("Login Microsoft no configurado (MIALU_MS_CLIENT_ID)");
        }
        try {
            Optional<LaunchAuth> launchAuth = resolveLaunchAuth();
            if (launchAuth.isEmpty()) {
                return MicrosoftSessionStatus.disconnected("No hay sesion premium activa");
            }
            LaunchAuth auth = launchAuth.get();
            return new MicrosoftSessionStatus(true, auth.playerName(), auth.playerUuid(), auth.expiresAt(), "Sesion premium activa");
        } catch (Exception ex) {
            clearSession();
            return MicrosoftSessionStatus.disconnected("Sesion expirada. Inicia sesion de nuevo");
        }
    }

    public void logout() {
        clearSession();
    }

    public Optional<LaunchAuth> resolveLaunchAuth() {
        if (!isConfigured()) {
            return Optional.empty();
        }
        MicrosoftAuthStore.StoredMicrosoftSession current = this.session;
        if (current == null) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        if (current.minecraftExpiresAt() != null && now.isBefore(current.minecraftExpiresAt().minusSeconds(30))) {
            return Optional.of(new LaunchAuth(
                    current.playerName(),
                    current.playerUuid(),
                    current.minecraftAccessToken(),
                    current.xuid(),
                    current.minecraftExpiresAt()
            ));
        }

        if (current.refreshToken() == null || current.refreshToken().isBlank()) {
            clearSession();
            return Optional.empty();
        }

        JsonNode refresh = postForm(TOKEN_FLOW.TOKEN, Map.of(
                "grant_type", "refresh_token",
                "client_id", clientId,
                "refresh_token", current.refreshToken(),
                "scope", SCOPE
        ));

        if (refresh.has("error")) {
            clearSession();
            return Optional.empty();
        }

        String msAccessToken = refresh.path("access_token").asText("");
        String refreshToken = refresh.path("refresh_token").asText(current.refreshToken());
        if (msAccessToken.isBlank()) {
            clearSession();
            return Optional.empty();
        }

        MicrosoftSessionStatus updated = exchangeAndPersist(refreshToken, msAccessToken);
        return updated.connected() ? Optional.of(new LaunchAuth(
                updated.playerName(),
                updated.playerUuid(),
                this.session.minecraftAccessToken(),
                this.session.xuid(),
                this.session.minecraftExpiresAt()
        )) : Optional.empty();
    }

    private MicrosoftSessionStatus exchangeAndPersist(String refreshToken, String microsoftAccessToken) {
        XboxAuth xbox = authenticateXbox(microsoftAccessToken);
        MinecraftAuth minecraft = authenticateMinecraft(xbox);
        MinecraftProfile profile = fetchMinecraftProfile(minecraft.minecraftAccessToken());

        Instant mcExpiresAt = Instant.now().plusSeconds(Math.max(300, minecraft.expiresInSeconds()));
        MicrosoftAuthStore.StoredMicrosoftSession stored = new MicrosoftAuthStore.StoredMicrosoftSession(
                refreshToken,
                minecraft.minecraftAccessToken(),
                xbox.xuid(),
                profile.name(),
                profile.id(),
                mcExpiresAt
        );
        this.session = stored;
        store.save(stored);

        return new MicrosoftSessionStatus(true, profile.name(), profile.id(), mcExpiresAt, "Sesion premium activa");
    }

    private XboxAuth authenticateXbox(String microsoftAccessToken) {
        JsonNode xbl = postJson(XBL_AUTH_URI, Map.of(
                "Properties", Map.of(
                        "AuthMethod", "RPS",
                        "SiteName", "user.auth.xboxlive.com",
                        "RpsTicket", "d=" + microsoftAccessToken
                ),
                "RelyingParty", "http://auth.xboxlive.com",
                "TokenType", "JWT"
        ));

        String xblToken = xbl.path("Token").asText("");
        JsonNode xui = xbl.path("DisplayClaims").path("xui");
        String userHash = xui.isArray() && xui.size() > 0 ? xui.get(0).path("uhs").asText("") : "";
        if (xblToken.isBlank() || userHash.isBlank()) {
            throw new IllegalStateException("No se pudo autenticar en Xbox Live");
        }

        JsonNode xsts = postJson(XSTS_AUTH_URI, Map.of(
                "Properties", Map.of(
                        "SandboxId", "RETAIL",
                        "UserTokens", java.util.List.of(xblToken)
                ),
                "RelyingParty", "rp://api.minecraftservices.com/",
                "TokenType", "JWT"
        ));

        String xstsToken = xsts.path("Token").asText("");
        if (xstsToken.isBlank()) {
            throw new IllegalStateException("No se pudo obtener token XSTS");
        }

        String xuid = "";
        JsonNode xstsXui = xsts.path("DisplayClaims").path("xui");
        if (xstsXui.isArray() && xstsXui.size() > 0) {
            xuid = xstsXui.get(0).path("xid").asText("");
        }

        return new XboxAuth(userHash, xstsToken, xuid);
    }

    private MinecraftAuth authenticateMinecraft(XboxAuth xbox) {
        JsonNode login = postJson(MC_LOGIN_URI, Map.of(
                "identityToken", "XBL3.0 x=" + xbox.userHash() + ";" + xbox.xstsToken()
        ));

        String accessToken = login.path("access_token").asText("");
        long expiresIn = login.path("expires_in").asLong(0);
        if (accessToken.isBlank()) {
            throw new IllegalStateException("No se pudo autenticar en Minecraft Services");
        }
        return new MinecraftAuth(accessToken, expiresIn);
    }

    private MinecraftProfile fetchMinecraftProfile(String minecraftAccessToken) {
        JsonNode profile = getJsonWithBearer(MC_PROFILE_URI, minecraftAccessToken);
        String id = profile.path("id").asText("");
        String name = profile.path("name").asText("");
        if (id.isBlank() || name.isBlank()) {
            throw new IllegalStateException("La cuenta de Microsoft no tiene licencia de Minecraft Java");
        }
        return new MinecraftProfile(id, name);
    }

    private JsonNode postForm(TOKEN_FLOW flow, Map<String, String> params) {
        try {
            StringBuilder body = new StringBuilder();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (body.length() > 0) {
                    body.append('&');
                }
                body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                body.append('=');
                body.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
            URI endpoint = flow == TOKEN_FLOW.DEVICE_CODE ? DEVICE_CODE_URI : TOKEN_URI;
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return mapper.readTree(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo completar la autenticacion con Microsoft", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Autenticacion interrumpida", ex);
        }
    }

    private JsonNode postJson(URI uri, Object payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = mapper.readTree(response.body());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Error en autenticacion premium: " + body.toString());
            }
            return body;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo completar la autenticacion premium", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Autenticacion premium interrumpida", ex);
        }
    }

    private JsonNode getJsonWithBearer(URI uri, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = mapper.readTree(response.body());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Error consultando perfil de Minecraft");
            }
            return body;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo consultar el perfil de Minecraft", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Operacion interrumpida", ex);
        }
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalArgumentException("Falta configurar MIALU_MS_CLIENT_ID para usar login con Microsoft");
        }
    }

    private boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }

    private void clearSession() {
        this.session = null;
        store.clear();
    }

    private String buildAuthorizationUrl(String state, String codeChallenge) {
        Map<String, String> params = new HashMap<>();
        params.put("client_id", clientId);
        params.put("response_type", "code");
        params.put("redirect_uri", redirectUri);
        params.put("response_mode", "query");
        params.put("scope", SCOPE);
        params.put("state", state);
        params.put("prompt", "select_account");
        params.put("code_challenge", codeChallenge);
        params.put("code_challenge_method", "S256");

        StringBuilder qs = new StringBuilder();
        params.forEach((k, v) -> {
            if (qs.length() > 0) qs.append('&');
            qs.append(URLEncoder.encode(k, StandardCharsets.UTF_8));
            qs.append('=');
            qs.append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return AUTHORIZE_URI + "?" + qs;
    }

    private String randomToken(int bytes) {
        byte[] raw = new byte[bytes];
        new SecureRandom().nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private String pkceChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar PKCE", ex);
        }
    }

    private void cleanupExpiredPending() {
        Instant now = Instant.now();
        pendingByOperation.values().stream()
                .filter(p -> p.expiresAt().isBefore(now))
                .toList()
                .forEach(p -> {
                    p.future().completeExceptionally(new IllegalStateException("Operacion de login expirada"));
                    clearPending(p);
                });
    }

    private void clearPending(PendingBrowserLogin pending) {
        pendingByState.remove(pending.state());
        pendingByOperation.remove(pending.operationId());
    }

    private String callbackHtml(boolean success, String message) {
        String title = success ? "Login completado" : "Login fallido";
        String color = success ? "#22bb66" : "#cc3344";
        String safeMessage = message == null ? "" : message.replace("<", "&lt;").replace(">", "&gt;");
        return "<html><head><meta charset='utf-8'></head><body style='font-family:Segoe UI,sans-serif;background:#0f1222;color:#eaefff;padding:24px;'>"
                + "<h2 style='color:" + color + ";'>" + title + "</h2>"
                + "<p>" + safeMessage + "</p>"
                + "<p>Puedes cerrar esta pestaña y volver al launcher.</p>"
                + "</body></html>";
    }

    private void sleepSeconds(long seconds) {
        try {
            Thread.sleep(Math.max(1, seconds) * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Autenticacion interrumpida", ex);
        }
    }

    private enum TOKEN_FLOW { DEVICE_CODE, TOKEN }

    private record PendingBrowserLogin(
            String operationId,
            String state,
            String codeVerifier,
            Instant expiresAt,
            CompletableFuture<MicrosoftSessionStatus> future
    ) {
        private PendingBrowserLogin(String operationId, String state, String codeVerifier, Instant expiresAt) {
            this(operationId, state, codeVerifier, expiresAt, new CompletableFuture<>());
        }
    }

    private record XboxAuth(String userHash, String xstsToken, String xuid) {
    }

    private record MinecraftAuth(String minecraftAccessToken, long expiresInSeconds) {
    }

    private record MinecraftProfile(String id, String name) {
    }

    public record LaunchAuth(
            String playerName,
            String playerUuid,
            String accessToken,
            String xuid,
            Instant expiresAt
    ) {
    }
}





