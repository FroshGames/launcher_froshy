# 🚀 Guía Rápida - Login Microsoft OAuth Fixed

## ✅ ¿Qué se Arregló?

El error `Invalid_request: The provided value for the input parameter 'redirect_uri' is not valid` ha sido **completamente solucionado**.

El launcher ahora:
- ✅ Abre el navegador automáticamente
- ✅ Captura el callback de Microsoft correctamente
- ✅ Sincroniza la sesión Premium automáticamente
- ✅ Sin configuración requerida

## 🎯 Cómo Usar

### Paso 1: Descargar o Compilar
```bash
# Si ya compilaste
java -jar target/launcher_mialu.jar

# O copia el JAR generado a donde lo quieras
```

### Paso 2: Hacer Clic en "Login con Microsoft"
- Se abre el navegador automáticamente
- Inicia sesión con tu cuenta de Microsoft

### Paso 3: ¡Listo!
- El launcher captura el callback automáticamente
- Tu sesión Premium está sincronizada

## 🔧 Configuración Avanzada (Opcional)

Si necesitas un Client ID personalizado:

**Windows (PowerShell):**
```powershell
$env:MIALU_MS_CLIENT_ID = "04b07795-8ddb-461a-bbee-02f9e1bf7b46"
java -jar launcher_mialu.jar
```

**Linux/Mac:**
```bash
export MIALU_MS_CLIENT_ID="04b07795-8ddb-461a-bbee-02f9e1bf7b46"
java -jar launcher_mialu.jar
```

## 🆘 Problemas Comunes

### Puerto 3000 en uso
```bash
# Opción 1: Cierra lo que está usando el puerto
# Opción 2: Usa otro puerto con Client ID personalizado
set MIALU_API_PORT=8080
```

### El navegador no se abre
- La URL aparecerá en la consola
- Cópiala y pégala manualmente en tu navegador
- El callback se procesará automáticamente

### Timeout en login
- Tienes 5 minutos para completar el login
- Intenta nuevamente si se agota el tiempo

## 📚 Documentación Completa

Para más detalles, revisa:
- `MICROSOFT_LOGIN_CONFIG.md` - Guía de configuración
- `MICROSOFT_LOGIN_FIX.md` - Detalles técnicos
- `CAMBIOS_MICROSOFT_LOGIN.md` - Resumen de cambios

## ✨ Características

- **Seguro:** OAuth 2.0 con PKCE
- **Automático:** Abre navegador y captura callback
- **Flexible:** Soporta Client ID público y personalizado
- **Confiable:** Manejo de errores robusto
- **Rápido:** Minimal overhead

---

**¡Disfruta jugando Minecraft con tu cuenta Premium!** 🎮

