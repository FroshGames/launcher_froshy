package am.froshy.launcher;

public final class LauncherApplication {

    private LauncherApplication() {
    }

    public static void main(String[] args) {
        LauncherRuntime runtime = LauncherRuntime.start();
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::stop));

        System.out.printf("Launcher Froshy iniciado. API interna en puerto %d%n", runtime.apiPort());
        System.out.println("Endpoints: /internal/v1/health, /internal/v1/profiles, /internal/v1/launch, /internal/v1/downloads");
    }
}
