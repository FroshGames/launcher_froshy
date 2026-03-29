package am.froshy.mialu.launcher.config;

import java.nio.file.Path;
import java.nio.file.Files;

public record LauncherConfig(
        Path baseDirectory,
        Path profilesFile,
        Path gameDirectory,
        int internalApiPort,
        String launcherVersion,
        String updatesMetadataUrl
) {

    public static LauncherConfig fromEnvironment() {
        Path preferredBase = Path.of(System.getProperty("user.home"), ".mialu-launcher");
        Path legacyBase = Path.of(System.getProperty("user.home"), ".froshy-launcher");
        Path base = Files.exists(preferredBase) || !Files.exists(legacyBase) ? preferredBase : legacyBase;
        Path profiles = base.resolve("profiles.json");
        Path gameDir = base.resolve("game");
        int port = parsePort(readEnv("MIALU_API_PORT", "FROSHY_API_PORT"), 7878);
        String launcherVersion = readValue(readEnv("MIALU_LAUNCHER_VERSION", "FROSHY_LAUNCHER_VERSION"), LauncherVersion.get());
        String metadataUrl = readValue(readEnv("MIALU_UPDATE_METADATA_URL", "FROSHY_UPDATE_METADATA_URL"), "");
        return new LauncherConfig(base, profiles, gameDir, port, launcherVersion, metadataUrl);
    }

    private static String readEnv(String preferredKey, String legacyKey) {
        String preferred = System.getenv(preferredKey);
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return System.getenv(legacyKey);
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


