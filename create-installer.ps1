# PowerShell script to create an installer using jpackage
# Requires Java 14+ (or Java 17 like the project uses) and WiX Toolset on Windows for EXE installers

Write-Host "Realizando limpieza y empaquetado del Launcher..."
mvn clean package

$AppVersion = "0.7.0"   # jpackage requiere un formato especifico (major.minor.patch)
$AppDest = "target/"

# Buscamos el jar generado para asegurarnos de que el nombre sea correcto
$MainJar = Get-ChildItem -Path "$AppDest/launcher_mialu-*-shaded.jar" | Select-Object -ExpandProperty Name -First 1

if (-not $MainJar) {
    Write-Host "Error: No se encontró el JAR 'launcher_mialu-*-shaded.jar' en $AppDest." -ForegroundColor Red
    exit 1
}

Write-Host "JAR encontrado: $MainJar"

$MainClass = "am.froshy.mialu.launcher.LauncherUiApplication"

Write-Host "Generando Instalador EXE con jpackage..."

# jpackage tomara el JRE de Java 17 y el uber-jar para crear un instalador .exe
jpackage --type exe `
    --input $AppDest `
    --main-jar $MainJar `
    --main-class $MainClass `
    --name "FroshyLauncher" `
    --app-version $AppVersion `
    --win-dir-chooser `
    --win-menu `
    --win-shortcut

if ($?) {
    Write-Host "¡Instalador creado exitosamente!" -ForegroundColor Green
} else {
    Write-Host "Hubo un error al ejecutar jpackage. (asegurate de tener WiX Toolset v3 instalado en Windows)." -ForegroundColor Red
}
