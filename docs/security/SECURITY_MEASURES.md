# 🔒 MEDIDAS DE SEGURIDAD - Launcher_Mialu v1.1

**Versión:** 1.1
**Fecha:** 31 de Marzo de 2026
**Clasificación:** Documentación Confidencial

---

## 🎯 OBJETIVO DE SEGURIDAD

Implementar múltiples capas de protección contra:
- ✅ Ataques de fuerza bruta
- ✅ Inyecciones (SQL, Command, XSS)
- ✅ Suplantación de identidad (Spoofing)
- ✅ Robos de tokens/credenciales
- ✅ Ataques Man-in-the-Middle (MITM)
- ✅ Acceso no autorizado
- ✅ Modificación de datos
- ✅ Denegación de servicio (DoS)

---

## 🛡️ COMPONENTES DE SEGURIDAD

### 1. SecurityManager.java
**Propósito:** Gestor centralizado de políticas de seguridad

**Funcionalidades:**
- Validación de redirect_uri
- Validación de tokens
- Rate limiting para login
- Gestión de permisos de archivos
- Validación de tamaño de solicitud

**Políticas:**
- Máximo 5 intentos de login por IP/usuario
- Lockout de 15 minutos después de superar intentos
- Máximo tamaño de token: 10 KB
- Máximo tamaño de solicitud: 1 MB
- Puertos permitidos: 3000, 7878, 8080, 8443
- Hosts permitidos: localhost (127.0.0.1, ::1, etc.)

---

### 2. RateLimiter.java
**Propósito:** Protección contra ataques de fuerza bruta

**Características:**
- Tracking de intentos por usuario/IP
- Lockout automático después de N intentos
- Reset automático después de timeout
- Almacenamiento en memoria (thread-safe)

**Configuración:**
```
Max Attempts: 5
Lockout Duration: 15 minutos
```

**Ejemplo:**
```
Usuario intenta login 5 veces en 1 minuto
↓
Sistema bloquea por 15 minutos
↓
Usuario debe esperar antes de reintentar
```

---

### 3. InputValidator.java
**Propósito:** Validación y sanitización de entrada

**Tipos de Validación:**
- **Username:** 3-32 caracteres, alphanumériques + _ - .
- **Email:** RFC compliant
- **URL:** Solo http/https, caracteres seguros
- **Token:** JWT-like format, 50-10000 bytes
- **Safe String:** Caracteres alfanuméricos básicos

**Sanitización (XSS Protection):**
- Reemplaza `<` con `&lt;`
- Reemplaza `>` con `&gt;`
- Reemplaza `&` con `&amp;`
- Etc.

**Ejemplo:**
```java
// Input malicioso
String input = "<script>alert('xss')</script>";

// Sanitizado
String safe = validator.sanitize(input);
// Resultado: "&lt;script&gt;alert(&#x27;xss&#x27;)&lt;&#x2F;script&gt;"
```

---

### 4. SecurityAuditLogger.java
**Propósito:** Auditoría y tracking de eventos de seguridad

**Eventos Logged:**
- Intentos de autenticación (exitosos/fallidos)
- Acceso a recursos sensibles
- Violaciones de política
- Excepciones de seguridad
- Intento de redirect_uri inválido
- Rate limit triggered

**Ubicación:** `~/.mialu-launcher/security-audit.log`

**Formato:**
```
[2026-03-31T14:30:00.123] AUTHENTICATION_ATTEMPT | Status: SUCCESS | Details: User: u****
[2026-03-31T14:31:15.456] LOGIN_ATTEMPT | Status: FAILED | Details: Too many attempts for: 127.0.0.1
[2026-03-31T14:32:30.789] SENSITIVE_RESOURCE_ACCESS | Status: ALLOWED | Details: Resource: tokens | User: u****
```

**Ventajas:**
- Rastrea actividades sospechosas
- Auditoría en caso de brechas
- Análisis de patrones de ataque
- Cumplimiento normativo

---

### 5. TokenEncryption.java
**Propósito:** Encriptación de tokens en reposo

