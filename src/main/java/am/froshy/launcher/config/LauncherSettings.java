package am.froshy.launcher.config;

/**
 * Configuración persisitida del launcher (modo rendimiento, intervalos de polling, etc.).
 */
public record LauncherSettings(
        boolean performanceMode,
        int consolePollIntervalMs,
        int launchPollIntervalMs,
        int progressThresholdPct
) {
    public LauncherSettings {
        if (consolePollIntervalMs <= 0) consolePollIntervalMs = 600;
        if (launchPollIntervalMs  <= 0) launchPollIntervalMs  = 300;
        if (progressThresholdPct  <= 0) progressThresholdPct  = 1;
    }

    public static LauncherSettings defaults() {
        return new LauncherSettings(false, 600, 300, 1);
    }

    public LauncherSettings withPerformanceMode(boolean perf) {
        return new LauncherSettings(perf, perf ? 1200 : 600, perf ? 600 : 300, perf ? 3 : 1);
    }
}

