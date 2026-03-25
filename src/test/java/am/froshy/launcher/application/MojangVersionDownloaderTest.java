package am.froshy.launcher.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MojangVersionDownloaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSubstituteForgeModulePathPlaceholders() throws IOException {
        Path gameDir = tempDir.resolve("game");
        Path versionDir = gameDir.resolve("versions").resolve("forge-test");
        Path librariesDir = gameDir.resolve("libraries");

        Files.createDirectories(versionDir);
        Files.createDirectories(librariesDir);
        Files.createDirectories(gameDir.resolve("assets").resolve("indexes"));

        Path secureJar = librariesDir.resolve("cpw/mods/securejarhandler/2.1.10/securejarhandler-2.1.10.jar");
        Path bootstrapJar = librariesDir.resolve("cpw/mods/bootstraplauncher/1.1.2/bootstraplauncher-1.1.2.jar");
        Files.createDirectories(secureJar.getParent());
        Files.createDirectories(bootstrapJar.getParent());
        Files.write(secureJar, new byte[]{'P', 'K', 3, 4});
        Files.write(bootstrapJar, new byte[]{'P', 'K', 3, 4});
        Files.write(versionDir.resolve("forge-test.jar"), new byte[]{'P', 'K', 3, 4});

        String versionJson = """
                {
                  "id": "forge-test",
                  "mainClass": "cpw.mods.bootstraplauncher.BootstrapLauncher",
                  "type": "release",
                  "assetIndex": { "id": "1.20" },
                  "libraries": [
                    {
                      "name": "cpw.mods:securejarhandler:2.1.10",
                      "downloads": {
                        "artifact": {
                          "path": "cpw/mods/securejarhandler/2.1.10/securejarhandler-2.1.10.jar"
                        }
                      }
                    },
                    {
                      "name": "cpw.mods:bootstraplauncher:1.1.2",
                      "downloads": {
                        "artifact": {
                          "path": "cpw/mods/bootstraplauncher/1.1.2/bootstraplauncher-1.1.2.jar"
                        }
                      }
                    }
                  ],
                  "arguments": {
                    "jvm": [
                      "-p",
                      "${library_directory}/cpw/mods/securejarhandler/2.1.10/securejarhandler-2.1.10.jar${classpath_separator}${library_directory}/cpw/mods/bootstraplauncher/1.1.2/bootstraplauncher-1.1.2.jar",
                      "--add-modules",
                      "ALL-MODULE-PATH",
                      "--add-opens",
                      "java.base/java.lang.invoke=cpw.mods.securejarhandler",
                      "-DlibraryDirectory=${library_directory}",
                      "-cp",
                      "${classpath}"
                    ],
                    "game": [
                      "--username",
                      "${auth_player_name}"
                    ]
                  }
                }
                """;
        Files.writeString(versionDir.resolve("forge-test.json"), versionJson);

        MojangVersionDownloader downloader = new MojangVersionDownloader();
        VersionInstallation install = downloader.buildInstallation("forge-test", gameDir, "Steve");

        assertEquals("cpw.mods.bootstraplauncher.BootstrapLauncher", install.mainClass());
        assertTrue(install.jvmArguments().contains("--add-modules"));
        assertTrue(install.jvmArguments().contains("ALL-MODULE-PATH"));
        assertTrue(install.jvmArguments().contains("java.base/java.lang.invoke=cpw.mods.securejarhandler"));
        assertTrue(install.gameArguments().containsAll(List.of("--username", "Steve")));
        assertTrue(install.jvmArguments().stream().noneMatch(arg -> arg.contains("${")));

        int modulePathIndex = install.jvmArguments().indexOf("-p");
        assertTrue(modulePathIndex >= 0);
        String modulePath = install.jvmArguments().get(modulePathIndex + 1);
        String normalizedModulePath = modulePath.replace('\\', '/');
        assertTrue(normalizedModulePath.contains(secureJar.toAbsolutePath().toString().replace('\\', '/')));
        assertTrue(normalizedModulePath.contains(bootstrapJar.toAbsolutePath().toString().replace('\\', '/')));
        assertTrue(modulePath.contains(java.io.File.pathSeparator));

        assertTrue(install.jvmArguments().stream()
                .anyMatch(arg -> arg.equals("-DlibraryDirectory=" + librariesDir.toAbsolutePath())));
        assertFalse(install.classpath().isEmpty());
    }
}


