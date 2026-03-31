# Configuración de Login con Microsoft - Launcher_Mialu

## Opciones de Configuración

### 1. Usar el Client ID Público (Recomendado)
El launcher usará automáticamente el Client ID público registrado en Microsoft.
Puerto: 3000

**Ventajas:**
- Sin configuración requerida
- Funciona out-of-the-box
- No requiere registro en Azure

**Desventajas:**
- El puerto 3000 debe estar disponible
- Es compartido entre todas las instancias

**Uso:**
```bash
# Sin configuración especial necesaria
java -jar launcher_mialu.jar
```

---

### 2. Usar un Client ID Personalizado
Registra tu propio Application ID en Azure AD y úsalo.

**Ventajas:**
- Control total de tu aplicación
- Puedes usar cualquier puerto
- Mayor seguridad

**Desventajas:**
- Requiere registro en Azure
- Requiere configuración

**Pasos:**

#### 2.1 Registrar en Azure Portal

1. Ve a https://portal.azure.com
2. Ve a "Azure Active Directory" > "App registrations"
3. Haz clic en "New registration"
4. Nombre: "Launcher_Mialu"
5. Supported account types: "Accounts in any organizational directory and personal Microsoft accounts"
6. Redirect URI: `http://localhost:7878/` o el puerto que desees
7. Haz clic en "Register"

#### 2.2 Configurar la Aplicación

1. Ve a "Certificates & secrets"
2. Crea un nuevo "Client secret"
3. Copia el "Application (client) ID"

#### 2.3 Usar la Configuración

**Windows (PowerShell):**
```powershell
$env:MIALU_MS_CLIENT_ID = "tu-application-client-id"
$env:MIALU_MS_CLIENT_SECRET = "tu-client-secret"  # Opcional
java -jar launcher_mialu.jar
```

**Windows (CMD):**
```cmd
set MIALU_MS_CLIENT_ID=tu-application-client-id
set MIALU_MS_CLIENT_SECRET=tu-client-secret
java -jar launcher_mialu.jar
```

**Linux/Mac:**
```bash
export MIALU_MS_CLIENT_ID="tu-application-client-id"
export MIALU_MS_CLIENT_SECRET="tu-client-secret"  # Opcional
java -jar launcher_mialu.jar
```

**Variables de Entorno del Sistema (Permanente):**

Windows:
```
setx MIALU_MS_CLIENT_ID "tu-application-client-id"
setx MIALU_MS_CLIENT_SECRET "tu-client-secret"
```

Linux (~/.bashrc):
```bash
export MIALU_MS_CLIENT_ID="tu-application-client-id"
export MIALU_MS_CLIENT_SECRET="tu-client-secret"
```

---

## Variables de Configuración Disponibles

| Variable | Descripción | Por Defecto |
|----------|-------------|------------|
| `MIALU_MS_CLIENT_ID` | Application Client ID de Azure | `04b07795-8ddb-461a-bbee-02f9e1bf7b46` (público) |
| `MIALU_MS_CLIENT_SECRET` | Client Secret (opcional) | Vacío |
| `MIALU_API_PORT` | Puerto del API interno | `7878` |

**Nota:** Las variables heredadas `FROSHY_MS_CLIENT_ID` y `FROSHY_API_PORT` también funcionan pero son depreciadas.

---

## Solución de Problemas

### "Puerto 3000 ya está en uso"

**Opción 1: Cierra la aplicación que usa el puerto**
```bash
# En Windows (PowerShell)
Stop-Process -Id (Get-NetTCPConnection -LocalPort 3000 -ErrorAction SilentlyContinue).OwningProcess -Force

# En Linux/Mac
lsof -i :3000 | grep LISTEN | awk '{print $2}' | xargs kill -9
```

**Opción 2: Usa un Client ID personalizado con otro puerto**
```bash
set MIALU_MS_CLIENT_ID="tu-custom-id"
set MIALU_API_PORT=8080
java -jar launcher_mialu.jar
```

### "La sesión expira sin completar el login"

1. Verifica tu conexión a internet
2. El timeout predeterminado es de 5 minutos
3. Intenta nuevamente desde el launcher

### "No se abre el navegador automáticamente"

Si el navegador no se abre:
1. El launcher mostrará la URL de login en la consola
2. Cópiala y pégala en tu navegador manualmente
3. El callback se procesará automáticamente

### "Error: Invalid redirect_uri"

Esto ocurre cuando:
1. **Usas Client ID público pero otro puerto**: Cambia a puerto 3000
2. **Usas Client ID personalizado sin registrarlo en Azure**: Registra correctamente el Redirect URI

---

## Flujo de Autenticación

```
┌─────────────────────────────────────────────────────────────┐
│ Usuario hace clic en "Login con Microsoft"                  │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ Launcher abre el navegador con URL de autorización          │
│ (Incluye: client_id, redirect_uri, state, code_challenge)  │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ Usuario inicia sesión en Microsoft                          │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ Microsoft redirige a http://localhost:3000/?code=...        │
│ (o http://localhost:7878/ si usas Client ID personalizado) │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ Servidor de Callback captura el código                      │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ Launcher intercambia el código por token                    │
│ (POST a https://login.microsoftonline.com/oauth2/v2.0/token)
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ Launcher autentica en Xbox Live y Minecraft Services       │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ Sesión Premium sincronizada en el launcher                 │
└─────────────────────────────────────────────────────────────┘
```

---

## Seguridad

- Los tokens de acceso se almacenan cifrados en el disco
- Los refresh tokens permiten mantener la sesión sin re-autenticar
- PKCE (Proof Key for Code Exchange) previene ataques de autorización
- La sesión expira automáticamente si los tokens son inválidos

---

## Preguntas Frecuentes

**P: ¿Puedo usar el launcher sin login con Microsoft?**
R: Sí, el login con Microsoft es opcional. Puedes usar un username/nombre de usuario local.

**P: ¿Dónde se guardan mis credenciales?**
R: Los tokens se guardan cifrados en `~/.mialu-launcher/` (o `~/.froshy-launcher/` en instalaciones antiguas).

**P: ¿Qué sucede si me deslogo?**
R: La sesión Premium se cierra pero tus perfiles locales permanecen.

**P: ¿Puedo cambiar de cuenta de Microsoft?**
R: Sí, haz clic en "Logout" y luego "Login con Microsoft" de nuevo.

---

## Soporte

Para reportar problemas, crea un issue en el repositorio del proyecto con:
1. Sistema operativo y versión
2. Versión del launcher
3. Pasos para reproducir el problema
4. Mensajes de error (sin tokens o información sensible)

