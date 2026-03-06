package am.froshy.launcher.domain;

public record LauncherUpdateStatus(
        String state,
        String currentVersion,
        String latestVersion,
        String downloadUrl,
        String notes
) {
}

