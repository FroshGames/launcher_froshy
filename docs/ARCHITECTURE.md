# Guía de Arquitectura - Froshy Launcher

## Visión General

Froshy Launcher sigue una arquitectura **limpia y desacoplada** en capas:

```
┌────────────────────────────────────────┐
│         PRESENTATION (Swing)           │  LauncherFrame
├────────────────────────────────────────┤
│       API ADAPTER (HTTP)               │  InternalApiServer/Client
├────────────────────────────────────────┤
│       APPLICATION LOGIC                │  LauncherService
├────────────────────────────────────────┤
│       DOMAIN ENTITIES                  │  MinecraftProfile, etc.
├────────────────────────────────────────┤
│       INFRASTRUCTURE                   │  ProfileStore, Config
└────────────────────────────────────────┘
```

## Principios de Diseño

### 1. Separación de Responsabilidades

Cada clase/componente hace **una sola cosa bien**:

- **Domain**: Modelos puros, sin lógica de negocio compleja
- **Application**: Orquestación de casos de uso
- **Infrastructure**: Detalles técnicos (persistencia, config)
- **API**: Adaptación HTTP de casos de uso
- **UI**: Presentación e interacción del usuario

### 2. Inyección de Dependencias

No usamos frameworks DI, pero seguimos el patrón:

```java
// Bad - acoplamiento fuerte
public class LauncherService {
    private ProfileStore store = new ProfileStore(); // new es malo
}

// Good - inyección
public class LauncherService {
    private final ProfileStore store;
    
    public LauncherService(ProfileStore store) {
        this.store = store; // inyectado
    }
}
```

**Ventajas**:
- Fácil de testear (inyectar mocks)
- Bajo acoplamiento
- Flexible para cambiar implementaciones

### 3. Interfaces donde Hacen Sentido

Aunque aquí no usamos mucho (proyecto pequeño), el patrón sería:

```java
// Interfaz = contrato
public interface IProfileStore {
    List<MinecraftProfile> load();
    void save(Collection<MinecraftProfile> profiles);
}

// Implementación JSON
public class JsonProfileStore implements IProfileStore { ... }

// En servicio, inyectar la interfaz
public LauncherService(IProfileStore store) { ... }
```

Esto permite cambiar a BD sin tocar `LauncherService`.

### 4. Inmutabilidad

Records garantizan inmutabilidad:

```java
public record MinecraftProfile(
    String id,
    String displayName,
    // ... fields
) { }

// Cuando se serializa y deserializa, es siempre seguro
MinecraftProfile p1 = new MinecraftProfile(...);
MinecraftProfile p2 = p1;  // Seguro - misma instancia
```

### 5. Validación en Fronteras

Validamos en puntos críticos:

```java
// En el constructor de record
public record LaunchRequest(String profileId, boolean demoMode) {
    public LaunchRequest {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId es obligatorio");
        }
    }
}

// En API server antes de procesar
if (!"POST".equalsIgnoreCase(method)) {
    sendMethodNotAllowed(exchange, List.of("POST"));
    return;
}

// En la UI
if (idField.getText().trim().isEmpty()) {
    JOptionPane.showMessageDialog(this, "ID obligatorio");
    return;
}
```

---

## Flujos Principales

### Flujo 1: Crear un Perfil

```
UI (LauncherFrame)
  ↓ Usuario llena formulario y hace click "Crear"
  ↓ createProfile() en thread executor
  ↓ InternalApiClient.createProfile(MinecraftProfile)
    ↓ HttpRequest POST /internal/v1/profiles
    ↓ json: {"id": "...", "displayName": "...", ...}
  ↓ InternalApiServer recibe request
    ↓ handleProfiles() → POST branch
    ↓ readBody(exchange, MinecraftProfile.class)
    ↓ LauncherService.createProfile(profile)
      ↓ Valida que no exista (profiles.containsKey())
      ↓ profiles.put(id, profile) en RAM
      ↓ ProfileStore.save(profiles.values()) → profiles.json
      ↓ Retorna MinecraftProfile creado
    ↓ sendJson(exchange, 201, profile)
  ↓ Retorna al cliente HttpResponse
  ↓ Deserializa JSON a MinecraftProfile
  ↓ Vuelve al hilo EDT
  ↓ Refresca lista de perfiles
  ↓ Muestra mensaje en output "Perfil creado: ..."
```

### Flujo 2: Lanzar Juego

```
UI (LauncherFrame)
  ↓ Usuario selecciona perfil y hace click "Launch"
  ↓ launchSelectedProfile() en thread executor
  ↓ InternalApiClient.launch(LaunchRequest)
    ↓ HttpRequest POST /internal/v1/launch
    ↓ json: {"profileId": "default", "demoMode": false}
  ↓ InternalApiServer
    ↓ handleLaunch() → POST
    ↓ readBody(exchange, LaunchRequest.class)
    ↓ LauncherService.launch(request)
      ↓ Obtiene MinecraftProfile por ID
      ↓ Genera UUID como launchId
      ↓ Construye línea de comandos:
         "java -Xmx2G -jar minecraft-1.20.1.jar ..."
      ↓ Retorna LaunchResult(launchId, profileId, cmd, time, "STARTED")
    ↓ sendJson(exchange, 202, result)
  ↓ Retorna al cliente
  ↓ Deserializa LaunchResult
  ↓ En hilo EDT: muestra comando en output
  ↓ (Actualmente simulado - no ejecuta proceso real)
```

