package am.froshy.mialu.launcher;

import am.froshy.mialu.launcher.api.internal.InternalApiClient;
import am.froshy.mialu.launcher.ui.LauncherFrame;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

/**
 * Punto de entrada oficial con Interfaz Gráfica (GUI).
 * Maneja el arranque de la API subyacente y delega
 * la renderización y experiencia del usuario a {@link LauncherFrame}.
 */
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



