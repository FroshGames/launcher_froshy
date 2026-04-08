package am.froshy.mialu.launcher;

/**
 * Clase principal / Punto de entrada estático para la versión de servidor o sin interfaz (CLI)
 * de Launcher_Mialu. Inicializa el ciclo de vida del Runtime y expone los puertos de API.
 * 
 * Si se busca el arranque con Interfaz Gráfica (Swing), revisar {@link LauncherUiApplication}.
 */
public final class LauncherApplication {

    private LauncherApplication() {
    }

    public static void main(String[] args) {
        LauncherRuntime runtime = LauncherRuntime.start();
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::stop));

        System.out.printf("Launcher_Mialu iniciado. API interna en puerto %d%n", runtime.apiPort());
        System.out.println("Endpoints: /internal/v1/health, /internal/v1/profiles, /internal/v1/launch, /internal/v1/downloads");
    }
}


