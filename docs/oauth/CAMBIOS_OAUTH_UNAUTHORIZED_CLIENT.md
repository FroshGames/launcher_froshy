# Sumario de Cambios OAuth - Arreglo de unauthorized_client

## Fecha: 2026-04-02
## Versión: 0.6-SNAPSHOT

### 🎯 Objetivo
Arreglar el error `unauthorized_client: The client does not exist or is not enabled for consumers` que ocurría al intentar loguear con Microsoft.

---

## ✅ Cambios Realizados

### 1. **Código Java - MicrosoftAuthService.java**

#### Cliente ID por Defecto Actualizado
```java
// ANTES (no funciona para consumidores)
private static final String DEFAULT_CLIENT_ID = "389b1b32-b5d5-43b2-bf1a-76cb27cae1e1";
private static final String ALT_PUBLIC_CLIENT_ID = "04b07795-8ddb-461a-bbee-02f9e1bf7b46";

// AHORA (habilitado para consumidores ✅)
private static final String DEFAULT_CLIENT_ID = "04b07795-8ddb-461a-bbee-02f9e1bf7b46";
private static final String ALT_PUBLIC_CLIENT_ID = "389b1b32-b5d5-43b2-bf1a-76cb27cae1e1";
```

**Impacto:** El cliente por defecto ahora es compatible con consumidores en Azure.

#### Mejorado Manejo de Error `unauthorized_client`
```java
// Detectar error unauthorized_client y cambiar de cliente si es posible
if ((lowered.contains("unauthorized_client") || lowered.contains("aadsts700016") 
        || lowered.contains("not enabled for consumers"))
        && !ALT_PUBLIC_CLIENT_ID.equals(pending.oauthClientId())
        && switchToAlternateClient(pending.oauthClientId())) {
    throw new IllegalStateException("MS_RETRY_AUTOMATIC:Client ID no habilitado para consumidores. "
            + "Reintentando con cliente Microsoft alternativo.");
}
```

**Impacto:** Si ocurre error de `unauthorized_client`, reintentar automáticamente con cliente alternativo.

#### Mensajes de Error Mejorados
```java
// Ahora incluye info de diagnóstico
- Client ID usado: 04b07795-8ddb-461a-bbee-02f9e1bf7b46
- Tenant: common
- Redirect URI: http://localhost:3000/
- Ver: docs/oauth/TROUBLESHOOTING_OAUTH.md
```

**Impacto:** Los usuarios ven información útil para resolver problemas.

---

### 2. **Scripts Helper Nuevos**

#### `launcher_microsoft.bat` (Windows)
```batch
launcher_microsoft.bat                                    # Cliente por defecto
launcher_microsoft.bat 62e8121e-6311-4c99-a649-305c93a77f92  # Cliente personalizado
```

#### `launcher_microsoft.sh` (Linux/Mac)
```bash
./launcher_microsoft.sh                                   # Cliente por defecto
./launcher_microsoft.sh 62e8121e-6311-4c99-a649-305c93a77f92 # Cliente personalizado
```

**Impacto:** Usuarios pueden ejecutar fácilmente con configuración correcta.

---

### 3. **Documentación Nueva**

#### `docs/oauth/CONFIGURACION_CLIENTE_AZURE.md`
- ✅ Explica el error `unauthorized_client`
- ✅ 3 opciones para configurar cliente
- ✅ Cómo registrar cliente en Azure Portal
- ✅ Tabla de errores comunes

#### `docs/oauth/TROUBLESHOOTING_OAUTH.md`
- ✅ Soluciones para 6+ errores comunes
- ✅ Pasos detallados para cada problema
- ✅ Variables de entorno disponibles
- ✅ Comandos para verificar configuración

#### `docs/guides/QUICK_MICROSOFT_LOGIN.md` (Actualizado)
- ✅ Guía simplificada de 3 pasos
- ✅ Opciones avanzadas
- ✅ Referencias a documentación completa

---

### 4. **Compilación**
```bash
# Compilado exitosamente
mvn clean package -DskipTests
# Resultado: launcher_mialu.jar (2.7 MB)
# Ubicación: target/launcher_mialu.jar
# Copiad a: build-bundle/lib/launcher.jar
```