### Flujo 3: Monitorear Descarga

```
UI (LauncherFrame)
  ↓ Usuario ingresa "assets" y hace click "Descargar"
  ↓ startDownload() en executor
  ↓ InternalApiClient.startDownload("assets")
    ↓ HttpRequest POST /internal/v1/downloads
    ↓ json: {"target": "assets"}
  ↓ InternalApiServer
    ↓ handleDownloads() → POST
    ↓ LauncherService.startDownload("assets")
      ↓ Genera downloadId UUID
      ↓ Crea DownloadStatus(id, "assets", "QUEUED", 0)
      ↓ Inicia ScheduledFuture que cada 300ms:
         → Incrementa progress 0→20→40→...→100
         → Actualiza state "QUEUED"→"IN_PROGRESS"→"DONE"
         → Cancela tarea cuando llega a 100%
      ↓ Retorna DownloadStatus inicial
    ↓ sendJson(exchange, 202, status)
  ↓ Cliente recibe response
  ↓ En EDT: inicia polling con Timer(400ms)
    ↓ pollDownload(downloadId) llama GET /internal/v1/downloads/{id}
    ↓ Cada respuesta actualiza:
       → progressBar.setValue(status.progress())
       → appendOutput("Download ... 45%")
    ↓ Cuando state=="DONE", detiene Timer
```

---

## Manejo de Errores

### Estrategia Global

1. **En Domain**: Validación en constructores de records
   ```java
   public record MinecraftProfile(...) {
       public MinecraftProfile {
           if (id == null) throw new IllegalArgumentException(...);
       }
   }
   ```

2. **En Application**: Validación lógica
   ```java
   public MinecraftProfile createProfile(MinecraftProfile p) {
       if (profiles.containsKey(p.id())) {
           throw new IllegalArgumentException("Ya existe...");
       }
       // ...
   }
   ```

3. **En API**: Catch y retorna HTTP error
   ```java
   private void withErrorHandling(HttpExchange exchange, ...) {
       try {
           handler.handle(exchange);
       } catch (IllegalArgumentException ex) {
           sendJson(exchange, 400, Map.of("error", ex.getMessage()));
       } catch (Exception ex) {
           sendJson(exchange, 500, Map.of("error", "Error interno", "detail", ...));
       }
   }
   ```

4. **En UI**: Muestra al usuario sin bloquear
   ```java
   private void runAsync(Runnable action) {
       executor.submit(() -> {
           try {
               action.run();
           } catch (Exception ex) {
               SwingUtilities.invokeLater(() -> 
                   appendOutput("Error: " + ex.getMessage())
               );
           }
       });
   }
   ```

### Códigos HTTP

| Código | Significado | Ejemplo |
|--------|-------------|---------|
| 200 | OK | GET /health exitoso |
| 201 | Created | Perfil creado exitosamente |
| 202 | Accepted | Launch o descarga iniciada |
| 400 | Bad Request | Campo obligatorio faltante |
| 404 | Not Found | Descarga con ID no encontrada |
| 405 | Method Not Allowed | GET a endpoint POST-only |
| 500 | Internal Server Error | Excepción inesperada |

---

## Patrones de Concurrencia

### 1. ExecutorService en LauncherFrame

```java
private final ExecutorService executor = Executors.newSingleThreadExecutor();

private void runAsync(Runnable action) {
    executor.submit(() -> {
        try {
            action.run();
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> appendOutput("Error: " + ex));
        }
    });
}
```

**Garantías**:
- Las acciones se ejecutan **en orden** (single thread)
- No bloquean el EDT (Event Dispatch Thread)
- Los resultados se marshalling de vuelta al EDT con `SwingUtilities.invokeLater()`

### 2. ScheduledExecutorService en LauncherService

```java
private final ScheduledExecutorService scheduler = 
    Executors.newScheduledThreadPool(2);

public DownloadStatus startDownload(String target) {
    String downloadId = UUID.randomUUID().toString();
    
    ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
        DownloadStatus current = downloads.get(downloadId);
        if (current == null || "DONE".equals(current.state())) {
            return; // auto-cancela
        }
        
        int progress = Math.min(100, current.progress() + 20);
        String state = progress >= 100 ? "DONE" : "IN_PROGRESS";
        downloads.put(downloadId, new DownloadStatus(...));
        
        if (progress >= 100) {
            downloadTasks.remove(downloadId).cancel(false);
        }
    }, 200, 300, TimeUnit.MILLISECONDS);
    
    downloadTasks.put(downloadId, task);
    return downloads.get(downloadId);
}
```

**Garantías**:
- Las simulaciones de descarga avanzan cada 300ms
- Se autodetienen cuando llegan a 100%
- Thread-safe con `ConcurrentHashMap`

