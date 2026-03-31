package am.froshy.mialu.launcher.config;

import java.time.Instant;

/**
 * Configuración persisitida del launcher (modo rendimiento, intervalos de polling, etc.).
 */
public record LauncherSettings(
        boolean performanceMode,
        int consolePollIntervalMs,
        int launchPollIntervalMs,
        int progressThresholdPct,
        Instant lastUpdateCheckAt,
        String lastDownloadedVersion,
        String lastDownloadedAssetName,
        String lastDownloadedFile
) {
    public LauncherSettings {
        if (consolePollIntervalMs <= 0) consolePollIntervalMs = 600;
        if (launchPollIntervalMs  <= 0) launchPollIntervalMs  = 300;
        if (progressThresholdPct  <= 0) progressThresholdPct  = 1;
    }

    public static LauncherSettings defaults() {
        return new LauncherSettings(false, 600, 300, 1, null, "", "", "");
    }

    public LauncherSettings withPerformanceMode(boolean perf) {
        return new LauncherSettings(
                perf,
                perf ? 1200 : 600,
                perf ? 600 : 300,
                perf ? 3 : 1,
                lastUpdateCheckAt,
                lastDownloadedVersion,
                lastDownloadedAssetName,
                lastDownloadedFile
        );
    }

    public LauncherSettings withLastUpdateCheckAt(Instant checkedAt) {
        return new LauncherSettings(
                performanceMode,
                consolePollIntervalMs,
                launchPollIntervalMs,
                progressThresholdPct,
                checkedAt,
                lastDownloadedVersion,
                lastDownloadedAssetName,
                lastDownloadedFile
        );
    }

    public LauncherSettings withDownloadedUpdate(String version, String assetName, String file) {
        return new LauncherSettings(
                performanceMode,
                consolePollIntervalMs,
                launchPollIntervalMs,
                progressThresholdPct,
                lastUpdateCheckAt,
                version == null ? "" : version,
                assetName == null ? "" : assetName,
                file == null ? "" : file
        );
    }
}



