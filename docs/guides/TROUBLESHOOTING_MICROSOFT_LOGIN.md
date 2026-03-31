# Guía de Troubleshooting - Login Microsoft

## Problemas Comunes y Soluciones

### ❌ Error: "First Party Application"

**Mensajes de error:**
```
invalid_request: The request is not valid. The application is a first party application...
```

**Causas comunes:**
- Usas una cuenta de trabajo o escuela (Azure AD corporativa)
- El administrador de Azure ha bloqueado el consentimiento

**Soluciones:**

1. **Opción 1: Usa una cuenta personal (RECOMENDADO)**
   - Crea una cuenta Outlook gratuita: https://outlook.live.com
   - Usa esa cuenta en lugar de la corporativa

2. **Opción 2: Registra tu propio Client ID**
   - Ver: [SOLUCION_FIRST_PARTY_ERROR.md](./SOLUCION_FIRST_PARTY_ERROR.md)

---

### ❌ Error: "redirect_uri no válido"

**Mensajes de error:**
```
invalid_request: The provided value for the input parameter 'redirect_uri' is not valid...
```

**Causas:**
- El Client ID no tiene registrado `http://localhost:3000/` como redirect URI
- Conflicto de puertos

**Soluciones:**

```bash
# Verifica que el puerto 3000 está disponible
# Windows
netstat -ano | findstr :3000

# macOS/Linux
lsof -i :3000

# Si está en uso, libéralo o cambia el cliente
```

**Si usas un Client ID personalizado:**
1. Ve a https://portal.azure.com
2. Busca tu aplicación en "App registrations"
3. Ve a "Authentication"
4. Asegúrate que `http://localhost:3000/` está en "Redirect URIs"

---

### ❌ Error: "Scope inválido"

**Mensajes de error:**
```
The scope 'XboxLive.signin offline_access' is not configured for this tenant...
```

**Causas:**
- El Client ID no tiene los permisos necesarios configurados
- Problema de configuración de Azure

**Soluciones:**

```bash
# El launcher intentará automáticamente con scope reducido
# Si sigue fallando, configura el scope manualmente en Azure Portal:

# 1. Ve a tu app en https://portal.azure.com
# 2. API permissions → Add a permission
# 3. Busca "Xbox Live"
# 4. Selecciona XboxLive.signin
```

---

### ❌ Error: "Login cancelado" o "No se recibió callback"

**Causas:**
- Navegador bloqueó la redirección
- Firewall/Antivirus interfiere
- Tiempo de espera agotado

**Soluciones:**

1. **Verifica firewall:**
   ```bash
   # Asegúrate que localhost:3000 está permitido
   # Windows: Firewall de Windows → Permitir aplicación
   # macOS: System Preferences → Security & Privacy
   ```

2. **Aumenta el tiempo de espera:**
   - Intenta de nuevo, puede tomar 30-60 segundos
   - No cierres el navegador durante el login

3. **Limpia cookies del navegador:**
   ```
   Abre DevTools (F12) → Application → Clear site data
   ```

---

### ❌ Error: "Sesión expirada" o "No hay sesión premium activa"

**Causas:**
- Token de sesión expiró (válido por 1 hora)
- Session fue borrada

**Soluciones:**

```bash
# Inicia sesión nuevamente
# Los datos se guardan automáticamente

# Si quieres forzar logout:
# Elimina el archivo de sesión guardado
# Ubicación: ~/.launcher_mialu/sessions/
```

---

### ❌ Error: "MIALU_MS_CLIENT_ID no configurado"

**Mensaje:**
```
Falta configurar MIALU_MS_CLIENT_ID para usar login con Microsoft
```

**Causa:**
- Variable de entorno no está establecida
- Launcher usando Client ID por defecto que no está disponible

**Soluciones:**

```bash
# Opción 1: Establecer variable de entorno

# Windows (PowerShell)
$env:MIALU_MS_CLIENT_ID = "389b1b32-b5d5-43b2-bf1a-76cb27cae1e1"
java -jar launcher_mialu.jar

# Windows (CMD)
set MIALU_MS_CLIENT_ID=389b1b32-b5d5-43b2-bf1a-76cb27cae1e1
java -jar launcher_mialu.jar

# Linux/macOS
export MIALU_MS_CLIENT_ID="389b1b32-b5d5-43b2-bf1a-76cb27cae1e1"
java -jar launcher_mialu.jar

# Opción 2: Pasar como propiedad Java
java -Dmialu.ms.clientId=389b1b32-b5d5-43b2-bf1a-76cb27cae1e1 -jar launcher_mialu.jar
```

---

## Cliente ID Disponibles

### Cliente por Defecto (Recomendado)
```
389b1b32-b5d5-43b2-bf1a-76cb27cae1e1
```
- ✅ Optimizado para aplicaciones de escritorio
- ✅ Soporta localhost redirect
- ✅ Compatible con cuentas personales
- ⚠️ Puede tener limitaciones con cuentas corporativas

### Cliente Alternativo
```
04b07795-8ddb-461a-bbee-02f9e1bf7b46
```
- Para fallbacks automáticos
- Úsalo solo si el primero no funciona

---

## Configuración Avanzada

### Establecer Client ID personalizado permanentemente

**Windows:**
```powershell
# Añade a tu perfil de PowerShell
# $PROFILE (C:\Users\tu_usuario\Documents\PowerShell\profile.ps1)

$env:MIALU_MS_CLIENT_ID = "tu-client-id-aqui"
```

**Linux/macOS:**
```bash
# Añade a ~/.bashrc o ~/.zshrc
export MIALU_MS_CLIENT_ID="tu-client-id-aqui"
```

---

## Debug

### Activar logs detallados

```bash
# Windows
java -Dlogback.configurationFile=logback-debug.xml -jar launcher_mialu.jar

# Linux/macOS
java -Dlogback.configurationFile=logback-debug.xml -jar launcher_mialu.jar
```

### Verificar conectividad a Microsoft

```bash
# Verifica que puedes alcanzar los servidores de Microsoft
curl -I https://login.microsoftonline.com/
curl -I https://auth.xboxlive.com/
curl -I https://api.minecraftservices.com/
```

---

## Contacto y Reportes

Si después de estas soluciones el problema persiste:

1. Recopila la información:
   - Tipo de cuenta (personal/corporativa)
   - Sistema operativo
   - Error exacto que ves
   - Steps que has intentado

2. Reporta en el repositorio del proyecto

---

## Referencias

- [Documentación de OAuth](./SOLUCION_FIRST_PARTY_ERROR.md)
- [Variables de Entorno](../setup/ENVIRONMENT_VARIABLES.md)
- [Configuración Microsoft](./README.md)

