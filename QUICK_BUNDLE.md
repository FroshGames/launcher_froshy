# 🎮 FROSHY LAUNCHER - Inicio Rápido con Bundle Único

## ⚡ La Forma Más Fácil (3 Comandos)

### Windows - PowerShell

```powershell
cd C:\ruta\a\launcher_froshy
mvn clean package -DskipTests
PowerShell -ExecutionPolicy Bypass -File "create-bundle.ps1"
```

### Linux/Mac - Terminal

```bash
cd /ruta/a/launcher_froshy
mvn clean package -DskipTests
bash create-bundle.sh
```

**¡Eso es todo!** Se creará el ejecutable único con Java integrado.

---

## 🚀 Usar el Launcher

### Windows
```powershell
.\launcher_froshy.bat

# O haz doble clic en:
# - launcher_froshy.bat
# - Froshy Launcher.lnk (acceso directo)
```

### Linux/Mac
```bash
./launcher_froshy

# O haz doble clic en launcher_froshy (si tu gestor de archivos lo permite)
```

---

## ✨ Qué es este Bundle

El bundle `launcher_froshy` es:

✅ **Un único ejecutable** (con Java incluido)  
✅ **Sin dependencias externas** (nada que descargar)  
✅ **Listo para distribuir** (compartir en USB, web, etc.)  
✅ **Como Minecraft** (Java ya está adentro)  
✅ **Multiplataforma** (creas uno por OS)  

---

## 📊 Tamaño del Bundle

- **Windows**: ~250-300 MB (ZIP comprimido)
- **Linux**: ~200-250 MB (TAR.GZ comprimido)
- **macOS**: ~200-250 MB (TAR.GZ comprimido)

Incluye:
- Java 17 (OpenJDK Temurin) - ~180 MB
- Launcher JAR - ~2.5 MB
- Scripts y metadatos

---

## 📚 Documentación Completa

Ver [BUILD_BUNDLE.md](BUILD_BUNDLE.md) para:
- Detalles técnicos de creación
- Cómo personalizar (memoria JVM, versión Java)
- Distribución para usuarios
- Troubleshooting

---

## 📖 Documentación General

- [README.md](README.md) - Descripción completa
- [INSTALLATION.md](INSTALLATION.md) - Instalación alternativa
- [docs/ARCHITECTURE_DETAIL.md](docs/ARCHITECTURE_DETAIL.md) - Arquitectura
- [docs/API_REFERENCE.md](docs/API_REFERENCE.md) - API REST (10+ endpoints)

---

## ✅ Resultado

Después de seguir los pasos, tienes:

```
project/
├── launcher_froshy.bat                  (ejecutable - Windows)
├── launcher_froshy.bat.lnk              (acceso directo - Windows)
├── launcher_froshy                      (ejecutable - Linux/Mac)
├── launcher_froshy.tar.gz               (comprimido - Linux/Mac)
├── launcher_froshy-win64.zip            (comprimido - Windows)
└── build-bundle/                        (estructura del bundle)
    ├── launcher_froshy.bat (o sin ext)
    ├── jdk/                            (Java 17 integrado)
    └── lib/
        └── launcher.jar
```

**El usuario solo necesita ejecutar `launcher_froshy`** (sin descargar nada adicional)

---

**¡Listo para distribución!** 🚀

