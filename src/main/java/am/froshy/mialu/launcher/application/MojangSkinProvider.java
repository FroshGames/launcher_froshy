package am.froshy.mialu.launcher.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Proveedor de skins de Mojang.
 * Obtiene información de skins de jugadores premium de Minecraft.
 */
public final class MojangSkinProvider {
    private static final String MOJANG_API = "https://api.mojang.com";
    private static final String SESSION_API = "https://sessionserver.mojang.com";
    
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    
    public MojangSkinProvider() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.mapper = new ObjectMapper();
    }
    
    /**
     * Obtiene el UUID de un jugador por su nombre de usuario.
     * @param username Nombre de usuario de Minecraft
     * @return UUID del jugador si existe, empty() si no
     */
    public Optional<String> getUuidByUsername(String username) {
        try {
            String url = MOJANG_API + "/users/profiles/minecraft/" + username;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode node = mapper.readTree(response.body());
                String uuid = node.get("id").asText();
                return Optional.of(uuid);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    /**
     * Obtiene la información de perfil (incluyendo skin) de un jugador por su UUID.
     * @param uuid UUID del jugador
     * @return Path al archivo de texture (skin) descargado, empty() si falla
     */
    public Optional<Path> downloadSkinByUuid(String uuid, Path cachePath) {
        try {
            String url = SESSION_API + "/session/minecraft/profile/" + uuid + "?unsigned=false";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            
            JsonNode profileNode = mapper.readTree(response.body());
            JsonNode propertiesArray = profileNode.get("properties");
            
            if (propertiesArray == null || propertiesArray.isEmpty()) {
                return Optional.empty();
            }
            
            // Buscar la propiedad de textures
            for (JsonNode prop : propertiesArray) {
                if ("textures".equals(prop.get("name").asText())) {
                    String texturesJson = prop.get("value").asText();
                    JsonNode texturesNode = mapper.readTree(texturesJson);
                    
                    JsonNode texturesObj = texturesNode.get("textures");
                    if (texturesObj != null && texturesObj.has("SKIN")) {
                        String skinUrl = texturesObj.get("SKIN").get("url").asText();
                        return downloadSkinFromUrl(skinUrl, cachePath);
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    /**
     * Descarga una skin desde una URL.
     * @param skinUrl URL de la skin
     * @param cachePath Ruta donde guardar la skin
     * @return Path al archivo descargado
     */
    private Optional<Path> downloadSkinFromUrl(String skinUrl, Path cachePath) {
        try {
            Files.createDirectories(cachePath);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(skinUrl))
                    .GET()
                    .build();
            
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                Path skinFile = cachePath.resolve("skin.png");
                Files.write(skinFile, response.body());
                return Optional.of(skinFile);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    /**
     * Obtiene la skin completa (descarga por username si es necesario).
     * @param username Nombre de usuario
     * @param cachePath Ruta para cache
     * @return Path al archivo de skin, empty() si falla o no existe
     */
    public Optional<Path> getSkinByUsername(String username, Path cachePath) {
        Optional<String> uuid = getUuidByUsername(username);
        if (uuid.isEmpty()) {
            return Optional.empty();
        }
        return downloadSkinByUuid(uuid.get(), cachePath);
    }
}



