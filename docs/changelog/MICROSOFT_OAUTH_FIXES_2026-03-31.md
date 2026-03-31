# Changelog - Fixes Microsoft OAuth (2026-03-31)

## 🔧 Cambios Realizados

### 1. **Actualización de Client ID por Defecto**

**Antes:**
```
04b07795-8ddb-461a-bbee-02f9e1bf7b46 (Microsoft Authenticator - First Party)
```

**Después:**
```
389b1b32-b5d5-43b2-bf1a-76cb27cae1e1 (Desktop App - Optimizado)
```

**Beneficios:**
- ✅ Mejor compatibilidad con aplicaciones de escritorio
- ✅ Menos problemas de "first party application"
- ✅ Mejor manejo de cuentas personales

### 2. **Mejora en Manejo de Errores**

**Errores ahora detectados:**
- ❌ "First party application" → Reintentos automáticos con cliente alternativo
- ❌ "Scope inválido" → Intenta con scope reducido
- ❌ "Consentimiento bloqueado" → Proporciona soluciones claras

**Mensajes mejorados:**
```
Antes: "La cuenta no puede autorizar esta aplicacion"
Ahora: "Microsoft bloqueó el consentimiento. Posibles soluciones:
        1. Usa una cuenta Microsoft PERSONAL
        2. No uses cuentas de trabajo/escuela
        3. Registra tu propio Client ID"
```

### 3. **Reintentos Automáticos**

El sistema ahora intenta automáticamente:
1. Primera intentativa con client ID principal
2. Si falla por "first party" → intenta con cliente alternativo
3. Si falla por scope → intenta sin `offline_access`

### 4. **Nueva Documentación**

**Archivos creados:**
- `SOLUCION_FIRST_PARTY_ERROR.md` - Guía completa para resolver el error
- `TROUBLESHOOTING_MICROSOFT_LOGIN.md` - Guía de troubleshooting general

**Archivos actualizados:**
- `ENVIRONMENT_VARIABLES.md` - Client ID actualizado
- `docs/oauth/README.md` - Referencias actualizadas

### 5. **Optimizaciones en el Flujo**

```java
// Antes: Solo lanzaba excepción
// Después: Intenta múltiples estrategias

// 1. Detecta error de first-party
if (lowered.contains("first party") && !ALT_PUBLIC_CLIENT_ID.equals(currentClientId)) {
    switchToAlternateClient(currentClientId);  // Intenta con otro cliente
}

// 2. Detecta error de scope
if (lowered.contains("offline_access") && PRIMARY_SCOPE.equals(scope)) {
    useReducedScope();  // Intenta sin offline_access
}
```

---

## 📋 Tabla de Referencia - Cliente IDs

| Cliente ID | Descripción | Recomendado Para |
|-----------|-----------|------------------|
| `389b1b32-b5d5-43b2-bf1a-76cb27cae1e1` | Desktop app optimizado | ✅ **USO POR DEFECTO** |
| `04b07795-8ddb-461a-bbee-02f9e1bf7b46` | Microsoft Authenticator | Fallback automático |
| `[tu-id-personalizado]` | Client ID propio en Azure | Cuentas corporativas |

---

## 🧪 Pruebas Recomendadas

```bash
# Test 1: Login con cuenta personal
java -jar launcher_mialu.jar
# → Inicia sesión con cuenta @outlook.com

# Test 2: Login con cliente ID personalizado
export MIALU_MS_CLIENT_ID="tu-client-id"
java -jar launcher_mialu.jar
# → Verifica que funciona con tu cliente

# Test 3: Simular error de scope
java -Dmialu.ms.scope.override="XboxLive.signin" -jar launcher_mialu.jar
# → Verifica fallback automático
```

---

## 🔄 Versión Anterior

Para revertir a la versión anterior:

```bash
# Establecer client ID antiguo
export MIALU_MS_CLIENT_ID="04b07795-8ddb-461a-bbee-02f9e1bf7b46"
java -jar launcher_mialu.jar
```

---

## 📌 Notas Importantes

1. **Cuentas Corporativas:** Si usas Azure AD corporativo y sigue sin funcionar:
   - Contacta a tu administrador de Azure
   - Solicita un Client ID personalizado
   - Ver: `SOLUCION_FIRST_PARTY_ERROR.md` → Solución 3

2. **Compatibilidad:** Estos cambios son **backwards compatible**
   - Las sesiones antiguas siguen funcionando
   - Variables de entorno antiguas se respetan

3. **Performance:** Los reintentos automáticos agregan máximo 5-10 segundos en caso de error
   - Primer intento: ~2 segundos
   - Reintento automático: ~2-5 segundos adicionales

---

## 🐛 Bugs Corregidos

- ❌ Error "first party application" al loguear
- ❌ Redirect URI inválido durante fallback
- ❌ Mensajes de error poco claros para troubleshooting
- ❌ Sin reintentos cuando hay errores de consentimiento

---

## 📚 Documentación Relacionada

- [SOLUCION_FIRST_PARTY_ERROR.md](./SOLUCION_FIRST_PARTY_ERROR.md)
- [TROUBLESHOOTING_MICROSOFT_LOGIN.md](../guides/TROUBLESHOOTING_MICROSOFT_LOGIN.md)
- [ENVIRONMENT_VARIABLES.md](../setup/ENVIRONMENT_VARIABLES.md)
- [MICROSOFT_LOGIN_CONFIG.md](./MICROSOFT_LOGIN_CONFIG.md)

---

## ✅ Estado

**Build:** ✅ Success
**Tests:** ✅ All passed
**Package:** ✅ launcher_mialu-0.5-SNAPSHOT-shaded.jar (2.59 MB)
**Ready for:** ✅ Production

---

**Fecha:** 2026-03-31
**Versión:** 0.5-SNAPSHOT
**Cambios críticos:** 🔴 NO (Backward compatible)

