# Froshy Launcher - Documentación Completa

## 📋 Índice

1. [Visión General](#visión-general)
2. [Arquitectura](#arquitectura)
3. [Estructura del Proyecto](#estructura-del-proyecto)
4. [Instalación y Setup](#instalación-y-setup)
5. [Uso del Launcher](#uso-del-launcher)
6. [API Interna](#api-interna)
7. [Desarrollo](#desarrollo)
8. [Componentes Principales](#componentes-principales)
9. [Testing](#testing)
10. [Próximos Pasos](#próximos-pasos)

---

## Visión General

**Froshy Launcher** es un launcher de Minecraft desarrollado en Java con las siguientes características:

- **API HTTP interna** embebida que expone funcionalidades del launcher
- **Interfaz gráfica Swing** conectada a la API interna
- **Gestión de perfiles** con persistencia en JSON
- **Descarga simulada** de assets con monitoreo en tiempo real
- **Arquitectura desacoplada** entre backend, API y UI

### Tecnología

- **Lenguaje**: Java 17+
- **Framework**: Swing (UI nativa sin dependencias externas)
- **HTTP Server**: `com.sun.net.httpserver.HttpServer` (JDK)
- **Serialización**: Jackson 2.18.2
- **Build**: Maven 3.9.9
- **Testing**: JUnit 5

---

## Arquitectura

### Diagrama de Capas

```
┌──────────────────────────────────────────────────────────────┐
│                      LAUNCHER UI (Swing)                     │
│                    (LauncherFrame.java)                      │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────┐
│            CLIENTE HTTP INTERNO (InternalApiClient)          │
│         Resuelve http://localhost:PORT/internal/v1/*         │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────┐
│                  API INTERNA (InternalApiServer)             │
│                 Endpoints REST embebidos                      │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────┐
│            CAPA DE APLICACIÓN (LauncherService)              │
│              Lógica de negocio del launcher                   │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────┐
│              INFRAESTRUCTURA & PERSISTENCIA                   │
│    ProfileStore (JSON) | Dominio (records de tipos)           │
└──────────────────────────────────────────────────────────────┘
```

### Flujo de Arranque (modo visual)

```
1. LauncherUiApplication.main()
   ↓
2. LauncherRuntime.start()
   ├─ Crea LauncherConfig (env + defaults)
   ├─ Instancia LauncherService
   ├─ Instancia InternalApiServer (puerto aleatorio o 7878)
   └─ Inicia servidor HTTP en thread del servidor
   ↓
3. InternalApiClient crea cliente HTTP con baseUri
   ↓
4. SwingUtilities.invokeLater() abre LauncherFrame en EDT
   ↓
5. Usuario interactúa con UI → LauncherFrame → InternalApiClient
   → http://localhost:PORT/internal/v1/* → InternalApiServer
   → LauncherService → lógica de negocio
```

---

## Estructura del Proyecto

```
launcher_froshy/
├── pom.xml                                # Configuracion Maven
├── README.md                              # Quickstart
├── docs/                                  # (NEW) Documentación detallada
│   ├── ARCHITECTURE.md
│   ├── API_REFERENCE.md
│   ├── DEVELOPER_GUIDE.md
│   └── CHANGELOG.md
│
├── src/main/java/am/froshy/launcher/
│   ├── LauncherApplication.java           # Entrypoint API-only (--headless)
│   ├── LauncherUiApplication.java         # Entrypoint UI visual (default)
│   ├── LauncherRuntime.java               # Bootstrap compartido
│   │
│   ├── config/
│   │   └── LauncherConfig.java            # Configuracion centralizada
│   │
│   ├── domain/
│   │   ├── MinecraftProfile.java          # Perfil de juego (record)
│   │   ├── LaunchRequest.java             # Solicitud de lanzamiento
│   │   ├── LaunchResult.java              # Resultado del lanzamiento
│   │   └── DownloadStatus.java            # Estado de descarga
│   │
│   ├── application/
│   │   └── LauncherService.java           # Lógica central del launcher
│   │
│   ├── infrastructure/
│   │   └── ProfileStore.java              # Persistencia en JSON
│   │
│   ├── api/internal/
│   │   ├── InternalApiServer.java         # Servidor HTTP embebido
│   │   └── InternalApiClient.java         # Cliente HTTP (UI)
│   │
│   └── ui/
│       └── LauncherFrame.java             # Ventana principal Swing
│
└── src/test/java/am/froshy/launcher/
    ├── application/
    │   └── LauncherServiceTest.java       # Tests unitarios del servicio
    └── api/internal/
        ├── InternalApiServerTest.java     # Tests del servidor HTTP
        └── InternalApiClientTest.java     # Tests integracion cliente+servidor
```

---

## Instalación y Setup

### Requisitos

- **JDK 17+** (probado en 17.0.12)
- **Maven 3.9.9+**
- **Git** (para clonar el repo)

### Pasos de Instalación

1. **Clonar el repositorio**

   ```bash
   git clone https://github.com/tu-usuario/launcher_froshy.git
   cd launcher_froshy
   ```

2. **Compilar el proyecto**

   ```bash
   mvn clean compile
   ```

3. **Ejecutar pruebas**

   ```bash
   mvn test
   ```

   Resultado esperado: **4 tests OK, 0 errors**

4. **Empaquetar como JAR**

   ```bash
   mvn package
   ```

   Genera: `target/launcher-1.0-SNAPSHOT.jar`

### Variables de Entorno

| Variable | Default | Descripción |
|----------|---------|-------------|
| `FROSHY_API_PORT` | `7878` | Puerto en el que escucha la API interna |

**Ejemplo**:

```bash
export FROSHY_API_PORT=9999
java -jar target/launcher-1.0-SNAPSHOT.jar
```

---

## Uso del Launcher

### Modo 1: Visual (UI Swing) - PREDETERMINADO

Abre ventana gráfica con interfaz interactiva.

```bash
java -jar target/launcher-1.0-SNAPSHOT.jar
```

**O compilando desde fuente**:

```bash
mvn compile
java -cp target/classes am.froshy.launcher.LauncherUiApplication
```

### Modo 2: API-only (headless)

Solo arranca servidor HTTP sin UI.

```bash
java -cp target/classes am.froshy.launcher.LauncherApplication
```

Sale a consola:
```
Launcher Froshy iniciado. API interna en puerto 7878
Endpoints: /internal/v1/health, /internal/v1/profiles, /internal/v1/launch, /internal/v1/downloads
```

### Interfaz de Usuario (Swing)

La ventana se divide en 2 paneles principales:

#### Panel Izquierdo: Gestión de Perfiles

- **Lista de perfiles**: Muestra todos los perfiles creados
  - Formato: `Nombre [id] - versión`
  - Selecciona uno para lanzar
- **Botón "Refrescar perfiles"**: Recarga la lista desde el servidor

#### Panel Derecho: Acciones y Creación

**Sección "Nuevo perfil"**:
- **ID**: Identificador único del perfil
- **Nombre**: Nombre visible del perfil
- **Version**: Version de Minecraft (ej: `1.20.1`)
- **Java**: Ruta al ejecutable Java (ej: `java` o `/usr/bin/java`)
- **JVM args**: Argumentos JVM (ej: `-Xmx2G -Xms1G`)
- **Game args**: Argumentos del juego (ej: `--username Steve`)

**Sección "Acciones"**:
- **Botón "Crear perfil"**: Crea nuevo perfil con valores del formulario
- **Checkbox "Demo mode"**: Ejecuta el perfil seleccionado en modo demo
- **Botón "Launch"**: Lanza el perfil seleccionado (genera comando simulado)
- **Botón "Health"**: Consulta estado de la API interna
- **Target descarga**: Nombre del asset a descargar (ej: `assets` o `libraries`)
- **Botón "Descargar"**: Inicia descarga simulada
- **Barra de progreso**: Muestra progreso de descarga en tiempo real

**Sección "Output"**:
- Registra todas las acciones ejecutadas
- Muestra errores y confirmaciones

---

## API Interna

### Base URI

```
http://localhost:7878/internal/v1
```

(El puerto puede variar según `FROSHY_API_PORT`)

### Endpoints

#### 1. Health Check

```http
GET /internal/v1/health
```

**Response (200 OK)**:
```json
{
  "status": "UP",
  "apiPort": 7878,
  "profiles": 2,
  "timestamp": "2026-03-04T04:30:00Z"
}
```

#### 2. Listar Perfiles

```http
GET /internal/v1/profiles
```

**Response (200 OK)**:
```json
[
  {
    "id": "default",
    "displayName": "Perfil Principal",
    "javaPath": "java",
    "gameVersion": "1.20.1",
    "jvmArgs": ["-Xmx2G"],
    "gameArgs": ["--username", "Steve"]
  },
  {
    "id": "pvp",
    "displayName": "Perfil PvP",
    "javaPath": "java",
    "gameVersion": "1.8.9",
    "jvmArgs": ["-Xmx1G"],
    "gameArgs": []
  }
]
```

#### 3. Crear Perfil

```http
POST /internal/v1/profiles
Content-Type: application/json

{
  "id": "builder",
  "displayName": "Perfil Builder",
  "javaPath": "java",
  "gameVersion": "1.20.1",
  "jvmArgs": ["-Xmx2G"],
  "gameArgs": ["--username", "Alex"]
}
```

**Response (201 Created)**:
```json
{
  "id": "builder",
  "displayName": "Perfil Builder",
  "javaPath": "java",
  "gameVersion": "1.20.1",
  "jvmArgs": ["-Xmx2G"],
  "gameArgs": ["--username", "Alex"]
}
```

**Errores**:
- `400 Bad Request`: ID duplicado o campos obligatorios vacios
- `500 Internal Server Error`: Errores de persistencia

#### 4. Lanzar Juego

```http
POST /internal/v1/launch
Content-Type: application/json

{
  "profileId": "default",
  "demoMode": false
}
```

**Response (202 Accepted)**:
```json
{
  "launchId": "550e8400-e29b-41d4-a716-446655440000",
  "profileId": "default",
  "commandLine": "java -Xmx2G -jar minecraft-1.20.1.jar --gameDir C:\\Users\\User\\.froshy-launcher\\game --username Steve",
  "startedAt": "2026-03-04T04:30:45Z",
  "status": "STARTED"
}
```

**Nota**: Actualmente **simulado**. No ejecuta el proceso real.

#### 5. Iniciar Descarga

```http
POST /internal/v1/downloads
Content-Type: application/json

{
  "target": "assets"
}
```

**Response (202 Accepted)**:
```json
{
  "downloadId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
  "target": "assets",
  "state": "QUEUED",
  "progress": 0
}
```

#### 6. Obtener Estado de Descarga

```http
GET /internal/v1/downloads/{downloadId}
```

**Response (200 OK)**:
```json
{
  "downloadId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
  "target": "assets",
  "state": "IN_PROGRESS",
  "progress": 45
}
```

Estados posibles: `QUEUED`, `IN_PROGRESS`, `DONE`

---

## Desarrollo

### Estructura de Código

#### 1. Domain (Modelos Puros)

Ubicación: `src/main/java/am/froshy/launcher/domain/`

Son **records** inmutables que representan conceptos del negocio:

```java
// MinecraftProfile.java
public record MinecraftProfile(
    String id,                    // Identificador único
    String displayName,           // Nombre visible
    String javaPath,              // Ruta a java
    String gameVersion,           // Versión del juego
    List<String> jvmArgs,         // Args para la JVM
    List<String> gameArgs         // Args para el juego
) { /* validaciones en constructor canónico */ }
```

**Ventajas de usar records**:
- Inmutables por defecto
- `equals()`, `hashCode()`, `toString()` generados automáticamente
- Constructor compacto con validaciones

#### 2. Application (Lógica de Negocio)

Ubicación: `src/main/java/am/froshy/launcher/application/LauncherService.java`

Orquesta perfiles, descargas y lanzamientos:

```java
public class LauncherService {
    private final Map<String, MinecraftProfile> profiles;
    private final ProfileStore store;
    
    // Métodos públicos = casos de uso
    public List<MinecraftProfile> listProfiles() { ... }
    public MinecraftProfile createProfile(MinecraftProfile p) { ... }
    public LaunchResult launch(LaunchRequest req) { ... }
    public DownloadStatus startDownload(String target) { ... }
}
```

#### 3. Infrastructure (Persistencia)

Ubicación: `src/main/java/am/froshy/launcher/infrastructure/ProfileStore.java`

Maneja lectura/escritura de perfiles en JSON:

```java
public class ProfileStore {
    public List<MinecraftProfile> load() { /* lee profiles.json */ }
    public void save(Collection<MinecraftProfile> profiles) { /* escribe */ }
}
```

Ubicación del archivo: `~/.froshy-launcher/profiles.json`

Formato:
```json
{
  "profiles": [
    { "id": "default", "displayName": "...", ... }
  ]
}
```

#### 4. API Internal (HTTP)

Ubicación: `src/main/java/am/froshy/launcher/api/internal/`

**InternalApiServer**: 
- Sirve endpoints HTTP
- Usa `com.sun.net.httpserver.HttpServer` (JDK built-in)
- Maneja deserialización de requests y serialización de responses

**InternalApiClient**:
- Consumidor HTTP de la API
- Usa `java.net.http.HttpClient` (JDK 11+)
- Resuelve rutas relativas contra baseUri

#### 5. UI (Presentación)

Ubicación: `src/main/java/am/froshy/launcher/ui/LauncherFrame.java`

Panel Swing principal con:
- Listado de perfiles (`JList<MinecraftProfile>`)
- Formularios (`JTextField`, `JCheckBox`)
- Acciones ("Crear", "Launch", "Descargar")
- Área de output (`JTextArea`)
- Operaciones asincrónicas via `ExecutorService`

---

## Testing

### Tests Incluidos

#### 1. LauncherServiceTest

Prueba lógica de negocio sin I/O real.

```bash
mvn test -Dtest=LauncherServiceTest
```

**Casos**:
- `shouldCreateAndListProfiles()`: CRUD de perfiles
- `shouldBuildLaunchResultWithCommand()`: Generación de línea de comandos

#### 2. InternalApiServerTest

Prueba servidor HTTP con cliente real.

```bash
mvn test -Dtest=InternalApiServerTest
```

**Casos**:
- `shouldExposeHealthEndpoint()`: Verify `/health` respond 200

#### 3. InternalApiClientTest

Prueba cliente contra servidor en el mismo proceso.

```bash
mvn test -Dtest=InternalApiClientTest
```

**Casos**:
- `shouldCreateProfileAndLaunchThroughApi()`: Flujo completo create→launch

### Ejecutar Todos los Tests

```bash
mvn test
```

Salida esperada:
```
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

### Patrones de Testing

#### Test con TempDir (archivos temporales)

```java
@Test
void shouldPersistProfiles(@TempDir Path tempDir) {
    LauncherConfig config = new LauncherConfig(
        tempDir, tempDir.resolve("profiles.json"), 
        tempDir.resolve("game"), 7878
    );
    // Test usa directorio temporal que se limpia después
}
```

#### Test de Servidor y Cliente

```java
@Test
void shouldCreateAndList() {
    InternalApiServer server = new InternalApiServer(0, service); // puerto 0 = aleatorio
    server.start();
    try {
        InternalApiClient client = new InternalApiClient(
            URI.create("http://localhost:" + server.getPort() + "/internal/v1/")
        );
        client.createProfile(...);
        // assertions
    } finally {
        server.stop();
    }
}
```

---

## Componentes Principales

### 1. LauncherConfig

**Responsabilidad**: Centralizar configuración.

```java
public record LauncherConfig(
    Path baseDirectory,      // ~/.froshy-launcher
    Path profilesFile,       // ~/.froshy-launcher/profiles.json
    Path gameDirectory,      // ~/.froshy-launcher/game
    int internalApiPort      // 7878 (default)
)
```

**Lectura desde env**:
```java
LauncherConfig config = LauncherConfig.fromEnvironment();
```

Variables usadas:
- `user.home`: Para ubicar `~/.froshy-launcher`
- `FROSHY_API_PORT`: Para overridear puerto API

### 2. LauncherRuntime

**Responsabilidad**: Bootstrap compartido entre modo visual y headless.

```java
public class LauncherRuntime {
    public static LauncherRuntime start() {
        // 1. Crea config
        // 2. Instancia servicio
        // 3. Inicia servidor API
        // 4. Retorna runtime con referencias
    }
    
    public URI apiBaseUri() { ... }
    public int apiPort() { ... }
    public void stop() { /* limpia recursos */ }
}
```

### 3. LauncherService

**Responsabilidad**: Orquestar casos de uso.

Mantiene en memoria:
- `Map<String, MinecraftProfile> profiles`: perfiles en RAM
- `Map<String, DownloadStatus> downloads`: estado de descargas
- `ScheduledExecutorService scheduler`: simula descargas async

Métodos públicos:
```java
List<MinecraftProfile> listProfiles()
MinecraftProfile createProfile(MinecraftProfile profile)
LaunchResult launch(LaunchRequest request)
DownloadStatus startDownload(String target)
Optional<DownloadStatus> getDownloadStatus(String id)
Map<String, Object> health()
void shutdown()
```

### 4. InternalApiServer

**Responsabilidad**: Servir HTTP con endpoints.

```java
public class InternalApiServer {
    private final HttpServer server; // com.sun.net.httpserver
    
    public InternalApiServer(int port, LauncherService service) {
        // Crea servidor, registra contextos, configura executor
    }
    
    public void start() { server.start(); }
    public void stop() { /* para servidor y servicio */ }
    public int getPort() { /* retorna puerto real */ }
}
```

**Contextos registrados**:
```
/internal/v1/health    → GET
/internal/v1/profiles  → GET, POST
/internal/v1/launch    → POST
/internal/v1/downloads → GET, POST
```

Manejo de errores centralizado:
```java
private void withErrorHandling(HttpExchange ex, ExchangeHandler handler) {
    try {
        handler.handle(ex);
    } catch (IllegalArgumentException e) {
        sendJson(ex, 400, Map.of("error", e.getMessage()));
    } catch (Exception e) {
        sendJson(ex, 500, Map.of("error", "Error interno", "detail", e.getMessage()));
    }
}
```

### 5. InternalApiClient

**Responsabilidad**: Consumidor HTTP.

```java
public class InternalApiClient {
    private final HttpClient httpClient; // java.net.http
    private final URI baseUri;
    
    public MinecraftProfile createProfile(MinecraftProfile p) { ... }
    public LaunchResult launch(LaunchRequest req) { ... }
    // etc
}
```

**Normalización de URI**:
```java
public InternalApiClient(URI baseUri) {
    String normalized = baseUri.toString().endsWith("/") 
        ? baseUri.toString() 
        : baseUri + "/";
    this.baseUri = URI.create(normalized);
}
```

### 6. LauncherFrame (Swing)

**Responsabilidad**: Presentación interactiva.

```java
public class LauncherFrame extends JFrame {
    private final DefaultListModel<MinecraftProfile> profilesModel;
    private final InternalApiClient apiClient;
    private final ExecutorService executor;
    
    private void initUi() {
        // Construye layout con BorderLayout, GridLayout
        // Wireups de eventos (listeners)
    }
    
    private void runAsync(Runnable action) {
        // Ejecuta acciones en background para no bloquear EDT
        executor.submit(() -> {
            try {
                action.run();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> appendOutput("Error: " + ex));
            }
        });
    }
}
```

**Polling de descargas**:
```java
private void pollDownload(String downloadId) {
    Timer timer = new Timer(400, null); // cada 400ms
    timer.addActionListener(e -> runAsync(() -> {
        DownloadStatus status = apiClient.getDownloadStatus(downloadId);
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(status.progress());
            if ("DONE".equals(status.state())) {
                timer.stop();
            }
        });
    }));
    timer.start();
}
```

---

## Próximos Pasos

### Fase 2: Autenticación Real

- [ ] Integrar Microsoft OAuth2
- [ ] Login visual en la UI
- [ ] Almacenar token en keystore seguro
- [ ] Endpoint `/internal/v1/auth/login` y `/internal/v1/auth/logout`

### Fase 3: Descarga Real de Assets

- [ ] Consumir Minecraft Version Manifest API (launcher.mojang.com)
- [ ] Descargar JARs y librerías reales
- [ ] Validar integridad con SHA-256
- [ ] Mostrar progreso en bytes/MB

### Fase 4: Ejecución Real del Juego

- [ ] Usar `ProcessBuilder` para lanzar java.exe
- [ ] Capturar stdout/stderr del juego
- [ ] Mostrar logs en la UI
- [ ] Detector de crash/exit

### Fase 5: Características Avanzadas

- [ ] Gestor de mods (Forge, Fabric)
- [ ] Editor de JVM args desde UI
- [ ] Descarga paralela multi-thread
- [ ] Caché de versiones
- [ ] Auto-update del launcher

### Fase 6: Distribución

- [ ] Empaquetar como EXE (jpackage)
- [ ] Firmar digitalmente
- [ ] Auto-updater en el launcher
- [ ] Repositorio de releases en GitHub

---

## Contacto & Contribuciones

Por ahora este es un proyecto de demostración. Para reportar bugs o contribuir, abre un issue o pull request.

**Licencia**: MIT (o la que elijas)

---

**Última actualización**: 2026-03-04  
**Versión**: 1.0-SNAPSHOT

