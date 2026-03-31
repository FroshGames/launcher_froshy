package am.froshy.mialu.launcher.infrastructure;

import am.froshy.mialu.launcher.domain.MinecraftProfile;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ProfileStore {
    private final Path file;
    private final ObjectMapper objectMapper;

    public ProfileStore(Path file, ObjectMapper objectMapper) {
        this.file = file;
        this.objectMapper = objectMapper;
    }

    public List<MinecraftProfile> load() {
        if (!Files.exists(file)) {
            return List.of();
        }

        try {
            ProfilesDocument document = objectMapper.readValue(file.toFile(), ProfilesDocument.class);
            if (document == null || document.profiles == null) {
                return List.of();
            }
            return List.copyOf(document.profiles);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudieron cargar los perfiles", ex);
        }
    }

    public void save(Collection<MinecraftProfile> profiles) {
        try {
            Files.createDirectories(file.getParent());
            ProfilesDocument document = new ProfilesDocument();
            document.profiles = new ArrayList<>(profiles);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), document);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudieron guardar los perfiles", ex);
        }
    }

    public static final class ProfilesDocument {
        public List<MinecraftProfile> profiles = new ArrayList<>();
    }
}



