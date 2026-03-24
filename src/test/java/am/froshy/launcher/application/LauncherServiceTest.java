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
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherServiceTest {

    /** Crea un MinecraftVersionDownloader falso que simula una instalación mínima. */
    private static MinecraftVersionDownloader fakeDownloader() {
        return new MinecraftVersionDownloader() {
            @Override
            public void downloadVersion(String version, Path gameDir,
                                        BiConsumer<Integer, String> progress)
                    throws IOException {
                progress.accept(30, "Descargando fake…");
                // Crear estructura mínima: versions/{version}/{version}.jar y .json
                Path versionDir = gameDir.resolve("versions").resolve(version);
                Files.createDirectories(versionDir);
                Path jar  = versionDir.resolve(version + ".jar");
                Path json = versionDir.resolve(version + ".json");
                // ZIP magic bytes para pasar la validación de jar
                Files.write(jar, new byte[]{'P', 'K', 3, 4, 0, 0, 0, 0});
                Files.writeString(json, "{}");
                progress.accept(100, "Listo");
            }

            @Override
            public VersionInstallation buildInstallation(String version, Path gameDir,
                                                          String username) throws IOException {
                Path versionDir = gameDir.resolve("versions").resolve(version);
                Path jar        = versionDir.resolve(version + ".jar");
                Path nativesDir = versionDir.resolve("natives");
                Files.createDirectories(nativesDir);
                return new VersionInstallation(
                        List.of(jar),
                        "net.minecraft.client.main.Main",
                        List.of("-cp", jar.toAbsolutePath().toString()),
                        List.of("--username", username, "--version", version),
                        nativesDir
                );
            }
        };
    }

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
        LauncherService service = new LauncherService(
                config, new ProfileStore(config.profilesFile(), new ObjectMapper()));

        MinecraftProfile profile = new MinecraftProfile(
                "default", "Perfil principal", "java", "1.20.1",
                List.of("-Xmx2G"), List.of("--username", "Steve")
        );

        service.createProfile(profile);

        assertEquals(1, service.listProfiles().size());
        assertEquals("default", service.listProfiles().get(0).id());
        service.shutdown();
    }

    @Test
    void shouldBuildLaunchResultWithCommandAndPrepareVersion() throws Exception {
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
                fakeDownloader()
        );

        try {
            service.createProfile(new MinecraftProfile(
                    "pvp", "Perfil PvP", "java", "1.8.9",
                    List.of("-Xmx1G"), List.of()));

            var prep = service.prepareVersion("1.8.9");
            int tries = 0;
            while (!"DONE".equals(service.getDownloadStatus(prep.downloadId()).orElseThrow().state()) && tries < 20) {
                Thread.sleep(100);
                tries++;
            }

            Path versionJar = config.gameDirectory()
                    .resolve("versions").resolve("1.8.9").resolve("1.8.9.jar");
            assertTrue(Files.exists(versionJar), "El JAR debe existir en: " + versionJar);
            assertTrue(startsWithJarMagic(versionJar), "El JAR debe tener magic bytes PK");

            LaunchResult result = service.launch(new LaunchRequest("pvp", false));
            assertNotNull(result.launchId());
            assertEquals("STARTED", result.status());
            assertTrue(result.commandLine().contains(versionJar.toAbsolutePath().toString()),
                    "El comando debe incluir el JAR: " + result.commandLine());
        } finally {
            service.shutdown();
        }
    }

    private boolean startsWithJarMagic(Path file) {
        try {
            byte[] header = Files.readAllBytes(file);
            return header.length >= 4
                    && header[0] == 'P' && header[1] == 'K'
                    && header[2] == 3  && header[3] == 4;
        } catch (IOException ex) {
            return false;
        }
    }
}
