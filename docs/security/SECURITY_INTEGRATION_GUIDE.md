# 🔒 GUÍA DE INTEGRACIÓN DE SEGURIDAD

**Fecha:** 31 de Marzo de 2026
**Versión:** 1.1
**Autor:** Security Team

---

## 📋 CLASES DE SEGURIDAD IMPLEMENTADAS

```
security/
├── SecurityManager.java           ← Gestor central
├── RateLimiter.java              ← Protección fuerza bruta
├── InputValidator.java           ← Validación entrada
├── SecurityAuditLogger.java      ← Auditoría y logging
└── TokenEncryption.java          ← Encriptación AES-256
```

---

## 🔧 CÓMO USAR EN EL CÓDIGO

### 1. Inicializar SecurityManager

```java
import am.froshy.mialu.launcher.security.SecurityManager;
import java.nio.file.Path;

// En tu clase principal
Path securityLogPath = Path.of(System.getProperty("user.home"), 
    ".mialu-launcher", "logs");
SecurityManager securityManager = new SecurityManager(securityLogPath);
```

### 2. Validar Input

```java
// Validar entrada
if (!securityManager.validateInput(username, "username")) {
    throw new SecurityException("Username inválido");
}

// Sanitizar para XSS protection
String safe = securityManager.sanitizeInput(userInput);
```

### 3. Rate Limiting para Login

```java
// Verificar si puede intentar
String identifier = ipAddress; // O usuario
if (!securityManager.canAttemptLogin(identifier)) {
    throw new SecurityException("Demasiados intentos. Intenta más tarde.");
}

// Si login exitoso
try {
    login(username, password);
    securityManager.recordLoginSuccess(identifier, username);
} catch (Exception ex) {
    securityManager.recordLoginFailure(identifier, ex.getMessage());
    throw ex;
}
```

### 4. Validar Redirect URI

```java
// Antes de usar redirect_uri
if (!securityManager.validateRedirectUri(redirectUri)) {
    throw new SecurityException("Redirect URI no válido");
}
```

### 5. Encriptar Tokens

```java
import am.froshy.mialu.launcher.security.TokenEncryption;

// Encriptar token antes de guardar
TokenEncryption encryption = new TokenEncryption();
String encryptedToken = encryption.encrypt(rawToken);
Files.writeString(tokenFile, encryptedToken);

// Desencriptar cuando necesites
String rawToken = encryption.decrypt(encryptedToken);

// IMPORTANTE: Limpiar de memoria
TokenEncryption.clearSensitiveData(rawToken.toCharArray());
```

### 6. Auditoría de Eventos

```java
// Log de autenticación
securityManager.getAuditLogger()
    .logAuthenticationAttempt(username, true, "OAuth Microsoft");

// Log de acceso a recursos sensibles
securityManager.getAuditLogger()
    .logSensitiveResourceAccess("player_profile", username, true);

// Log de excepciones
securityManager.getAuditLogger()
    .logSecurityException("Token validation", exception);

// Log de violaciones de política
securityManager.getAuditLogger()
    .logPolicyViolation("XSS_ATTEMPT", "Script tag detected in input");
```

---

## 📝 INTEGRACIÓN EN MicrosoftAuthService

```java
// Agregar al constructor
private final SecurityManager securityManager;

public MicrosoftAuthService(LauncherConfig config, 
                           MicrosoftAuthStore store,
                           SecurityManager securityManager) {
    // ... código existente ...
    this.securityManager = securityManager;
}

// En buildRedirectUri()
if (!securityManager.validateRedirectUri(redirectUri)) {
    throw new IllegalArgumentException("Invalid redirect_uri");
}

// En startBrowserLogin()
String userIP = getClientIP();
if (!securityManager.canAttemptLogin(userIP)) {
    throw new SecurityException("Too many login attempts");
}

// En completeBrowserLogin()
try {
    // ... login code ...
    securityManager.recordLoginSuccess(userIP, playerName);
} catch (Exception ex) {
    securityManager.recordLoginFailure(userIP, ex.getMessage());
    throw ex;
}
```

---

## 📊 FLUJO DE SEGURIDAD COMPLETO

```
Usuario hace clic en "Login Microsoft"
    ↓
SecurityManager.canAttemptLogin(ip)
    ├─ ✓ Sí → Continuar
    └─ ✗ No → Bloquear (demasiados intentos)
    ↓
Validar redirect_uri
    ├─ ✓ Válido → Continuar
    └─ ✗ Inválido → Error (log de auditoría)
    ↓
Usuario inicia sesión en Microsoft
    ↓
Recibir callback con código
    ↓
InputValidator.validate(código, "token")
    ├─ ✓ Válido → Continuar
    └─ ✗ Inválido → Error (log de auditoría)
    ↓
Intercambiar código por tokens
    ↓
TokenEncryption.encrypt(tokens)
    ↓
Guardar tokens encriptados en disco
    ↓
SecurityManager.recordLoginSuccess(ip, username)
    ↓
✅ Login exitoso
```

---

## 🛡️ ESCENARIOS DE ATAQUE PREVENTIVOS

### Ataque: Fuerza Bruta (100 intentos/min)
**Protección:** RateLimiter
**Resultado:** 
- Primeros 5 intentos → Bloqueados
- Siguiente 15 minutos → Lockout automático
- Intento #6 → Error: "Too many attempts"

