package am.froshy.launcher.domain;

public record DownloadStatus(String downloadId, String target, String state, int progress, String message) {

    /** Constructor de compatibilidad sin mensaje. */
    public DownloadStatus(String downloadId, String target, String state, int progress) {
        this(downloadId, target, state, progress, "");
    }
}


