package am.froshy.mialu.launcher.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class GitHubUpdater {

    private static final String GITHUB_API_URL = "https://api.github.com/repos/FroshGames/launcher_froshy/releases/latest";
    private static final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Revisa GitHub Releases y descarga la ultima version si hay un .exe nuevo.
     * @param currentVersion version actual, e.g. "0.7-DevUpdate"
     */
    public static void checkForUpdatesAndInstall(String currentVersion) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_URL))
                    .header("Accept", "application/vnd.github.v3+json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return;

            JsonNode root = mapper.readTree(response.body());
            String latestVersion = root.get("tag_name").asText();

            if (!latestVersion.equals(currentVersion)) {
                JsonNode assets = root.get("assets");
                for (JsonNode asset : assets) {
                    String assetName = asset.get("name").asText().toLowerCase();
                    if (assetName.endsWith(".exe")) {
                        String downloadUrl = asset.get("browser_download_url").asText();
                        downloadAndApplyUpdate(downloadUrl, assetName);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void downloadAndApplyUpdate(String downloadUrl, String fileName) throws Exception {
        System.out.println("Descargando actualizacion desde: " + downloadUrl);
        Path updateFile = Paths.get(System.getProperty("java.io.tmpdir"), fileName);
        
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).GET().build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofFile(updateFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE));
        
        System.out.println("Actualizacion descargada. Preparando reemplazo...");
        applyUpdate(updateFile, fileName);
    }

    private static void applyUpdate(Path updateFile, String fileName) throws IOException {
        if (fileName.contains("installer") || fileName.contains("setup")) {
            // Es un instalador, solo lo ejecutamos
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "\"\"", "\"" + updateFile.toAbsolutePath() + "\""});
            System.exit(0);
            return;
        }

        // Es un portable o ejecutable directo, lo reemplazamos
        // Fallback robusto para encontrar el exe actual
        Path currentExeDir = Paths.get(".").toAbsolutePath().normalize();
        Path targetExe = currentExeDir.resolve("mialulauncher.exe");

        // Crea un bat temporal para reemplazar el archivo mientras se cierra el launcher
        Path batPath = Paths.get(System.getProperty("java.io.tmpdir"), "update_launcher.bat");
        String batContent = "@echo off\n"
                + "ping 127.0.0.1 -n 3 > nul\n"
                + "move /y \"" + updateFile.toAbsolutePath() + "\" \"" + targetExe.toAbsolutePath() + "\"\n"
                + "start \"\" \"" + targetExe.toAbsolutePath() + "\"\n"
                + "del \"%~f0\"";

        Files.writeString(batPath, batContent);

        // Ejecutar BAT y cerrar
        Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "\"\"", "\"" + batPath.toAbsolutePath() + "\""});
        System.exit(0);
    }
}
