package am.froshy.launcher.domain;

import java.util.List;

/**
 * Representa el manifiesto parseado de un modpack (Modrinth o CurseForge).
 */
public record ModpackManifest(
        String name,
        String packVersion,
        String minecraftVersion,
        String loaderType,    // VANILLA | FORGE | NEOFORGE | FABRIC | QUILT
        String loaderVersion, // versión del loader (ej. "47.3.0" para Forge)
        List<ModpackFile> files,
        String source         // MODRINTH | CURSEFORGE | CUSTOM
) {}

