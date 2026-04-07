package am.froshy.mialu.launcher.application;

import am.froshy.mialu.launcher.config.LauncherConfig;
import am.froshy.mialu.launcher.domain.MicrosoftBrowserLogin;
import am.froshy.mialu.launcher.domain.MicrosoftDeviceCode;
import am.froshy.mialu.launcher.domain.MicrosoftSessionStatus;
import am.froshy.mialu.launcher.infrastructure.MicrosoftAuthStore;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.step.AbstractStep;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import net.raphimc.minecraftauth.step.msa.StepLocalWebServer;
import net.raphimc.minecraftauth.step.msa.StepMsaDeviceCode;
import net.raphimc.minecraftauth.util.MicrosoftConstants;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class MicrosoftAuthService {
    // Metodo oficial recomendado para Minecraft Java: Device Code + Java title ID.
    private static final String DEFAULT_CLIENT_ID = MicrosoftConstants.JAVA_TITLE_ID;
    private static final long LOGIN_EXPIRY_SECONDS = 300;

    private final MicrosoftAuthStore store;
    private final Map<String, PendingBrowserLogin> pendingByOperation = new ConcurrentHashMap<>();
    private volatile MicrosoftAuthStore.StoredMicrosoftSession session;

    public MicrosoftAuthService(LauncherConfig config, MicrosoftAuthStore store) {
        this.store = store;
        this.session = store.load().orElse(null);
    }

    private String readClientId() {
        return DEFAULT_CLIENT_ID;
    }

    public MicrosoftBrowserLogin startBrowserLogin() {
        String clientId = readClientId();

        String operationId = "msa-" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(LOGIN_EXPIRY_SECONDS);
        CompletableFuture<String> urlFuture = new CompletableFuture<>();
        CompletableFuture<MicrosoftSessionStatus> authFuture = new CompletableFuture<>();

        new Thread(() -> {
            try {
                HttpClient httpClient = MinecraftAuth.createHttpClient();
                AbstractStep<StepMsaDeviceCode.MsaDeviceCodeCallback, StepFullJavaSession.FullJavaSession> authStep =
                        buildDeviceCodeAuthStep(clientId);

                StepMsaDeviceCode.MsaDeviceCodeCallback callback = new StepMsaDeviceCode.MsaDeviceCodeCallback(deviceCode -> {
                    // URL directa con OTC para minimizar pasos manuales del usuario.
                    urlFuture.complete(deviceCode.getDirectVerificationUri());
                });

                StepFullJavaSession.FullJavaSession result = authStep.getFromInput(httpClient, callback);
                saveSession(authStep, result);
                authFuture.complete(new MicrosoftSessionStatus(
                        true,
                        result.getMcProfile().getName(),
                        result.getMcProfile().getId().toString().replace("-", ""),
                        session.minecraftExpiresAt(),
                        "Sesion premium activa"
                ));
            } catch (Exception ex) {
                if (!urlFuture.isDone()) urlFuture.completeExceptionally(ex);
                authFuture.completeExceptionally(ex);
            }
        }, "minecraftauth-login").start();

        try {
            String url = urlFuture.get(30, TimeUnit.SECONDS);
            pendingByOperation.put(operationId, new PendingBrowserLogin(operationId, expiresAt, authFuture));
            return new MicrosoftBrowserLogin(operationId, url, expiresAt);
        } catch (Exception ex) {
            throw new IllegalStateException("Error al generar URL de login Microsoft: " + rootMessage(ex), ex);
        }
    }

    public MicrosoftSessionStatus completeBrowserLogin(String operationId) {
        PendingBrowserLogin pending = pendingByOperation.get(operationId);
        if (pending == null) throw new IllegalArgumentException("Operacion de login no encontrada");
        if (pending.expiresAt().isBefore(Instant.now())) {
            pendingByOperation.remove(operationId);
            throw new IllegalStateException("Login Microsoft caducado. Intenta de nuevo.");
        }

        try {
            long remainingMs = Math.max(100L, Duration.between(Instant.now(), pending.expiresAt()).toMillis());
            return pending.future().get(remainingMs, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("Login Microsoft fallido: " + rootMessage(ex), ex);
        } finally {
            pendingByOperation.remove(operationId);
        }
    }

    public String handleBrowserCallback(String state, String code, String err, String errDesc) {
        return "<html><body>Login en progreso. Puedes volver al launcher.</body></html>";
    }

    public MicrosoftDeviceCode startDeviceLogin() {
        throw new UnsupportedOperationException();
    }

    public MicrosoftSessionStatus completeDeviceLogin(String deviceCode) {
        throw new UnsupportedOperationException();
    }

    public MicrosoftSessionStatus getSessionStatus() {
        try {
            Optional<LaunchAuth> auth = resolveLaunchAuth();
            if (auth.isEmpty()) return MicrosoftSessionStatus.disconnected("No hay sesion premium activa");
            return new MicrosoftSessionStatus(true, auth.get().playerName(), auth.get().playerUuid(), auth.get().expiresAt(), "Sesion premium activa");
        } catch (Exception ex) {
            clearSession();
            return MicrosoftSessionStatus.disconnected("Sesion expirada. Inicia sesion con Microsoft antes de jugar.");
        }
    }

    public void logout() {
        clearSession();
    }

    private void clearSession() {
        this.session = null;
        store.clear();
    }

    public Optional<LaunchAuth> resolveLaunchAuth() {
        if (this.session == null) return Optional.empty();
        try {
            Instant mcExpiresAt = session.minecraftExpiresAt();
            if (mcExpiresAt != null && mcExpiresAt.isAfter(Instant.now().plusSeconds(60))) {
                return Optional.of(new LaunchAuth(
                        session.playerName(),
                        session.playerUuid().replace("-", ""),
                        session.minecraftAccessToken(),
                        session.xuid(),
                        session.minecraftExpiresAt()
                ));
            }

            if (session.sessionJson() == null || session.sessionJson().isBlank()) {
                clearSession();
                return Optional.empty();
            }

            String clientId = readClientId();
            HttpClient httpClient = MinecraftAuth.createHttpClient();
            AbstractStep<StepMsaDeviceCode.MsaDeviceCodeCallback, StepFullJavaSession.FullJavaSession> authStep =
                    buildDeviceCodeAuthStep(clientId);

            JsonObject json = JsonParser.parseString(session.sessionJson()).getAsJsonObject();
            StepFullJavaSession.FullJavaSession result = authStep.fromJson(json);

            if (result.getMcProfile().isExpiredOrOutdated()) {
                result = authStep.refresh(httpClient, result);
                saveSession(authStep, result);
            }

            return Optional.of(new LaunchAuth(
                    result.getMcProfile().getName(),
                    result.getMcProfile().getId().toString().replace("-", ""),
                    result.getMcProfile().getMcToken().getAccessToken(),
                    null,
                    Instant.ofEpochMilli(result.getMcProfile().getMcToken().getExpireTimeMs())
            ));
        } catch (Exception ex) {
            clearSession();
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private AbstractStep<StepMsaDeviceCode.MsaDeviceCodeCallback, StepFullJavaSession.FullJavaSession> buildDeviceCodeAuthStep(String clientId) {
        return (AbstractStep<StepMsaDeviceCode.MsaDeviceCodeCallback, StepFullJavaSession.FullJavaSession>)
                (Object) MinecraftAuth.builder()
                        .withClientId(clientId)
                        .withScope(MicrosoftConstants.SCOPE_TITLE_AUTH)
                        .withTimeout((int) LOGIN_EXPIRY_SECONDS)
                        .deviceCode()
                        .withDeviceToken("Win32")
                        .sisuTitleAuthentication(MicrosoftConstants.JAVA_XSTS_RELYING_PARTY)
                        .buildMinecraftJavaProfileStep(true);
    }

    private void saveSession(
            AbstractStep<?, StepFullJavaSession.FullJavaSession> authStep,
            StepFullJavaSession.FullJavaSession result
    ) {
        String uuid = result.getMcProfile().getId().toString().replace("-", "");
        String name = result.getMcProfile().getName();
        Instant mcExpiresAt = Instant.ofEpochMilli(result.getMcProfile().getMcToken().getExpireTimeMs());
        JsonObject json = authStep.toJson(result);
        this.session = new MicrosoftAuthStore.StoredMicrosoftSession(
                null,
                result.getMcProfile().getMcToken().getAccessToken(),
                null,
                name,
                uuid,
                mcExpiresAt,
                json.toString()
        );
        store.save(this.session);
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        String message = t.getMessage();
        return (message == null || message.isBlank()) ? t.getClass().getSimpleName() : message;
    }

    private record PendingBrowserLogin(String operationId, Instant expiresAt, CompletableFuture<MicrosoftSessionStatus> future) {
    }

    public record LaunchAuth(String playerName, String playerUuid, String accessToken, String xuid, Instant expiresAt) {
    }
}
