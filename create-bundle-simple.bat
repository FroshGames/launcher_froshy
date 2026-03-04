@echo off
REM Script para crear bundle de Froshy Launcher (Windows)
REM Este script crea la estructura sin descargar Java (asumir que ya está descargado)

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set BUILD_DIR=%SCRIPT_DIR%build-bundle
set JAR_FILE=%SCRIPT_DIR%target\launcher-1.0-SNAPSHOT.jar
set JDK_DIR=%BUILD_DIR%\jdk

echo.
echo ╔═══════════════════════════════════════════════════════════╗
echo ║  Creando Bundle de Froshy Launcher                       ║
echo ║  (Asume que Java ya está en %JDK_DIR%)        ║
echo ╚═══════════════════════════════════════════════════════════╝
echo.

REM Verificar JAR
if not exist "%JAR_FILE%" (
    echo ❌ Error: JAR no encontrado en %JAR_FILE%
    echo.
    echo Compila primero:
    echo   mvn clean package -DskipTests
    pause
    exit /b 1
)

REM Crear estructura
echo 1️⃣  Preparando estructura...
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
mkdir "%BUILD_DIR%\jdk"
mkdir "%BUILD_DIR%\lib"
echo ✅ Estructura creada

REM Copiar JAR
echo.
echo 2️⃣  Copiando JAR...
copy "%JAR_FILE%" "%BUILD_DIR%\lib\launcher.jar" >nul
echo ✅ JAR copiado

REM Crear launcher_froshy.bat
echo.
echo 3️⃣  Creando script ejecutable...
(
    echo @echo off
    echo setlocal enabledelayedexpansion
    echo set SCRIPT_DIR=%%~dp0
    echo set JAVA_EXE=%%SCRIPT_DIR%%jdk\bin\java.exe
    echo set JAR_FILE=%%SCRIPT_DIR%%lib\launcher.jar
    echo if not exist "!JAVA_EXE!" set JAVA_EXE=java.exe
    echo "!JAVA_EXE!" -Xmx2G -Xms512M -jar "!JAR_FILE!" %%*
    echo endlocal
) > "%BUILD_DIR%\launcher_froshy.bat"

copy "%BUILD_DIR%\launcher_froshy.bat" "%SCRIPT_DIR%launcher_froshy.bat" >nul
echo ✅ Ejecutable creado

echo.
echo ════════════════════════════════════════════════════════════
echo.
echo ✅ ¡Bundle estructura creada!
echo.
echo ARCHIVOS GENERADOS:
echo   📁 %BUILD_DIR%\
echo      ├── jdk\                    (Coloca Java aquí)
echo      ├── lib\
echo      │   └── launcher.jar
echo      └── launcher_froshy.bat
echo.
echo   🎯 %SCRIPT_DIR%launcher_froshy.bat
echo.
echo PRÓXIMOS PASOS:
echo   1. Descarga OpenJDK 17 desde:
echo      https://github.com/adoptium/temurin17-binaries/releases/
echo   2. Extrae en: %JDK_DIR%
echo   3. Ejecuta: launcher_froshy.bat
echo.
echo ════════════════════════════════════════════════════════════
echo.

endlocal
pause

