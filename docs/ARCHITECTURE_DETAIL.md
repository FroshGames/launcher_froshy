# 🏗️ Arquitectura de Launcher_Mialu

## Descripción General

Launcher_Mialu es una aplicación de escritorio construida con **arquitectura hexagonal** (puertos y adaptadores) que proporciona una interfaz moderna para gestionar y ejecutar instancias de Minecraft.

## 📐 Diagrama de Capas

```
┌─────────────────────────────────────────────────────────────┐
│                    UI Layer (Swing)                         │
│                   (LauncherFrame.java)                      │
│              Interfaz gráfica con 4 pestañas               │
└──────────────┬──────────────────────────────────────────────┘
               │ (HTTP/REST)
┌──────────────▼──────────────────────────────────────────────┐
│                   API Layer (Internal)                       │
│            (InternalApiServer/InternalApiClient)            │
│           Servidor HTTP en puerto 8080                      │
└──────────────┬──────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────┐
│              Application Layer (Services)                    │
│                 (LauncherService.java)                      │
│          Lógica de negocio y orquestación                   │
└──────────────┬──────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────┐
│               Domain Layer (Models)                          │
│        (MinecraftProfile, LaunchRequest, etc.)              │
│          Modelos de dominio y lógica pura                   │
└──────────────┬──────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────┐
│          Infrastructure Layer (Persistence)                  │
│               (ProfileStore.java)                           │
│          Almacenamiento JSON con Jackson                    │
└─────────────────────────────────────────────────────────────┘
```

## 🔄 Flujo de Datos

### Creación de un Perfil

```
LauncherFrame (UI)
    ↓
    └→ Botón "Crear Perfil"
       ↓
       InternalApiClient.createProfile(profile)
       ↓ (HTTP POST /internal/v1/profiles)
       InternalApiServer.handleCreateProfile()
       ↓
       LauncherService.createProfile(profile)
       ↓
       ProfileStore.saveProfile(profile)
       ↓
       Archivo profiles.json actualizado
```

### Ejecución de Minecraft

```
LauncherFrame (UI)
    ↓
    └→ Botón "Jugar"
       ↓
       InternalApiClient.launch(launchRequest)
       ↓ (HTTP POST /internal/v1/launch)
       InternalApiServer.handleLaunch()
       ↓
       LauncherService.launch(launchRequest)
       ↓
       ProcessBuilder.start()
       ↓
       Minecraft inicia
```

## 📦 Estructura de Paquetes

### `am.froshy.mialu.launcher`
Paquete raíz con clases de punto de entrada:
- **LauncherApplication.java** - Aplicación base no-UI
- **LauncherUiApplication.java** - Punto de entrada con UI
- **LauncherRuntime.java** - Gestor del runtime

### `am.froshy.mialu.launcher.api.internal`
API REST interna:
- **InternalApiServer.java** - Servidor HTTP (puerto 8080)
- **InternalApiClient.java** - Cliente HTTP para UI
- **ApiResponse.java** - DTOs de respuesta
- Endpoints de REST

**Endpoints disponibles**:
```
GET    /internal/v1/health                    - Estado del sistema
GET    /internal/v1/profiles                  - Listar perfiles
POST   /internal/v1/profiles                  - Crear perfil
GET    /internal/v1/profiles/{id}             - Obtener perfil
PUT    /internal/v1/profiles/{id}             - Actualizar perfil
DELETE /internal/v1/profiles/{id}             - Eliminar perfil
POST   /internal/v1/launch                    - Lanzar juego
GET    /internal/v1/downloads/{downloadId}    - Verificar descarga
POST   /internal/v1/downloads                 - Iniciar descarga
```

### `am.froshy.mialu.launcher.application`
Lógica de aplicación:
- **LauncherService.java** - Servicios de negocio
  - Gestión de perfiles
  - Lanzamiento de procesos
  - Descargas

### `am.froshy.mialu.launcher.domain`
Modelos de dominio (entidades):
- **MinecraftProfile.java** - Configuración de perfil (record)
- **LaunchRequest.java** - Solicitud de lanzamiento
- **LaunchResult.java** - Resultado de lanzamiento
- **DownloadStatus.java** - Estado de descarga

### `am.froshy.mialu.launcher.infrastructure`
Persistencia y acceso a datos:
- **ProfileStore.java** - Almacenamiento de perfiles
  - Lee/escribe profiles.json
  - Utiliza Jackson para serialización

### `am.froshy.mialu.launcher.config`
Configuración:
- **LauncherConfig.java** - Variables de configuración
  - Puerto API (8080)
  - Ruta de perfiles
  - Memoria JVM
  - Timeouts