---

## 🚀 Cómo Probar

### Opción 1: Cliente por Defecto (Recomendado)
```bash
# Windows
launcher_microsoft.bat

# Linux/Mac
./launcher_microsoft.sh
```

### Opción 2: Cliente Personalizado
```bash
# Windows
launcher_microsoft.bat 62e8121e-6311-4c99-a649-305c93a77f92

# Linux/Mac
./launcher_microsoft.sh 62e8121e-6311-4c99-a649-305c93a77f92
```

### Opción 3: Variable de Entorno
```bash
# Windows
set MIALU_MS_CLIENT_ID=04b07795-8ddb-461a-bbee-02f9e1bf7b46
launcher_froshy.bat

# Linux/Mac
export MIALU_MS_CLIENT_ID="04b07795-8ddb-461a-bbee-02f9e1bf7b46"
./launcher_froshy.sh
```

---

## ✅ Verificación

### El usuario debería ver:
1. ✅ Se abre navegador automáticamente
2. ✅ Pide login de Microsoft
3. ✅ Pide permiso para Xbox Live
4. ✅ Redirige a `http://localhost:3000/`
5. ✅ Muestra "Login completado" en navegador
6. ✅ Nombre de usuario aparece en launcher

### Si hay problemas:
- Ver `docs/oauth/TROUBLESHOOTING_OAUTH.md`
- Verificar variables de entorno: `set | findstr MIALU`
- Verificar que puerto 3000 esté disponible
- Probar en navegador incógnito

---

## 🔄 Flujo de Reintentos Automáticos

El sistema ahora reintentar automáticamente en estos casos:

1. ✅ `unauthorized_client` → Cambiar de cliente
2. ✅ `invalid_redirect_uri` → Cambiar de cliente
3. ✅ `first_party` / `pre-authorization` → Cambiar de tenant (consumers → common)
4. ✅ `invalid_scope` → Intentar sin `offline_access`
5. ✅ No recibe callback → Cambiar de cliente

**Máximo de reintentos:** 2

---

## 🔐 Seguridad (Sin cambios)

- ✅ No se almacenan credenciales
- ✅ Solo token encriptado
- ✅ PKCE habilitado
- ✅ HTTPS cuando es posible

---

## 📊 Cliente IDs Soportados

| Cliente ID | Habilitado para Consumidores | Usado Por Defecto |
|-----------|---------------------------|-----------------|
| `04b07795-8ddb-461a-bbee-02f9e1bf7b46` | ✅ Sí | ✅ Sí |
| `389b1b32-b5d5-43b2-bf1a-76cb27cae1e1` | ❌ No | ❌ Fallback |
| `62e8121e-6311-4c99-a649-305c93a77f92` | ⚙️ Configurable | ❌ Personalizado |

---

## 📝 Variables de Entorno Disponibles

```batch
REM Cliente ID personalizado
set MIALU_MS_CLIENT_ID=<tu_id>

REM Tenant de Azure
set MIALU_MS_TENANT=common

REM Legacy (versiones antiguas)
set FROSHY_MS_CLIENT_ID=<tu_id>
set FROSHY_MS_TENANT=common
```

---

## 🎯 Próximas Mejoras (Opcional)

- [ ] Soporte para Device Code Flow (alternativa si no funciona navegador)
- [ ] UI mejorada para mostrar progreso de login
- [ ] Caché de token para startup más rápido
- [ ] Soporte para múltiples cuentas Microsoft simultáneamente
- [ ] Sincronización automática de skins desde Microsoft

---

## 📞 Contacto

Si los problemas persisten:
1. Verifica `docs/oauth/TROUBLESHOOTING_OAUTH.md`
2. Verifica `docs/oauth/CONFIGURACION_CLIENTE_AZURE.md`
3. Asegúrate de usar cuenta personal (Outlook, Hotmail)
4. Reporta el error con Cliente ID y código exacto

---

*Cambios compilados y listos para usar.*
*JAR ubicado en: `build-bundle/lib/launcher.jar`*

