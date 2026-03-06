package am.froshy.launcher.application;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.IntConsumer;

@FunctionalInterface
public interface MinecraftVersionDownloader {
    void downloadClientJar(String version, Path destination, IntConsumer progressConsumer) throws IOException, InterruptedException;
}

