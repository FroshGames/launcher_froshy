package am.froshy.mialu.launcher.domain;

import java.time.Instant;

public record MicrosoftDeviceCode(
        String deviceCode,
        String userCode,
        String verificationUri,
        String verificationUriComplete,
        String message,
        long intervalSeconds,
        Instant expiresAt
) {
}


