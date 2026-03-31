# 📚 REFERENCIA TÉCNICA - Launcher_Mialu

## Cambios de Package

### Antes
```java
package <namespace-anterior>.*;
import <namespace-anterior>.*;
```

### Ahora
```java
package am.froshy.mialu.launcher.*;
import am.froshy.mialu.launcher.*;
```

**Archivos Afectados:** 30 Java + 5 Test + 1 pom.xml

---

## Estructura de Directorios

```
src/main/java/am/froshy/mialu/launcher/
├── LauncherApplication.java (punto de entrada)
├── LauncherUiApplication.java (main con Swing)
├── LauncherRuntime.java (gestor de runtime)
├── api/
│   └── internal/
│       ├── InternalApiClient.java
│       ├── InternalApiServer.java
│       └── ...
├── application/
│   ├── LauncherService.java
│   ├── ModpackInstaller.java
│   ├── MojangSkinProvider.java (⭐ NUEVO)
│   └── ...
├── config/
│   ├── LauncherConfig.java
│   └── LauncherSettings.java
├── domain/
│   ├── MinecraftProfile.java
│   ├── LaunchRequest.java
│   └── ...
├── infrastructure/
│   └── ProfileStore.java
└── ui/
    ├── LauncherFrame.java (⭐ MODIFICADO)
    ├── CustomButtonUI.java
    └── ...
```

---

## Nueva Clase: MojangSkinProvider

**Ubicación:** `src/main/java/am/froshy/mialu/launcher/application/MojangSkinProvider.java`

**Métodos principales:**
```java
// Obtiene UUID del jugador
Optional<String> getUuidByUsername(String username)

// Descarga skin por UUID
Optional<Path> downloadSkinByUuid(String uuid, Path cachePath)

// Método combinado (username → UUID → skin)
Optional<Path> getSkinByUsername(String username, Path cachePath)

// Descarga desde URL (auxiliar)
private Optional<Path> downloadSkinFromUrl(String skinUrl, Path cachePath)
```

**APIs Utilizadas:**
```
GET https://api.mojang.com/users/profiles/minecraft/{username}
GET https://sessionserver.mojang.com/session/minecraft/profile/{uuid}
GET {skinUrl} (URL de textura de Mojang)
```

**Cache:**
```
~/.mialustudio/launcher/skins/
└── skin.png
```

---

## Cambios en LauncherFrame.java

### Imports Nuevos
```java
import am.froshy.mialu.launcher.application.MojangSkinProvider;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
```

### Variables Nuevas
```java
private final MojangSkinProvider mojangSkinProvider;
private ScheduledFuture<?> skinLoadingTask = null;
```

### Métodos Nuevos
```java
// Programa carga de skin con debounce
private void scheduleLoadSkin()

// Carga skin del username actual
private void loadSkinForCurrentUsername()
```

### Listener Agregado
```java
usernameField.getDocument().addDocumentListener(new DocumentListener() {
    @Override public void insertUpdate(DocumentEvent e) { scheduleLoadSkin(); }
    @Override public void removeUpdate(DocumentEvent e) { scheduleLoadSkin(); }
    @Override public void changedUpdate(DocumentEvent e) { scheduleLoadSkin(); }
});
```

### Botones Consolidados
- Removido: `profilesPlayBtn` (en sección perfiles)
- Mantenido: `launchBtn` (en header)
- Tamaño: 300x50px
- Fuente: 20pt bold

---

## Cambios en pom.xml

### GroupId
```xml
<!-- Antes -->
<groupId>&lt;groupId-anterior&gt;</groupId>

<!-- Ahora -->
<groupId>am.froshy.mialu</groupId>
```

### ArtifactId
```xml
<!-- Antes -->
<artifactId>launcher</artifactId>

<!-- Ahora -->
<artifactId>launcher-mialu</artifactId>
```

### MainClass
```xml
<!-- Antes -->
<mainClass>&lt;mainClass-anterior&gt;</mainClass>

<!-- Ahora -->
<mainClass>am.froshy.mialu.launcher.LauncherUiApplication</mainClass>
```

---

## Archivos de Test

Todos los archivos de test en `src/test/java/am/froshy/mialu/launcher/` han sido actualizados:

