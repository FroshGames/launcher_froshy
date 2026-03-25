@echo off
REM ═══════════════════════════════════════════════════════════
REM Froshy Launcher - Ejecutable Único
REM Java está autocontenido, solo necesitas ejecutar este archivo
REM ═══════════════════════════════════════════════════════════

setlocal enabledelayedexpansion

REM Obtener el directorio del script
set SCRIPT_DIR=%~dp0
set JAR_FILE=%SCRIPT_DIR%target\launcher.jar

REM Intentar encontrar Java (primero local, luego del sistema)
set JAVA_EXE=java.exe

REM Si existe Java local en build-bundle, usarlo
if exist "%SCRIPT_DIR%build-bundle\jdk\bin\java.exe" (
    set JAVA_EXE=%SCRIPT_DIR%build-bundle\jdk\bin\java.exe
)

REM Configurar memoria JVM
set JVM_MEMORY=-Xmx2G -Xms512M

REM Ejecutar el launcher
echo.
echo ╔═══════════════════════════════════════════════════════════╗
echo ║          🎮 Froshy Launcher v1.0 - Iniciando 🎮          ║
echo ╚═══════════════════════════════════════════════════════════╝
echo.

"%JAVA_EXE%" %JVM_MEMORY% -jar "%JAR_FILE%"

if errorlevel 1 (
    echo.
    echo ❌ Error al ejecutar el launcher
    echo.
    echo Asegúrate de que:
    echo   1. Java 17+ está instalado (o usa create-bundle.ps1)
    echo   2. El JAR existe: %JAR_FILE%
    echo.
    pause
)

endlocal


