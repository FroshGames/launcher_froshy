@echo off
REM Script para ejecutar el launcher con configuracion de Microsoft OAuth
REM Uso: launcher_microsoft.bat [CLIENT_ID_OPCIONAL]

setlocal enabledelayedexpansion

REM Nuevo metodo oficial: login premium via Device Code de MinecraftAuth.
REM Limpiar overrides viejos evita errores unauthorized_client por Client IDs invalidos.
set "MIALU_MS_CLIENT_ID="
set "FROSHY_MS_CLIENT_ID="

REM Ejecutar el launcher
if exist "launcher_froshy.bat" (
    call launcher_froshy.bat
) else if exist "launcher.bat" (
    call launcher.bat
) else (
    echo Error: No se encontro launcher.bat
    pause
    exit /b 1
)

pause
