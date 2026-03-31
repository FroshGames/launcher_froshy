#!/bin/bash
# Script para descargar y preparar Java 17 portable
# Este script descarga OpenJDK 17 y lo organiza para usar con el launcher

set -e

DOWNLOAD_DIR="${1:-.}/jdk"
JAVA_VERSION="17"

echo ""
echo "Descargando Java 17 portable..."
echo ""

# Crear directorio si no existe
mkdir -p "$DOWNLOAD_DIR"

# Detectar el SO y la arquitectura
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    ARCH="$(uname -m)"
    if [ "$ARCH" == "arm64" ]; then
        # Apple Silicon
        JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.9_9.tar.gz"
    else
        # Intel Mac
        JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_mac_hotspot_17.0.9_9.tar.gz"
    fi
    ARCHIVE_TYPE="tar.gz"
elif [[ "$OSTYPE" == "linux"* ]]; then
    # Linux
    JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.9_9.tar.gz"
    ARCHIVE_TYPE="tar.gz"
else
    echo "Sistema operativo no soportado: $OSTYPE"
    exit 1
fi

JDK_ARCHIVE="$DOWNLOAD_DIR/jdk.$ARCHIVE_TYPE"

# Descargar JDK
if [ ! -f "$JDK_ARCHIVE" ]; then
    echo "Descargando desde: $JDK_URL"
    echo ""

    if command -v curl &> /dev/null; then
        curl -L -o "$JDK_ARCHIVE" "$JDK_URL"
    elif command -v wget &> /dev/null; then
        wget -O "$JDK_ARCHIVE" "$JDK_URL"
    else
        echo "Error: curl o wget no encontrados"
        exit 1
    fi

    echo ""
    echo "Java descargado: $JDK_ARCHIVE"
else
    echo "Java ya existe en: $JDK_ARCHIVE"
fi

# Extraer JDK
echo "Extrayendo Java..."

if [[ "$ARCHIVE_TYPE" == "tar.gz" ]]; then
    tar -xzf "$JDK_ARCHIVE" -C "$DOWNLOAD_DIR"
elif [[ "$ARCHIVE_TYPE" == "zip" ]]; then
    unzip -q "$JDK_ARCHIVE" -d "$DOWNLOAD_DIR"
fi

# Encontrar y renombrar el directorio extraído
EXTRACTED_DIR=$(find "$DOWNLOAD_DIR" -maxdepth 1 -type d -name "jdk-*" | head -1)

if [ ! -z "$EXTRACTED_DIR" ]; then
    FINAL_DIR="$DOWNLOAD_DIR/jdk17"
    if [ -d "$FINAL_DIR" ]; then
        rm -rf "$FINAL_DIR"
    fi
    mv "$EXTRACTED_DIR" "$FINAL_DIR"
    echo "Java preparado en: $FINAL_DIR"
fi

# Limpiar archivo descargado
rm -f "$JDK_ARCHIVE"

echo ""
echo "Preparación completada exitosamente!"
echo ""

