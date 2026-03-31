@echo off
setlocal
set SCRIPT_DIR=%~dp0

if exist "%SCRIPT_DIR%launcher.bat" (
    call "%SCRIPT_DIR%launcher.bat" %*
    exit /b %errorlevel%
)

echo No se encontro launcher.bat en %SCRIPT_DIR%
exit /b 1

