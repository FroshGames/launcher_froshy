# 🚀 Guía Rápida - Login Microsoft OAuth

## ✅ ¿Qué es Login Microsoft?

El launcher soporta login con cuentas Microsoft para acceso **Premium** a Minecraft.

## 🎯 Cómo Usar (3 pasos)

### 1️⃣ Abre el Launcher
```bash
# Windows
launcher_microsoft.bat

# Linux/Mac
./launcher_microsoft.sh
```

### 2️⃣ Haz Clic en "Login Microsoft"
- Se abrirá tu navegador automáticamente
- Inicia sesión con tu cuenta de Microsoft

### 3️⃣ ¡Listo!
- El launcher sincroniza automáticamente
- Tu nombre aparecerá en "Premium"

---

## 🔧 Opciones Avanzadas

### Usar Cliente ID Personalizado
```bash
# Windows
launcher_microsoft.bat 62e8121e-6311-4c99-a649-305c93a77f92

# Linux/Mac
./launcher_microsoft.sh 62e8121e-6311-4c99-a649-305c93a77f92
```

### Variable de Entorno
```bash
# Windows
set MIALU_MS_CLIENT_ID=04b07795-8ddb-461a-bbee-02f9e1bf7b46

# Linux/Mac
export MIALU_MS_CLIENT_ID="04b07795-8ddb-461a-bbee-02f9e1bf7b46"
```

---

## ❌ Problemas Frecuentes

| Problema | Solución |
|----------|----------|
| `unauthorized_client` | El launcher intenta automáticamente con cliente alternativo. Si sigue fallando, contacta a soporte. |
| No se abre navegador | Busca la URL en la consola del launcher y abre manualmente |
| Error `redirect_uri` | Verifica que tengas `http://localhost:3000/` en Azure Portal |
| Cuentas de trabajo | **NO funciona** con cuentas de trabajo/escuela. Usa cuenta personal (Outlook, Hotmail) |
| Timeout | Completa el login dentro de 5 minutos |

**📖 Para más detalles:** Ver `TROUBLESHOOTING_OAUTH.md`

---

## 📋 Requisitos

- ✅ Cuenta Microsoft personal (Outlook, Hotmail, Live, etc)
- ✅ Licencia de Minecraft Java Edition
- ✅ Conexión a internet
- ✅ Navegador web instalado

**❌ NO funciona con:**
- Cuentas de trabajo/escuela
- Cuentas Xbox Game Pass
- Cuentas sin licencia de Minecraft Java

---

## 🔐 Seguridad

- ✅ Credenciales **nunca se almacenan**
- ✅ Solo se guarda **token encriptado**
- ✅ Login en **servidores oficiales de Microsoft**
- ✅ Usa **PKCE** para seguridad

---

## 📚 Documentación Completa

- **Configuración Detallada**: `CONFIGURACION_CLIENTE_AZURE.md`
- **Troubleshooting Avanzado**: `TROUBLESHOOTING_OAUTH.md`
- **Referencia Técnica**: `REFERENCIA_TECNICA_OAUTH.md`

---

*Versión: 0.6+*
*Última actualización: 2026-04-02*
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

