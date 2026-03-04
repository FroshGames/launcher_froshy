package am.froshy.launcher.application;

import am.froshy.launcher.config.LauncherConfig;
import am.froshy.launcher.domain.LaunchRequest;
import am.froshy.launcher.domain.LaunchResult;
import am.froshy.launcher.domain.MinecraftProfile;
import am.froshy.launcher.infrastructure.ProfileStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateAndListProfiles() {
        LauncherConfig config = new LauncherConfig(tempDir, tempDir.resolve("profiles.json"), tempDir.resolve("game"), 7878);
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
    void shouldBuildLaunchResultWithCommand() {
        LauncherConfig config = new LauncherConfig(tempDir, tempDir.resolve("profiles.json"), tempDir.resolve("game"), 7878);
        LauncherService service = new LauncherService(config, new ProfileStore(config.profilesFile(), new ObjectMapper()));

        service.createProfile(new MinecraftProfile("pvp", "Perfil PvP", "java", "1.8.9", List.of("-Xmx1G"), List.of()));
        LaunchResult result = service.launch(new LaunchRequest("pvp", false));

        assertNotNull(result.launchId());
        assertEquals("STARTED", result.status());
        assertTrue(result.commandLine().contains("minecraft-1.8.9.jar"));
        service.shutdown();
    }
}
