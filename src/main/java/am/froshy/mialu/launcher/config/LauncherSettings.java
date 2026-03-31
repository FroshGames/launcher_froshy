package am.froshy.mialu.launcher.config;

import java.time.Instant;

/**
 * Configuracion persistida del launcher (rendimiento, updates e identidad global).
 */
public record LauncherSettings(
        boolean performanceMode,
        int consolePollIntervalMs,
        int launchPollIntervalMs,
        int progressThresholdPct,
        Instant lastUpdateCheckAt,
        String lastDownloadedVersion,
        String lastDownloadedAssetName,
        String lastDownloadedFile,
        String globalUsername,
        boolean preferPremiumLogin,
        String oauthClientId,
        String oauthTenant
) {
    public LauncherSettings {
        if (consolePollIntervalMs <= 0) consolePollIntervalMs = 600;
        if (launchPollIntervalMs  <= 0) launchPollIntervalMs  = 300;
        if (progressThresholdPct  <= 0) progressThresholdPct  = 1;
        if (globalUsername == null || globalUsername.isBlank()) globalUsername = "Steve";
        if (oauthClientId == null) oauthClientId = "";
        if (oauthTenant == null || oauthTenant.isBlank()) oauthTenant = "consumers";
    }

    public static LauncherSettings defaults() {
        return new LauncherSettings(false, 600, 300, 1, null, "", "", "", "Steve", true, "", "consumers");
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
                lastDownloadedFile,
                globalUsername,
                preferPremiumLogin,
                oauthClientId,
                oauthTenant
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
                lastDownloadedFile,
                globalUsername,
                preferPremiumLogin,
                oauthClientId,
                oauthTenant
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
                file == null ? "" : file,
                globalUsername,
                preferPremiumLogin,
                oauthClientId,
                oauthTenant
        );
    }

    public LauncherSettings withGlobalUser(String username, boolean preferPremium) {
        return new LauncherSettings(
                performanceMode,
                consolePollIntervalMs,
                launchPollIntervalMs,
                progressThresholdPct,
                lastUpdateCheckAt,
                lastDownloadedVersion,
                lastDownloadedAssetName,
                lastDownloadedFile,
                username,
                preferPremium,
                oauthClientId,
                oauthTenant
        );
    }

    public LauncherSettings withOAuthConfig(String clientId, String tenant) {
        return new LauncherSettings(
                performanceMode,
                consolePollIntervalMs,
                launchPollIntervalMs,
                progressThresholdPct,
                lastUpdateCheckAt,
                lastDownloadedVersion,
                lastDownloadedAssetName,
                lastDownloadedFile,
                globalUsername,
                preferPremiumLogin,
                clientId == null ? "" : clientId.trim(),
                (tenant == null || tenant.isBlank()) ? "consumers" : tenant.trim()
        );
    }
}
