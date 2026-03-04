package am.froshy.launcher.domain;

import java.util.List;

public record MinecraftProfile(
        String id,
        String displayName,
        String javaPath,
        String gameVersion,
        List<String> jvmArgs,
        List<String> gameArgs
) {
    public MinecraftProfile {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El id del perfil es obligatorio");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("El nombre del perfil es obligatorio");
        }
        if (javaPath == null || javaPath.isBlank()) {
            javaPath = "java";
        }
        if (gameVersion == null || gameVersion.isBlank()) {
            gameVersion = "1.20.1";
        }
        jvmArgs = jvmArgs == null ? List.of() : List.copyOf(jvmArgs);
        gameArgs = gameArgs == null ? List.of() : List.copyOf(gameArgs);
    }
}

