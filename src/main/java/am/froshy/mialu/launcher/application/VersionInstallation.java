package am.froshy.mialu.launcher.application;

import java.nio.file.Path;
import java.util.List;

/**
 * Representa una instalación de Minecraft completamente preparada, lista para lanzar.
 */
public record VersionInstallation(
        List<Path> classpath,
        String mainClass,
        List<String> jvmArguments,
        List<String> gameArguments,
        Path nativesDir
) {}



