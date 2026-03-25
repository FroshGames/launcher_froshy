@echo off
setlocal enabledelayedexpansion
set SCRIPT_DIR=%~dp0
set JAVA_EXE=%SCRIPT_DIR%jdk\bin\java.exe
set JAR_FILE=%SCRIPT_DIR%target\launcher.jar
if not exist "%JAVA_EXE%" set JAVA_EXE=java.exe
"%JAVA_EXE%" -Xmx2G -Xms512M -jar "%JAR_FILE%" %*
endlocal
