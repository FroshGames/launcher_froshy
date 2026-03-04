# 🚀 Froshy Launcher - Guía de Instalación

Bienvenido a **Froshy Launcher**, un launcher moderno y eficiente para Minecraft construido en Java con una arquitectura profesional.

## 📋 Requisitos del Sistema

- **Windows 7+**, **macOS 10.13+**, o **Linux** (Ubuntu 18.04+)
- **Memoria RAM**: Mínimo 2GB (se recomienda 4GB+)
- **Espacio en disco**: 2GB mínimo para el launcher + descargas

> ⚠️ **Nota importante**: El launcher incluye Java 17 portable, por lo que **NO necesitas instalar Java por separado**.

## 🎯 Instalación Rápida

### En Windows

1. **Descarga Java** (solo la primera vez):
   ```cmd
   PowerShell -ExecutionPolicy Bypass -File "scripts\download-jdk.ps1"
   ```

2. **Inicia el launcher**:
   ```cmd
   launcher.bat
   ```

### En macOS/Linux

1. **Descarga Java** (solo la primera vez):
   ```bash
   bash scripts/download-jdk.sh
   ```

2. **Inicia el launcher**:
   ```bash
   bash launcher.sh
   ```

## 🎮 Uso del Launcher

### Panel de Perfiles

- **Refrescar**: Actualiza la lista de perfiles
- **Jugar**: Inicia el perfil seleccionado

### Panel de Configuración

Crea nuevos perfiles de Minecraft con:
- **ID**: Identificador único (ej: `profile-1`)
- **Nombre**: Nombre visible (ej: `Mi Juego`)
- **Versión**: Versión de Minecraft (ej: `1.20.1`)
- **Java**: Ruta del ejecutable de Java
- **JVM Args**: Argumentos de JVM (ej: `-Xmx2G`)
- **Game Args**: Argumentos del juego

### Panel de Descargas

Descarga recursos necesarios para Minecraft:
- Ingresa el destino (ej: `assets`)
- Monitoriza el progreso en tiempo real

### Panel de Consola

- Visualiza logs de operaciones
- Verifica la salud del sistema
- Diagnóstico de problemas

## 🏗️ Estructura del Proyecto

```
launcher_froshy/
├── src/
│   ├── main/java/am/froshy/launcher/
│   │   ├── LauncherApplication.java           # Clase principal
│   │   ├── LauncherUiApplication.java         # Punto de entrada UI
│   │   ├── LauncherRuntime.java               # Gestor de runtime
│   │   ├── api/
│   │   │   └── internal/                      # API interna REST
│   │   ├── application/
│   │   │   └── LauncherService.java          # Lógica de negocio
│   │   ├── config/
│   │   │   └── LauncherConfig.java           # Configuración
│   │   ├── domain/                            # Modelos de dominio
│   │   │   ├── MinecraftProfile.java
│   │   │   ├── LaunchRequest.java
│   │   │   ├── LaunchResult.java
│   │   │   └── DownloadStatus.java
│   │   ├── infrastructure/                    # Persistencia
│   │   │   └── ProfileStore.java
│   │   └── ui/
│   │       └── LauncherFrame.java            # Interfaz gráfica
│   └── test/                                  # Tests unitarios
├── scripts/
│   ├── download-jdk.ps1                      # Descarga Java (Windows)
│   └── download-jdk.sh                       # Descarga Java (Linux/Mac)
├── launcher.bat                               # Script de ejecución (Windows)
├── launcher.sh                                # Script de ejecución (Linux/Mac)
├── pom.xml                                    # Configuración Maven
└── docs/                                      # Documentación

```

## 🔧 Arquitectura Técnica

### Componentes Principales

#### 1. **LauncherRuntime**
Gestor central del ciclo de vida de la aplicación:
- Inicializa la configuración
- Levanta el servidor API interno
- Maneja el shutdown ordenado

#### 2. **API Interna (REST)**
Servidor HTTP interno que proporciona:
- `/internal/v1/profiles` - CRUD de perfiles
- `/internal/v1/launch` - Lanzamiento de juego
- `/internal/v1/downloads` - Gestión de descargas
- `/internal/v1/health` - Estado del sistema