```
✅ InternalApiClientTest.java
✅ InternalApiServerTest.java
✅ LauncherServiceTest.java
✅ ModpackInstallerTest.java
✅ MojangVersionDownloaderTest.java
```

---

## Comandos Útiles

### Limpiar y compilar
```bash
mvn clean compile
```

### Ejecutar tests
```bash
mvn test
```

### Ejecutar test específico
```bash
mvn test -Dtest=MojangVersionDownloaderTest
```

### Empaquetar
```bash
mvn clean package
```

### Ejecutar JAR
```bash
java -jar target/launcher_mialu.jar
```

### Con argumentos JVM
```bash
java -Xmx4G -jar target/launcher_mialu.jar
```

### Limpiar caché Maven
```bash
mvn clean -q
```

---

## Configuración de Java

**Requerido:**
- Java 17+
- Maven 3.8+

**Propiedades Maven:**
```xml
<maven.compiler.source>17</maven.compiler.source>
<maven.compiler.target>17</maven.compiler.target>
<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
```

---

## Dependencias

```xml
<!-- Jackson (serialización JSON) -->
com.fasterxml.jackson.core:jackson-databind:2.18.2
com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2

<!-- JUnit (testing) -->
org.junit.jupiter:junit-jupiter:5.11.4

<!-- Swing (incluido en JDK 17) -->
javax.swing (built-in)

<!-- HTTP Client (incluido en JDK 17) -->
java.net.http (built-in)
```

---

## Estructura de API Interna

```
Base: http://localhost:8080/internal/v1/

GET    /health                  - Estado del sistema
GET    /profiles                - Listar perfiles
POST   /profiles                - Crear perfil
GET    /profiles/{id}           - Obtener perfil
PUT    /profiles/{id}           - Actualizar perfil
DELETE /profiles/{id}           - Eliminar perfil
POST   /launch                  - Lanzar juego
GET    /game-output/{launchId}  - Output del juego
```

---

## Rutas de Almacenamiento

```
Windows:
~/.mialu-launcher/
├── game/
│   └── instances/
│       └── [profile-name]/
└── profiles.json

Unix/Linux:
~/.mialu-launcher/
├── game/
│   └── instances/
│       └── [profile-name]/
└── profiles.json

Skins (nuevo):
~/.mialustudio/launcher/skins/
└── [username-hash]/skin.png
```

---

## Validación de Username

**Patrón:** `^[A-Za-z0-9_]{3,16}$`

**Ejemplos válidos:**
- Steve
- Alex_123
- player2024

**Ejemplos inválidos:**
- ab (muy corto)
- player@123 (caracteres especiales)
- thisusernameiswaytoolong (más de 16 caracteres)

---

## Debounce de Skins

**Delay:** 800 milisegundos

Cuando el usuario escribe en el campo de username:
1. Se cancela cualquier carga de skin pendiente
2. Se programa una nueva carga en 800ms
3. Si el usuario sigue escribiendo, se reinicia el contador
4. Solo cuando deje de escribir 800ms, se inicia la descarga

**Ventaja:** Evita sobrecargar la API de Mojang

---

## Logs de Consola

### Información de Skins
```
[Skin] Skin cargada para: [username]
```

### Información General
```
[Launcher] Archivos del modpack: 0/38 ya presentes
[Launcher] JAR del cliente listo
[Launcher] Minecraft iniciado
=== Minecraft ha terminado ===
```

### Errores
```
ERROR: [mensaje de error]
```

---

## Próximas Mejoras (Futuro)

- [ ] Visualización de skins en UI
- [ ] Validación de premium en tiempo real
- [ ] Historial de usernames usados
- [ ] Importación de perfiles de launchers anteriores
- [ ] Dark/Light theme selector
- [ ] Soporte para custom Java path
- [ ] Base de datos SQLite
- [ ] Sincronización en la nube

---

## Versiones

**Actual:** 0.5-SNAPSHOT
**Próximas:** 0.5 (release), 0.6, 1.0

---

## Contacto y Soporte

- Email: support@mialustudio.am
- Organización: MialuStudio
- Licencia: Propietaria (ver LICENSE.md)











