#!/bin/bash
# Guía RÁPIDA de inicio para Launcher_Mialu

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║           🎮 Launcher_Mialu - Inicio Rápido 🎮           ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Detectar SO
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "win32" ]]; then
    echo "Detected: Windows"
    echo ""
    echo "1️⃣  Descargar Java (solo la primera vez):"
    echo "   PowerShell -ExecutionPolicy Bypass -File \"scripts\download-jdk.ps1\""
    echo ""
    echo "2️⃣  Ejecutar Launcher:"
    echo "   launcher.bat"
    echo ""
elif [[ "$OSTYPE" == "darwin"* ]]; then
    echo "Detected: macOS"
    echo ""
    echo "1️⃣  Descargar Java (solo la primera vez):"
    echo "   bash scripts/download-jdk.sh"
    echo ""
    echo "2️⃣  Ejecutar Launcher:"
    echo "   bash launcher.sh"
    echo ""
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    echo "Detected: Linux"
    echo ""
    echo "1️⃣  Descargar Java (solo la primera vez):"
    echo "   bash scripts/download-jdk.sh"
    echo ""
    echo "2️⃣  Ejecutar Launcher:"
    echo "   bash launcher.sh"
    echo ""
else
    echo "⚠️  Sistema operativo no detectado: $OSTYPE"
fi

echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "❓ ¿Problemas?"
echo "   Ver: INSTALLATION.md para solución de problemas"
echo ""
echo "📚 Documentación:"
echo "   README.md             - Características completas"
echo "   INSTALLATION.md       - Guía de instalación detallada"
echo "   IMPLEMENTATION_SUMMARY.md - Resumen de implementación"
echo ""
echo "🌐 API (si necesitas integración):"
echo "   http://localhost:8080/internal/v1/"
echo "   Ver: docs/API_REFERENCE.md"
echo ""
echo "═══════════════════════════════════════════════════════════════"
echo ""

