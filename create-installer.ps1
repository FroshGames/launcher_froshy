# PowerShell script to create an installer using jpackage
# Requires Java 14+ (or Java 17 like the project uses) and WiX Toolset on Windows for EXE installers

Write-Host "Realizando limpieza y empaquetado del Launcher..."
mvn clean package

$pom = [xml](Get-Content "pom.xml")
$AppVersion = $pom.project.version.Trim()
Write-Host "Version sincronizada del proyecto: $AppVersion"

$AppDest = "build-bundle"
$MainJar = "launcher_mialu.jar"
$MainClass = "am.froshy.mialu.launcher.LauncherUiApplication"

Write-Host "Preparando entorno de instalador..."
if (Test-Path $AppDest) { Remove-Item -Recurse -Force $AppDest }
New-Item -ItemType Directory -Force -Path $AppDest | Out-Null

$shadedJar = Get-ChildItem -Path "target" -Filter "launcher_mialu.jar" | Select-Object -First 1
if ($shadedJar) {
    Copy-Item $shadedJar.FullName -Destination "$AppDest/$MainJar"
} else {
    Write-Host "Error: No se encontró el JAR 'launcher_mialu-*-shaded.jar' en target/." -ForegroundColor Red
    exit 1
}

Write-Host "JAR encontrado: $MainJar"

Write-Host "Generando Instalador EXE con jpackage..."

# jpackage tomara el JRE de Java 17 y el uber-jar para crear un instalador .exe
jpackage --type exe `
    --input $AppDest `
    --main-jar $MainJar `
    --main-class $MainClass `
    --name "MialuLauncher" `
    --app-version $AppVersion `
    --icon "src\main\resources\assets\icons\icon.ico" `
    --win-dir-chooser `
    --win-menu `
    --win-shortcut

if ($?) {
    $installerName = "mialulauncher-$AppVersion.exe"
    if (Test-Path $installerName) {
        Rename-Item -Path $installerName -NewName "mialuLauncherInstaller-$AppVersion.exe" -Force
    } else {
        # Check if jpackage ignored version in name
        if (Test-Path "mialulauncher.exe") {
            Rename-Item -Path "mialulauncher.exe" -NewName "mialuLauncherInstaller-$AppVersion.exe" -Force
        }
    }
    Write-Host "Instalador creado exitosamente!" -ForegroundColor Green
} else {
    Write-Host "Hubo un error al ejecutar jpackage. (asegurate de tener WiX Toolset v3 instalado en Windows)." -ForegroundColor Red
}
