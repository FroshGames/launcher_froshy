# Guía de Desarrollo - Froshy Launcher

## Setup de Desarrollo

### IDE Recomendado

- **IntelliJ IDEA Community** (gratuito): https://www.jetbrains.com/idea/download/
- **Eclipse IDE**: https://www.eclipse.org/
- **VS Code + Extension Pack for Java**: https://code.visualstudio.com/

### Configuración IntelliJ IDEA

1. **Abrir proyecto**:
   - File → Open → Selecciona carpeta `launcher_froshy`
   - Espera a que IDE indexe

2. **Configurar SDK**:
   - File → Project Structure → SDK
   - Selecciona JDK 17+
   - Si no aparece, "Add SDK" → JDK → Download JDK 17

3. **Verificar Maven**:
   - Maven debería detectarse automáticamente
   - Si no: File → Settings → Build, Execution, Deployment → Build Tools → Maven
   - Maven home path: `C:\workspace\apache-maven-3.9.9` (o tu instalación)

---

## Workflow Típico de Desarrollo

### 1. Implementar Feature Nueva

```bash
# 1. Actualizar rama (si aplica)
git checkout main
git pull

# 2. Crear rama feature
git checkout -b feature/mi-nueva-funcionalidad

# 3. Código + Tests
# (editar archivos en IDE)

# 4. Compilar localmente
mvn clean compile

# 5. Ejecutar tests
mvn test

# 6. Empaquetar
mvn package

# 7. Testing manual
java -jar target/launcher-1.0-SNAPSHOT.jar
# (interactuar con UI)

# 8. Commit
git add .
git commit -m "feat: descripción de cambios"

# 9. Push y PR
git push origin feature/mi-nueva-funcionalidad
```

### 2. Debuggear Código

#### Con IDE (Recomendado)

1. **Abrir clase** a debuggear (ej: `LauncherService.java`)
2. **Click izquierda** en número de línea → pone breakpoint (punto rojo)
3. **Run → Debug 'LauncherUiApplication'** (Shift+F9)
4. Interactúa con UI hasta que llegues al breakpoint
5. Panel "Debugger" muestra:
   - Variables locales
   - Stack trace
   - Watches personalizadas
6. Controles: Step Over (F8), Step Into (F7), Continue (F9)

#### Con System.out (Emergencia)

```java
System.out.println("DEBUG: profileId=" + profileId);
// Output en consola
```

---

## Agregar Nueva Feature

### Ejemplo: Endpoint para Eliminar Perfil

#### Paso 1: Agregar método a LauncherService

```java
public class LauncherService {
    // ... existing code ...
    
    public void deleteProfile(String profileId) {
        if (!profiles.containsKey(profileId)) {
            throw new IllegalArgumentException("Perfil no encontrado: " + profileId);
        }
        profiles.remove(profileId);
        profileStore.save(profiles.values());
    }
}
```

#### Paso 2: Agregar test

```java
// LauncherServiceTest.java
@Test
void shouldDeleteProfile(@TempDir Path tempDir) {
    LauncherService service = ...;
    service.createProfile(profile);
    
    assertEquals(1, service.listProfiles().size());
    service.deleteProfile("default");
    assertEquals(0, service.listProfiles().size());
}
```

Ejecutar:
```bash
mvn test -Dtest=LauncherServiceTest#shouldDeleteProfile
```

#### Paso 3: Agregar endpoint HTTP

```java
// InternalApiServer.java
private void createContexts() {
    // ... existing ...
    server.createContext("/internal/v1/profiles", 
        exchange -> withErrorHandling(exchange, this::handleProfiles));
}

private void handleProfiles(HttpExchange exchange) throws IOException {
    String method = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getPath();
    
    if ("GET".equalsIgnoreCase(method)) {
        sendJson(exchange, 200, launcherService.listProfiles());
        return;
    }
    
    if ("POST".equalsIgnoreCase(method)) {
        MinecraftProfile profile = readBody(exchange, MinecraftProfile.class);
        sendJson(exchange, 201, launcherService.createProfile(profile));
        return;
    }
    
    // NEW: DELETE
    if ("DELETE".equalsIgnoreCase(method) && path.contains("/profiles/")) {
        String profileId = path.substring(path.lastIndexOf('/') + 1);
        launcherService.deleteProfile(profileId);
        sendJson(exchange, 204, new Object()); // 204 No Content
        return;
    }
    
    sendMethodNotAllowed(exchange, List.of("GET", "POST", "DELETE"));
}
```

