# 📦 Cómo Crear el Bundle Ejecutable (Launcher Único)

Tu **Froshy Launcher** ahora puede empaquetarse como un único ejecutable que incluye Java integrado, sin necesidad de descargas adicionales.

## 🎯 Resultado Final

Después de ejecutar este proceso, tendrás:

```
launcher_froshy           (ejecutable único para Linux/Mac)
launcher_froshy.bat       (ejecutable único para Windows)
launcher_froshy.tar.gz    (bundle comprimido para Linux/Mac)
launcher_froshy-win64.zip (bundle comprimido para Windows)
```

**El usuario solo necesita ejecutar estos archivos. ¡Nada más!**

---

## 🔨 Crear Bundle en Windows

### Opción 1: PowerShell (RECOMENDADO)

```powershell
# Navega al directorio del proyecto
cd C:\ruta\a\launcher_froshy

# Ejecuta el script de creación
PowerShell -ExecutionPolicy Bypass -File "create-bundle.ps1"
```

El script automáticamente:
1. ✅ Compila el JAR (si no existe)
2. ✅ Descarga OpenJDK 17
3. ✅ Crea la estructura del bundle
4. ✅ Genera launcher_froshy.bat
5. ✅ Empaqueta en launcher_froshy-win64.zip
6. ✅ Crea acceso directo "Froshy Launcher.lnk"

### Resultado en Windows:
```
project/
├── launcher_froshy.bat                (ejecutable único)
├── launcher_froshy-win64.zip          (bundle comprimido)
├── Froshy Launcher.lnk                (acceso directo)
└── build-bundle/
    ├── launcher_froshy.bat
    ├── jdk/                           (Java integrado)
    └── lib/
        └── launcher.jar
```

---

## 🔨 Crear Bundle en Linux/Mac

### Terminal (RECOMENDADO)

```bash
cd /ruta/a/launcher_froshy

# Dale permisos de ejecución al script
chmod +x create-bundle.sh

# Ejecuta el script
./create-bundle.sh
```

El script automáticamente:
1. ✅ Compila el JAR (si no existe)
2. ✅ Detecta tu OS (Linux o macOS, Intel o Apple Silicon)
3. ✅ Descarga OpenJDK 17 apropiado
4. ✅ Crea la estructura del bundle
5. ✅ Genera launcher_froshy
6. ✅ Empaqueta en launcher_froshy.tar.gz

### Resultado en Linux/Mac:
```
project/
├── launcher_froshy                    (ejecutable único)
├── launcher_froshy.tar.gz             (bundle comprimido)
└── build-bundle/
    ├── launcher_froshy                (script bash)
    ├── jdk/                           (Java integrado)
    └── lib/
        └── launcher.jar
```

---

## 🎮 Usar el Bundle

### Distribución Comprimida

#### Windows:
```powershell
# 1. Descomprime
Expand-Archive launcher_froshy-win64.zip -DestinationPath .

# 2. Ejecuta
.\build-bundle\launcher_froshy.bat
```

#### Linux/Mac:
```bash
# 1. Descomprime
tar -xzf launcher_froshy.tar.gz

# 2. Ejecuta
./build-bundle/launcher_froshy
```

### Ejecución Directa

#### Windows:
```powershell
.\launcher_froshy.bat
```

O simplemente **haz doble clic** en `launcher_froshy.bat`

#### Linux/Mac:
```bash
./launcher_froshy
```

O **haz doble clic** en `launcher_froshy` (en algunos gestores de archivos)

---

## ⚙️ Qué Incluye el Bundle

### Tamaño Total
- **~200-300 MB** (Java + JAR + recursos)

### Contenido
```
build-bundle/
├── launcher_froshy      (o .bat en Windows)
├── jdk/
│   ├── bin/
│   │   └── java         (OpenJDK 17)
│   ├── lib/
│   ├── conf/
│   └── ...              (Java runtime completo)
└── lib/
    └── launcher.jar     (JAR del launcher - 2.5 MB)
```

### Ventajas
✅ **Autocontenido**: Sin dependencias externas  
✅ **Portátil**: Copia y ejecuta en cualquier máquina  
✅ **Java integrado**: No requiere instalación  
✅ **Versión consistente**: Siempre OpenJDK 17  
✅ **Fácil distribución**: Un solo archivo o carpeta  

---

## 📊 Proceso de Creación Detallado

### Windows (PowerShell)

