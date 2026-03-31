package am.froshy.mialu.launcher.domain;

import java.time.Instant;

public record LaunchResult(
        String launchId,
        String profileId,
        String commandLine,
        Instant startedAt,
        String status
) {
}



