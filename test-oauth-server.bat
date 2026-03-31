@echo off
REM Script de prueba para verificar el servidor de callback OAuth en puerto 3000

echo.
echo === Prueba del Servidor OAuth Callback ===
echo.
echo 1. Verificando si el puerto 3000 esta disponible...

netstat -ano | findstr :3000 >nul 2>&1
if %errorlevel% equ 0 (
    echo    [AVISO] Puerto 3000 ya esta en uso
) else (
    echo    [OK] Puerto 3000 disponible
)

echo.
echo 2. Compilando el proyecto...
call mvn clean compile -q

if %errorlevel% equ 0 (
    echo    [OK] Compilacion exitosa
) else (
    echo    [ERROR] Error en la compilacion
    exit /b 1
)

echo.
echo 3. Empaquetando...
call mvn package -q -DskipTests

if %errorlevel% equ 0 (
    echo    [OK] Empaquetamiento exitoso
) else (
    echo    [ERROR] Error en el empaquetamiento
    exit /b 1
)

echo.
echo === Prueba Completada ===
echo.
echo Para iniciar el launcher:
echo   java -jar target\launcher_mialu.jar
echo.
echo Para iniciar con Client ID personalizado:
echo   set MIALU_MS_CLIENT_ID=tu-client-id
echo   java -jar target\launcher_mialu.jar
echo.
pause

