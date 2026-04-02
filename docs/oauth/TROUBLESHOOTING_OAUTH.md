# Troubleshooting: Errores de Login Microsoft

## Índice de Errores

- [unauthorized_client](#unauthorized_client)
- [invalid_request (redirect_uri)](#invalid_request-redirect_uri)
- [invalid_request (scope)](#invalid_request-scope)
- [first_party / pre-authorization](#first_party--pre-authorization)
- [El navegador no se abre](#el-navegador-no-se-abre)
- [Timeout esperando login](#timeout-esperando-login)

---

## unauthorized_client

### Mensaje Completo
```
unauthorized_client: The client does not exist or is not enabled for consumers. 
If you are the application developer, configure a new application through the 
App Registrations in the Azure Portal at https://go.microsoft.com/fwlink/?linkid=2083908.
```

### Causa
El Cliente ID de Microsoft no está habilitado para consumidores en Azure Portal.

### Solución

#### 1. Verificar el Cliente ID Usado
El launcher debería intentar automáticamente con:
1. Cliente Principal: `04b07795-8ddb-461a-bbee-02f9e1bf7b46` ✅
2. Cliente Alternativo: `389b1b32-b5d5-43b2-bf1a-76cb27cae1e1` (fallback)

Si el error persiste incluso después de reintentos automáticos, sigue el paso 2.

#### 2. Usar un Cliente ID Personalizado (Recomendado)

**Opción A: Variable de Entorno (Windows)**
```batch
set MIALU_MS_CLIENT_ID=62e8121e-6311-4c99-a649-305c93a77f92
launcher.bat
```

**Opción B: Variable de Entorno Permanente (Windows)**
1. Presiona `Win + X` → "Sistema"
2. "Configuración avanzada del sistema" → "Variables de entorno"
3. Nueva variable: `MIALU_MS_CLIENT_ID = 62e8121e-6311-4c99-a649-305c93a77f92`
4. Reinicia el launcher

**Opción C: Usar Script Helper (Windows)**
```batch
launcher_microsoft.bat 62e8121e-6311-4c99-a649-305c93a77f92
```

**Opción D: Linux/Mac**
```bash
export MIALU_MS_CLIENT_ID="62e8121e-6311-4c99-a649-305c93a77f92"
./launcher_microsoft.sh
```

#### 3. Registrar tu Propio Cliente en Azure

Si usas tu propio Cliente ID, en Azure Portal:

1. **Ir a App Registrations** → Selecciona tu app
2. **Authentication**:
   - ✅ Marca "Treat application as a public client" = **YES**
   - Añade redirect URI: `http://localhost:3000/`
   - Añade redirect URI: `http://localhost:8080/` (fallback)

3. **API permissions**:
   - Añade `XboxLive.signin` (delegated)
   - Añade `offline_access` (delegated)

4. **Grant admin consent** (si es posible)

---

## invalid_request (redirect_uri)

### Mensaje Completo
```
invalid_request: The provided value for the input parameter 'redirect_uri' is not valid. 
The expected value is a URI which matches a redirect URI registered for this client application.
```

### Causa
El `redirect_uri` que el launcher intenta usar no está registrado en Azure.

### Solución

#### 1. Verificar Redirect URIs en Azure
En Azure Portal → Tu App → **Authentication**:

Deberían estar estos valores:
```
http://localhost:3000/
http://localhost:8080/
```

Si no existen, añádelos:
1. Click en "Add a Redirect URI"
2. Ingresa `http://localhost:3000/`
3. Click en "Add a Redirect URI" nuevamente
4. Ingresa `http://localhost:8080/`
5. Guarda

#### 2. Usar Script Helper
```bash
# Windows
launcher_microsoft.bat 62e8121e-6311-4c99-a649-305c93a77f92

# Linux/Mac
./launcher_microsoft.sh 62e8121e-6311-4c99-a649-305c93a77f92
```

---

## invalid_request (scope)

### Mensaje Completo
```
invalid_request: The request is not valid. The scope 'XboxLive.signin offline_access' 
is not configured for this tenant.
```

### Causa
El cliente ID no tiene permisos para usar los scopes solicitados.

### Solución

#### 1. Verificar Permisos en Azure
En Azure Portal → Tu App → **API permissions**:

Deberían estar:
- ✅ `XboxLive.signin` (Delegated permission)
- ✅ `offline_access` (Delegated permission)

Si faltan:
1. Click "Add a permission"
2. Busca "Xbox Live API"
3. Selecciona "XboxLive.signin"
4. Marca "Delegated permissions"
5. Click "Add permissions"

Repite para `offline_access`:
1. Click "Add a permission"
2. Busca "Microsoft Graph"
3. Selecciona "offline_access"
4. Click "Add permissions"

#### 2. Grant Admin Consent
Si eres admin del tenant:
1. Click "Grant admin consent for [tenant]"
2. Confirma

Si no eres admin:
1. Notifica al administrador del tenant
2. O usa una cuenta personal de Microsoft (Outlook, Hotmail)

---

## first_party / pre-authorization

### Mensaje Completo
```
invalid_request: The request is not valid. The application is a first party application, 
the user does not have consent, and users are not permitted to consent to first party applications.
```

### Causa
Microsoft está bloqueando el consentimiento para esta aplicación. Generalmente ocurre con:
- Cuentas corporativas (trabajo/escuela)
- Cuentas de tenant azure vs consumidor

### Solución

#### 1. Usar Cuenta Personal de Microsoft
**Cambiar a una cuenta personal:**
- Outlook.com
- Hotmail.com
- Live.com
- Gmail (linked a Microsoft)

**No usar:**
- ❌ Cuentas de trabajo (@empresa.com)
- ❌ Cuentas de escuela (@universidad.edu)

#### 2. Limpiar Cookies y Caché
Intenta nuevamente en modo **Incógnito/Privado** del navegador:
- Chrome: `Ctrl + Shift + N`
- Firefox: `Ctrl + Shift + P`
- Edge: `Ctrl + Shift + InPrivate`

#### 3. Usar Cliente ID Personalizado con Tenant "common"
```batch
set MIALU_MS_CLIENT_ID=62e8121e-6311-4c99-a649-305c93a77f92
launcher.bat
```

Y en Azure, configura:
- **Tenant type**: "Multi-tenant"
- **Redirect URI**: `http://localhost:3000/`

---

## El navegador no se abre

### Síntomas
- Se abre el launcher pero no aparece ventana del navegador
- Dice "Esperando login en navegador..." pero no pasa nada

### Causa
El launcher intentó abrir el navegador pero falló.

### Solución

#### 1. Abrir Manualmente
Busca en la consola del launcher un mensaje como:
```
URL: https://login.live.com/oauth20_authorize.srf?client_id=...
```

Copia la URL y abre manualmente en tu navegador.

#### 2. Permitir Firewall
Si tienes firewall:
1. Abre "Windows Defender Firewall" → "Permitir una aplicación"
2. Busca tu navegador (Chrome, Firefox, Edge)
3. Marca "Privada" y "Pública"
4. Click "Permitir"

#### 3. Verificar Permisos de Puerto 3000
El launcher usa `localhost:3000` para recibir el callback.

**Windows:**
```batch
netstat -ano | findstr ":3000"
```

Si algo está usando el puerto 3000:
- Cierra la aplicación que lo usa
- O configura el puerto alternativo en launcher

---

## Timeout esperando login

### Síntomas
```
ERROR: Timeout esperando login Microsoft
[Premium] Esperando inicio de sesion en el navegador... (intento 1/2)
```

### Causa
El navegador no completó el login dentro del tiempo límite (5 minutos).

### Soluciones

#### 1. Completar Login Más Rápido
- Ingresa credenciales más rápido
- Asegúrate de tener conexión a internet
- Verifica que no haya 2FA (autenticación de dos factores) en tu cuenta

#### 2. Verificar Conexión de Red
```bash
# Windows
ping login.live.com

# Linux/Mac
ping -c 4 login.live.com
```

Debería mostrar `Reply from` sin pérdida de paquetes.

#### 3. Verificar Firewall/Antivirus
Algunos antivirus pueden bloquear las conexiones OAuth:
- Temporalmente deshabilita el antivirus
- O añade excepciones para `login.live.com` y `xboxlive.com`

#### 4. Aumentar Timeout
Edita el launcher o contacta a soporte.

---

## Errores en el Comando

### No se reconoce el comando

**Windows:**
```batch
# ❌ Incorrecto
launcher_microsoft

# ✅ Correcto
launcher_microsoft.bat

# ✅ También correcto
launcher_microsoft.bat 62e8121e-6311-4c99-a649-305c93a77f92
```

**Linux/Mac:**
```bash
# ❌ Incorrecto
launcher_microsoft.sh

# ✅ Correcto
./launcher_microsoft.sh

# ✅ También correcto
./launcher_microsoft.sh 62e8121e-6311-4c99-a649-305c93a77f92

# ✅ Si falla permiso
chmod +x launcher_microsoft.sh
./launcher_microsoft.sh
```

---

## Resumen Rápido

| Problema | Comando Rápido |
|----------|---|
| `unauthorized_client` | `launcher_microsoft.bat` (reintentos automáticos) |
| Seguir sin funcionar | `launcher_microsoft.bat 62e8121e-6311-4c99-a649-305c93a77f92` |
| Cuenta de trabajo | Cambiar a cuenta personal (Outlook, Hotmail) |
| No se abre navegador | Copiar URL manual a navegador |
| Timeout | Completar login más rápido, verificar internet |

---

## Contacto y Soporte

Si ninguna solución funciona:

1. Verifica que uses **Windows 10+**, **macOS 10.12+**, o **Ubuntu 18.04+**
2. Asegúrate de tener **conexión a internet estable**
3. Intenta con una **cuenta Microsoft personal diferente**
4. Reporta el error con:
   - Tu Cliente ID (si lo tienes)
   - El código de error exacto
   - Sistema operativo
   - Navegador por defecto

---

## Variables de Entorno Disponibles

```batch
REM Cliente ID personalizado
set MIALU_MS_CLIENT_ID=<tu_id>

REM Tenant de Azure (default: common)
set MIALU_MS_TENANT=common

REM Legacy (versiones antiguas)
set FROSHY_MS_CLIENT_ID=<tu_id>
set FROSHY_MS_TENANT=common
```

---

*Última actualización: 2026-04-02*
*Versión del Launcher: 0.6+*

