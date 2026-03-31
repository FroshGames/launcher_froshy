# 🔬 Referencia Técnica - OAuth Microsoft en Launcher_Mialu

## 📋 Arquitectura de Autenticación

```
┌─────────────────────────────────────────────────────────────┐
│                    LAUNCHER_MIALU                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         MicrosoftAuthService (Principal)             │  │
│  │  • Gestiona el flujo de autenticación OAuth 2.0     │  │
│  │  • PKCE (Proof Key for Code Exchange)              │  │
│  │  • Almacenamiento de tokens cifrados               │  │
│  └──────┬───────────────────────────────────────────────┘  │
│         │                                                    │
│         ├──► buildRedirectUri()                             │
│         │    • Detecta Client ID                           │
│         │    • Selecciona puerto (3000 o configurado)      │
│         │                                                    │
│         ├──► startBrowserLogin()                            │
│         │    • Genera code challenge (PKCE)               │
│         │    • Construye URL de autorización              │
│         │    • Retorna al UI para abrir navegador         │
│         │                                                    │
│         └──► completeBrowserLogin()                         │
│              • Espera callback en servidor                 │
│              • Procesa datos de callback                   │
│              • Intercambia código por tokens              │
│              • Autentica Xbox Live                        │
│              • Obtiene perfil de Minecraft               │
│              • Persiste sesión                           │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │   MicrosoftOAuthCallbackServer (En puerto 3000)     │  │
│  │  • Escucha GET requests en http://localhost:3000/  │  │
│  │  • Captura parámetros: code, state, error          │  │
│  │  • Devuelve HTML al navegador                      │  │
│  │  • Notifica al launcher del resultado              │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         MicrosoftAuthStore (Almacenamiento)         │  │
│  │  • Guarda tokens cifrados en disco                  │  │
│  │  • Carga sesiones persistentes                      │  │
│  │  • Maneja limpieza de sesiones expiradas            │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔑 Conceptos Clave

### OAuth 2.0 Authorization Code Flow

```
1. USER_ACTION
   └─► startBrowserLogin()
       • Generate state, code_verifier
       • Build authorization URL
       • Return (operationId, authUrl, expiresAt)

2. UI_OPENS_BROWSER
   └─► Desktop.browse(authUrl)

3. MICROSOFT_AUTHORIZATION
   │
   └─► User logs in to Microsoft
       └─► User authorizes launcher
           └─► Microsoft redirects to callback

4. CALLBACK_RECEIVED
   └─► MicrosoftOAuthCallbackServer receives GET request
       • Extract: code, state, error
       • Validate state matches
       • Return HTML response

5. CODE_EXCHANGE
   └─► completeBrowserLogin(operationId)
       • Retrieve pending operation
       • Exchange code for token
       POST https://login.microsoftonline.com/oauth2/v2.0/token
       Parameters:
         - grant_type: authorization_code
         - client_id: Client ID
         - code: Authorization code
         - redirect_uri: http://localhost:3000/
         - code_verifier: PKCE verifier
         - scope: XboxLive.signin offline_access

6. TOKEN_RESPONSE
   └─► Receive from Microsoft
       • access_token (short-lived)
       • refresh_token (long-lived)
       • expires_in (seconds)

7. XBOX_AUTHENTICATION
   └─► POST https://user.auth.xboxlive.com/user/authenticate
       • d={msAccessToken}
       └─► Get XBL token and UHS

8. XSTS_AUTHENTICATION
   └─► POST https://xsts.auth.xboxlive.com/xsts/authorize
       • UserTokens: [xblToken]
       └─► Get XSTS token

9. MINECRAFT_LOGIN
   └─► POST https://api.minecraftservices.com/authentication/login_with_xbox
       • identityToken: XBL3.0 x={userHash};{xstsToken}
       └─► Get Minecraft access token

10. PROFILE_FETCH
    └─► GET https://api.minecraftservices.com/minecraft/profile
        Authorization: Bearer {mcAccessToken}
        └─► Get player profile (name, uuid)

11. PERSISTENCE
    └─► MicrosoftAuthStore.save()
        • Store tokens encrypted
        • Store profile info
        • Store expiration times

12. SUCCESS
    └─► MicrosoftSessionStatus.connected()
        • playerName
        • playerUuid
        • accessToken
        • expiresAt
```

---

## 🔐 Seguridad: PKCE (Proof Key for Code Exchange)

### Problema que Resuelve
Previene ataques de autorización interceptando el código de autorización.

### Implementación

```
CLIENT (Launcher)
├─ Generate random codeVerifier (64 bytes)
│  └─ codeVerifier = base64url(secureRandom(64))
│
├─ Compute codeChallenge from codeVerifier
│  └─ codeChallenge = base64url(SHA256(codeVerifier))
│
└─ Send in authorization request
   Authorization URL parameters:
   ├─ code_challenge: {codeChallenge}
   └─ code_challenge_method: S256 (SHA256)

