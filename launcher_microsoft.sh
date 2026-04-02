#!/bin/bash
# Script para ejecutar el launcher con configuracion de Microsoft OAuth
# Uso: ./launcher_microsoft.sh [CLIENT_ID_OPCIONAL]

# Nuevo metodo oficial: login premium via Device Code de MinecraftAuth.
# Limpiar overrides antiguos evita unauthorized_client por apps no habilitadas.
unset MIALU_MS_CLIENT_ID
unset FROSHY_MS_CLIENT_ID

# Ejecutar el launcher
if [ -f "launcher_froshy.sh" ]; then
    ./launcher_froshy.sh
elif [ -f "launcher.sh" ]; then
    ./launcher.sh
else
    echo "Error: No se encontro launcher.sh"
    exit 1
fi
