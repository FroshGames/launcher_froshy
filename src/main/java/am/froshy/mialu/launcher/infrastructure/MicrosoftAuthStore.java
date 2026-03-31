package am.froshy.mialu.launcher.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class MicrosoftAuthStore {
    private final Path file;
    private final ObjectMapper mapper;

    public MicrosoftAuthStore(Path file, ObjectMapper mapper) {
        this.file = file;
        this.mapper = mapper;
    }

    public Optional<StoredMicrosoftSession> load() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            StoredMicrosoftSession data = mapper.readValue(file.toFile(), StoredMicrosoftSession.class);
            return Optional.ofNullable(data);
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    public void save(StoredMicrosoftSession session) {
        try {
            Files.createDirectories(file.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), session);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar la sesion de Microsoft", ex);
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    public record StoredMicrosoftSession(
            String refreshToken,
            String minecraftAccessToken,
            String xuid,
            String playerName,
            String playerUuid,
            java.time.Instant minecraftExpiresAt
    ) {
    }
}

