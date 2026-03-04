package am.froshy.launcher.config;

import java.nio.file.Path;

public record LauncherConfig(Path baseDirectory, Path profilesFile, Path gameDirectory, int internalApiPort) {

    public static LauncherConfig fromEnvironment() {
        Path base = Path.of(System.getProperty("user.home"), ".froshy-launcher");
        Path profiles = base.resolve("profiles.json");
        Path gameDir = base.resolve("game");
        int port = parsePort(System.getenv("FROSHY_API_PORT"), 7878);
        return new LauncherConfig(base, profiles, gameDir, port);
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
}

