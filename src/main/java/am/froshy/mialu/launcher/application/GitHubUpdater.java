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

    private static final String GITHUB_API_URL = "https://api.github.com/repos/FroshGames/launcher_froshy/releases";
    private static final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Revisa GitHub Releases y descarga la ultima version si hay un .exe nuevo.
     * @param currentVersion version actual, e.g. "0.7-DevUpdate"
     */
    public static void checkForUpdatesAndInstall(String currentVersion) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_URL))
                    .header("Accept", "application/vnd.github.v3+json")
                    .GET();

            String token = System.getenv("GITHUB_TOKEN");
            if (token != null && !token.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                System.err.println("Error 404: Repositorio no encontrado o privado.");
                javax.swing.JOptionPane.showMessageDialog(null, "Error verificando updates (HTTP 404).\nEl repositorio (" + GITHUB_API_URL + ") es privado o no existe.\nHázlo público o define la variable de entorno GITHUB_TOKEN.");
                return;
            } else if (response.statusCode() != 200) {
                System.err.println("Error verificando updates: HTTP " + response.statusCode());
                javax.swing.JOptionPane.showMessageDialog(null, "Error verificando updates (HTTP " + response.statusCode() + ").");
                return;
            }

            JsonNode rootArray = mapper.readTree(response.body());
            if (!rootArray.isArray() || rootArray.isEmpty()) {
                System.out.println("No se encontraron releases.");
                javax.swing.JOptionPane.showMessageDialog(null, "No se encontraron releases en GitHub.");
                return;
            }
            
            JsonNode latestRelease = rootArray.get(0);
            String latestVersion = latestRelease.get("tag_name").asText();
            
            String cleanLatest = latestVersion.replaceAll("^v|V", "");
            String cleanCurrent = currentVersion.replaceAll("^v|V", "");

            if (!cleanLatest.equals(cleanCurrent)) {
                JsonNode assets = latestRelease.get("assets");
                boolean foundAsset = false;
                for (JsonNode asset : assets) {
                    String assetName = asset.get("name").asText().toLowerCase();
                    if (assetName.endsWith(".exe")) {
                        foundAsset = true;
                        String downloadUrl = asset.has("url") ? asset.get("url").asText() : asset.get("browser_download_url").asText();
                        javax.swing.JOptionPane.showMessageDialog(null, "Actualización " + latestVersion + " encontrada. Descargando ahora...");
                        downloadAndApplyUpdate(downloadUrl, assetName);
                        break;
                    }
                }
                if (!foundAsset) {
                    javax.swing.JOptionPane.showMessageDialog(null, "Se encontró la versión " + latestVersion + " pero sin instalador o exe.");
                }
            } else {
                javax.swing.JOptionPane.showMessageDialog(null, "Ya tienes la última versión instalada (" + currentVersion + ").");
            }
        } catch (Exception e) {
            e.printStackTrace();
            javax.swing.JOptionPane.showMessageDialog(null, "Excepción al buscar updates: " + e.getMessage());
        }
    }

    private static void downloadAndApplyUpdate(String downloadUrl, String fileName) throws Exception {
        System.out.println("Descargando actualizacion desde: " + downloadUrl);
        Path updateFile = Paths.get(System.getProperty("java.io.tmpdir"), fileName);
        
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).GET();

        String token = System.getenv("GITHUB_TOKEN");
        if (token != null && !token.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + token);
            requestBuilder.header("Accept", "application/octet-stream");
        }

        HttpRequest request = requestBuilder.build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofFile(updateFile));

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
