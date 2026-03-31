# Referencia de API Interna

## Base URL

```
http://localhost:7878/internal/v1
```

**Nota**: El puerto puede cambiar según variable de entorno `MIALU_API_PORT`.

---

## 1. Health Check

Verifica el estado del servidor.

### Request

```http
GET /internal/v1/health
```

### Response

**Status**: `200 OK`

```json
{
  "status": "UP",
  "apiPort": 7878,
  "profiles": 2,
  "timestamp": "2026-03-04T04:30:00Z"
}
```

### Campos

| Campo | Tipo | Descripción |
|-------|------|-------------|
| status | string | Estado del servidor (`UP` o `DOWN`) |
| apiPort | integer | Puerto de la API |
| profiles | integer | Cantidad de perfiles cargados |
| timestamp | string | Timestamp ISO-8601 de cuando se consultó |

### Casos de Error

Prácticamente no hay errores - siempre retorna 200.

### Ejemplo curl

```bash
curl http://localhost:7878/internal/v1/health
```

---

## 2. Listar Perfiles

Obtiene todos los perfiles guardados.

### Request

```http
GET /internal/v1/profiles
```

No requiere body.

### Response

**Status**: `200 OK`

```json
[
  {
    "id": "default",
    "displayName": "Perfil Principal",
    "javaPath": "java",
    "gameVersion": "1.20.1",
    "jvmArgs": ["-Xmx2G", "-Xms1G"],
    "gameArgs": ["--username", "Steve"]
  },
  {
    "id": "pvp",
    "displayName": "Perfil PvP",
    "javaPath": "java",
    "gameVersion": "1.8.9",
    "jvmArgs": ["-Xmx1G"],
    "gameArgs": []
  }
]
```

Array vacío si no hay perfiles:
```json
[]
```

### Campos

Cada perfil es un objeto con:

| Campo | Tipo | Descripción |
|-------|------|-------------|
| id | string | Identificador único |
| displayName | string | Nombre visible |
| javaPath | string | Ruta al ejecutable java |
| gameVersion | string | Versión de Minecraft |
| jvmArgs | array[string] | Argumentos JVM |
| gameArgs | array[string] | Argumentos del juego |

### Ejemplo curl

```bash
curl http://localhost:7878/internal/v1/profiles
```

---

## 3. Crear Perfil

Crea un nuevo perfil de juego.

### Request

```http
POST /internal/v1/profiles
Content-Type: application/json

{
  "id": "builder",
  "displayName": "Perfil Builder",
  "javaPath": "java",
  "gameVersion": "1.20.1",
  "jvmArgs": ["-Xmx2G"],
  "gameArgs": ["--username", "Alex"]
}
```

### Response - Éxito

**Status**: `201 Created`

```json
{
  "id": "builder",
  "displayName": "Perfil Builder",
  "javaPath": "java",
  "gameVersion": "1.20.1",
  "jvmArgs": ["-Xmx2G"],
  "gameArgs": ["--username", "Alex"]
}
```

### Response - Error: ID Duplicado

**Status**: `400 Bad Request`

```json
{
  "error": "Ya existe un perfil con id: builder"
}
```

### Response - Error: Campo Faltante

**Status**: `400 Bad Request`

```json
{
  "error": "El id del perfil es obligatorio"
}
```

### Response - Error: Método No Permitido

**Status**: `405 Method Not Allowed`

```json
{
  "error": "Metodo no permitido"
}
```

Con header:
```
Allow: GET, POST
```

### Validaciones

- `id`: Obligatorio, único en el sistema
- `displayName`: Obligatorio, no vacío
- `javaPath`: Defaults a `"java"` si no se proporciona
- `gameVersion`: Defaults a `"1.20.1"` si no se proporciona
- `jvmArgs`: Defaults a lista vacía
- `gameArgs`: Defaults a lista vacía

### Ejemplo curl

```bash
curl -X POST http://localhost:7878/internal/v1/profiles \
  -H "Content-Type: application/json" \
  -d '{
    "id": "builder",
    "displayName": "Perfil Builder",
    "javaPath": "java",
    "gameVersion": "1.20.1",
    "jvmArgs": ["-Xmx2G"],
    "gameArgs": ["--username", "Alex"]
  }'
```

---

## 4. Lanzar Juego

Genera el comando para lanzar un perfil.

### Request

```http
POST /internal/v1/launch
Content-Type: application/json

{
  "profileId": "default",
  "demoMode": false
}
```

### Response - Éxito

**Status**: `202 Accepted`

```json
{
  "launchId": "550e8400-e29b-41d4-a716-446655440000",
  "profileId": "default",
  "commandLine": "java -Xmx2G -jar minecraft-1.20.1.jar --gameDir C:\\Users\\User\\.mialu-launcher\\game --username Steve",
  "startedAt": "2026-03-04T04:30:45Z",
  "status": "STARTED"
}
```

### Response - Error: Perfil No Encontrado

**Status**: `400 Bad Request`

```json
{
  "error": "Perfil no encontrado: unknown_id"
}
```

### Response - Error: Campo Faltante

**Status**: `400 Bad Request`

```json
{
  "error": "profileId es obligatorio"
}
```

### Campos Response

| Campo | Tipo | Descripción |
|-------|------|-------------|
| launchId | string | UUID único para este lanzamiento |
| profileId | string | ID del perfil lanzado |
| commandLine | string | Línea de comandos completa |
| startedAt | string | Timestamp ISO-8601 |
| status | string | Siempre `"STARTED"` (por ahora) |

### Nota Importante

**Actualmente SIMULADO**: No ejecuta el proceso real. El `commandLine` es generado pero no se lanza.

Ejemplo de comando generado:
```bash
java -Xmx2G -jar minecraft-1.20.1.jar --gameDir ~/.mialu-launcher/game --username Steve
```

