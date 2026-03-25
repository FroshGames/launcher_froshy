package am.froshy.launcher.domain;

public record ModpackFile(
        String path,
        String downloadUrl,
        String sha1,
        String sha512,
        long size,
        boolean required,
        boolean clientOnly
) {
    /** Constructor de conveniencia para mods con datos mínimos. */
    public ModpackFile(String path, String downloadUrl, String sha1, long size, boolean required) {
        this(path, downloadUrl, sha1, null, size, required, false);
    }
}