#### 3. **LauncherService**
Capa de lógica de negocio que:
- Valida operaciones
- Persiste datos
- Coordina descargas

#### 4. **ProfileStore**
Persistencia de datos:
- Almacena perfiles en JSON
- Serialización con Jackson
- Validación de integridad

#### 5. **UI (Swing)**
Interfaz de usuario moderna:
- Diseño responsive
- 4 pestañas (Perfiles, Config, Descargas, Consola)
- Ejecución asincrónica

## 📊 Flujo de Ejecución

```
launcher.bat/sh
    ↓
    └─→ Verifica Java local
    └─→ Verifica JAR
    └─→ Ejecuta: java -Xmx2G -jar launcher-1.0-SNAPSHOT.jar
        ↓
        LauncherUiApplication.main()
        ↓
        LauncherRuntime.start()
        ├─→ Carga LauncherConfig
        ├─→ Inicializa ProfileStore
        ├─→ Crea LauncherService
        ├─→ Levanta InternalApiServer (puerto 8080)
        └─→ Devuelve LauncherRuntime
        ↓
        LauncherFrame (UI)
        ├─→ Conexión a API via InternalApiClient
        └─→ Espera interacción del usuario
```

## 🚀 Compilación del Código Fuente

Si deseas compilar el proyecto desde el código fuente:

### Requisitos Previos

- Java Development Kit (JDK) 17+
- Maven 3.8+

### Pasos de Compilación

```bash
# 1. Navegar al directorio del proyecto
cd launcher_froshy

# 2. Compilar y empaquetar
mvn clean package -DskipTests

# 3. El JAR resultante estará en:
# target/launcher-1.0-SNAPSHOT.jar
```

### Ejecutar Tests

```bash
mvn test
```

## 📝 Configuración Avanzada

### Variables de Entorno

```bash
# Windows
set LAUNCHER_PORT=8080
set LAUNCHER_PROFILES=C:\Users\Usuario\.froshy\profiles.json

# Linux/Mac
export LAUNCHER_PORT=8080
export LAUNCHER_PROFILES=~/.froshy/profiles.json
```

### Memoria JVM Personalizada

Edita el script de ejecución:

**Windows (launcher.bat)**:
```batch
set JVM_MEMORY=-Xmx4G -Xms1G
```

**Linux/Mac (launcher.sh)**:
```bash
JVM_MEMORY="-Xmx4G -Xms1G"
```

## 🐛 Solución de Problemas

### Error: "Java no encontrado"

**Solución**: Ejecuta el script de descarga de Java:
- **Windows**: `PowerShell -ExecutionPolicy Bypass -File "scripts\download-jdk.ps1"`
- **Linux/Mac**: `bash scripts/download-jdk.sh`

### Error: "JAR no encontrado"

**Solución**: Compila el proyecto:
```bash
mvn clean package -DskipTests
```

### Error: "Puerto 8080 en uso"

**Solución**: La API interna necesita el puerto 8080. Cierra otras aplicaciones que lo usen o cambia el puerto en `LauncherConfig.java`.

### Baja velocidad de descarga

**Solución**: 
- Aumenta la memoria JVM: `-Xmx4G`
- Usa conexión de red más rápida
- Cierra otras aplicaciones que usen ancho de banda

## 📚 Documentación Adicional

- [API Reference](docs/API_REFERENCE.md) - Especificación completa de la API
- [Architecture](docs/ARCHITECTURE.md) - Diseño de la arquitectura
- [Developer Guide](docs/DEVELOPER_GUIDE.md) - Guía para desarrolladores
- [Changelog](docs/CHANGELOG.md) - Historial de cambios

## 🤝 Contribución

Las contribuciones son bienvenidas. Por favor:

1. Fork del repositorio
2. Crea una rama (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

## 📄 Licencia

Este proyecto está bajo la Licencia MIT. Ver `LICENSE` para más detalles.

## 💬 Soporte

Para reportar problemas o sugerencias, abre un issue en el repositorio.

---

**Versión**: 1.0-SNAPSHOT
**Última actualización**: Marzo 2026
**Estado**: ✅ Funcional

¡Disfruta tu Minecraft! 🎮

