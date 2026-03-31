#!/bin/bash
# Script de prueba para verificar el servidor de callback OAuth en puerto 3000

echo "=== Prueba del Servidor OAuth Callback ==="
echo ""
echo "1. Verificando si el puerto 3000 está disponible..."

if nc -z localhost 3000 2>/dev/null; then
    echo "   ⚠️  Puerto 3000 ya está en uso"
else
    echo "   ✓ Puerto 3000 disponible"
fi

echo ""
echo "2. Compilando el proyecto..."
mvn clean compile -q

if [ $? -eq 0 ]; then
    echo "   ✓ Compilación exitosa"
else
    echo "   ✗ Error en la compilación"
    exit 1
fi

echo ""
echo "3. Empaquetando..."
mvn package -q -DskipTests

if [ $? -eq 0 ]; then
    echo "   ✓ Empaquetamiento exitoso"
else
    echo "   ✗ Error en el empaquetamiento"
    exit 1
fi

echo ""
echo "=== Prueba Completada ==="
echo ""
echo "Para iniciar el launcher:"
echo "  java -jar target/launcher_mialu.jar"
echo ""
echo "Para iniciar con Client ID personalizado:"
echo "  export MIALU_MS_CLIENT_ID='tu-client-id'"
echo "  java -jar target/launcher_mialu.jar"

