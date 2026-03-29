package am.froshy.mialu.launcher;

import am.froshy.mialu.launcher.api.internal.InternalApiServer;
import am.froshy.mialu.launcher.application.LauncherService;
import am.froshy.mialu.launcher.application.LauncherUpdateService;
import am.froshy.mialu.launcher.config.LauncherConfig;
import am.froshy.mialu.launcher.infrastructure.ProfileStore;
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
        LauncherUpdateService updateService = new LauncherUpdateService(config.launcherVersion(), config.updatesMetadataUrl());
        InternalApiServer apiServer = new InternalApiServer(config.internalApiPort(), launcherService, updateService);
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