### 3. Polling desde UI

```java
private void pollDownload(String downloadId) {
    Timer timer = new Timer(400, null);
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

**Garantías**:
- Query al servidor cada 400ms
- Actualiza barra de progreso en EDT
- Se detiene automáticamente cuando termina

---

## Serialización JSON

### Jackson Configuration

```java
ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
```

**Módulos registrados**:
- `jackson-databind`: Core JSON processing
- `jackson-datatype-jsr310`: Soporte para `java.time.Instant`

### Serialización de Records

Records se serializan automáticamente (getters generados):

```java
public record MinecraftProfile(String id, String displayName, ...) { }

// Se serializa como:
{
  "id": "default",
  "displayName": "Perfil Principal",
  ...
}
```

### Deserialización de Objetos Complejos

```java
// Deserializar a un Map genérico
Map<String, String> map = objectMapper.readValue(
    json, 
    new TypeReference<Map<String, String>>() {}
);

// Deserializar a record específico
MinecraftProfile profile = objectMapper.readValue(
    json, 
    MinecraftProfile.class
);
```

---

## Performance y Optimizaciones

### Memoria

- **Perfiles en RAM**: `ConcurrentHashMap<String, MinecraftProfile>`
  - Ventaja: Acceso O(1)
  - Desventaja: Limited by heap size
  - Mitigation: Validar en create que no haya duplicados

- **Descargas simuladas**: Se limpian automáticamente en `downloads` cuando terminan

### Concurrencia

- **Thread-safety**: 
  - `ConcurrentHashMap` para profiles y downloads
  - `ScheduledExecutorService` para tareas simuladas
  - `ExecutorService` para operaciones de UI async

- **Timeouts**:
  - HttpClient: 3s connect, 10s request
  - Si HTTP cuelga, la UI no se bloquea (async executor)

### Caching

Actualmente:
- Perfiles se cargan al startup desde JSON
- Se refrescan desde servidor vía API
- No hay caché de HTTP responses

Futuros:
- Caché de versiones de MC (version manifest)
- Caché de assets descargados (por SHA-256)

---

## Testing Strategy

### Unit Tests

```java
// LauncherServiceTest
@Test
void shouldCreateAndListProfiles(@TempDir Path tempDir) {
    // Aislado: sin servidor, sin UI
    LauncherService service = new LauncherService(config, store);
    service.createProfile(profile);
    assertEquals(1, service.listProfiles().size());
}
```

### Integration Tests

```java
// InternalApiClientTest
@Test
void shouldCreateProfileAndLaunchThroughApi(@TempDir Path tempDir) {
    // Real: servidor HTTP + cliente HTTP
    InternalApiServer server = new InternalApiServer(0, service);
    server.start();
    
    InternalApiClient client = new InternalApiClient(...);
    client.createProfile(profile);
    LaunchResult launch = client.launch(...);
    
    assertEquals("STARTED", launch.status());
}
```

### E2E Manual

1. Compilar: `mvn package`
2. Ejecutar JAR: `java -jar target/launcher-1.0-SNAPSHOT.jar`
3. Crear perfil en UI
4. Lanzar perfil
5. Ver log en output

---

## Extensibilidad Futura

### 1. Soporte para Mods

```java
// New domain
public record ModProfile(
    String id,
    String name,
    String loaderType,        // FORGE, FABRIC, QUILT
    String loaderVersion,
    List<String> modIds
) { }

// New endpoint
POST /internal/v1/mod-profiles
GET  /internal/v1/mod-profiles/{id}
```

### 2. Autenticación Microsoft

```java
public record AuthToken(
    String accessToken,
    String refreshToken,
    Instant expiresAt
) { }

// Endpoints
POST /internal/v1/auth/login   → redirect a OAuth flow
GET  /internal/v1/auth/status  → token info
POST /internal/v1/auth/logout
```

### 3. Version Manifest

```java
public record MinecraftVersion(
    String id,
    String type,              // release, snapshot, old_release
    String url,               // manifest para esta version
    Instant releaseTime
) { }

// Endpoint
GET /internal/v1/versions/all
```

### 4. Descarga Real

```java
public class AssetDownloader {
    public void downloadAssets(String versionId) { ... }
    public void downloadLibraries(String versionId) { ... }
}
```

---

## Debugging

### Logs

No hay logging configurado aún. Opciones:

1. **SLF4J + Logback** (recomendado)
   ```xml
   <dependency>
       <groupId>org.slf4j</groupId>
       <artifactId>slf4j-api</artifactId>
       <version>2.0.7</version>
   </dependency>
   ```

2. **System.out.println** (simple, actual)
   ```java
   System.out.println("Debug: perfil creado " + profile.id());
   ```

### Breakpoints en IDE

IntelliJ IDEA permite debuggear fácilmente:

1. Run → Edit Configurations → Add "Application"
2. Main class: `am.froshy.launcher.LauncherUiApplication`
3. Run → Debug (Shift+F9)
4. Establece breakpoints en código
5. Interactúa con UI, código se detiene en breakpoints

---

**Última actualización**: 2026-03-04

