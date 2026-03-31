package am.froshy.mialu.launcher.domain;

public record LauncherUpdateStatus(
        String state,
        String currentVersion,
        String latestVersion,
        String downloadUrl,
        String notes,
        String checkedAt,
        String downloadedAssetName,
        String downloadedFile
) {
}



