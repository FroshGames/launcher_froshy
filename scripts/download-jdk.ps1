# Script para descargar y preparar Java 17 portable
# Este script descarga OpenJDK 17 y lo organiza para usar con el launcher

param(
    [string]$DownloadDir = ".\jdk",
    [string]$JavaVersion = "17"
)

$ErrorActionPreference = "Stop"

# URL de descarga de OpenJDK 17 (Eclipse Temurin)
$JdkUrl = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.9_9.zip"
$JdkZip = "$DownloadDir\jdk.zip"

Write-Host "Descargando Java 17 portable..." -ForegroundColor Cyan

# Crear directorio si no existe
if (-not (Test-Path $DownloadDir)) {
    New-Item -ItemType Directory -Path $DownloadDir -Force | Out-Null
}

# Descargar JDK
if (-not (Test-Path $JdkZip)) {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $JdkUrl -OutFile $JdkZip -UseBasicParsing
    Write-Host "Java descargado: $JdkZip" -ForegroundColor Green
} else {
    Write-Host "Java ya existe en: $JdkZip" -ForegroundColor Yellow
}

# Extraer JDK
Write-Host "Extrayendo Java..." -ForegroundColor Cyan
Expand-Archive -Path $JdkZip -DestinationPath $DownloadDir -Force

# Encontrar y renombrar el directorio extraído
$ExtractedDir = Get-ChildItem -Path $DownloadDir -Directory | Where-Object { $_.Name -like "jdk-*" } | Select-Object -First 1
if ($ExtractedDir) {
    $FinalDir = Join-Path $DownloadDir "jdk17"
    if (Test-Path $FinalDir) {
        Remove-Item $FinalDir -Recurse -Force
    }
    Rename-Item -Path $ExtractedDir.FullName -NewName "jdk17" -Force
    Write-Host "Java preparado en: $FinalDir" -ForegroundColor Green
}

# Limpiar ZIP
Remove-Item $JdkZip -Force

Write-Host "Preparación completada exitosamente!" -ForegroundColor Green

