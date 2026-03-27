# Quick Start & Troubleshooting - Launcher_Mialu

## ⚡ Instalación Rápida

### Windows

```powershell
# 1. Descargar e instalar JDK 17
# https://www.oracle.com/java/technologies/downloads/#java17

# 2. Descargar e instalar Maven
# https://maven.apache.org/download.cgi

# 3. Clonar repositorio
git clone https://github.com/tu-usuario/launcher_froshy.git
cd launcher_froshy

# 4. Compilar
mvn clean compile

# 5. Tests
mvn test

# 6. Empaquetar
mvn package

# 7. Ejecutar
java -jar target/launcher-1.0-SNAPSHOT.jar
```

✅ **El JAR ya incluye todas las dependencias (Jackson) automáticamente.**

### macOS

```bash
# 1. Instalar Homebrew (si no está)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 2. Instalar Java y Maven
brew install openjdk@17
brew install maven

# 3. Clonar
git clone https://github.com/tu-usuario/launcher_froshy.git
cd launcher_froshy

# 4-7. Mismo que en Windows
mvn clean compile
mvn test
mvn package
java -jar target/launcher-1.0-SNAPSHOT.jar
```

### Linux

```bash
# 1. Ubuntu/Debian
sudo apt-get update
sudo apt-get install openjdk-17-jdk maven

# 2. CentOS/RHEL
sudo yum install java-17-openjdk java-17-openjdk-devel maven

# 3-7. Igual que macOS
git clone https://github.com/tu-usuario/launcher_froshy.git
cd launcher_froshy
mvn clean compile
mvn test
mvn package
java -jar target/launcher-1.0-SNAPSHOT.jar
```

---

## 🔍 Verificar Instalación

### Verificar Java

```bash
java -version
# Debería mostrar: OpenJDK 17.x.x
```

### Verificar Maven

```bash
mvn -version
# Debería mostrar: Apache Maven 3.9.x
```

### Verificar Git

```bash
git --version
# Debería mostrar: git version 2.x.x
```

---

## 🚀 Ejecutar el Launcher

### Modo Visual (Recomendado)

```bash
java -jar target/launcher-1.0-SNAPSHOT.jar
```

Se abre una ventana con la interfaz gráfica.

✅ **El JAR incluye todas las dependencias automáticamente.**

### Modo API-only (Headless)

```bash
java -cp target/classes am.froshy.launcher.LauncherApplication
```

Solo inicia el servidor HTTP:
```
Launcher_Mialu iniciado. API interna en puerto 7878
Endpoints: /internal/v1/health, /internal/v1/profiles, /internal/v1/launch, /internal/v1/downloads
```

### Cambiar Puerto de API

```bash
# Windows
set FROSHY_API_PORT=9999
java -jar target/launcher-1.0-SNAPSHOT.jar

# macOS/Linux
export FROSHY_API_PORT=9999
java -jar target/launcher-1.0-SNAPSHOT.jar
```

---

## ❌ Troubleshooting

### Error: "Command 'java' not found"

**Causa**: Java no está instalado o no está en PATH.

**Solución**:
1. Instalar JDK 17
2. Agregar a PATH:
   - **Windows**: `C:\Program Files\Java\jdk-17\bin`
   - **macOS**: Usualmente automático con Homebrew
   - **Linux**: Usualmente automático con apt/yum

Verificar: `java -version`

---

### Error: "Command 'mvn' not found"

**Causa**: Maven no está instalado o no está en PATH.

**Solución**:
1. Instalar Maven 3.9.x
2. Agregar `MAVEN_HOME/bin` a PATH

Verificar: `mvn -version`

---

### Error: "COMPILATION ERROR: release version 23 not supported"

**Causa**: Intentar compilar con Java 23 en JDK 17.

**Solución**: El proyecto está configurado para Java 17. Editar `pom.xml`:

```xml
<maven.compiler.source>17</maven.compiler.source>
<maven.compiler.target>17</maven.compiler.target>
```

---

### Error: "Address already in use: 7878"

**Causa**: Puerto 7878 ya está en uso por otro proceso.

**Solución 1**: Cambiar puerto

```bash
export FROSHY_API_PORT=8888
java -jar target/launcher-1.0-SNAPSHOT-with-dependencies.jar
```

**Solución 2**: Matar proceso existente

```bash
# Windows
netstat -ano | findstr :7878
taskkill /PID <PID> /F

# macOS/Linux
lsof -i :7878
kill -9 <PID>
```

---

### Error: "Tests fail with 'Cannot find symbol'"

**Causa**: IDE index desincronizado o classpath corrupto.

**Solución**:

```bash
mvn clean
# Esperar a que IDE reindexa
mvn compile
mvn test
```

Si en IDE IntelliJ:
- File → Invalidate Caches → Invalidate and Restart

---

### Error: "UI se abre pero sin eventos (botones no responden)"

