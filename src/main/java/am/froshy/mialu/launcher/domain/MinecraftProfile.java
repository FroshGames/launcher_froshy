package am.froshy.mialu.launcher.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MinecraftProfile(
        String id,
        String displayName,
        String javaPath,
        String gameVersion,
        List<String> jvmArgs,
        List<String> gameArgs,
        String loaderType,    // VANILLA | FORGE | NEOFORGE | FABRIC | QUILT
        String loaderVersion, // versión del loader (ej. "47.3.0")
        String modpackPath    // ruta absoluta al archivo de modpack (null = sin modpack)
) {
    public MinecraftProfile {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("El id del perfil es obligatorio");
        if (displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("El nombre del perfil es obligatorio");
        if (javaPath == null || javaPath.isBlank()) javaPath = "java";
        if (gameVersion == null || gameVersion.isBlank()) gameVersion = "1.20.1";
        if (loaderType == null || loaderType.isBlank()) loaderType = "VANILLA";
        if (loaderVersion == null) loaderVersion = "";
        if (modpackPath == null) modpackPath = "";
        jvmArgs  = jvmArgs  == null ? List.of() : List.copyOf(jvmArgs);
        gameArgs = gameArgs == null ? List.of() : List.copyOf(gameArgs);
    }

    /** Factory Jackson para compatibilidad con perfiles guardados sin los nuevos campos. */
    @JsonCreator
    public static MinecraftProfile fromJson(
            @JsonProperty("id")            String id,
            @JsonProperty("displayName")   String displayName,
            @JsonProperty("javaPath")      String javaPath,
            @JsonProperty("gameVersion")   String gameVersion,
            @JsonProperty("jvmArgs")       List<String> jvmArgs,
            @JsonProperty("gameArgs")      List<String> gameArgs,
            @JsonProperty("loaderType")    String loaderType,
            @JsonProperty("loaderVersion") String loaderVersion,
            @JsonProperty("modpackPath")   String modpackPath) {
        return new MinecraftProfile(id, displayName, javaPath, gameVersion,
                jvmArgs, gameArgs, loaderType, loaderVersion, modpackPath);
    }

    /** Crea un perfil vanilla sin modpack (retrocompatibilidad). */
    public static MinecraftProfile vanilla(String id, String displayName, String javaPath,
            String gameVersion, List<String> jvmArgs, List<String> gameArgs) {
        return new MinecraftProfile(id, displayName, javaPath, gameVersion,
                jvmArgs, gameArgs, "VANILLA", "", "");
    }

    public boolean hasModpack() {
        return modpackPath != null && !modpackPath.isBlank();
    }

    public boolean hasLoader() {
        return loaderType != null && !"VANILLA".equalsIgnoreCase(loaderType) && !loaderType.isBlank();
    }
}



