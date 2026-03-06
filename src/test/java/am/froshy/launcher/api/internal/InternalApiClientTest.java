package am.froshy.launcher.api.internal;

import am.froshy.launcher.application.LauncherService;
import am.froshy.launcher.application.LauncherUpdateService;
import am.froshy.launcher.application.MinecraftVersionDownloader;
import am.froshy.launcher.config.LauncherConfig;
import am.froshy.launcher.domain.DownloadStatus;
import am.froshy.launcher.domain.LaunchRequest;
import am.froshy.launcher.domain.LaunchResult;
import am.froshy.launcher.domain.MinecraftProfile;
import am.froshy.launcher.infrastructure.ProfileStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalApiClientTest {

    private static final MinecraftVersionDownloader FAKE_DOWNLOADER = (version, destination, progressConsumer) -> {
        progressConsumer.accept(40);
        Files.createDirectories(destination.getParent());
        Files.write(destination, new byte[]{'P', 'K', 3, 4, 0});
        progressConsumer.accept(100);
    };

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateProfileAndLaunchThroughApi() throws Exception {
        LauncherConfig config = new LauncherConfig(
                tempDir,
                tempDir.resolve("profiles.json"),
                tempDir.resolve("game"),
                0,
                "1.0-SNAPSHOT",
                ""
        );
        LauncherService service = new LauncherService(
                config,
                new ProfileStore(config.profilesFile(), new ObjectMapper()),
                FAKE_DOWNLOADER
        );
        LauncherUpdateService updateService = new LauncherUpdateService(config.launcherVersion(), config.updatesMetadataUrl());
        InternalApiServer server = new InternalApiServer(0, service, updateService);
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

            DownloadStatus preparation = client.prepareVersion("1.20.1");
            DownloadStatus status = preparation;
            int attempts = 0;
            while (!"DONE".equals(status.state()) && attempts < 10) {
                Thread.sleep(100);
                status = client.getDownloadStatus(preparation.downloadId());
                attempts++;
            }

            assertEquals("DONE", status.state());

            LaunchResult launch = client.launch(new LaunchRequest("builder", false));
            assertNotNull(launch.launchId());
            assertTrue(launch.commandLine().contains("minecraft-1.20.1.jar"));
        } finally {
            server.stop();
        }
    }
}