### `am.froshy.mialu.launcher.ui`
Interfaz gráfica:
- **LauncherFrame.java** - Ventana principal
  - Panel de Perfiles
  - Panel de Configuración
  - Panel de Descargas
  - Panel de Consola
- **ProfileCellRenderer.java** - Renderizador de lista de perfiles

## 🔐 Seguridad

### Validaciones

1. **Entrada de Usuario**
   - Validación de IDs de perfil (sin caracteres especiales)
   - Sanitización de nombres de archivo
   - Rango de puertos válidos

2. **API**
   - Comunicación local (localhost:8080)
   - Validación de JSON con Jackson
   - Manejo de excepciones

3. **Ejecución**
   - Escape de argumentos de línea de comandos
   - Validación de rutas de archivo

## 🧪 Estrategia de Testing

```
src/test/
└── java/am/froshy/mialu/launcher/
    ├── api/internal/
    │   ├── InternalApiServerTest.java
    │   └── InternalApiClientTest.java
    └── application/
        └── LauncherServiceTest.java
```

### Cobertura de Tests

- **API**: Endpoints REST y serialización
- **Servicio**: Lógica de negocio y validaciones
- **Persistencia**: Lectura/escritura de perfiles

## 📊 Configuración de Compilación

### Maven Plugins

1. **maven-compiler-plugin** (3.13.0)
   - Target: Java 17
   - Release: 17

2. **maven-shade-plugin** (3.5.0)
   - Empaquetar dependencias en JAR único
   - Transformador de Manifest
   - ServicesResourceTransformer

3. **maven-surefire-plugin** (3.5.2)
   - Ejecutar tests unitarios

4. **maven-jar-plugin** (3.3.0)
   - Configuración de Manifest
   - Clase principal

## 🎨 Paleta de Colores UI

```java
PRIMARY_COLOR   = Color(25, 118, 210)    // Azul
SECONDARY_COLOR = Color(56, 142, 60)     // Verde
ACCENT_COLOR    = Color(255, 111, 0)     // Naranja
BG_COLOR        = Color(240, 240, 240)   // Gris claro
DARK_BG         = Color(50, 50, 50)      // Gris oscuro
TEXT_COLOR      = Color(30, 30, 30)      // Negro suave
```

## ⚙️ Variables de Entorno

```properties
# Configuración del launcher
LAUNCHER_PORT=8080                               # Puerto de la API
LAUNCHER_PROFILES=~/.froshy/profiles.json       # Ubicación de perfiles
LAUNCHER_JAVA_PATH=/path/to/java/bin/java       # Ruta de Java (opcional)
```

## 🔄 Ciclo de Vida

### Inicialización

```java
LauncherRuntime runtime = LauncherRuntime.start()
    ├─→ LauncherConfig.fromEnvironment()
    ├─→ new ObjectMapper().findAndRegisterModules()
    ├─→ new ProfileStore(config.profilesFile(), objectMapper)
    ├─→ new LauncherService(config, profileStore)
    ├─→ new InternalApiServer(port, launcherService)
    └─→ apiServer.start()
```

### Shutdown

```java
Runtime.getRuntime().addShutdownHook(new Thread(runtime::stop))
    └─→ apiServer.stop()
```

## 📈 Escalabilidad Futura

### Mejoras Planificadas

1. **Base de Datos**
   - Migrar de JSON a SQLite/PostgreSQL
   - Mejora de rendimiento con muchos perfiles

2. **Características**
   - Gestor de mods
   - Selector de shaders
   - Gestor de recursos
   - Sincronización en la nube

3. **Interfaz**
   - Tema oscuro/claro customizable
   - Soporte multiidioma
   - Instalador visual

4. **API**
   - Autenticación
   - Validación de token
   - Rate limiting

## 🚀 Optimizaciones de Rendimiento

1. **Caching**
   - Caché de perfiles en memoria
   - TTL para datos dinámicos

2. **Ejecución Asincrónica**
   - ExecutorService para operaciones I/O
   - SwingUtilities.invokeLater para actualizaciones UI

3. **Serialización**
   - Jackson con configuración optimizada
   - Módulos registrados automáticamente

## 📝 Convenciones de Código

### Nombres

- **Clases**: PascalCase (ej: `LauncherFrame`)
- **Variables**: camelCase (ej: `profilesModel`)
- **Constantes**: UPPER_SNAKE_CASE (ej: `PRIMARY_COLOR`)

### Métodos

- Privados para lógica interna
- Públicos solo para puntos de entrada
- Nombres descriptivos (ej: `refreshProfiles()`)

### Records

- Para modelos inmutables (Java 17+)
- Ejemplo: `MinecraftProfile`

---

**Versión**: 1.0
**Última actualización**: Marzo 2026
**Estándar de arquitectura**: Hexagonal + Clean Architecture








