#!/bin/bash
# Script para crear un bundle ejecutable de Froshy Launcher con Java integrado
# Esto crea un archivo único "launcher_froshy" que contiene todo

set -e

echo ""
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  Creando Bundle Ejecutable: launcher_froshy                  ║"
echo "║  (Java integrado, sin dependencias externas)                 ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""

# Configuración
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BUILD_DIR="$SCRIPT_DIR/build-bundle"
BUNDLE_NAME="launcher_froshy"
JAR_FILE="$SCRIPT_DIR/target/launcher-1.0-SNAPSHOT.jar"
JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.9_9.tar.gz"

# Detectar sistema operativo
if [[ "$OSTYPE" == "darwin"* ]]; then
    ARCH="$(uname -m)"
    if [ "$ARCH" == "arm64" ]; then
        JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.9_9.tar.gz"
    else
        JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_mac_hotspot_17.0.9_9.tar.gz"
    fi
elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "win32" ]]; then
    JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.9_9.zip"
fi

echo "1️⃣  Verificando JAR compilado..."
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR no encontrado. Compilando..."
    cd "$SCRIPT_DIR"
    mvn clean package -DskipTests
fi
echo "✅ JAR verificado"

echo ""
echo "2️⃣  Preparando estructura de bundle..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/jdk"
mkdir -p "$BUILD_DIR/lib"

echo "✅ Estructura creada"

echo ""
echo "3️⃣  Descargando Java 17..."
JDK_ARCHIVE="$BUILD_DIR/jdk.tar.gz"
if [ ! -f "$JDK_ARCHIVE" ]; then
    curl -L -o "$JDK_ARCHIVE" "$JDK_URL" 2>/dev/null || wget -O "$JDK_ARCHIVE" "$JDK_URL" 2>/dev/null
    echo "✅ Java descargado"
else
    echo "✅ Java ya existe"
fi

echo ""
echo "4️⃣  Extrayendo Java..."
tar -xzf "$JDK_ARCHIVE" -C "$BUILD_DIR/jdk"
EXTRACTED_DIR=$(find "$BUILD_DIR/jdk" -maxdepth 1 -type d -name "jdk-*" | head -1)
if [ ! -z "$EXTRACTED_DIR" ]; then
    mv "$EXTRACTED_DIR"/* "$BUILD_DIR/jdk/"
    rmdir "$EXTRACTED_DIR"
fi
rm "$JDK_ARCHIVE"
echo "✅ Java extraído"

echo ""
echo "5️⃣  Copiando JAR..."
cp "$JAR_FILE" "$BUILD_DIR/lib/launcher.jar"
echo "✅ JAR copiado"

echo ""
echo "6️⃣  Creando script ejecutable..."

# Crear script wrapper
cat > "$BUILD_DIR/$BUNDLE_NAME" << 'LAUNCHER_SCRIPT'
#!/bin/bash
# Froshy Launcher - Ejecutable autocontendio

# Obtener directorio del script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
JDK_DIR="$SCRIPT_DIR/jdk"
JAR_FILE="$SCRIPT_DIR/lib/launcher.jar"
JAVA_EXE="$JDK_DIR/bin/java"

# Detectar si es macOS y ajustar ruta
if [[ "$OSTYPE" == "darwin"* ]]; then
    JAVA_EXE="$JDK_DIR/Contents/Home/bin/java"
fi

# Hacer ejecutable java si es necesario
chmod +x "$JAVA_EXE" 2>/dev/null || true

# Configurar memoria JVM
JVM_MEMORY="-Xmx2G -Xms512M"

# Ejecutar
exec "$JAVA_EXE" $JVM_MEMORY -jar "$JAR_FILE" "$@"
LAUNCHER_SCRIPT

chmod +x "$BUILD_DIR/$BUNDLE_NAME"
echo "✅ Script ejecutable creado"

echo ""
echo "7️⃣  Empaquetando..."

# Crear tarball comprimido
cd "$BUILD_DIR/.."
tar -czf "$SCRIPT_DIR/$BUNDLE_NAME.tar.gz" -C "$BUILD_DIR/.." "$(basename $BUILD_DIR)"
echo "✅ Bundle comprimido creado"

# Copiar script al directorio raíz también (sin comprimir para ejecución directa)
cp "$BUILD_DIR/$BUNDLE_NAME" "$SCRIPT_DIR/$BUNDLE_NAME"
echo "✅ Ejecutable principal copiado"

echo ""
echo "════════════════════════════════════════════════════════════════"
echo ""
echo "✅ ¡Bundle creado exitosamente!"
echo ""
echo "RESULTADO:"
echo "  📦 Bundle comprimido: $SCRIPT_DIR/$BUNDLE_NAME.tar.gz"
echo "  🎯 Ejecutable único:  $SCRIPT_DIR/$BUNDLE_NAME"
echo ""
echo "CÓMO USAR:"
echo "  Opción 1 (recomendado): Extrae el .tar.gz y ejecuta"
echo "    tar -xzf launcher_froshy.tar.gz"
echo "    ./build-bundle/launcher_froshy"
echo ""
echo "  Opción 2: Ejecuta directamente si tienes el bundle local"
echo "    ./launcher_froshy"
echo ""
echo "════════════════════════════════════════════════════════════════"
echo ""

