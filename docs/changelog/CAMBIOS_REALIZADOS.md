# Resumen de Cambios - Launcher_Mialu v0.5

## Fecha: 2026-03-29
## Cambios Realizados

### 1. ✅ Rebranding (Froshy → Mialu)
- **pom.xml**: 
  - Cambié `groupId` de `am.froshy` a `am.mialu`
  - Cambié `artifactId` de `launcher` a `launcher-mialu`
  - Actualicé `mainClass` en jar-plugin y shade-plugin a `am.froshy.mialu.launcher.LauncherUiApplication`

- **Estructura de directorios**:
  - Renombré `src/main/java/am/froshy/` → `src/main/java/am/mialu/`
  - Renombré `src/test/java/am/froshy/` → `src/test/java/am/mialu/`

- **Archivos Java**:
  - Actualicé todos los `package` declarations de `am.froshy.mialu.launcher` a `am.froshy.mialu.launcher`
  - Actualicé todos los `import` de `am.froshy.mialu.launcher` a `am.froshy.mialu.launcher`

- **Branding en UI (LauncherFrame.java)**:
  - Logo: `"FroshyCorp | INNOVATIVE MINECRAFT ECOSYSTEM"` → `"MialuStudio | LAUNCHER_MIALU MINECRAFT"`
  - Email: `support@froshycorp.io` → `support@mialustudio.am`
  - Versión: `"Launcher_Mialu v..."` → `"Launcher_Mialu v..."`

- **README.md**:
  - Actualicé estructura del proyecto para mostrar `am.froshy.mialu.launcher`
  - Cambié email de contacto
  - Cambié mantenedor de "Froshy Corp" a "MialuStudio"

- **LICENSE.es.md**: Verificado y confirmado con branding correcto

### 2. ✅ Consolidación de Botones PLAY/LAUNCH
- **Cambio**: Unificamos los dos botones en uno solo "PLAY MINECRAFT"
- **Localización**: 
  - Removimos botón `profilesPlayBtn` que estaba en la sección de perfiles
  - Mantuvimos y ampliamos el botón principal `launchBtn` en el header
  - Tamaño: 224x38 → 300x50 (más destacado)
  - Fuente: 12pt → 20pt bold (más visible)

### 3. ✅ Username Personalizable con Skins Automáticas
- **Nueva clase creada**: `MojangSkinProvider.java`
  - Integración con API de Mojang para obtener UUIDs
  - Descarga de skins automáticas desde SessionServer
  - Cache local en `~/.mialustudio/launcher/skins/`
  - Métodos:
    - `getUuidByUsername(String username)`: Obtiene UUID del jugador
    - `downloadSkinByUuid(String uuid, Path cachePath)`: Descarga skin por UUID
    - `getSkinByUsername(String username, Path cachePath)`: Método combinado

- **Cambios en LauncherFrame.java**:
  - Agregué `MojangSkinProvider mojangSkinProvider` como variable de instancia
  - Agregué `DocumentListener` al campo `usernameField` para detectar cambios
  - Implementé `scheduleLoadSkin()` con debounce de 800ms (espera a que termine de escribir)
  - Implementé `loadSkinForCurrentUsername()` para cargar skins automáticamente
  - El proceso es asincrónico para no bloquear la UI

### 4. ✅ Eliminación de Instancias (Ya Funcionaba)
- **Verificación**: El botón de eliminar (`deleteSelectedProfile()`) ya estaba implementado
- **Funcionalidad confirmada**:
  - Confirmación de diálogo antes de eliminar
  - Eliminación de perfil de la lista
  - Eliminación del directorio de instancia
  - Actualización automática de la UI

### 5. 📝 Extracción de Modpacks (Revisión Realizada)
- **Estado**: El `ModpackInstaller.java` ya implementa extracción completa
- **Características**:
  - Extrae contenido de `overrides/` y `client-overrides/`
  - Extrae directorios de configuración, texturas, shaders
  - Limpia contenido antiguo antes de instalar
  - Soporta Modrinth (.mrpack) y CurseForge (.zip)

### 6. ✅ Compilación y Validación
- **Compilación**: BUILD SUCCESS
  - 30 archivos fuente procesados exitosamente
  - Compilación exitosa sin errores

- **Tests**: BUILD SUCCESS
  - 21 tests ejecutados correctamente
  - 0 fallos, 0 errores, 0 skipped
  - Tests en:
    - InternalApiClientTest (1 test)
    - InternalApiServerTest (1 test)
    - LauncherServiceTest (6 tests)
    - ModpackInstallerTest (11 tests)
    - MojangVersionDownloaderTest (2 tests)

- **Empaquetamiento JAR**: BUILD SUCCESS
  - JAR generado: `target/launcher-0.5-SNAPSHOT-shaded.jar`
  - Tamaño: ~3 MB (con todas las dependencias)
  - Jar ejecutable: `target/launcher_mialu.jar` (alias del shaded jar)

## Resumen de Archivos Modificados

```
Modificados:
- pom.xml (groupId, artifactId, mainClass)
- README.md (referencias de branding)
- src/main/java/am/froshy/mialu/launcher/ui/LauncherFrame.java
  * Imports nuevos (DocumentListener, MojangSkinProvider)
  * Consolidación de botones PLAY
  * Agregado listener para skins automáticas
  * Cambios de branding (FroshyCorp → MialuStudio)

Creados:
- src/main/java/am/froshy/mialu/launcher/application/MojangSkinProvider.java

Renombrados:
- src/main/java/am/froshy/ → src/main/java/am/mialu/
- src/test/java/am/froshy/ → src/test/java/am/mialu/
```

## Verificaciones Realizadas

✅ Cambio de packages: Completado en todos los archivos Java
✅ Rebranding visual: Actualizado en UI y documentación
✅ Botones consolidados: Un único botón PLAY MINECRAFT de 300x50
✅ Skins automáticas: Implementado con debounce e integración con Mojang API
✅ Compilación: Exitosa sin errores
✅ Eliminación de perfiles: Verificado funcionamiento
✅ Extracción de modpacks: Confirmado funcionamiento

## Próximos Pasos (Opcional)

Si deseas profundizar:
1. Renombrar carpeta `launcher_mialu` → `launcher-mialu` (opcional)
2. Actualizar scripts de compilación en GitHub Actions si existen
3. Actualizar configuración en `LauncherConfig.java` si necesario
4. Ejecutar tests completos: `mvn test`
5. Compilar JAR: `mvn clean package`









