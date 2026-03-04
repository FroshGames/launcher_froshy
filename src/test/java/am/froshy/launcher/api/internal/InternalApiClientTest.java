package am.froshy.launcher.api.internal;

import am.froshy.launcher.application.LauncherService;
import am.froshy.launcher.config.LauncherConfig;
import am.froshy.launcher.domain.LaunchRequest;
import am.froshy.launcher.domain.LaunchResult;
import am.froshy.launcher.domain.MinecraftProfile;
import am.froshy.launcher.infrastructure.ProfileStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalApiClientTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateProfileAndLaunchThroughApi() {
        LauncherConfig config = new LauncherConfig(tempDir, tempDir.resolve("profiles.json"), tempDir.resolve("game"), 0);
        LauncherService service = new LauncherService(config, new ProfileStore(config.profilesFile(), new ObjectMapper()));
        InternalApiServer server = new InternalApiServer(0, service);
        server.start();

        try {
            InternalApiClient client = new InternalApiClient(URI.create("http://localhost:" + server.getPort() + "/internal/v1"));

            MinecraftProfile created = client.createProfile(new MinecraftProfile(
                    "builder",
                    "Perfil Builder",
                    "java",
                    "1.20.1",
                    List.of("-Xmx2G"),
                    List.of("--username", "Alex")
            ));

            assertEquals("builder", created.id());
            assertEquals(1, client.listProfiles().size());

            LaunchResult launch = client.launch(new LaunchRequest("builder", false));
            assertNotNull(launch.launchId());
            assertTrue(launch.commandLine().contains("minecraft-1.20.1.jar"));
        } finally {
            server.stop();
        }
    }
}

