package am.froshy.launcher;

import am.froshy.launcher.api.internal.InternalApiServer;
import am.froshy.launcher.application.LauncherService;
import am.froshy.launcher.config.LauncherConfig;
import am.froshy.launcher.infrastructure.ProfileStore;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;

public final class LauncherRuntime {
    private final LauncherConfig config;
    private final InternalApiServer apiServer;

    private LauncherRuntime(LauncherConfig config, InternalApiServer apiServer) {
        this.config = config;
        this.apiServer = apiServer;
    }

    public static LauncherRuntime start() {
        LauncherConfig config = LauncherConfig.fromEnvironment();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        LauncherService launcherService = new LauncherService(config, new ProfileStore(config.profilesFile(), objectMapper));
        InternalApiServer apiServer = new InternalApiServer(config.internalApiPort(), launcherService);
        apiServer.start();

        return new LauncherRuntime(config, apiServer);
    }

    public URI apiBaseUri() {
        return URI.create("http://localhost:" + apiServer.getPort() + "/internal/v1");
    }

    public int apiPort() {
        return apiServer.getPort();
    }

    public LauncherConfig config() {
        return config;
    }

    public void stop() {
        apiServer.stop();
    }
}

