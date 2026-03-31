#!/bin/bash
# ═══════════════════════════════════════════════════════════
# Launcher_Mialu - Ejecutable Único
# Java está autocontenido, solo necesitas ejecutar este archivo
# ═══════════════════════════════════════════════════════════

set -e

# Obtener el directorio del script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
JAR_FILE="$SCRIPT_DIR/target/launcher_mialu.jar"

# Intentar encontrar Java (primero local, luego del sistema)
JAVA_EXE="java"

# Si existe Java local en build-bundle, usarlo
if [ -f "$SCRIPT_DIR/build-bundle/jdk/bin/java" ]; then
    JAVA_EXE="$SCRIPT_DIR/build-bundle/jdk/bin/java"
elif [ -f "$SCRIPT_DIR/build-bundle/jdk/Contents/Home/bin/java" ]; then
    # Para macOS
    JAVA_EXE="$SCRIPT_DIR/build-bundle/jdk/Contents/Home/bin/java"
fi

# Hacer ejecutable si es necesario
chmod +x "$JAVA_EXE" 2>/dev/null || true

# Configurar memoria JVM
JVM_MEMORY="-Xmx2G -Xms512M"

# Ejecutar el launcher
echo ""
echo "╔═══════════════════════════════════════════════════════════╗"
echo "║          🎮 Launcher_Mialu v1.0 - Iniciando 🎮          ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo ""

"$JAVA_EXE" $JVM_MEMORY -jar "$JAR_FILE"

# Mostrar error si falla
if [ $? -ne 0 ]; then
    echo ""
    echo "❌ Error al ejecutar el launcher"
    echo ""
    echo "Asegúrate de que:"
    echo "  1. Java 17+ está instalado (o usa create-bundle.sh)"
    echo "  2. El JAR existe: $JAR_FILE"
    echo ""
fi



