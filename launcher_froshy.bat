@echo off
setlocal
set SCRIPT_DIR=%~dp0

if exist "%SCRIPT_DIR%launcher_mialu.bat" (
	call "%SCRIPT_DIR%launcher_mialu.bat" %*
	exit /b %errorlevel%
)

if exist "%SCRIPT_DIR%launcher.bat" (
	call "%SCRIPT_DIR%launcher.bat" %*
	exit /b %errorlevel%
)

echo No se encontro launcher_mialu.bat ni launcher.bat en %SCRIPT_DIR%
exit /b 1
