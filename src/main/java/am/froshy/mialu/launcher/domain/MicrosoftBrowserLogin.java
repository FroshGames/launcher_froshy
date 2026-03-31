package am.froshy.mialu.launcher.domain;

import java.time.Instant;

public record MicrosoftBrowserLogin(
        String operationId,
        String authorizationUrl,
        Instant expiresAt
) {
}

