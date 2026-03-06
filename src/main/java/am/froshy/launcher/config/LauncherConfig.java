package am.froshy.launcher.config;

import java.nio.file.Path;

public record LauncherConfig(
        Path baseDirectory,
        Path profilesFile,
        Path gameDirectory,
        int internalApiPort,
        String launcherVersion,
        String updatesMetadataUrl
) {

    public static LauncherConfig fromEnvironment() {
        Path base = Path.of(System.getProperty("user.home"), ".froshy-launcher");
        Path profiles = base.resolve("profiles.json");
        Path gameDir = base.resolve("game");
        int port = parsePort(System.getenv("FROSHY_API_PORT"), 7878);
        String launcherVersion = readValue(System.getenv("FROSHY_LAUNCHER_VERSION"), "1.0-SNAPSHOT");
        String metadataUrl = readValue(System.getenv("FROSHY_UPDATE_METADATA_URL"), "");
        return new LauncherConfig(base, profiles, gameDir, port, launcherVersion, metadataUrl);
    }

    private static int parsePort(String rawPort, int fallback) {
        if (rawPort == null || rawPort.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(rawPort);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String readValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
