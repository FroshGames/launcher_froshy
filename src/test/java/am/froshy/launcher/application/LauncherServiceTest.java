package am.froshy.launcher.application;

import am.froshy.launcher.config.LauncherConfig;
import am.froshy.launcher.domain.LaunchRequest;
import am.froshy.launcher.domain.LaunchResult;
import am.froshy.launcher.domain.MinecraftProfile;
import am.froshy.launcher.infrastructure.ProfileStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherServiceTest {

    private static final MinecraftVersionDownloader FAKE_DOWNLOADER = (version, destination, progressConsumer) -> {
        progressConsumer.accept(30);
        Files.createDirectories(destination.getParent());
        Files.write(destination, new byte[]{'P', 'K', 3, 4, 0});
        progressConsumer.accept(100);
    };

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateAndListProfiles() {
        LauncherConfig config = new LauncherConfig(
                tempDir,
                tempDir.resolve("profiles.json"),
                tempDir.resolve("game"),
                7878,
                "1.0-SNAPSHOT",
                ""
        );
        LauncherService service = new LauncherService(config, new ProfileStore(config.profilesFile(), new ObjectMapper()));

        MinecraftProfile profile = new MinecraftProfile(
                "default",
                "Perfil principal",
                "java",
                "1.20.1",
                List.of("-Xmx2G"),
                List.of("--username", "Steve")
        );

        service.createProfile(profile);

        assertEquals(1, service.listProfiles().size());
        assertEquals("default", service.listProfiles().get(0).id());
        service.shutdown();
    }

    @Test
    void shouldBuildLaunchResultWithCommandAndPrepareVersion() {
        LauncherConfig config = new LauncherConfig(
                tempDir,
                tempDir.resolve("profiles.json"),
                tempDir.resolve("game"),
                7878,
                "1.0-SNAPSHOT",
                ""
        );
        LauncherService service = new LauncherService(
                config,
                new ProfileStore(config.profilesFile(), new ObjectMapper()),
                FAKE_DOWNLOADER
        );

        service.createProfile(new MinecraftProfile("pvp", "Perfil PvP", "java", "1.8.9", List.of("-Xmx1G"), List.of()));
        LaunchResult result = service.launch(new LaunchRequest("pvp", false));

        Path versionJar = config.gameDirectory().resolve("versions").resolve("minecraft-1.8.9.jar");
        assertNotNull(result.launchId());
        assertEquals("STARTED", result.status());
        assertTrue(result.commandLine().contains(versionJar.toAbsolutePath().toString()));
        assertTrue(Files.exists(versionJar));
        assertTrue(startsWithJarMagic(versionJar));
        service.shutdown();
    }

    private boolean startsWithJarMagic(Path file) {
        try {
            byte[] header = Files.readAllBytes(file);
            return header.length >= 4 && header[0] == 'P' && header[1] == 'K' && header[2] == 3 && header[3] == 4;
        } catch (IOException ex) {
            return false;
        }
    }
}
