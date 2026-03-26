package am.froshy.launcher.application;

import am.froshy.launcher.domain.ModpackManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModpackInstallerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldParseNestedModrinthManifest() throws IOException {
        Path zip = tempDir.resolve("nested-modrinth.mrpack");
        String index = """
                {
                  "name": "Nested Modrinth",
                  "versionId": "1.0.0",
                  "dependencies": {
                    "minecraft": "1.20.1",
                    "fabric-loader": "0.15.11"
                  },
                  "files": []
                }
                """;

        createZip(zip, Map.of("my-pack/modrinth.index.json", index));

        ModpackInstaller installer = new ModpackInstaller();
        ModpackManifest manifest = installer.parseModpack(zip);

        assertEquals("MODRINTH", manifest.source());
        assertEquals("1.20.1", manifest.minecraftVersion());
        assertEquals("FABRIC", manifest.loaderType());
        assertEquals("0.15.11", manifest.loaderVersion());
    }

    @Test
    void shouldParseNestedCurseManifest() throws IOException {
        Path zip = tempDir.resolve("nested-curse.zip");
        String manifestJson = """
                {
                  "name": "Nested Curse",
                  "version": "1.2.3",
                  "minecraft": {
                    "version": "1.20.1",
                    "modLoaders": [{"id": "forge-47.3.0"}]
                  },
                  "files": []
                }
                """;

        createZip(zip, Map.of("pack-root/manifest.json", manifestJson));

        ModpackInstaller installer = new ModpackInstaller();
        ModpackManifest manifest = installer.parseModpack(zip);

        assertEquals("CURSEFORGE", manifest.source());
        assertEquals("FORGE", manifest.loaderType());
        assertEquals("47.3.0", manifest.loaderVersion());
    }

    @Test
    void shouldExtractClientOverridesFromNestedMrpack() throws IOException, InterruptedException {
        Path zip = tempDir.resolve("nested-overrides.mrpack");
        String index = """
                {
                  "name": "Nested Overrides",
                  "versionId": "1.0.0",
                  "dependencies": {
                    "minecraft": "1.20.1"
                  },
                  "files": []
                }
                """;

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("pack/modrinth.index.json", index);
        entries.put("pack/client-overrides/config/example.txt", "ok");
        createZip(zip, entries);

        ModpackInstaller installer = new ModpackInstaller();
        ModpackManifest parsed = installer.parseModpack(zip);
        Path gameDir = tempDir.resolve("game");

        installer.installModpackFiles(zip, parsed, gameDir, (p, m) -> { });

        assertTrue(Files.exists(gameDir.resolve("config").resolve("example.txt")));
        assertEquals("ok", Files.readString(gameDir.resolve("config").resolve("example.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void shouldOverwriteExistingOverrideFiles() throws IOException, InterruptedException {
        Path zip = tempDir.resolve("overwrite-overrides.mrpack");
        String index = """
                {
                  "name": "Overwrite Overrides",
                  "versionId": "1.0.0",
                  "dependencies": {
                    "minecraft": "1.20.1"
                  },
                  "files": []
                }
                """;

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("pack/modrinth.index.json", index);
        entries.put("pack/client-overrides/config/example.txt", "new-value");
        createZip(zip, entries);

        ModpackInstaller installer = new ModpackInstaller();
        ModpackManifest parsed = installer.parseModpack(zip);
        Path gameDir = tempDir.resolve("game-overwrite");
        Files.createDirectories(gameDir.resolve("config"));
        Files.writeString(gameDir.resolve("config").resolve("example.txt"), "old-value", StandardCharsets.UTF_8);

        installer.installModpackFiles(zip, parsed, gameDir, (p, m) -> { });

        assertEquals("new-value", Files.readString(gameDir.resolve("config").resolve("example.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void shouldExtractFullClientContentIntoInstance() throws IOException, InterruptedException {
        Path zip = tempDir.resolve("full-content.mrpack");
        String index = """
                {
                  "name": "Full Content",
                  "versionId": "1.0.0",
                  "dependencies": {
                    "minecraft": "1.20.1"
                  },
                  "files": []
                }
                """;

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("pack/modrinth.index.json", index);
        entries.put("pack/client-overrides/config/example.txt", "cfg");
        entries.put("pack/client-overrides/resourcepacks/retro.zip", "rp");
        entries.put("pack/client-overrides/texturepacks/classic.zip", "tp");
        entries.put("pack/client-overrides/shaderpacks/shiny.zip", "sp");
        entries.put("pack/client-overrides/options.txt", "gamma:1.0");
        createZip(zip, entries);

        ModpackInstaller installer = new ModpackInstaller();
        ModpackManifest parsed = installer.parseModpack(zip);
        Path gameDir = tempDir.resolve("instance-full");

        installer.installModpackFiles(zip, parsed, gameDir, (p, m) -> { });

        assertEquals("cfg", Files.readString(gameDir.resolve("config").resolve("example.txt"), StandardCharsets.UTF_8));
        assertEquals("rp", Files.readString(gameDir.resolve("resourcepacks").resolve("retro.zip"), StandardCharsets.UTF_8));
        assertEquals("tp", Files.readString(gameDir.resolve("texturepacks").resolve("classic.zip"), StandardCharsets.UTF_8));
        assertEquals("sp", Files.readString(gameDir.resolve("shaderpacks").resolve("shiny.zip"), StandardCharsets.UTF_8));
        assertEquals("gamma:1.0", Files.readString(gameDir.resolve("options.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void shouldRemovePreviousManagedContentWhenInstallingAnotherModpack() throws IOException, InterruptedException {
        Path firstZip = tempDir.resolve("first-pack.mrpack");
        Path secondZip = tempDir.resolve("second-pack.mrpack");
        String index = """
                {
                  "name": "State Test",
                  "versionId": "1.0.0",
                  "dependencies": {
                    "minecraft": "1.20.1"
                  },
                  "files": []
                }
                """;

        createZip(firstZip, Map.of(
                "pack/modrinth.index.json", index,
                "pack/client-overrides/resourcepacks/obsolete.zip", "old-pack",
                "pack/client-overrides/config/old.cfg", "legacy"
        ));
        createZip(secondZip, Map.of(
                "pack/modrinth.index.json", index,
                "pack/client-overrides/resourcepacks/current.zip", "new-pack"
        ));

        ModpackInstaller installer = new ModpackInstaller();
        Path gameDir = tempDir.resolve("instance-state");

        installer.installModpackFiles(firstZip, installer.parseModpack(firstZip), gameDir, (p, m) -> { });
        installer.installModpackFiles(secondZip, installer.parseModpack(secondZip), gameDir, (p, m) -> { });

        assertTrue(Files.notExists(gameDir.resolve("resourcepacks").resolve("obsolete.zip")));
        assertTrue(Files.notExists(gameDir.resolve("config").resolve("old.cfg")));
        assertEquals("new-pack", Files.readString(gameDir.resolve("resourcepacks").resolve("current.zip"), StandardCharsets.UTF_8));
    }

    @Test
    void shouldExtractClientContentFromMinecraftFolderStructure() throws IOException, InterruptedException {
        Path zip = tempDir.resolve("minecraft-structure.mrpack");
        String index = """
                {
                  "name": "Minecraft Structure",
                  "versionId": "1.0.0",
                  "dependencies": {
                    "minecraft": "1.20.1"
                  },
                  "files": []
                }
                """;

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("pack/modrinth.index.json", index);
        entries.put("pack/minecraft/config/from-minecraft.cfg", "m-root");
        entries.put("pack/minecraft/resourcepacks/pack.zip", "rp-root");
        entries.put("pack/.minecraft/options.txt", "opts-root");
        createZip(zip, entries);

        ModpackInstaller installer = new ModpackInstaller();
        Path gameDir = tempDir.resolve("instance-minecraft-root");
        installer.installModpackFiles(zip, installer.parseModpack(zip), gameDir, (p, m) -> { });

        assertEquals("m-root", Files.readString(gameDir.resolve("config").resolve("from-minecraft.cfg"), StandardCharsets.UTF_8));
        assertEquals("rp-root", Files.readString(gameDir.resolve("resourcepacks").resolve("pack.zip"), StandardCharsets.UTF_8));
        assertEquals("opts-root", Files.readString(gameDir.resolve("options.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void shouldExtractClientContentFromZipRootWhenPresent() throws IOException, InterruptedException {
        Path zip = tempDir.resolve("root-content.mrpack");
        String index = """
                {
                  "name": "Root Content",
                  "versionId": "1.0.0",
                  "dependencies": {
                    "minecraft": "1.20.1"
                  },
                  "files": []
                }
                """;

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("modrinth.index.json", index);
        entries.put("config/root.cfg", "root-config");
        entries.put("shaderpacks/root.zip", "root-shader");
        entries.put("texturepacks/root.zip", "root-texture");
        createZip(zip, entries);

        ModpackInstaller installer = new ModpackInstaller();
        Path gameDir = tempDir.resolve("instance-root-content");
        installer.installModpackFiles(zip, installer.parseModpack(zip), gameDir, (p, m) -> { });

        assertEquals("root-config", Files.readString(gameDir.resolve("config").resolve("root.cfg"), StandardCharsets.UTF_8));
        assertEquals("root-shader", Files.readString(gameDir.resolve("shaderpacks").resolve("root.zip"), StandardCharsets.UTF_8));
        assertEquals("root-texture", Files.readString(gameDir.resolve("texturepacks").resolve("root.zip"), StandardCharsets.UTF_8));
    }

    private void createZip(Path zipPath, Map<String, String> entries) throws IOException {
        try (OutputStream out = Files.newOutputStream(zipPath);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }
}