MICROSOFT (OAuth Server)
├─ Store code_challenge temporarily
│
└─ On authorization code redemption:
   ├─ Receive code_verifier from client
   ├─ Compute SHA256(code_verifier)
   ├─ Compare with stored code_challenge
   └─ If mismatch → Reject request

ATTACKER CANNOT
├─ Intercept the code
│  └─ Even with code, can't execute without codeVerifier
│
└─ Forge the codeVerifier
   └─ Can't reverse SHA256
```

---

## 📡 Flujo de Puertos

```
USER BROWSER
    ↓
    └─► https://login.microsoftonline.com:443
        (Microsoft OAuth endpoint)
        ↓
        └─► Redirect to http://localhost:3000/
            (Public Client ID)
            OR
            http://localhost:7878/
            (Custom Client ID)
            ↓
LAUNCHER (MicrosoftOAuthCallbackServer)
    ├─ Port 3000 (for public Client ID)
    │  └─ Listening on http://0.0.0.0:3000
    │
    └─ Port 7878 (for custom Client ID)
       └─ Listening on http://0.0.0.0:7878
           (via InternalApiServer)
```

---

## 🗃️ Almacenamiento de Datos

### StoredMicrosoftSession (Cifrado en Disco)

```java
record StoredMicrosoftSession(
    String refreshToken,           // Long-lived token
    String minecraftAccessToken,   // Minecraft API token
    String xuid,                   // Xbox Live ID
    String playerName,             // Minecraft player name
    String playerUuid,             // Minecraft UUID
    Instant minecraftExpiresAt     // Token expiration
)
```

**Ubicación:** `~/.mialu-launcher/microsoft-session.json` (Cifrado)

**Ciclo de Vida:**
1. Se crea después de login exitoso
2. Se persiste en disco cifrado
3. Se carga en la siguiente sesión
4. Se usa para renovar tokens automáticamente
5. Se elimina al logout

---

## ⏱️ Timeouts y Expiración

```
LOGIN_EXPIRY_SECONDS = 300  // 5 minutes

┌─────────────────────────────────────────┐
│ completeBrowserLogin() called           │
├─────────────────────────────────────────┤
│ Waiting for callback...                 │
│ ├─ 0s: User not yet logged in          │
│ ├─ 60s: User logging in                │
│ ├─ 120s: User authorizing              │
│ ├─ 150s: Microsoft redirecting         │
│ ├─ 160s: Callback received ✓          │
│ └─ 160s: Token exchange ✓             │
│                                         │
│ OR                                      │
│                                         │
│ ├─ 300s: Timeout reached               │
│ └─ ✗ Login failed                      │
└─────────────────────────────────────────┘

MINECRAFT_TOKEN_EXPIRY
├─ Server response: expires_in (seconds)
├─ Add 30 second buffer for safety
└─ Renew if now + 30s > expiresAt
```

---

## 🔍 Estados de Sesión

```
DISCONNECTED
├─ No hay sesión activa
├─ XBOX account no autenticada
└─ No hay acceso a Minecraft Premium
    ↓ [Clic en "Login"]
    ↓
CONNECTING
├─ Esperando callback de Microsoft
├─ Navegador abierto
└─ 5 minutos de timeout
    ↓ [Usuario completa login]
    ↓
CONNECTED
├─ Token obtenido
├─ Perfil sincronizado
├─ Acceso a Minecraft Premium
└─ Token se renueva automáticamente
    ↓ [Logout O Token expira]
    ↓
DISCONNECTED (Vuelve al inicio)
```

---

## 🐛 Manejo de Errores

### Error Scenarios

```
SCENARIO 1: Invalid redirect_uri
├─ Causa: Puerto no coincide con registrado en Azure
├─ Solución: buildRedirectUri() selecciona correctamente
└─ Result: ✓ Automatic selection based on Client ID

SCENARIO 2: Timeout
├─ Causa: Usuario no completa login en 5 minutos
├─ Resultado: IllegalStateException("Timeout en login Microsoft")
└─ Solución: Usuario puede intentar nuevamente

SCENARIO 3: User Cancels
├─ Causa: Usuario hace clic en "Cancel" en pantalla de login
├─ Parámetro: error=access_denied
└─ Resultado: IllegalStateException con detalles de cancelación

SCENARIO 4: Missing Permission
├─ Causa: Usuario no tiene licencia de Minecraft
├─ Resultado: IllegalStateException("No licencia de Minecraft")
└─ Solución: Comprar licencia en store.minecraft.net

SCENARIO 5: Network Error
├─ Causa: No hay conexión a Internet
├─ Resultado: InterruptedException
└─ Solución: Verificar conexión e intentar nuevamente

