package am.froshy.mialu.launcher.domain;

public record LaunchRequest(String profileId, boolean demoMode) {
    public LaunchRequest {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId es obligatorio");
        }
    }
}



