# 🚀 Arreglo del Error "unauthorized_client" en Login Microsoft

## Estado: ✅ RESUELTO

El error `unauthorized_client: The client does not exist or is not enabled for consumers` ha sido **completamente arreglado** en la versión 0.6+.

---

## 📋 Resumen del Problema

**Error anterior:**
```
unauthorized_client: The client does not exist or is not enabled for consumers.
If you are the application developer, configure a new application through the 
App Registrations in the Azure Portal.
```

**Causa:** El Cliente ID por defecto no estaba habilitado para consumidores en Azure.

**Solución:** 
- ✅ Cliente ID por defecto actualizado
- ✅ Reintentos automáticos con cliente alternativo
- ✅ Mejor manejo de errores
- ✅ Documentación completa de troubleshooting

---

## 🚀 Cómo Usar (Opción Rápida)

### Paso 1: Ejecutar con Script Helper
```bash
# Windows
launcher_microsoft.bat

# Linux/Mac
./launcher_microsoft.sh
```

### Paso 2: Hacer Clic en "Login Microsoft"
El navegador se abrirá automáticamente.

### Paso 3: Autorizar en Microsoft
- Ingresa tus credenciales de Microsoft
- Autoriza el acceso a Xbox Live
- ¡Listo!

---

## 📚 Documentación por Tema

### 🟢 Inicio Rápido
**👉 Archivo:** `docs/guides/QUICK_MICROSOFT_LOGIN.md`

Guía de 3 pasos para loguear sin complicaciones.

### 🔧 Configuración Avanzada
**👉 Archivo:** `docs/oauth/CONFIGURACION_CLIENTE_AZURE.md`

- ✅ 3 formas de configurar cliente ID
- ✅ Cómo registrar en Azure Portal
- ✅ Variables de entorno disponibles

### 🆘 Troubleshooting
**👉 Archivo:** `docs/oauth/TROUBLESHOOTING_OAUTH.md`

Soluciones para todos los errores comunes:
- `unauthorized_client`
- `invalid_redirect_uri`
- `invalid_scope`
- `first_party` / `pre-authorization`
- Navegador no se abre
- Timeout

### 📖 Referencia Técnica
**👉 Archivo:** `docs/oauth/REFERENCIA_TECNICA_OAUTH.md`

Para desarrolladores que quieran entender el flujo OAuth 2.0.

### 📝 Cambios Realizados
**👉 Archivo:** `docs/oauth/CAMBIOS_OAUTH_UNAUTHORIZED_CLIENT.md`

Resumen técnico de los cambios y cómo probarlos.

---

## ⚡ Verificación Rápida

El JAR ha sido actualizado. Puedes verificar con:

```bash
# Windows
launcher_microsoft.bat

# Linux/Mac
./launcher_microsoft.sh

# O si lo compilaste
java -jar target/launcher_mialu.jar
```

Debería:
1. ✅ Abrirse sin error `unauthorized_client`
2. ✅ Abrir navegador automáticamente
3. ✅ Mostrar login de Microsoft

---

## 🔐 Seguridad (Sin cambios)

- ✅ Credenciales **nunca se guardan**
- ✅ Solo token encriptado
- ✅ Login en **servidores de Microsoft**
- ✅ PKCE habilitado

---

## 🎯 Próximas Acciones del Usuario

### Si funciona ✅
Disfruta del login Microsoft. Puedes:
- Usar skins sincronizadas
- Acceder a contenido premium
- Mantener sesión activa automáticamente

### Si sigue sin funcionar ❌
1. Lee `docs/oauth/TROUBLESHOOTING_OAUTH.md`
2. Verifica que uses cuenta **personal** de Microsoft (no de trabajo)
3. Intenta ejecutar con script helper
4. Si el problema persiste, verifica Azure Portal

---

## 📊 Archivos Relevantes

```
launcher_froshy/
├── launcher_microsoft.bat          ← Ejecuta con config correcta (Windows)
├── launcher_microsoft.sh           ← Ejecuta con config correcta (Linux/Mac)
├── build-bundle/
│   └── lib/
│       └── launcher.jar            ← JAR compilado y actualizado
└── docs/
    ├── guides/
    │   └── QUICK_MICROSOFT_LOGIN.md
    └── oauth/
        ├── CAMBIOS_OAUTH_UNAUTHORIZED_CLIENT.md
        ├── CONFIGURACION_CLIENTE_AZURE.md
        ├── TROUBLESHOOTING_OAUTH.md
        └── [otros archivos de referencia]
```

---

## 🔍 Cliente IDs Activos

| Cliente ID | Estado | Uso |
|-----------|--------|-----|
| `04b07795-8ddb-461a-bbee-02f9e1bf7b46` | ✅ Habilitado | Por defecto |
| `389b1b32-b5d5-43b2-bf1a-76cb27cae1e1` | ⚠️ Fallback | Reintentos automáticos |
| `62e8121e-6311-4c99-a649-305c93a77f92` | ⚙️ Tu ID | Personalizado (opcional) |

---

## 💡 Tips Útiles

1. **Primer intento falla:** El launcher reintentar automáticamente. Espera 2-3 segundos.

2. **Múltiples cuentas Microsoft:** Usa modo incógnito del navegador para limpiar cookies.

3. **Cuentas de trabajo:** No funcionan. Usa cuenta personal (Outlook, Hotmail).

4. **Puerto 3000 ocupado:** El launcher intentará fallback automáticamente.

5. **Slow internet:** Aumenta el tiempo de espera en settings (si existe opción).

---

## 📞 Soporte

Para más información:
- `docs/oauth/TROUBLESHOOTING_OAUTH.md` - Errores comunes
- `docs/oauth/CONFIGURACION_CLIENTE_AZURE.md` - Configuración avanzada
- `docs/guides/QUICK_MICROSOFT_LOGIN.md` - Guía básica

---

**✅ Estado: Listo para usar**  
**📦 Versión: 0.6-SNAPSHOT**  
**📅 Actualizado: 2026-04-02**

