#!/bin/bash
# Script para ejecutar el launcher con configuración de Microsoft OAuth
# Uso: ./launcher_microsoft.sh [CLIENT_ID_OPCIONAL]

if [ -z "$1" ]; then
    # Usar cliente por defecto
    echo "Ejecutando launcher con cliente ID por defecto..."
    export MIALU_MS_CLIENT_ID="04b07795-8ddb-461a-bbee-02f9e1bf7b46"
else
    # Usar cliente personalizado
    echo "Ejecutando launcher con cliente ID personalizado: $1"
    export MIALU_MS_CLIENT_ID="$1"
fi

# Ejecutar el launcher
if [ -f "launcher_froshy.sh" ]; then
    ./launcher_froshy.sh
elif [ -f "launcher.sh" ]; then
    ./launcher.sh
else
    echo "Error: No se encontro launcher.sh"
    exit 1
fi