```powershell
1️⃣  Compilar JAR
    mvn clean package -DskipTests

2️⃣  Crear estructura
    mkdir build-bundle\jdk
    mkdir build-bundle\lib

3️⃣  Descargar Java
    Invoke-WebRequest -Uri $JdkUrl -OutFile $JdkZip

4️⃣  Extraer Java
    Expand-Archive -Path $JdkZip -DestinationPath build-bundle\jdk

5️⃣  Copiar JAR
    Copy-Item target\launcher-1.0-SNAPSHOT.jar build-bundle\lib\launcher.jar

6️⃣  Crear script batch
    @echo off
    set JAVA=.\jdk\bin\java.exe
    !JAVA! -Xmx2G -jar .\lib\launcher.jar

7️⃣  Comprimir
    Compress-Archive -Path build-bundle\* -DestinationPath launcher_froshy-win64.zip
```

### Linux/Mac (Bash)

```bash
1️⃣  Compilar JAR
    mvn clean package -DskipTests

2️⃣  Crear estructura
    mkdir -p build-bundle/jdk build-bundle/lib

3️⃣  Descargar Java
    curl -L -o jdk.tar.gz $JDK_URL

4️⃣  Extraer Java
    tar -xzf jdk.tar.gz -C build-bundle/jdk

5️⃣  Copiar JAR
    cp target/launcher-1.0-SNAPSHOT.jar build-bundle/lib/launcher.jar

6️⃣  Crear script shell
    #!/bin/bash
    JAVA=./jdk/bin/java
    $JAVA -Xmx2G -jar ./lib/launcher.jar

7️⃣  Comprimir
    tar -czf launcher_froshy.tar.gz build-bundle/
```

---

## 🚀 Distribución para Usuarios

### Opción 1: ZIP/TAR.GZ (Recomendado)

Distribuye los archivos comprimidos:
- Windows: `launcher_froshy-win64.zip`
- Linux/Mac: `launcher_froshy.tar.gz`

**Instrucciones para el usuario:**
1. Descomprime el archivo
2. Ejecuta `launcher_froshy.bat` (Windows) o `launcher_froshy` (Linux/Mac)

### Opción 2: Ejecutable Único

Distribuye directamente:
- Windows: `launcher_froshy.bat` (requiere que el usuario tenga build-bundle también)
- Linux/Mac: `launcher_froshy` (requiere build-bundle)

### Opción 3: Instalador (Avanzado)

Puedes crear un instalador visual usando:
- **Windows**: NSIS, Inno Setup, o MSI
- **Linux**: DEB, RPM
- **macOS**: DMG, PKG

---

## 🔧 Personalización

### Cambiar Memoria JVM

En los scripts generados, busca:
```bash
# Linux/Mac
JVM_MEMORY="-Xmx2G -Xms512M"

# Windows
set JVM_MEMORY=-Xmx2G -Xms512M
```

Cambia a:
```bash
# Para 4 GB
JVM_MEMORY="-Xmx4G -Xms1G"

# Para 6 GB
JVM_MEMORY="-Xmx6G -Xms1G"
```

### Cambiar Versión de Java

En los scripts `create-bundle.*`, busca `JDK_URL` y cambia la versión:
```powershell
# De OpenJDK 17 a 21
$JdkUrl = "https://github.com/adoptium/temurin21-binaries/releases/..."
```

---

## ❓ Preguntas Frecuentes

**P: ¿Por qué es tan grande (~300 MB)?**
R: Java 17 completo ocupa ~180-200 MB. El resto es el JAR y metadatos.

**P: ¿Puedo distribuir esto comercialmente?**
R: Sí, OpenJDK 17 es open source (GPLv2).

**P: ¿Funciona en todas las máquinas?**
R: Sí, mientras tengan el mismo OS (Windows, Linux, macOS).

**P: ¿Puedo actualizar solo el JAR?**
R: Sí, reemplaza `lib/launcher.jar` sin actualizar Java.

**P: ¿Es seguro ejecutar .bat/.sh desde internet?**
R: Sí, solo compila desde fuente y crea tu propio bundle.

---

## 📝 Checklist de Creación

- [ ] Compilar JAR: `mvn clean package -DskipTests`
- [ ] Ejecutar script: `create-bundle.ps1` (Windows) o `create-bundle.sh` (Linux/Mac)
- [ ] Verificar que se creó `launcher_froshy` o `launcher_froshy.bat`
- [ ] Probar ejecutar el bundle
- [ ] Comprimir para distribución
- [ ] Documentar versión Java incluida

---

## 🎉 Resultado Final

Después de completar este proceso, tienes:

✅ **Ejecutable único** que el usuario puede ejecutar directamente  
✅ **Java integrado** sin instalación adicional  
✅ **Sin dependencias externas**  
✅ **Listo para distribución**  
✅ **Multiplataforma** (Windows, Linux, macOS)  

---

**Versión**: 1.0-SNAPSHOT  
**Última actualización**: Marzo 2026

