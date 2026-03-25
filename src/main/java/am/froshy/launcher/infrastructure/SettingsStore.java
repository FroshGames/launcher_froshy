package am.froshy.launcher.infrastructure;

import am.froshy.launcher.config.LauncherSettings;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SettingsStore {
    private final Path settingsFile;
    private final ObjectMapper mapper;

    public SettingsStore(Path settingsFile, ObjectMapper mapper) {
        this.settingsFile = settingsFile;
        this.mapper = mapper;
    }

    public LauncherSettings load() {
        if (!Files.exists(settingsFile)) return LauncherSettings.defaults();
        try {
            return mapper.readValue(settingsFile.toFile(), LauncherSettings.class);
        } catch (Exception ignored) {
            return LauncherSettings.defaults();
        }
    }

    public void save(LauncherSettings settings) {
        try {
            Files.createDirectories(settingsFile.getParent());
            mapper.writeValue(settingsFile.toFile(), settings);
        } catch (IOException ignored) {}
    }
}

