# Solución: Error "First Party Application"

## Problema

Al intentar loguear con Microsoft, aparece el error:

```
invalid_request: The request is not valid. The application is a first party application, 
the user does not have consent, and users are not permitted to consent to first party applications. 
Rather, the first party application should obtain pre-authorization for the resource server.
```

## Causas

Este error ocurre cuando:

1. **Usas una cuenta de trabajo o escuela** (Azure AD corporativa)
2. **El Client ID es una aplicación de primera parte de Microsoft** que no permite consentimiento normal
3. **Tu administrador de Azure AD ha bloqueado el consentimiento** para aplicaciones externas

## Soluciones

### ✅ Solución 1: Usa una cuenta Microsoft PERSONAL (RECOMENDADO)

La forma más fácil es usar una cuenta Microsoft personal en lugar de corporativa:

1. Ve a https://outlook.live.com
2. Crea una cuenta gratuita si no tienes (ej: tu_nombre@outlook.com)
3. Inicia sesión en Launcher_Mialu con esa cuenta personal
4. El login debería funcionar sin problemas

**Ventajas:**
- No requiere configuración adicional
- Funciona inmediatamente
- Compatible con todas las versiones

### ✅ Solución 2: Registra tu propio Client ID (RECOMENDADO PARA CUENTAS CORPORATIVAS)

Si necesitas usar una cuenta corporativa, puedes crear tu propio Client ID en Azure:

#### Paso 1: Acceder a Azure Portal

1. Abre https://portal.azure.com
2. Inicia sesión con tu cuenta de Microsoft/Azure

#### Paso 2: Crear una nueva aplicación

1. Busca "App registrations" (Registros de aplicaciones)
2. Click en "New registration"
3. Completa los datos:
   - **Name:** `Launcher_Mialu` (o cualquier nombre)
   - **Supported account types:** Selecciona "Accounts in any organizational directory and personal Microsoft accounts"
   - **Redirect URI:** 
     - Platform: `Web`
     - URI: `http://localhost:3000/`

#### Paso 3: Configurar permisos

1. En el menú lateral, ve a "API permissions"
2. Click en "Add a permission"
3. Busca "Xbox Live"
4. Selecciona los permisos necesarios:
   - `XboxLive.signin`
   - `offline_access`

#### Paso 4: Obtener el Client ID

1. En el menú lateral, ve a "Overview"
2. Copia el valor de "Application (client) ID"

#### Paso 5: Configurar en Launcher_Mialu

Establece la variable de entorno antes de ejecutar el launcher:

**Windows (PowerShell):**
```powershell
$env:MIALU_MS_CLIENT_ID = "tu-client-id-aqui"
```

**Windows (CMD):**
```cmd
set MIALU_MS_CLIENT_ID=tu-client-id-aqui
```

**Linux/Mac:**
```bash
export MIALU_MS_CLIENT_ID="tu-client-id-aqui"
java -jar launcher_mialu.jar
```

### ✅ Solución 3: Contacta al administrador de Azure

Si usas una cuenta corporativa, el administrador puede:

1. Dar consentimiento a nivel de tenant
2. Permitir el consentimiento de usuario para aplicaciones
3. Configurar un Client ID corporativo

Contacta a tu equipo IT y pide:
> "Necesito permiso para usar una aplicación OAuth de terceros con Microsoft account. 
> El Client ID es: [tu-client-id]"

## Cambios Recientes

El Launcher_Mialu ahora utiliza:
- **Client ID mejorado:** `389b1b32-b5d5-43b2-bf1a-76cb27cae1e1` (optimizado para desktop apps)
- **Reintentos automáticos** cuando hay problemas de consentimiento
- **Mejores mensajes de error** con soluciones

## Alternativa: No usar Premium

Si no necesitas las características premium:
- Juega en modo offline
- No requiere autenticación con Microsoft
- Acceso completo a modpacks locales

## Más Información

- [Azure App Registration Guide](https://docs.microsoft.com/en-us/azure/active-directory/develop/quickstart-register-app)
- [Microsoft OAuth Scopes](https://docs.microsoft.com/en-us/graph/permissions-reference)
- [Xbox Live API](https://developer.microsoft.com/en-us/games/xbox/docs/xboxliveapi/)

## Contacto

Si el problema persiste después de estas soluciones, reporta un issue en el repositorio del proyecto con:
- El error exacto que ves
- Tu información de cuenta (si es corporativa o personal)
- Pasos que has intentado

