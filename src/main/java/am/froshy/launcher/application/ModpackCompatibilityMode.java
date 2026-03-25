package am.froshy.launcher.application;

/**
 * Define que fuentes de modpack están permitidas.
 *
 * BOTH            -> acepta Modrinth y CurseForge
 * MODRINTH_ONLY   -> solo modpacks Modrinth (.mrpack)
 * CURSEFORGE_ONLY -> solo modpacks CurseForge (.zip con manifest.json)
 */
public enum ModpackCompatibilityMode {
    BOTH,
    MODRINTH_ONLY,
    CURSEFORGE_ONLY;

    public static ModpackCompatibilityMode fromEnvironment(String raw) {
        if (raw == null || raw.isBlank()) return BOTH;
        return switch (raw.trim().toUpperCase()) {
            case "MODRINTH_ONLY", "MODRINTH" -> MODRINTH_ONLY;
            case "CURSEFORGE_ONLY", "CURSEFORGE" -> CURSEFORGE_ONLY;
            default -> BOTH;
        };
    }

    public boolean isAllowed(String source) {
        if (source == null || source.isBlank()) return false;
        return switch (this) {
            case BOTH -> true;
            case MODRINTH_ONLY -> "MODRINTH".equalsIgnoreCase(source);
            case CURSEFORGE_ONLY -> "CURSEFORGE".equalsIgnoreCase(source);
        };
    }
}