#### Paso 4: Actualizar cliente

```java
// InternalApiClient.java
public void deleteProfile(String profileId) {
    send("profiles/" + profileId, "DELETE", null, new TypeReference<>() {});
}
```

#### Paso 5: Agregar a UI

```java
// LauncherFrame.java
private void initUi() {
    // ... existing ...
    
    JButton deleteButton = new JButton("Eliminar");
    deleteButton.addActionListener(e -> deleteSelectedProfile());
    
    actionsPanel.add(deleteButton);
}

private void deleteSelectedProfile() {
    MinecraftProfile selected = profilesList.getSelectedValue();
    if (selected == null) {
        JOptionPane.showMessageDialog(this, "Selecciona un perfil");
        return;
    }
    
    int confirm = JOptionPane.showConfirmDialog(this,
        "¿Eliminar \"" + selected.displayName() + "\"?",
        "Confirmar", JOptionPane.YES_NO_OPTION);
    
    if (confirm != JOptionPane.YES_OPTION) return;
    
    runAsync(() -> {
        apiClient.deleteProfile(selected.id());
        SwingUtilities.invokeLater(() -> {
            appendOutput("Perfil eliminado: " + selected.id());
            refreshProfiles();
        });
    });
}
```

#### Paso 6: Tests de integración

```java
// InternalApiClientTest.java
@Test
void shouldDeleteProfile(@TempDir Path tempDir) {
    // ... setup server ...
    
    InternalApiClient client = ...;
    client.createProfile(profile);
    assertEquals(1, client.listProfiles().size());
    
    client.deleteProfile("builder");
    assertEquals(0, client.listProfiles().size());
}
```

#### Paso 7: Validar

```bash
mvn clean test
mvn package
java -jar target/launcher-1.0-SNAPSHOT.jar
# Crear, listar, eliminar en UI
```

---

## Estructura de Commits

