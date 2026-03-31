package am.froshy.mialu.launcher.application;

import am.froshy.mialu.launcher.config.LauncherConfig;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public final class MicrosoftAuthService {

    private static final URI DEVICE_CODE_URI = URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode");
    private static final URI TOKEN_URI = URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token");
    private static final URI XBL_AUTH_URI = URI.create("https://user.auth.xboxlive.com/user/authenticate");
    private static final URI XSTS_AUTH_URI = URI.create("https://xsts.auth.xboxlive.com/xsts/authorize");
    private static final URI MC_LOGIN_URI = URI.create("https://api.minecraftservices.com/authentication/login_with_xbox");
    private static final URI MC_PROFILE_URI = URI.create("https://api.minecraftservices.com/minecraft/profile");

    // Cliente publico usado para flujo device code cuando no se define uno personalizado.
    private static final String DEFAULT_CLIENT_ID = "00000000402b5328";
    private static final String SCOPE = "XboxLive.signin offline_access";

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final MicrosoftAuthStore store;
    private final String clientId;

    private volatile MicrosoftAuthStore.StoredMicrosoftSession session;

    public MicrosoftAuthService(LauncherConfig config, MicrosoftAuthStore store) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.mapper = new ObjectMapper().findAndRegisterModules();
        this.store = store;
        this.clientId = readClientId();
        this.session = store.load().orElse(null);
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

    public MicrosoftDeviceCode startDeviceLogin() {
        ensureConfigured();
        JsonNode node = postForm(TOKEN_FLOW.DEVICE_CODE, Map.of(
                "client_id", clientId,
                "scope", SCOPE
        ));

        Instant expiresAt = Instant.now().plusSeconds(node.path("expires_in").asLong(900));
        return new MicrosoftDeviceCode(
                node.path("device_code").asText(""),
                node.path("user_code").asText(""),
                node.path("verification_uri").asText("https://microsoft.com/link"),
                node.path("verification_uri_complete").asText(node.path("verification_uri").asText("https://microsoft.com/link")),
                node.path("message").asText("Inicia sesion en Microsoft para continuar."),
                Math.max(2, node.path("interval").asLong(5)),
                expiresAt
        );
    }

    public MicrosoftSessionStatus completeDeviceLogin(String deviceCode) {
        ensureConfigured();
        if (deviceCode == null || deviceCode.isBlank()) {
            throw new IllegalArgumentException("deviceCode es obligatorio");
        }

        String refreshToken = null;
        String microsoftAccessToken = null;
        long interval = 5;
        Instant timeout = Instant.now().plusSeconds(900);

        while (Instant.now().isBefore(timeout)) {
            JsonNode tokenResponse = postForm(TOKEN_FLOW.TOKEN, Map.of(
                    "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id", clientId,
                    "device_code", deviceCode
            ));

            if (tokenResponse.has("error")) {
                String error = tokenResponse.path("error").asText("");
                if ("authorization_pending".equals(error)) {
                    sleepSeconds(interval);
                    continue;
                }
                if ("slow_down".equals(error)) {
                    interval = Math.min(15, interval + 2);
                    sleepSeconds(interval);
                    continue;
                }
                throw new IllegalStateException("Microsoft auth error: " + error);
            }

            microsoftAccessToken = tokenResponse.path("access_token").asText("");
            refreshToken = tokenResponse.path("refresh_token").asText("");
            if (!microsoftAccessToken.isBlank()) {
                break;
            }
            sleepSeconds(interval);
        }

        if (microsoftAccessToken == null || microsoftAccessToken.isBlank()) {
            throw new IllegalStateException("Tiempo de espera agotado al iniciar sesion con Microsoft");
        }

        return exchangeAndPersist(refreshToken, microsoftAccessToken);
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

    private void sleepSeconds(long seconds) {
        try {
            Thread.sleep(Math.max(1, seconds) * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Autenticacion interrumpida", ex);
        }
    }

    private enum TOKEN_FLOW { DEVICE_CODE, TOKEN }

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





