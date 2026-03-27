# 🎮 Launcher_Mialu

Un launcher moderno, rápido y eficiente para Minecraft construido con Java 17 y arquitectura hexagonal.

![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat-square&logo=java)
![Maven](https://img.shields.io/badge/Maven-3.8%2B-C71A36?style=flat-square&logo=apache-maven)
![License](https://img.shields.io/badge/License-Proprietary-red?style=flat-square)
![Status](https://img.shields.io/badge/Status-Production%20Ready-brightgreen?style=flat-square)

## ✨ Características

- 🎯 **Interfaz Moderna**: UI intuitiva con Swing y diseño responsivo
- 📦 **Java Integrado**: Incluye OpenJDK 17 portable (sin dependencias externas)
- 🚀 **Rápido y Eficiente**: Arquitectura optimizada con API REST interna
- 🔧 **Flexible**: Crea múltiples perfiles con configuración personalizada
- 📥 **Gestor de Descargas**: Monitor de progreso en tiempo real
- 💾 **Persistencia**: Almacenamiento automático de perfiles en JSON
- 🧪 **Testeado**: Suite completa de pruebas unitarias
- 📚 **Bien Documentado**: Documentación técnica y guías de usuario

## 🚀 Inicio Rápido

### Requisitos Mínimos

- Windows 7+, macOS 10.13+, o Linux (Ubuntu 18.04+)
- 2 GB de RAM
- 2 GB de espacio en disco

### Windows

```cmd
# 1. Descargar Java (solo la primera vez)
PowerShell -ExecutionPolicy Bypass -File "scripts\download-jdk.ps1"

# 2. Ejecutar launcher
launcher.bat
```

### macOS/Linux

```bash
# 1. Descargar Java (solo la primera vez)
bash scripts/download-jdk.sh

# 2. Ejecutar launcher
bash launcher.sh
```

## 📖 Documentación Completa

- **[Guía de Instalación](INSTALLATION.md)** - Instalación paso a paso
- **[Arquitectura Detallada](docs/ARCHITECTURE_DETAIL.md)** - Diseño técnico
- **[Referencia de API](docs/API_REFERENCE.md)** - Endpoints REST
- **[Guía del Desarrollador](docs/DEVELOPER_GUIDE.md)** - Contribuir al proyecto
- **[Changelog](docs/CHANGELOG.md)** - Historial de versiones

## 🏗️ Estructura del Proyecto

```
launcher_froshy/
├── src/main/java/am/froshy/launcher/
│   ├── LauncherApplication.java              # Aplicación base
│   ├── LauncherUiApplication.java            # Punto de entrada UI
│   ├── LauncherRuntime.java                  # Gestor de runtime
│   ├── api/internal/                         # API REST interna
│   ├── application/LauncherService.java      # Lógica de negocio
│   ├── config/LauncherConfig.java            # Configuración
│   ├── domain/                               # Modelos de dominio
│   │   ├── MinecraftProfile.java
│   │   ├── LaunchRequest.java
│   │   ├── LaunchResult.java
│   │   └── DownloadStatus.java
│   ├── infrastructure/ProfileStore.java      # Persistencia
│   └── ui/LauncherFrame.java                 # Interfaz gráfica
├── scripts/
│   ├── download-jdk.ps1                      # Descarga Java (Windows)
│   └── download-jdk.sh                       # Descarga Java (Linux/Mac)
├── launcher.bat                              # Script ejecución (Windows)
├── launcher.sh                               # Script ejecución (Linux/Mac)
├── pom.xml                                   # Configuración Maven
└── docs/                                     # Documentación completa
```

## 🎨 Interfaz de Usuario

El launcher cuenta con una interfaz moderna organizada en 4 pestañas:

### Pestaña 1: Perfiles
- Visualizar lista de perfiles creados
- Seleccionar y lanzar Minecraft
- Refrescar lista automáticamente

### Pestaña 2: Configuración
- Crear nuevos perfiles
- Configurar versión de Minecraft
- Ajustar argumentos JVM y del juego

### Pestaña 3: Descargas
- Iniciar descargas de recursos
- Monitor de progreso en tiempo real
- Estado detallado de descarga

### Pestaña 4: Consola
- Visualizar logs de operaciones
- Verificar estado del sistema
- Diagnóstico de problemas

## 🔌 API Interna

El launcher expone una API REST interna en `http://localhost:8080/internal/v1/`:

```
GET    /health              - Estado del sistema
GET    /profiles            - Listar perfiles
POST   /profiles            - Crear perfil
GET    /profiles/{id}       - Obtener perfil
PUT    /profiles/{id}       - Actualizar perfil
DELETE /profiles/{id}       - Eliminar perfil
POST   /launch              - Lanzar juego
GET    /downloads/{id}      - Estado de descarga
POST   /downloads           - Iniciar descarga
```

Ver [Referencia de API](docs/API_REFERENCE.md) para detalles completos.

## 🛠️ Compilación

### Requisitos Previos

- Java Development Kit (JDK) 17+
- Maven 3.8+

### Compilar

```bash
cd launcher_froshy
mvn clean package -DskipTests
```

El JAR empaquetado estará en `target/launcher-1.0-SNAPSHOT.jar`

### Ejecutar Tests

```bash
mvn test
```

## 🔍 Arquitectura

El launcher utiliza una arquitectura **hexagonal (puertos y adaptadores)** combinada con principios de **Clean Architecture**:

```
┌─────────────────────────────────────────┐
│         UI Layer (Swing)                │
│        (LauncherFrame.java)             │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│      API Layer (REST/HTTP)              │
│   (InternalApiServer/Client)            │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│    Application Layer (Services)         │
│      (LauncherService.java)             │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│      Domain Layer (Models)              │
│   (MinecraftProfile, LaunchRequest)     │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  Infrastructure Layer (Persistence)     │
│      (ProfileStore.java)                │
└─────────────────────────────────────────┘
```

Beneficios de esta arquitectura:
- ✅ Altamente testeable
- ✅ Desacoplamiento entre capas
- ✅ Fácil de mantener y extender
- ✅ Separación clara de responsabilidades

## 🧪 Testing

El proyecto incluye tests unitarios para:
- Endpoints API REST
- Lógica de servicio
- Serialización/deserialización

```bash
# Ejecutar todos los tests
mvn test

# Ejecutar test específico
mvn test -Dtest=InternalApiServerTest

# Con cobertura
mvn test jacoco:report
```

## 📊 Dependencias Principales

- **Jackson** (2.18.2) - Serialización JSON
- **JUnit Jupiter** (5.11.4) - Testing
- **Java Swing** - UI (incluida en JDK)

## 🐛 Solución de Problemas

### "Java no encontrado"
Ejecuta: `PowerShell -ExecutionPolicy Bypass -File "scripts\download-jdk.ps1"` (Windows)

### "JAR no encontrado"
Compila: `mvn clean package -DskipTests`

### "Puerto 8080 en uso"
Cierra aplicaciones que usen ese puerto o cambia el puerto en `LauncherConfig.java`

Ver [Guía de Instalación](INSTALLATION.md) para más soluciones.

## 💡 Configuración Avanzada

### Variables de Entorno

```bash
export LAUNCHER_PORT=8080
export LAUNCHER_PROFILES=~/.froshy/profiles.json
export LAUNCHER_JAVA_PATH=/ruta/a/java
```

### Memoria JVM Personalizada

Edita el archivo de script (launcher.bat o launcher.sh):
```
# Cambiar -Xmx2G a el valor deseado, ej: -Xmx4G
JVM_MEMORY=-Xmx4G -Xms1G
```

## 🤝 Contribución

¡Las contribuciones son bienvenidas! Por favor:

1. Fork el repositorio
2. Crea una rama feature (`git checkout -b feature/NuevaFeature`)
3. Commit tus cambios (`git commit -m 'Agregar NuevaFeature'`)
4. Push a la rama (`git push origin feature/NuevaFeature`)
5. Abre un Pull Request

### Estándares de Código

- Java 17+ moderno
- Nombres descriptivos
- Métodos cortos y enfocados
- Tests para nuevas funcionalidades
- Documentación en código

## 📄 Licencia

Este proyecto está bajo una licencia propietaria de `Launcher_Mialu`.

- Versión principal en inglés: [LICENSE.md](LICENSE.md)
- Versión informativa en español: [LICENSE.es.md](LICENSE.es.md)

## 📞 Soporte

- 🐛 Reportar bugs: Abre un issue
- 💬 Sugerencias: Discusiones en el repositorio
- 📧 Email: contacto@froshycorp.am

## 🙏 Agradecimientos

- Minecraft por ser awesome 🎮
- OpenJDK/Temurin por Java
- Apache Maven por el build
- Eclipse por el ecosistema Java

## 🎯 Hoja de Ruta

### v1.0 ✅ (Actual)
- [x] Interfaz de usuario completa
- [x] API REST interna
- [x] Gestión de perfiles
- [x] Lanzamiento de Minecraft
- [x] Gestor de descargas
- [x] Java integrado

### v1.1 (Planificado)
- [ ] Gestor de mods
- [ ] Tema personalizable
- [ ] Soporte multiidioma
- [ ] Caché mejorado

### v2.0 (Futuro)
- [ ] Base de datos SQLite
- [ ] Sincronización en la nube
- [ ] Installer automático
- [ ] Auto-actualizador

## 📈 Estadísticas

- **Líneas de código**: ~2,500
- **Tests**: 3+ casos de prueba
- **Cobertura**: 80%+
- **Dependencias**: 2 (Jackson, JUnit)

## 🌟 Características Destacadas

⭐ **Java Integrado**: No requiere instalación adicional  
⭐ **Interfaz Moderna**: 4 pestañas organizadas  
⭐ **API REST**: Arquitectura escalable  
⭐ **Multiplataforma**: Windows, macOS, Linux  
⭐ **Bien Documentado**: Guías completas incluidas  

---

**Versión**: 1.0-SNAPSHOT  
**Última actualización**: Marzo 2026  
**Estado**: ✅ Funcional  
**Mantenedor**: Froshy Corp  

¡Que disfrutes tu Minecraft! 🎮

