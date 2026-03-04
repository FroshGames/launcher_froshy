# Changelog - Froshy Launcher

Todas las modificaciones notables en este proyecto serán documentadas en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/),
y el versionado sigue [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

### Planeado

- [ ] Autenticación real de Microsoft OAuth2
- [ ] Descarga real de assets desde Mojang API
- [ ] Ejecución real del juego con ProcessBuilder
- [ ] Captura de logs del juego en la UI
- [ ] Gestor de mods (Forge/Fabric support)
- [ ] Multi-threading para descargas paralelas
- [ ] Caché de versiones descargadas
- [ ] Auto-updater del launcher
- [ ] Empaquetamiento como EXE (jpackage)
- [ ] Soporte para Linux y macOS
- [ ] Temas personalizables (Light/Dark mode)
- [ ] Logging con SLF4J + Logback
- [ ] CI/CD con GitHub Actions
- [ ] Documentación en inglés

---

## [1.0-SNAPSHOT] - 2026-03-04

### Added

#### Backend (Fase 1)
- ✅ Estructura base en capas (Domain, Application, Infrastructure)
- ✅ `LauncherConfig` para centralizar configuración
- ✅ `LauncherService` con casos de uso principales:
  - Listar perfiles
  - Crear perfiles
  - Lanzar juego (simulado)
  - Simular descargas con progreso
- ✅ `ProfileStore` para persistencia JSON en `~/.froshy-launcher/profiles.json`
- ✅ Modelos de dominio como records (MinecraftProfile, LaunchRequest, etc.)

#### API HTTP (Fase 2)
- ✅ `InternalApiServer` embebido sin frameworks externos (JDK HttpServer)
- ✅ 6 endpoints implementados:
  - `GET /internal/v1/health` - Estado del servidor
  - `GET /internal/v1/profiles` - Listar perfiles
  - `POST /internal/v1/profiles` - Crear perfil
  - `POST /internal/v1/launch` - Lanzar juego
  - `POST /internal/v1/downloads` - Iniciar descarga
  - `GET /internal/v1/downloads/{id}` - Estado de descarga
- ✅ Manejo de errores centralizado con códigos HTTP apropriados
- ✅ Validación en fronteras (Domain → Application → API)

#### UI Visual (Fase 3)
- ✅ `LauncherFrame` construida con Swing puro
- ✅ Dos paneles principales:
  - Izquierda: Listado y gestión de perfiles
  - Derecha: Formulario de creación + acciones
- ✅ Formularios para crear perfiles (ID, Nombre, Versión, Java, JVM args, Game args)
- ✅ Botones para:
  - Refrescar lista de perfiles
  - Crear perfil nuevo
  - Lanzar juego seleccionado
  - Consultar health de API
  - Iniciar descarga
- ✅ Barra de progreso para monitorear descargas
- ✅ Área de output para logs de acciones y errores
- ✅ Polling asincrónico de descargas (Timer + ExecutorService)

#### Cliente HTTP
- ✅ `InternalApiClient` para consumir la API desde la UI
- ✅ Métodos para todos los endpoints
- ✅ Manejo de timeouts y errores
- ✅ Normalización de URIs

#### Configuración y Build
- ✅ `pom.xml` con:
  - Jackson 2.18.2 (JSON serialization)
  - Jackson JSR310 (java.time support)
  - JUnit Jupiter 5.11.4 (testing)
  - Plugins: compiler, surefire, jar
- ✅ `LauncherApplication` como entrypoint API-only (headless)
- ✅ `LauncherUiApplication` como entrypoint visual (default en JAR)
- ✅ `LauncherRuntime` para bootstrap compartido

#### Testing
- ✅ `LauncherServiceTest` (2 casos):
  - Crear y listar perfiles
  - Generar línea de comandos para lanzamiento
- ✅ `InternalApiServerTest` (1 caso):
  - Verificar endpoint `/health` retorna 200
- ✅ `InternalApiClientTest` (1 caso):
  - Flujo completo: create profile → list → launch
- ✅ 4/4 tests pasan

#### Documentación
- ✅ `README.md` en raíz con quickstart
- ✅ `docs/README.md` - Documentación completa (10 secciones)
- ✅ `docs/ARCHITECTURE.md` - Arquitectura y patrones
- ✅ `docs/API_REFERENCE.md` - Endpoints con ejemplos curl
- ✅ `docs/DEVELOPER_GUIDE.md` - Guía de desarrollo

### Changed

- Actualizado `pom.xml`: Java 17 (compatible con JDK 17.0.12)
- Refactorizado entrypoint de `LauncherApplication` para usar `LauncherRuntime`
- UI por defecto en JAR (cambio de mainClass)

### Fixed

- ✅ Serialización de `java.time.Instant` (agregado jackson-datatype-jsr310)
- ✅ Normalización de rutas HTTP en `InternalApiClient`
- ✅ Validaciones en constructores de records
- ✅ Thread-safety en descargas simuladas

### Notes

- Lanzamiento de juego aún es simulado (genera comando, no ejecuta)
- Descargas simuladas con incremento de progreso cada 300ms
- Perfiles persisten en JSON local
- UI bloqueante en operaciones evitada con ExecutorService

---

## Próximas Versiones

### v1.1 (planeado)

- **Autenticación**: Integración con OAuth2 de Microsoft
- **Descargas reales**: Consumir API de Mojang
- **Mejor UI**: Dark mode, themes
- **Logging**: SLF4J + Logback

### v2.0 (planeado)

- **Ejecución real**: ProcessBuilder para lanzar java
- **Mods**: Soporte para Forge/Fabric
- **Empaquetamiento**: jpackage para EXE
- **Auto-updater**: Actualización automática del launcher

---

## Estructura del Changelog

Cada versión documenta:

- **Added**: Nuevas features
- **Changed**: Cambios a features existentes
- **Fixed**: Bugs arreglados
- **Deprecated**: Features que serán removidas
- **Removed**: Features removidas
- **Security**: Cambios de seguridad

---

## Cómo Contribuir

Cuando hagas un commit, describe el cambio en el formato de Conventional Commits:

```
feat(scope): descripción
fix(scope): descripción
docs(scope): descripción
refactor(scope): descripción
test(scope): descripción
```

Ejemplo:

```
feat(api): agregar endpoint DELETE para eliminar perfiles
fix(ui): corregir nullpointer en renderizado de lista
docs(readme): actualizar instrucciones de setup
```

---

**Última actualización**: 2026-03-04