---

### Ataque: Inyección XSS
```
Input: <script>alert('xss')</script>
↓
InputValidator.sanitize()
↓
Resultado: &lt;script&gt;alert(&#x27;xss&#x27;)&lt;&#x2F;script&gt;
↓
✅ Neutralizado (aparece como texto, no como código)
```

---

### Ataque: Token Theft
```
Atacante obtiene token encriptado
↓
Intenta usar token
↓
TokenEncryption.decrypt(token)
↓
Necesita la clave secreta
↓
Clave es única por instancia
↓
Token no desencriptable
↓
❌ Ataque fallido
```

---

### Ataque: Redirect URI Falso
```
Atacante intenta: redirect_uri=https://evil.com
↓
SecurityManager.validateRedirectUri()
↓
Verifica: solo localhost permitido
↓
Rechaza evil.com
↓
Log: "Invalid redirect_uri: evil.com"
↓
❌ Ataque bloqueado
```

---

## 📖 ARCHIVO DE AUDITORÍA

**Ubicación:** `~/.mialu-launcher/logs/security-audit.log`

**Contenido:**
```
[2026-03-31T14:30:00.123] AUTHENTICATION_ATTEMPT | Status: SUCCESS | Details: User: user****
[2026-03-31T14:31:15.456] LOGIN_ATTEMPT | Status: FAILED | Details: Too many attempts for: 127.0.0.1
[2026-03-31T14:32:30.789] REDIRECT_URI_VALIDATION | Status: SUCCESS | Details: http://localhost:3000/
[2026-03-31T14:33:45.012] INPUT_VALIDATION | Status: FAILED | Details: Invalid username format
[2026-03-31T14:34:56.345] TOKEN_VALIDATION | Status: SUCCESS | Details: Token valid
```

**Cómo Usar:**
```bash
# Ver últimas 20 líneas
tail -20 ~/.mialu-launcher/logs/security-audit.log

# Buscar intentos fallidos
grep "FAILED" ~/.mialu-launcher/logs/security-audit.log

# Ver eventos en una hora específica
grep "2026-03-31T14:" ~/.mialu-launcher/logs/security-audit.log
```

---

## ✅ CHECKLIST DE SEGURIDAD

- [x] SecurityManager implementado
- [x] RateLimiter integrado
- [x] InputValidator activo
- [x] SecurityAuditLogger funcional
- [x] TokenEncryption configurado
- [x] Validación de redirect_uri
- [x] Sanitización XSS
- [x] Limpieza de memoria
- [x] Logging de auditoría
- [ ] Integración en LauncherService (próxima tarea)
- [ ] Integración en API Server (próxima tarea)
- [ ] Tests de seguridad (próxima tarea)

---

## 🚀 PRÓXIMOS PASOS

1. **Integrar en LauncherService**
   ```java
   private final SecurityManager securityManager;
   ```

2. **Integrar en InternalApiServer**
   ```java
   securityManager.validateInput(param);
   ```

3. **Crear SecurityTests**
   - Test rate limiting
   - Test input validation
   - Test token encryption

4. **Configurar permisos de archivos**
   ```bash
   chmod 600 ~/.mialu-launcher/config.json
   chmod 600 ~/.mialu-launcher/microsoft-session.json
   ```

5. **Documentar en README**
   - Cómo configurar seguridad
   - Cómo monitorear logs
   - Cómo reportar vulnerabilidades

---

## 📞 EJEMPLOS COMPLETOS

### Ejemplo 1: Login Seguro

```java
public class SecureLoginController {
    private final SecurityManager securityManager;
    
    public LoginResult login(String ip, String username, String password) {
        // 1. Verificar rate limit
        if (!securityManager.canAttemptLogin(ip)) {
            securityManager.recordLoginFailure(ip, "Rate limit exceeded");
            return LoginResult.TOO_MANY_ATTEMPTS;
        }
        
        // 2. Validar entrada
        if (!securityManager.validateInput(username, "username")) {
            securityManager.recordLoginFailure(ip, "Invalid username format");
            return LoginResult.INVALID_INPUT;
        }
        
        try {
            // 3. Realizar login
            User user = authenticate(username, password);
            
            // 4. Registrar éxito
            securityManager.recordLoginSuccess(ip, username);
            
            return LoginResult.SUCCESS(user);
        } catch (Exception ex) {
            // 5. Registrar fallo
            securityManager.recordLoginFailure(ip, ex.getMessage());
            return LoginResult.FAILURE;
        }
    }
}
```

### Ejemplo 2: Encriptación de Token

```java
public class TokenManager {
    private final TokenEncryption encryption = new TokenEncryption();
    
    public void saveToken(String token) throws Exception {
        // Encriptar
        String encrypted = encryption.encrypt(token);
        
        // Guardar
        Path tokenFile = getTokenPath();
        Files.writeString(tokenFile, encrypted);
        
        // Establecer permisos seguros
        setSecurePermissions(tokenFile);
    }
    
    public String loadToken() throws Exception {
        Path tokenFile = getTokenPath();
        String encrypted = Files.readString(tokenFile);
        
        // Desencriptar
        String token = encryption.decrypt(encrypted);
        
        return token;
    }
}
```

---

**Documento:** SECURITY_INTEGRATION_GUIDE.md
**Versión:** 1.1
**Última Actualización:** 31 de Marzo de 2026

