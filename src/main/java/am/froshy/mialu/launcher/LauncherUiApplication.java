package am.froshy.mialu.launcher;

import am.froshy.mialu.launcher.api.internal.InternalApiClient;
import am.froshy.mialu.launcher.ui.LauncherFrame;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

public final class LauncherUiApplication {

    private LauncherUiApplication() {
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("Entorno sin UI grafica disponible");
        }

        LauncherRuntime runtime = LauncherRuntime.start();
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::stop));

        InternalApiClient apiClient = new InternalApiClient(runtime.apiBaseUri());
        SwingUtilities.invokeLater(() -> {
            LauncherFrame frame = new LauncherFrame(apiClient, runtime.apiPort(), runtime.config().launcherVersion(), () -> {
                runtime.stop();
                System.exit(0);
            });
            frame.setVisible(true);
        });
    }
}



