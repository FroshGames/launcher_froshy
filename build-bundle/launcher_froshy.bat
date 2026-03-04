@echo off
setlocal enabledelayedexpansion
set SCRIPT_DIR=%~dp0
set JAVA_EXE=%SCRIPT_DIR%jdk\bin\java.exe
set JAR_FILE=%SCRIPT_DIR%lib\launcher.jar
if not exist "" set JAVA_EXE=java.exe
"" -Xmx2G -Xms512M -jar "E:\.froshycorp\proyectoslocos\launcher_froshy\target\launcher-1.0-SNAPSHOT.jar" %*
endlocal
