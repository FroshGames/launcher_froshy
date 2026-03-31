package am.froshy.mialu.launcher.domain;

public record PreparedLaunchStatus(
        String operationId,
        String profileId,
        String gameVersion,
        String state,
        int progress,
        String message,
        String launchId
) {
    public PreparedLaunchStatus(String operationId, String profileId, String gameVersion,
                                String state, int progress, String message) {
        this(operationId, profileId, gameVersion, state, progress, message, null);
    }
}