**Causa**: Posiblemente Swing requiere server X11 en Linux.

**Solución**:

```bash
# Si tienes headless server, usar VirtualDisplay
# En WSL2 (Windows Subsystem for Linux):
export DISPLAY=:0
java -jar target/launcher-1.0-SNAPSHOT.jar

# O usar modo API-only
java -cp target/classes am.froshy.launcher.LauncherApplication
```

---

### Error: "NullPointerException en LauncherFrame"

**Causa**: Problemas de timing en EDT (Event Dispatch Thread).

**Solución**: Revisar que todas las actualizaciones de UI estén en `SwingUtilities.invokeLater()`.

**Debuggear**:
```bash
mvn clean compile
# Abrir en IDE: Run → Debug 'LauncherUiApplication'
# Establecer breakpoint en LauncherFrame
# Interactuar con UI
```

---

### Error: "jackson-datatype-jsr310 not found"

**Causa**: Dependencia no descargada en repositorio local.

**Solución**:

```bash
mvn clean
mvn dependency:resolve
mvn compile
```

---

### Error: "Permission denied" en Linux/macOS

**Causa**: Permisos insuficientes al escribir `~/.froshy-launcher/`.

**Solución**:

```bash
mkdir -p ~/.froshy-launcher
chmod 755 ~/.froshy-launcher
java -jar target/launcher-1.0-SNAPSHOT-with-dependencies.jar
```

---

### Error: "profiles.json está corrupto"

**Causa**: Archivo JSON inválido en `~/.froshy-launcher/profiles.json`.

**Solución**:

```bash
# Windows
del %USERPROFILE%\.froshy-launcher\profiles.json

# macOS/Linux
rm ~/.froshy-launcher/profiles.json

# Reiniciar launcher
java -jar target/launcher-1.0-SNAPSHOT-with-dependencies.jar
```

---

## 📊 Verificar Instalación con Tests

```bash
mvn test
```

Salida esperada:
```
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 🔗 Probar API Manualmente

### Con curl

```bash
# Health
curl http://localhost:7878/internal/v1/health

# Listar perfiles
curl http://localhost:7878/internal/v1/profiles

# Crear perfil
curl -X POST http://localhost:7878/internal/v1/profiles \
  -H "Content-Type: application/json" \
  -d '{"id":"test","displayName":"Test","javaPath":"java","gameVersion":"1.20.1","jvmArgs":["-Xmx2G"],"gameArgs":[]}'
```

### Con Postman

1. Descargar: https://www.postman.com/downloads/
2. Crear colección "Launcher_Mialu"
3. Importar requests de [docs/API_REFERENCE.md](API_REFERENCE.md)
4. Ejecutar requests

---

## 📚 Documentación Completa

Para información detallada, ver:

- **[docs/README.md](../docs/README.md)** - Guía de usuario completa
- **[docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md)** - Arquitectura técnica
- **[docs/API_REFERENCE.md](../docs/API_REFERENCE.md)** - Todos los endpoints
- **[docs/DEVELOPER_GUIDE.md](../docs/DEVELOPER_GUIDE.md)** - Guía de desarrollo

---

## 💬 Preguntas Frecuentes

### ¿Necesito descargar Minecraft?

No. Este launcher es de **demostración**. El lanzamiento está simulado.

En versiones futuras se integrarán las APIs reales de Mojang.

### ¿Funciona en mi Mac M1/M2?

Sí, siempre que tengas JDK 17 para ARM64 instalado.

```bash
# Homebrew instala automáticamente la arquitectura correcta
brew install openjdk@17
```

### ¿Puedo usar un IDE diferente?

Sí. El proyecto es agnóstico de IDE:
- IntelliJ IDEA ✅
- Eclipse ✅
- VS Code ✅
- Neovim ✅

Solo necesitas `javac` y `mvn` en terminal.

### ¿Cómo contribuyo?

Ver [docs/DEVELOPER_GUIDE.md](../docs/DEVELOPER_GUIDE.md) sección "Release Process".

### ¿Es seguro ejecutar código de internet?

Este repositorio es de demostración educativa. Revisar el código antes de ejecutar en producción.

---

## 🆘 ¿Sigue sin funcionar?

### Pasos de Debugging

1. **Verificar Java y Maven**:
   ```bash
   java -version
   mvn -version
   ```

2. **Limpiar y recompilar**:
   ```bash
   mvn clean compile
   ```

3. **Ejecutar tests verbose**:
   ```bash
   mvn test -X
   ```

4. **Ver logs del servidor**:
   ```bash
   java -jar target/launcher-1.0-SNAPSHOT.jar 2>&1 | tee launcher.log
   ```

5. **Reportar issue**: Incluir:
   - SO y versión
   - `java -version`
   - `mvn -version`
   - Full stack trace / logs
   - Pasos para reproducir

---

**Última actualización**: 2026-03-04

