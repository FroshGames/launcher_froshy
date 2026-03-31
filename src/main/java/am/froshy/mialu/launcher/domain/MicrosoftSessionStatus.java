package am.froshy.mialu.launcher.domain;

import java.time.Instant;

public record MicrosoftSessionStatus(
        boolean connected,
        String playerName,
        String playerUuid,
        Instant accessTokenExpiresAt,
        String message
) {
    public static MicrosoftSessionStatus disconnected(String message) {
        return new MicrosoftSessionStatus(false, "", "", null, message == null ? "Sin sesion" : message);
    }
}