### Ejemplo curl

```bash
curl -X POST http://localhost:7878/internal/v1/launch \
  -H "Content-Type: application/json" \
  -d '{"profileId": "default", "demoMode": false}'
```

Con demo mode:
```bash
curl -X POST http://localhost:7878/internal/v1/launch \
  -H "Content-Type: application/json" \
  -d '{"profileId": "default", "demoMode": true}'
```

---

## 5. Iniciar Descarga

Simula la descarga de un asset.

### Request

```http
POST /internal/v1/downloads
Content-Type: application/json

{
  "target": "assets"
}
```

### Response - Éxito

**Status**: `202 Accepted`

```json
{
  "downloadId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
  "target": "assets",
  "state": "QUEUED",
  "progress": 0
}
```

### Response - Error: Target Faltante

**Status**: `400 Bad Request`

```json
{
  "error": "target es obligatorio"
}
```

### Campos Response

| Campo | Tipo | Descripción |
|-------|------|-------------|
| downloadId | string | UUID para trackear esta descarga |
| target | string | Nombre del asset descargado |
| state | string | Estado actual (`QUEUED`, `IN_PROGRESS`, `DONE`) |
| progress | integer | Porcentaje 0-100 |

### Estados

```
QUEUED        → 0% completado
  ↓ (200ms)
IN_PROGRESS   → 20%, 40%, 60%, 80%
  ↓ (cada 300ms)
DONE          → 100%
```

### Ejemplo curl

```bash
curl -X POST http://localhost:7878/internal/v1/downloads \
  -H "Content-Type: application/json" \
  -d '{"target": "assets"}'
```

Respuesta con `downloadId`:
```
a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6
```

---

## 6. Obtener Estado de Descarga

Consulta el progreso de una descarga.

### Request

```http
GET /internal/v1/downloads/{downloadId}
```

Parámetro de ruta:
- `{downloadId}`: UUID retornado al iniciar descarga

### Response - Éxito

**Status**: `200 OK`

```json
{
  "downloadId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
  "target": "assets",
  "state": "IN_PROGRESS",
  "progress": 45
}
```

### Response - Error: No Encontrada

**Status**: `404 Not Found`

```json
{
  "error": "Descarga no encontrada"
}
```

### Polling Recomendado

Para monitorear descarga:

```bash
# Cada 400ms
for i in {1..10}; do
  curl http://localhost:7878/internal/v1/downloads/a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6 \
    | jq '.progress, .state'
  sleep 0.4
done
```

### Ejemplo curl

```bash
curl http://localhost:7878/internal/v1/downloads/a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6
```

---

## Códigos de Estado HTTP

| Código | Significado | Cuándo |
|--------|-------------|--------|
| 200 | OK | Health, GET downloads |
| 201 | Created | POST profiles exitoso |
| 202 | Accepted | Launch, POST downloads |
| 400 | Bad Request | Validación fallida |
| 404 | Not Found | Recurso no existe |
| 405 | Method Not Allowed | GET a POST-only, etc. |
| 500 | Internal Server Error | Excepción no capturada |

---

## Headers Importantes

### Request

```
Content-Type: application/json
```

Obligatorio para POST/PUT.

### Response

```
Content-Type: application/json; charset=utf-8
Allow: GET, POST             (si 405 Method Not Allowed)
```

---

## Ejemplos Completos

### Caso 1: Crear Perfil y Lanzar

```bash
#!/bin/bash

API="http://localhost:7878/internal/v1"

# 1. Crear perfil
echo "Creating profile..."
curl -s -X POST $API/profiles \
  -H "Content-Type: application/json" \
  -d '{
    "id": "myprofile",
    "displayName": "Mi Perfil",
    "javaPath": "java",
    "gameVersion": "1.20.1",
    "jvmArgs": ["-Xmx2G"],
    "gameArgs": ["--username", "MiUsuario"]
  }' | jq '.'

# 2. Listar perfiles
echo ""
echo "Listing profiles..."
curl -s $API/profiles | jq '.[] | .displayName'

# 3. Lanzar
echo ""
echo "Launching..."
curl -s -X POST $API/launch \
  -H "Content-Type: application/json" \
  -d '{"profileId": "myprofile", "demoMode": false}' | jq '.commandLine'
```

### Caso 2: Monitorear Descarga

```bash
#!/bin/bash

API="http://localhost:7878/internal/v1"

# 1. Iniciar descarga
DOWNLOAD=$(curl -s -X POST $API/downloads \
  -H "Content-Type: application/json" \
  -d '{"target": "assets"}' | jq -r '.downloadId')

echo "Download ID: $DOWNLOAD"

# 2. Polling
for i in {1..15}; do
  STATUS=$(curl -s $API/downloads/$DOWNLOAD)
  PROGRESS=$(echo $STATUS | jq '.progress')
  STATE=$(echo $STATUS | jq -r '.state')
  
  echo "[$i] Progress: $PROGRESS% | State: $STATE"
  
  if [ "$STATE" = "DONE" ]; then
    echo "Download complete!"
    break
  fi
  
  sleep 0.5
done
```

---

## Testing con Postman

1. **Importar en Postman**:
   - New → HTTP
   - Paste: `GET http://localhost:7878/internal/v1/health`

2. **Crear colección de requests**:
   ```
   Launcher_Mialu
   ├── Health
   ├── List Profiles
   ├── Create Profile
   ├── Launch Game
   ├── Start Download
   └── Get Download Status
   ```

3. **Variables de entorno**:
   ```json
   {
     "api_host": "localhost",
     "api_port": "7878",
     "api_base": "http://{{api_host}}:{{api_port}}/internal/v1"
   }
   ```

---

**Última actualización**: 2026-03-04  
**Versión**: 1.0








