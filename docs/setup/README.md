# ⚙️ Instalación y Configuración

**Contenido:** Setup, instalación y variables de entorno

---

## 📋 Documentos

- **INSTALACION.md** - Guía de instalación completa
- **ENVIRONMENT_VARIABLES.md** - Variables de entorno
- **INSTALLATION.md** - Instalación (alternativa)

---

## 🎯 Empezar

### Instalación Rápida
1. Descargar: `launcher_mialu.jar`
2. Ejecutar: `java -jar launcher_mialu.jar`
3. ¡Listo!

### Instalación Avanzada
1. Leer `INSTALACION.md` completo
2. Configurar variables en `ENVIRONMENT_VARIABLES.md`
3. Personalizar según necesidades

---

## 🔧 Variables Importantes

### MIALU_MS_CLIENT_ID
Client ID para autenticación Microsoft
- Por defecto: Cliente público
- Personalizado: Tu aplicación Azure

### MIALU_API_PORT
Puerto del servidor API
- Por defecto: 7878
- Configurable: Según necesidades

### MIALU_UPDATE_METADATA_URL
URL de actualizaciones
- Por defecto: Vacío (sin actualizaciones)

---

## 📁 Estructura de Carpetas

```
~/.mialu-launcher/
├── profiles.json
├── microsoft-session.json (encriptado)
├── game/
│   ├── instances/
│   ├── libraries/
│   └── assets/
└── logs/
    └── security-audit.log
```

---

**Última Actualización:** 31 de Marzo de 2026

