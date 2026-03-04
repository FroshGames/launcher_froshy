package am.froshy.launcher.api.internal;

import am.froshy.launcher.application.LauncherService;
import am.froshy.launcher.config.LauncherConfig;
import am.froshy.launcher.infrastructure.ProfileStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalApiServerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExposeHealthEndpoint() throws Exception {
        LauncherConfig config = new LauncherConfig(tempDir, tempDir.resolve("profiles.json"), tempDir.resolve("game"), 0);
        LauncherService service = new LauncherService(config, new ProfileStore(config.profilesFile(), new ObjectMapper()));
        InternalApiServer apiServer = new InternalApiServer(0, service);

        apiServer.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(new URI("http://localhost:" + apiServer.getPort() + "/internal/v1/health"))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"status\""));
            assertTrue(response.body().contains("UP"));
        } finally {
            apiServer.stop();
        }
    }
}