**Algoritmo:** AES-256 (Advanced Encryption Standard)

**Características:**
- Clave de 256 bits
- IV (Initialization Vector) único por encriptación
- Base64 encoding para almacenamiento
- Limpieza de memoria segura

**Flujo:**
```
Token en memoria
    ↓
Encriptar con AES-256
    ↓
Agregar IV al inicio
    ↓
Base64 encode
    ↓
Almacenar en disco
    ========================================
Leer de disco
    ↓
Base64 decode
    ↓
Extraer IV y datos encriptados
    ↓
Desencriptar con AES-256
    ↓
Token en memoria
```

**Seguridad:**
- Tokens nunca se guardan en plano
- Clave no se expone
- IV previene patrones
- Limpieza de memoria después de usar

---

## 🔐 MECANISMOS DE PROTECCIÓN

### Protección contra Inyección SQL
```java
// ❌ INSEGURO
String query = "SELECT * FROM users WHERE username = '" + username + "'";

// ✅ SEGURO
String query = "SELECT * FROM users WHERE username = ?";
preparedStatement.setString(1, username);
```

**Implementado en:** Validador de entrada y queries seguras

---

### Protección contra XSS (Cross-Site Scripting)
```java
// ❌ Vulnerable
response.send("<h1>" + userInput + "</h1>");

// ✅ Seguro
String safe = validator.sanitize(userInput);
response.send("<h1>" + safe + "</h1>");
```

**Implementado en:** InputValidator.sanitize()

---

### Protección contra CSRF (Cross-Site Request Forgery)
```java
// Token único por sesión
String csrfToken = generateRandomToken();

// Validar en cada solicitud
if (!request.getParameter("csrf_token").equals(csrfToken)) {
    throw new SecurityException("CSRF token inválido");
}
```

**Implementado en:** MicrosoftAuthService (state parameter)

---

### Protección contra MITM (Man-in-the-Middle)
```java
// ✅ Solo HTTPS para endpoints sensibles
if (!request.isSecure()) {
    throw new SecurityException("Solo HTTPS permitido");
}

// ✅ Validación de certificados
HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
conn.setHostnameVerifier((hostname, session) -> {
    // Validar certificado
    return validateCertificate(session);
});
```

**Implementado en:** HTTP client configuration

---

### Protección contra Fuerza Bruta
```java
// ❌ Sin protección
for (int i = 0; i < 1000; i++) {
    tryLogin(username, password);  // Ataque posible
}

// ✅ Con Rate Limiting
if (!rateLimiter.canAttempt(identifier)) {
    throw new RateLimitException("Demasiados intentos");
}
login();
```

**Implementado en:** RateLimiter (máx 5 intentos, lockout 15 min)

---

## 📋 POLÍTICAS DE SEGURIDAD

### Política 1: Validación de Entrada
- **Regla:** Todo input debe ser validado
- **Excepción:** Ninguna
- **Implementación:** InputValidator

```
Input → Validate → Sanitize → Use
```

---

### Política 2: Encriptación de Datos Sensibles
- **Regla:** Tokens y credenciales encriptados en disco
- **Algoritmo:** AES-256
- **Excepto:** Datos en memoria durante procesamiento

---

### Política 3: Rate Limiting
- **Regla:** Máximo 5 intentos de login por usuario
- **Castigo:** Lockout de 15 minutos
- **Reset:** Automático tras timeout

---

### Política 4: Auditoría y Logging
- **Regla:** Todos los eventos de seguridad logged
- **Ubicación:** security-audit.log
- **Rotación:** Diaria (no implementado aún)

---

### Política 5: Gestión de Sesiones
- **Duración:** Máx 5 horas
- **Inactividad:** 30 minutos
- **Renovación:** Automática si < 30 min para expirar

---

### Política 6: Permisos de Archivos
- **Config files:** 600 (solo propietario)
- **Log files:** 644 (lectura pública, escritura propietario)
- **Token files:** 600 (solo propietario)