SCENARIO 6: Port Already Bound
├─ Causa: Puerto 3000 ya en uso
├─ Resultado: IllegalStateException
└─ Solución: Usar puerto diferente o Client ID personalizado
```

---

## 📊 Clases Principales

### MicrosoftAuthService
**Responsabilidad:** Orquestar todo el flujo OAuth

**Métodos Públicos:**
```java
MicrosoftBrowserLogin startBrowserLogin()
MicrosoftSessionStatus completeBrowserLogin(String operationId)
MicrosoftSessionStatus getSessionStatus()
void logout()
Optional<LaunchAuth> resolveLaunchAuth()
String handleBrowserCallback(state, code, error, error_description)
```

### MicrosoftOAuthCallbackServer
**Responsabilidad:** Servidor HTTP para recibir callbacks

**Métodos Públicos:**
```java
void start()
void stop()
Optional<OAuthCallbackData> waitForCallback(long timeoutMs)
```

### MicrosoftAuthStore
**Responsabilidad:** Persistencia de sesiones

**Métodos:**
```java
void save(StoredMicrosoftSession session)
Optional<StoredMicrosoftSession> load()
void clear()
```

---

## 🧪 Endpoints OAuth Utilizados

```
GET https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize
    └─► Presentar UI de login a usuario

POST https://login.microsoftonline.com/consumers/oauth2/v2.0/token
    ├─ client_id
    ├─ code
    ├─ redirect_uri
    ├─ code_verifier (PKCE)
    └─► access_token, refresh_token

POST https://user.auth.xboxlive.com/user/authenticate
    ├─ msAccessToken
    └─► xblToken, userHash

POST https://xsts.auth.xboxlive.com/xsts/authorize
    ├─ xblToken
    └─► xstsToken

POST https://api.minecraftservices.com/authentication/login_with_xbox
    ├─ xstsToken
    └─► minecraftAccessToken

GET https://api.minecraftservices.com/minecraft/profile
    ├─ Authorization: Bearer {minecraftAccessToken}
    └─► playerName, playerUuid
```

---

## 🔄 Diagrama de Clases

```
MicrosoftAuthService
├─ httpClient: HttpClient
├─ mapper: ObjectMapper
├─ store: MicrosoftAuthStore
├─ clientId: String
├─ redirectUri: String
├─ callbackServer: MicrosoftOAuthCallbackServer
├─ pendingByState: Map<String, PendingBrowserLogin>
├─ pendingByOperation: Map<String, PendingBrowserLogin>
└─ session: StoredMicrosoftSession

    ├─ startBrowserLogin(): MicrosoftBrowserLogin
    ├─ completeBrowserLogin(operationId): MicrosoftSessionStatus
    ├─ handleBrowserCallback(...): String (HTML)
    ├─ getSessionStatus(): MicrosoftSessionStatus
    ├─ logout(): void
    ├─ resolveLaunchAuth(): Optional<LaunchAuth>
    │
    ├─ (private) buildRedirectUri(config): String
    ├─ (private) readClientId(): String
    ├─ (private) processBrowserLoginData(...): MicrosoftSessionStatus
    ├─ (private) exchangeAndPersist(...): MicrosoftSessionStatus
    │
    ├─ (private) authenticateXbox(...): XboxAuth
    ├─ (private) authenticateMinecraft(...): MinecraftAuth
    ├─ (private) fetchMinecraftProfile(...): MinecraftProfile
    │
    ├─ (private) postForm(flow, params): JsonNode
    ├─ (private) postJson(uri, payload): JsonNode
    ├─ (private) getJsonWithBearer(uri, token): JsonNode
    │
    └─ ... (otros métodos auxiliares)
```

---

## 🚀 Performance

### Benchmarks (Estimado)

| Operación | Tiempo |
|-----------|--------|
| startBrowserLogin() | <100ms |
| Navegador abre | 1-3s |
| Usuario login | 10-30s (variable) |
| completeBrowserLogin() | 2-5s |
| Token exchange | 500-1000ms |
| Xbox auth | 300-500ms |
| Minecraft profile | 200-300ms |
| **Total** | **5-10 minutos** (incl. UI) |

---

## 🔗 Referencias Externas

- [OAuth 2.0 Authorization Framework](https://tools.ietf.org/html/rfc6749)
- [PKCE (RFC 7636)](https://tools.ietf.org/html/rfc7636)
- [Microsoft OAuth 2.0](https://learn.microsoft.com/oauth)
- [Xbox Live Authentication](https://learn.microsoft.com/xbox/api)
- [Minecraft Java API](https://wiki.vg)

---

**Documento Técnico:** Launcher_Mialu v1.0
**Fecha:** 31 de Marzo de 2026
**Precisión:** 100% actualizado al código

