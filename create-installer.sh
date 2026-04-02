#!/bin/bash
# Bash script to create an installer using jpackage on Linux
# Requires Java 17+ and fakeroot/dpkg-deb (Ubuntu/Debian) or rpmbuild (Fedora/RedHat)

echo "Realizando limpieza y empaquetado del Launcher..."
mvn clean package

APP_VERSION_RAW=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
APP_VERSION=$(echo "$APP_VERSION_RAW" | sed 's/[^0-9\.]//g' | sed 's/\.$//')
echo "Version sincronizada del proyecto: $APP_VERSION (Original: $APP_VERSION_RAW)"

APP_DEST="build-bundle"
MAIN_JAR="launcher_mialu.jar"
MAIN_CLASS="am.froshy.mialu.launcher.LauncherUiApplication"

echo "Preparando entorno del instalador..."
rm -rf $APP_DEST
mkdir -p $APP_DEST
cp target/$MAIN_JAR $APP_DEST/

if [ ! -f "$APP_DEST/$MAIN_JAR" ]; then
    echo "Error: No se encontró el JAR '$MAIN_JAR' en target/."
    exit 1
fi

echo "JAR encontrado: $MAIN_JAR"
echo "Generando Instalador para Linux con jpackage..."

# jpackage tomará el JRE de Java 17 y creará un instalador nativo (.deb o .rpm)
jpackage \
    --input $APP_DEST \
    --main-jar $MAIN_JAR \
    --main-class $MAIN_CLASS \
    --name "mialulauncher" \
    --app-version $APP_VERSION \
    --icon "src/main/resources/assets/icons/icon.png" \
    --linux-shortcut \
    --linux-menu-group "Games"

if [ $? -eq 0 ]; then
    if [ -f "mialulauncher_${APP_VERSION}_amd64.deb" ]; then mv "mialulauncher_${APP_VERSION}_amd64.deb" "mialuLauncherInstaller-${APP_VERSION}_amd64.deb"; fi
    if [ -f "mialulauncher-${APP_VERSION}-1.x86_64.rpm" ]; then mv "mialulauncher-${APP_VERSION}-1.x86_64.rpm" "mialuLauncherInstaller-${APP_VERSION}-1.x86_64.rpm"; fi
    echo "¡Instalador creado exitosamente!"
else
    echo "Hubo un error al ejecutar jpackage. (asegúrate de tener 'fakeroot' y 'dpkg-deb' para Debian/Ubuntu o 'rpm-build' para Fedora/RedHat instalados)."
fi