---

## 🚨 MONITOREO DE SEGURIDAD

### Alertas Generadas
```
❌ SECURITY_ALERT (Email inmediato):
  - 10+ intentos fallidos en 1 minuto
  - Acceso desde IP desconocida
  - Modificación de archivos de config
  - Encriptación fallida de tokens
  - Redirect_uri inválido detectado

⚠️  WARNING (Log):
  - 5+ intentos fallidos
  - Sanitización de entrada
  - Tokens próximos a expirar
  - Valores fuera de rango
```

---

## 🔑 GESTIÓN DE CREDENCIALES

### Tokens
- **Almacenamiento:** Encriptados en `~/.mialu-launcher/microsoft-session.json`
- **Duración:** Variable (según Microsoft)
- **Renovación:** Automática
- **Limpieza:** Al logout o expiración

### Passwords
- **Almacenamiento:** Nunca en disco
- **En memoria:** Borrados después de usar
- **Hashing:** Nunca necesario (OAuth)

### API Keys
- **Almacenamiento:** Variables de entorno
- **Nunca en código:** Verificación con pre-commit hooks
- **Rotación:** Trimestral

---

## 📊 LISTA DE VERIFICACIÓN DE SEGURIDAD

- [x] Validación de entrada completa
- [x] Encriptación de tokens
- [x] Rate limiting implementado
- [x] Auditoría de eventos
- [x] Sanitización XSS
- [x] CSRF protection (state param)
- [x] Gestión de permisos de archivos
- [x] Limpieza de memoria segura
- [x] Validación de redirect_uri
- [x] Logging centralizado
- [ ] Certificate pinning (próxima versión)
- [ ] Hardware security keys (próxima versión)
- [ ] Encriptación end-to-end (próxima versión)
- [ ] 2FA (próxima versión)

---

## 🚀 PRÓXIMAS MEJORAS (v1.2)

1. **Certificate Pinning**
   - Fijar certificados de Microsoft
   - Prevenir MITM con certificados falsificados

2. **Hardware Security Keys**
   - Soporte para FIDO2/U2F
   - Protección adicional para Premium users

3. **Two-Factor Authentication (2FA)**
   - Autenticación de dos factores
   - TOTP o SMS

4. **Encriptación End-to-End**
   - E2E entre launcher y servidor
   - Protocolo TLS 1.3 mínimo

5. **IP Whitelisting**
   - Listar IPs permitidas por usuario
   - Alertas de acceso desde nuevas IPs

6. **Device Fingerprinting**
   - Identificar dispositivos conocidos
   - Alerta de nuevo dispositivo

---

## 📞 REPORTAR VULNERABILIDADES

Si encuentras una vulnerabilidad de seguridad:

1. **NO** la publiques públicamente
2. Envía reporte a security@launcher-mialu.dev
3. Incluye:
   - Descripción del problema
   - Pasos para reproducir
   - Impacto potencial
   - Solución sugerida

4. Recibirás respuesta en 48 horas
5. Crédito en changelog si lo deseas

---

## 🔗 REFERENCIAS DE SEGURIDAD

- OWASP Top 10: https://owasp.org/www-project-top-ten/
- CWE (Common Weakness Enumeration): https://cwe.mitre.org/
- NIST Cybersecurity Framework: https://www.nist.gov/cyberframework
- OAuth 2.0: https://tools.ietf.org/html/rfc6749
- PKCE: https://tools.ietf.org/html/rfc7636

---

## ✅ VALIDACIÓN DE SEGURIDAD

**Esta implementación ha sido diseñada siguiendo:**
- OWASP Top 10 Protections
- CWE/SANS Top 25
- OAuth 2.0 Best Practices
- NIST Cybersecurity Framework

**No es:** Sistema de seguridad de nivel militar
**Es:** Protección robusta contra ataques comunes

---

**Documento:** SECURITY_MEASURES.md
**Clasificación:** Documentación Técnica
**Versión:** 1.1
**Última Actualización:** 31 de Marzo de 2026

