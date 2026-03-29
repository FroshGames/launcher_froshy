package am.froshy.mialu.launcher.application;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BiConsumer;

public interface MinecraftVersionDownloader {

    /**
     * Descarga todos los archivos necesarios (JAR, librerías, assets, nativos) para la versión
     * indicada. Los archivos se guardan bajo gameDir siguiendo la estructura estándar de Minecraft.
     *
     * @param version          identificador de versión (ej. "1.20.1")
     * @param gameDir          directorio raíz del juego
     * @param progressConsumer callback (progreso 0-100, mensaje descriptivo del paso actual)
     */
    void downloadVersion(String version, Path gameDir, BiConsumer<Integer, String> progressConsumer)
            throws IOException, InterruptedException;

    /**
     * Construye una {@link VersionInstallation} a partir de archivos ya descargados.
     * Debe llamarse DESPUÉS de {@link #downloadVersion}.
     */
    VersionInstallation buildInstallation(String version, Path gameDir, String username)
            throws IOException;
}