Usa [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Ejemplos

```
feat(profiles): agregar endpoint DELETE para eliminar perfiles
fix(api): corregir parsing de campos JSON en LaunchRequest
test(launcher): aumentar coverage de LauncherService
docs(readme): actualizar instrucciones de setup
refactor(ui): extraer método para polling de descargas
chore(deps): actualizar Jackson a 2.18.2
```

---

## Troubleshooting

### Problema: `mvn compile` falla con "cannot find symbol"

**Causa**: IDE indexing retrasado o classpath corrupto

**Solución**:
```bash
mvn clean
# Esperar a que IDE reindexa (esquina inferior derecha)
mvn compile
```

### Problema: Tests pasan localmente pero fallan en CI

**Causa**: Orden de pruebas no determinística, tests aislamiento insuficiente

**Solución**:
```bash
# Ejecutar tests en orden aleatorio
mvn test -Dorg.junit.jupiter.execution.parallel.enabled=true

# Cada test debe usar @TempDir y crear sus propias fixtures
```

### Problema: "Address already in use" al ejecutar tests

**Causa**: Puerto 7878 ya en uso (servidor anterior no paró)

**Solución**:
```bash
# Buscar proceso Java
lsof -i :7878     # En Mac/Linux
netstat -ano | findstr :7878  # En Windows

# Matar proceso
kill -9 <PID>     # En Mac/Linux
taskkill /PID <PID> /F  # En Windows

# O: Cambiar puerto en tests
new InternalApiServer(0, service)  // Puerto aleatorio
```

### Problema: Swing UI se abre pero sin eventos

**Causa**: Acciones fuera del EDT (Event Dispatch Thread)

**Solución**: Siempre usar `SwingUtilities.invokeLater()` para actualizar UI desde threads:

```java
// BAD
new Thread(() -> {
    label.setText("Done"); // ¡Crash!
}).start();

// GOOD
new Thread(() -> {
    SwingUtilities.invokeLater(() -> {
        label.setText("Done"); // Safe
    });
}).start();
```

---

## Performance Profiling

### Usar JProfiler o YourKit

1. Descargar: https://www.jprofiler.com/ (free trial)
2. Conectar a proceso Java
3. Ver:
   - CPU usage
   - Memoria
   - Threads
   - GC pressure

### Usar Mission Control (JDK integrado)

```bash
jcmd <PID> JFR.start filename=recording.jfr
jcmd <PID> JFR.stop
jcmd <PID> JFR.dump filename=recording.jfr

# Abrir en JDK Mission Control (tools/jmc)
```

---

## Code Coverage

### Agregar JaCoCo

En `pom.xml`:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.10</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Ejecutar:
```bash
mvn clean test jacoco:report
# Abrir: target/site/jacoco/index.html
```

---

## Dependencias Actuales

```xml
<!-- Jackson -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.18.2</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
    <version>2.18.2</version>
</dependency>

<!-- JUnit 5 -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.11.4</version>
    <scope>test</scope>
</dependency>
```

### Agregar Dependencia Nueva

1. **Buscar en Maven Central**: https://mvnrepository.com/
2. **Copiar XML** a `<dependencies>`
3. **mvn clean compile** para descargar
4. **Usar** en código

---

## Guidelines de Código

### Nombrado

```java
// Classes: PascalCase, sustantivos
public class LauncherService { }

// Methods: camelCase, verbos
public void startDownload(String target) { }

// Constants: UPPER_SNAKE_CASE
private static final String DEFAULT_VERSION = "1.20.1";

// Variables: camelCase, descriptivos
List<MinecraftProfile> loadedProfiles;
String javaExecutablePath;
```

### Longitud de Línea

Máximo 120 caracteres (configurable en IDE).

```java
// BAD: demasiado largo
String veryLongCommandLine = "java -Xmx2G -jar minecraft-1.20.1.jar --gameDir ~/.froshy-launcher/game --username Steve";

// GOOD: quebrado
String commandLine = "java -Xmx2G -jar minecraft-1.20.1.jar "
    + "--gameDir ~/.froshy-launcher/game "
    + "--username Steve";
```

### Null Handling

```java
// BAD
if (profile != null && profile.id() != null) { }

// GOOD: usar Optional
Optional<MinecraftProfile> profile = findById(id);
profile.ifPresent(p -> launch(p));

// O: records con validación
public record MinecraftProfile(...) {
    public MinecraftProfile {
        if (id == null) throw new IllegalArgumentException(...);
    }
}
```

### Javadoc

```java
/**
 * Crea un nuevo perfil de Minecraft.
 *
 * @param profile Perfil a crear (no null)
 * @return Perfil creado con los mismos datos
 * @throws IllegalArgumentException si el ID ya existe
 */
public MinecraftProfile createProfile(MinecraftProfile profile) {
    // ...
}
```

---

## Release Process

### 1. Preparar Release

```bash
# Asegurar todo está commiteado
git status

# Actualizar CHANGELOG
# vim docs/CHANGELOG.md

# Cambiar versión en pom.xml
# 1.0-SNAPSHOT → 1.0

# Commit
git add .
git commit -m "release: v1.0"

# Tag
git tag -a v1.0 -m "Froshy Launcher v1.0"

# Push
git push origin main
git push origin v1.0
```

### 2. Build Release

```bash
mvn clean package
# Genera: target/launcher-1.0.jar (sin SNAPSHOT)
```

### 3. Publicar

En GitHub:
- Releases → Draft new release
- Tag version: v1.0
- Attach JAR: `target/launcher-1.0.jar`
- Publish release

---

## Próximos Pasos

- [ ] Agregar logging (SLF4J + Logback)
- [ ] Implementar autenticación Microsoft
- [ ] Descargar assets reales de Mojang
- [ ] Ejecutar proceso real con ProcessBuilder
- [ ] Unit tests + Integration tests para todo
- [ ] Documentación de API con OpenAPI/Swagger
- [ ] CI/CD (GitHub Actions)
- [ ] Empaquetamiento con jpackage

---

**Última actualización**: 2026-03-04

