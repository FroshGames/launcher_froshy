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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherServiceTest {

    private static MinecraftVersionDownloader fakeDownloader() {
        return new MinecraftVersionDownloader() {
            @Override
            public void downloadVersion(String version, Path gameDir,
                                        BiConsumer<Integer, String> progressConsumer)
                    throws IOException {
                progressConsumer.accept(30, "Descargando fake JAR...");
                Path versionDir = gameDir.resolve("versions").resolve(version);
                Files.createDirectories(versionDir);
                Path jar = versionDir.resolve(version + ".jar");
                Files.write(jar, new byte[]{'P', 'K', 3, 4, 0, 0, 0, 0, 0, 0});
                progressConsumer.accept(100, "Descarga completada.");
            }

            @Override
            public VersionInstallation buildInstallation(String version, Path gameDir,
                                                         String username) {
                Path jar = gameDir.resolve("versions").resolve(version).resolve(version + ".jar");
                return new VersionInstallation(
                        List.of(jar),
                        "net.minecraft.client.main.Main",
                        List.of("-cp", jar.toAbsolutePath().toString()),
                        List.of("--username", username),
                        gameDir.resolve("natives")
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
                config,
                new ProfileStore(config.profilesFile(), new ObjectMapper())
        );

        MinecraftProfile profile = new MinecraftProfile(
                "default",
                "Perfil principal",
                "java",
                "1.20.1",
                List.of("-Xmx2G"),
                List.of("--username", "Steve"),
                "VANILLA",
                "",
                ""
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
                fakeDownloader()
        );

        try {
            service.createProfile(new MinecraftProfile(
                    "pvp", "Perfil PvP", "java", "1.8.9", List.of("-Xmx1G"), List.of(),
                    "VANILLA", "", ""
            ));

            LaunchResult result = service.prepareAndLaunch(new LaunchRequest("pvp", false));
            Path versionJar = config.gameDirectory().resolve("versions").resolve("1.8.9").resolve("1.8.9.jar");

            assertNotNull(result.launchId());
            assertEquals("STARTED", result.status());
            assertTrue(result.commandLine().contains(versionJar.toAbsolutePath().toString()));
            assertTrue(Files.exists(versionJar));
            assertTrue(startsWithJarMagic(versionJar));
        } finally {
            service.shutdown();
        }
    }

    @Test
    void shouldIgnoreIncomingIdWhenUpdatingProfile() {
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
                new ProfileStore(config.profilesFile(), new ObjectMapper())
        );

        try {
            service.createProfile(new MinecraftProfile(
                    "survival", "Survival", "java", "1.20.1",
                    List.of("-Xmx2G"), List.of("--username", "Steve"),
                    "VANILLA", "", ""
            ));

            MinecraftProfile updated = service.updateProfile("survival", new MinecraftProfile(
                    "otro-id", "Survival Editado", "java", "1.20.1",
                    List.of("-Xmx3G"), List.of("--username", "Alex"),
                    "FABRIC", "0.15.11", ""
            ));

            assertEquals("survival", updated.id());
            assertEquals("Survival Editado", updated.displayName());
            assertEquals(1, service.listProfiles().size());
            assertEquals("survival", service.listProfiles().get(0).id());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void shouldAcceptCurseForgeModpackWhenCompatibilityIsBoth() throws IOException {
        LauncherConfig config = new LauncherConfig(
                tempDir,
                tempDir.resolve("profiles.json"),
                tempDir.resolve("game"),
                7878,
                "1.0-SNAPSHOT",
                ""
        );
        Path modpack = createCurseManifestZip(tempDir.resolve("cf-pack.zip"));

        LauncherService service = new LauncherService(
                config,
                new ProfileStore(config.profilesFile(), new ObjectMapper()),
                fakeDownloader(),
                new ModpackInstaller(),
                ModpackCompatibilityMode.BOTH
        );

        try {
            MinecraftProfile profile = new MinecraftProfile(
                    "cf", "Curse Pack", "java", "1.20.1",
                    List.of("-Xmx1G"), List.of("--username", "Alex"),
                    "VANILLA", "", modpack.toAbsolutePath().toString()
            );
            service.createProfile(profile);

            assertNotNull(service.launch(new LaunchRequest("cf", false)).launchId());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void shouldRejectCurseForgeModpackWhenCompatibilityIsModrinthOnly() throws IOException {
        LauncherConfig config = new LauncherConfig(
                tempDir,
                tempDir.resolve("profiles.json"),
                tempDir.resolve("game"),
                7878,
                "0.5-SNAPSHOT",
                ""
        );
        Path modpack = createCurseManifestZip(tempDir.resolve("cf-blocked.zip"));

        LauncherService service = new LauncherService(
                config,
                new ProfileStore(config.profilesFile(), new ObjectMapper()),
                fakeDownloader(),
                new ModpackInstaller(),
                ModpackCompatibilityMode.MODRINTH_ONLY
        );

        try {
            MinecraftProfile profile = new MinecraftProfile(
                    "cf-blocked", "Curse Pack", "java", "1.20.1",
                    List.of("-Xmx1G"), List.of("--username", "Alex"),
                    "VANILLA", "", modpack.toAbsolutePath().toString()
            );
            service.createProfile(profile);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.launch(new LaunchRequest("cf-blocked", false)));
            assertTrue(ex.getMessage().contains("Formato de modpack no permitido"));
        } finally {
            service.shutdown();
        }
    }

    private Path createCurseManifestZip(Path zipPath) throws IOException {
        String manifest = """
                {
                  "minecraft": { "version": "1.20.1", "modLoaders": [] },
                  "name": "CF Test",
                  "version": "1.0.0",
                  "files": []
                }
                """;
        try (OutputStream out = Files.newOutputStream(zipPath);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return zipPath;
    }

    private boolean startsWithJarMagic(Path file) {
        try {
            byte[] header = Files.readAllBytes(file);
            return header.length >= 4
                    && header[0] == 'P'
                    && header[1] == 'K'
                    && header[2] == 3
                    && header[3] == 4;
        } catch (IOException ex) {
            return false;
        }
    }
}
