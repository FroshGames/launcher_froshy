package am.froshy.launcher.api.internal;

import am.froshy.launcher.application.LauncherService;
import am.froshy.launcher.application.LauncherUpdateService;
import am.froshy.launcher.application.MinecraftVersionDownloader;
import am.froshy.launcher.config.LauncherConfig;
import am.froshy.launcher.domain.LaunchRequest;
import am.froshy.launcher.domain.MinecraftProfile;
import am.froshy.launcher.domain.PreparedLaunchStatus;
import am.froshy.launcher.infrastructure.ProfileStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalApiClientTest {

    private static final MinecraftVersionDownloader FAKE_DOWNLOADER = new MinecraftVersionDownloader() {
        @Override
        public void downloadVersion(String version, Path gameDir,
                                    BiConsumer<Integer, String> progressConsumer) throws java.io.IOException {
            progressConsumer.accept(40, "fake");
            Path versionDir = gameDir.resolve("versions").resolve(version);
            Files.createDirectories(versionDir);
            Files.write(versionDir.resolve(version + ".jar"), new byte[]{'P', 'K', 3, 4, 0, 0, 0, 0});
            Files.writeString(versionDir.resolve(version + ".json"), "{}");
            Files.createDirectories(versionDir.resolve("natives"));
            Files.write(versionDir.resolve("natives").resolve("fake.dll"), new byte[]{1, 2, 3, 4});
            progressConsumer.accept(100, "done");
        }

        @Override
        public am.froshy.launcher.application.VersionInstallation buildInstallation(
                String version, Path gameDir, String username) {
            Path natives = gameDir.resolve("versions").resolve(version).resolve("natives");
            return new am.froshy.launcher.application.VersionInstallation(
                    List.of(),
                    SleepMain.class.getName(),
                    List.of("-cp", System.getProperty("java.class.path")),
                    List.of(username),
                    natives
            );
        }
    };

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateProfileAndLaunchThroughApi() throws InterruptedException {
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
                    List.of("--username", "Alex"),
                    "VANILLA",
                    "",
                    ""
            ));

            assertEquals("builder", created.id());
            assertEquals(1, client.listProfiles().size());

            MinecraftProfile updated = client.updateProfile("builder", new MinecraftProfile(
                    "id-nuevo", // debe ignorarse en modo edicion
                    "Perfil Builder Editado",
                    "java",
                    "1.20.1",
                    List.of("-Xmx3G"),
                    List.of("--username", "Alex"),
                    "VANILLA",
                    "",
                    ""
            ));

            assertEquals("builder", updated.id());
            assertEquals("Perfil Builder Editado", updated.displayName());

            String instancePath = client.getProfileInstancePath("builder");
            assertTrue(instancePath.contains("instances"));
            assertTrue(instancePath.endsWith("builder"));

            PreparedLaunchStatus op = client.startLaunchPreparedAsync(new LaunchRequest("builder", false));
            assertNotNull(op.operationId());

            PreparedLaunchStatus status = op;
            int attempts = 0;
            while (!"DONE".equals(status.state()) && attempts < 40) {
                Thread.sleep(100);
                status = client.getLaunchPreparedStatus(op.operationId());
                attempts++;
            }

            assertEquals("DONE", status.state());
            assertNotNull(status.launchId());
            assertTrue(client.isGameAlive(status.launchId()));

            assertEquals("BOTH", client.getModpackCompatibilityMode());
            assertEquals("MODRINTH_ONLY", client.setModpackCompatibilityMode("MODRINTH_ONLY"));
            assertEquals("MODRINTH_ONLY", client.getModpackCompatibilityMode());
        } finally {
            server.stop();
        }
    }

    public static final class SleepMain {
        public static void main(String[] args) throws Exception {
            Thread.sleep(2_000L);
        }
    }
}
