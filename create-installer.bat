@echo off
echo Obteniendo la version del proyecto desde pom.xml...
for /f "delims=" %%i in ('mvn help:evaluate -Dexpression^=project.version -q -DforceStdout') do set APP_VERSION_RAW=%%i

:: Remover caracteres no numéricos (asumiendo que las versiones estándar no tendrán letras como X.Y.Z)
:: Es más complejo en batch, extraeremos lo básico (hasta el guion de -SNAPSHOT por ejemplo)
for /f "tokens=1 delims=-" %%a in ("%APP_VERSION_RAW%") do set APP_VERSION=%%a

echo Version sincronizada: %APP_VERSION% (Original: %APP_VERSION_RAW%)

echo Realizando limpieza y empaquetado del Launcher...
call mvn clean package

set APP_DEST=build-bundle
set MAIN_JAR=launcher_mialu.jar
set MAIN_CLASS=am.froshy.mialu.launcher.LauncherUiApplication

echo Preparando entorno del instalador...
if exist "%APP_DEST%" rd /s /q "%APP_DEST%"
mkdir "%APP_DEST%"

for %%f in (target\launcher_mialu.jar) do (
    copy /Y "%%f" "%APP_DEST%\%MAIN_JAR%" > nul
    goto :jar_copied
)

:jar_copied

if not exist "%APP_DEST%\%MAIN_JAR%" (
    echo Error: No se encontro el JAR '%MAIN_JAR%' en %APP_DEST%.
    exit /b 1
)

echo JAR encontrado: %MAIN_JAR%
echo Generando Instalador EXE con jpackage...

jpackage --type exe ^
    --input %APP_DEST% ^
    --main-jar %MAIN_JAR% ^
    --main-class %MAIN_CLASS% ^
    --name "MialuLauncher" ^
    --app-version %APP_VERSION% ^
    --icon "src\main\resources\assets\icons\icon.ico" ^
    --win-dir-chooser ^
    --win-console ^
    --win-menu ^
    --win-shortcut ^
    --win-upgrade-uuid 1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d

if %ERRORLEVEL% EQU 0 (
    if exist "mialulauncher-%APP_VERSION%.exe" (
        ren "mialulauncher-%APP_VERSION%.exe" "mialuLauncherInstaller-%APP_VERSION%.exe"
    ) else if exist "mialulauncher.exe" (
        ren "mialulauncher.exe" "mialuLauncherInstaller-%APP_VERSION%.exe"
    )
    echo Instalador creado exitosamente!
) else (
    echo Hubo un error al ejecutar jpackage. Asegurate de tener WiX Toolset v3 instalado en Windows.
)
