# launcher_froshy

Launcher de Minecraft base en Java con API interna local y UI de escritorio (Swing).

## 🚀 Quickstart

### Requisitos

- JDK 17+
- Maven 3.9.9+

### Iniciar (modo visual)

```bash
mvn clean package
java -jar target/launcher-1.0-SNAPSHOT.jar
```

✅ **El JAR ya incluye todas las dependencias automáticamente.**

### Iniciar (modo API-only headless)

```bash
mvn compile
java -cp target/classes am.froshy.launcher.LauncherApplication
```

**¿Problemas? Ver [docs/QUICKSTART.md](docs/QUICKSTART.md) para troubleshooting completo.**

## 📚 Documentación

### 🆕 Empezar Aquí

- **[docs/QUICKSTART.md](docs/QUICKSTART.md)** ⚡ - Instalación rápida y troubleshooting
  - Instalación paso a paso (Windows, macOS, Linux)
  - Verificación de requisitos
  - Comandos comunes
  - FAQ y solución de problemas

### Para Usuarios

- **[docs/README.md](docs/README.md)** - Documentación completa del launcher
  - Visión general
  - Estructura del proyecto
  - Instalación y setup
  - Uso del launcher
  - API interna (endpoints)
  - Testing

### Para Desarrolladores

- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** - Arquitectura y diseño
  - Diagramas de capas
  - Principios de diseño (DI, inmutabilidad, validación)
  - Flujos principales (crear perfil, lanzar, descargar)
  - Manejo de errores y concurrencia
  - Extensibilidad futura

- **[docs/API_REFERENCE.md](docs/API_REFERENCE.md)** - Referencia completa de endpoints
  - Todos los endpoints con ejemplos curl
  - Códigos HTTP y manejo de errores
  - Casos de uso completos

- **[docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md)** - Guía práctica de desarrollo
  - Setup del IDE (IntelliJ, Eclipse, VS Code)
  - Workflow de desarrollo
  - Cómo agregar features nuevas
  - Debugging y profiling
  - Code coverage

### Historia del Proyecto

- **[docs/CHANGELOG.md](docs/CHANGELOG.md)** - Historial de cambios y versiones
  - Cambios por versión
  - Próximos pasos planeados

## 🏗️ Estructura

```
launcher_froshy/
├── docs/                    # Documentación completa (5 archivos)
│   ├── QUICKSTART.md       # ⭐ Empezar aquí
│   ├── README.md           # Guía completa de usuario
│   ├── ARCHITECTURE.md     # Diseño técnico
│   ├── API_REFERENCE.md    # Endpoints con ejemplos
│   ├── DEVELOPER_GUIDE.md  # Guía de desarrollo
│   └── CHANGELOG.md        # Historial de cambios
│
├── pom.xml                 # Configuración Maven
├── src/main/java/am/froshy/launcher/
│   ├── LauncherApplication.java        # Entrypoint API-only
│   ├── LauncherUiApplication.java      # Entrypoint visual (default)
│   ├── LauncherRuntime.java            # Bootstrap compartido
│   ├── config/
│   │   └── LauncherConfig.java
│   ├── domain/
│   │   ├── MinecraftProfile.java
│   │   ├── LaunchRequest.java
│   │   ├── LaunchResult.java
│   │   └── DownloadStatus.java
│   ├── application/
│   │   └── LauncherService.java
│   ├── infrastructure/
│   │   └── ProfileStore.java
│   ├── api/internal/
│   │   ├── InternalApiServer.java
│   │   └── InternalApiClient.java
│   └── ui/
│       └── LauncherFrame.java
│
└── src/test/java/am/froshy/launcher/
    ├── application/
    │   └── LauncherServiceTest.java
    └── api/internal/
        ├── InternalApiServerTest.java
        └── InternalApiClientTest.java
```

## ✨ Características

- ✅ **API HTTP interna** embebida (`/internal/v1/*`)
- ✅ **Interfaz Swing** visual e interactiva
- ✅ **Gestión de perfiles** con persistencia JSON
- ✅ **Simulación de descargas** con progreso en tiempo real
- ✅ **Arquitectura desacoplada** (backend ↔ API ↔ UI)
- ✅ **Tests unitarios e integración**
- ✅ **Documentación completa** (5 markdown files)

## 🔧 Comandos Comunes

```bash
# Compilar
mvn clean compile

# Tests (4/4 passing)
mvn test

# Empaquetar JAR
mvn package

# Limpiar
mvn clean

# Ejecutar visual
java -jar target/launcher-1.0-SNAPSHOT.jar

# Ejecutar API-only
java -cp target/classes am.froshy.launcher.LauncherApplication
```

## 📋 API Interna

```
GET  /internal/v1/health              → Estado del servidor
GET  /internal/v1/profiles            → Listar perfiles
POST /internal/v1/profiles            → Crear perfil
POST /internal/v1/launch              → Lanzar juego
POST /internal/v1/downloads           → Iniciar descarga
GET  /internal/v1/downloads/{id}      → Estado de descarga
```

**Para ejemplos curl y casos completos, ver [docs/API_REFERENCE.md](docs/API_REFERENCE.md).**

## 🎮 Uso de la UI

### Panel Izquierdo: Perfiles
- Listado de perfiles guardados
- Botón "Refrescar" para recargar desde API

### Panel Derecho: Acciones
- **Formulario**: Crear nuevo perfil (ID, Nombre, Versión, Java, JVM args, Game args)
- **Botones**:
  - "Crear perfil" → Valida y guarda
  - "Launch" → Genera comando de lanzamiento
  - "Health" → Consulta estado de la API
  - "Descargar" → Simula descarga de asset
- **Barra de progreso**: Monitorea descargas en tiempo real
- **Output**: Log de acciones, errores y confirmaciones

## 🚀 Próximos Pasos

1. **Autenticación real** de Microsoft/Launcher
2. **Descarga de assets** desde Mojang servers
3. **Ejecución real** con ProcessBuilder
4. **Gestor de mods** (Forge/Fabric)
5. **Auto-updater** del launcher
6. **Empaquetamiento** (EXE con jpackage)

Ver [docs/CHANGELOG.md](docs/CHANGELOG.md) para roadmap detallado.

## 📖 Recursos Externos

- [Minecraft Wiki](https://minecraft.wiki/)
- [Launcher Meta API](https://wiki.vg/Launcher.meta)
- [Java HttpClient Docs](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html)
- [Swing Tutorial](https://docs.oracle.com/javase/tutorial/uiswing/)
- [Maven Repository](https://mvnrepository.com/)

## 📝 Licencia

MIT

---

**Versión**: 1.0-SNAPSHOT  
**Última actualización**: 2026-03-04  
**Status**: ✅ Completamente documentado
