package am.froshy.mialu.launcher.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Lee la versión del launcher desde el archivo pom.properties que Maven
 * genera automáticamente en META-INF/maven/{groupId}/{artifactId}/pom.properties.
 * Siempre coincide con la versión declarada en pom.xml sin filtrado adicional.
 */
public final class LauncherVersion {

    private static final String POM_PROPERTIES =
            "/META-INF/maven/am.froshy.mialu/launcher_mialu/pom.properties";
    private static final String FALLBACK = "0.0.0-DEV";
    private static final String CACHED;

    static {
        String version = FALLBACK;
        try (InputStream in = LauncherVersion.class.getResourceAsStream(POM_PROPERTIES)) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String raw = props.getProperty("version", "").trim();
                if (!raw.isBlank()) version = raw;
            }
        } catch (IOException ignored) {}
        CACHED = version;
    }

    private LauncherVersion() {}

    /** Devuelve la versión del launcher tal como está en pom.xml (ej. "1.0-SNAPSHOT", "2.1.0"). */
    public static String get() {
        return CACHED;
    }
}





