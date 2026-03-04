# Script para crear un bundle ejecutable de Froshy Launcher con Java integrado (Windows)
# Esto crea un archivo único "launcher_froshy.exe" que contiene todo

param(
    [string]$BuildDir = ".\build-bundle"
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "╔═══════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  Creando Bundle Ejecutable: launcher_froshy.exe              ║" -ForegroundColor Cyan
Write-Host "║  (Java integrado, sin dependencias externas)                 ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Configuración
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BundleName = "launcher_froshy"
$JarFile = "$ScriptDir\target\launcher-1.0-SNAPSHOT.jar"
$JdkUrl = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.9_9.zip"
$FullBuildDir = Join-Path $ScriptDir $BuildDir

# Paso 1: Verificar JAR
Write-Host "1️⃣  Verificando JAR compilado..." -ForegroundColor Yellow
if (-not (Test-Path $JarFile)) {
    Write-Host "❌ JAR no encontrado. Compilando..." -ForegroundColor Red
    Push-Location $ScriptDir
    mvn clean package -DskipTests
    Pop-Location
}
Write-Host "✅ JAR verificado" -ForegroundColor Green

# Paso 2: Preparar estructura
Write-Host ""
Write-Host "2️⃣  Preparando estructura de bundle..." -ForegroundColor Yellow
if (Test-Path $FullBuildDir) {
    Remove-Item $FullBuildDir -Recurse -Force
}
New-Item -ItemType Directory -Path "$FullBuildDir\jdk" -Force | Out-Null
New-Item -ItemType Directory -Path "$FullBuildDir\lib" -Force | Out-Null
Write-Host "✅ Estructura creada" -ForegroundColor Green

# Paso 3: Descargar Java
Write-Host ""
Write-Host "3️⃣  Descargando Java 17..." -ForegroundColor Yellow
$JdkZip = "$FullBuildDir\jdk.zip"
if (-not (Test-Path $JdkZip)) {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $JdkUrl -OutFile $JdkZip -UseBasicParsing
    Write-Host "✅ Java descargado" -ForegroundColor Green
} else {
    Write-Host "✅ Java ya existe" -ForegroundColor Green
}

# Paso 4: Extraer Java
Write-Host ""
Write-Host "4️⃣  Extrayendo Java..." -ForegroundColor Yellow
Expand-Archive -Path $JdkZip -DestinationPath "$FullBuildDir\jdk" -Force
$ExtractedDir = Get-ChildItem -Path "$FullBuildDir\jdk" -Directory | Where-Object { $_.Name -like "jdk-*" } | Select-Object -First 1
if ($ExtractedDir) {
    Get-ChildItem -Path $ExtractedDir.FullName | Move-Item -Destination "$FullBuildDir\jdk" -Force
    Remove-Item $ExtractedDir.FullName -Force
}
Remove-Item $JdkZip -Force
Write-Host "✅ Java extraído" -ForegroundColor Green

# Paso 5: Copiar JAR
Write-Host ""
Write-Host "5️⃣  Copiando JAR..." -ForegroundColor Yellow
Copy-Item $JarFile "$FullBuildDir\lib\launcher.jar" -Force
Write-Host "✅ JAR copiado" -ForegroundColor Green

# Paso 6: Crear archivo batch
Write-Host ""
Write-Host "6️⃣  Creando archivo ejecutable..." -ForegroundColor Yellow

# Usar @" con escape para evitar interpolación
$BatchContent = '@echo off
REM Froshy Launcher - Ejecutable autocontenido

setlocal enabledelayedexpansion

REM Obtener directorio del script
set SCRIPT_DIR=%~dp0
set JDK_DIR=%SCRIPT_DIR%jdk
set JAR_FILE=%SCRIPT_DIR%lib\launcher.jar
set JAVA_EXE=%JDK_DIR%\bin\java.exe

REM Verificar Java
if not exist "!JAVA_EXE!" (
    echo Error: Java no encontrado en !JAVA_EXE!
    pause
    exit /b 1
)

REM Configurar memoria JVM
set JVM_MEMORY=-Xmx2G -Xms512M

REM Ejecutar
"!JAVA_EXE!" !JVM_MEMORY! -jar "!JAR_FILE!" %*

if errorlevel 1 (
    echo.
    echo Error al ejecutar el launcher
    echo.
    pause
)

endlocal'

$BatchFile = "$FullBuildDir\$BundleName.bat"
Set-Content -Path $BatchFile -Value $BatchContent -Encoding ASCII
Write-Host "✅ Archivo batch creado" -ForegroundColor Green

# Paso 7: Crear ejecutable wrapper (opcional, usa batch por ahora)
Write-Host ""
Write-Host "7️⃣  Creando paquete final..." -ForegroundColor Yellow

# Crear ZIP comprimido
$ZipPath = "$ScriptDir\$BundleName-win64.zip"
if (Test-Path $ZipPath) {
    Remove-Item $ZipPath -Force
}
Compress-Archive -Path "$FullBuildDir\*" -DestinationPath $ZipPath -Force
Write-Host "✅ Bundle comprimido creado" -ForegroundColor Green

# Copiar batch al directorio raíz
Copy-Item $BatchFile "$ScriptDir\$BundleName.bat" -Force
Write-Host "✅ Ejecutable principal copiado" -ForegroundColor Green

# Crear acceso directo (shortcut) opcional
Write-Host ""
Write-Host "📌 Creando acceso directo..." -ForegroundColor Yellow
$WshShell = New-Object -ComObject WScript.Shell
$ShortcutPath = "$ScriptDir\Froshy Launcher.lnk"
$Shortcut = $WshShell.CreateShortcut($ShortcutPath)
$Shortcut.TargetPath = "$FullBuildDir\$BundleName.bat"
$Shortcut.WorkingDirectory = $FullBuildDir
$Shortcut.Description = "Froshy Launcher - Minecraft Launcher"
$Shortcut.IconLocation = "C:\Windows\System32\imageres.dll,178"
$Shortcut.Save()
Write-Host "✅ Acceso directo creado" -ForegroundColor Green

Write-Host ""
Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "✅ ¡Bundle creado exitosamente!" -ForegroundColor Green
Write-Host ""
Write-Host "RESULTADO:" -ForegroundColor Yellow
Write-Host "  📦 Bundle comprimido: $ZipPath"
Write-Host "  🎯 Ejecutable único:  $ScriptDir\$BundleName.bat"
Write-Host "  🔗 Acceso directo:    $ShortcutPath"
Write-Host ""
Write-Host "CÓMO USAR:" -ForegroundColor Yellow
Write-Host "  Opción 1 (recomendado): Descomprime y ejecuta"
Write-Host "    1. Extrae $BundleName-win64.zip"
Write-Host "    2. Haz doble clic en launcher_froshy.bat"
Write-Host ""
Write-Host "  Opción 2: Ejecuta desde línea de comandos"
Write-Host "    launcher_froshy.bat"
Write-Host ""
Write-Host "  Opción 3: Usa el acceso directo"
Write-Host "    Froshy Launcher.lnk"
Write-Host ""
Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""


