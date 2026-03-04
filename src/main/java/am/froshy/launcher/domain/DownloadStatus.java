package am.froshy.launcher.domain;

public record DownloadStatus(String downloadId, String target, String state, int progress) {
}

