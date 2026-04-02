@echo off
REM Script para ejecutar el launcher con configuración de Microsoft OAuth
REM Uso: launcher_microsoft.bat [CLIENT_ID_OPCIONAL]

setlocal enabledelayedexpansion

if "%1"=="" (
    REM Usar cliente por defecto
    echo Ejecutando launcher con cliente ID por defecto...
    set "MIALU_MS_CLIENT_ID=04b07795-8ddb-461a-bbee-02f9e1bf7b46"
) else (
    REM Usar cliente personalizado
    echo Ejecutando launcher con cliente ID personalizado: %1
    set "MIALU_MS_CLIENT_ID=%1"
)

REM Exportar variable de entorno
set MIALU_MS_CLIENT_ID=!MIALU_MS_CLIENT_ID!

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

