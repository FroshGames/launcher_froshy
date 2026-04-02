# Variables de Entorno - Launcher_Mialu

## Autenticación Microsoft OAuth

### MIALU_MS_CLIENT_ID
**Descripción:** Client ID de Azure AD para autenticación con Microsoft

**Valores:**
- `389b1b32-b5d5-43b2-bf1a-76cb27cae1e1` (Público - Optimizado para apps de escritorio)
- Cualquier Client ID personalizado registrado en Azure

**Uso:**
```bash
# Windows (PowerShell)
$env:MIALU_MS_CLIENT_ID = "389b1b32-b5d5-43b2-bf1a-76cb27cae1e1"

# Windows (CMD)
set MIALU_MS_CLIENT_ID=389b1b32-b5d5-43b2-bf1a-76cb27cae1e1

# Linux/Mac
export MIALU_MS_CLIENT_ID="389b1b32-b5d5-43b2-bf1a-76cb27cae1e1"

# Java Properties
java -Dmialu.ms.clientId=389b1b32-b5d5-43b2-bf1a-76cb27cae1e1 -jar launcher_mialu.jar
```

**Notas:**
- Si no se especifica, usa el Client ID público optimizado para desktop
- Debe estar registrado en Azure con el redirect_uri correcto
- Se busca en orden: MIALU_MS_CLIENT_ID → FROSHY_MS_CLIENT_ID → propiedad Java → Default
- Si tienes problemas de "first party application", usa una cuenta Microsoft personal (no corporativa)

---

### MIALU_MS_CLIENT_SECRET (Opcional)
**Descripción:** Client Secret para Client IDs personalizados

**Uso:**
```bash
export MIALU_MS_CLIENT_SECRET="tu-client-secret"
java -jar launcher_mialu.jar
```

**Notas:**
- Solo necesario si usas un Client ID personalizado con secret
- No recomendado incluirlo en scripts de ejecución
- Usa variables de entorno del sistema en su lugar

---

## Configuración de API

### MIALU_API_PORT
**Descripción:** Puerto en el que escucha el API interno

**Valor por defecto:** `7878`

**Uso:**
```bash
export MIALU_API_PORT=8080
java -jar launcher_mialu.jar
```

**Notas:**
- El puerto debe estar disponible
- Si usas Client ID personalizado con redirect_uri registrado en otro puerto, actualiza esto
- Se busca en orden: MIALU_API_PORT → FROSHY_API_PORT → 7878

---

## Actualizaciones

### MIALU_UPDATE_METADATA_URL
**Descripción:** URL del servidor de metadata de actualizaciones

**Uso:**
```bash
export MIALU_UPDATE_METADATA_URL="https://mialu.example.com/updates.json"
java -jar launcher_mialu.jar
```

**Notas:**
- Vacío por defecto (sin actualizaciones automáticas)
- Debe ser una URL accesible

---

## Variables Heredadas (Deprecadas)

Las siguientes variables aún funcionan pero están deprecadas:

| Variable | Nueva Variable | Notas |
|----------|---|---|
| `FROSHY_MS_CLIENT_ID` | `MIALU_MS_CLIENT_ID` | Se busca como fallback |
| `FROSHY_API_PORT` | `MIALU_API_PORT` | Se busca como fallback |
| `FROSHY_LAUNCHER_VERSION` | `MIALU_LAUNCHER_VERSION` | Se busca como fallback |

**Ejemplo de fallback:**
```bash
# Si MIALU_MS_CLIENT_ID no está definido, se busca FROSHY_MS_CLIENT_ID
export FROSHY_MS_CLIENT_ID="04b07795-8ddb-461a-bbee-02f9e1bf7b46"
java -jar launcher_mialu.jar
```

---

## Ejemplos de Configuración

### 1. Configuración Mínima (Recomendada)
```bash
# Sin configuración, todo usa valores por defecto
java -jar launcher_mialu.jar
```

### 2. Client ID Personalizado
```bash
export MIALU_MS_CLIENT_ID="your-custom-client-id"
export MIALU_API_PORT=7878
java -jar launcher_mialu.jar
```

### 3. Configuración Completa
```bash
export MIALU_MS_CLIENT_ID="your-custom-client-id"
export MIALU_MS_CLIENT_SECRET="your-client-secret"
export MIALU_API_PORT=7878
export MIALU_UPDATE_METADATA_URL="https://updates.example.com/metadata.json"
export MIALU_LAUNCHER_VERSION="1.0.0"
java -jar launcher_mialu.jar
```

### 4. Usando Java Properties
```bash
java \
  -Dmialu.ms.clientId="your-custom-client-id" \
  -Dmialu.api.port=7878 \
  -jar launcher_mialu.jar
```

### 5. Configuración del Sistema Windows
```cmd
REM Configurar permanentemente en el sistema
setx MIALU_MS_CLIENT_ID "your-custom-client-id"
setx MIALU_API_PORT "7878"

REM Luego, simplemente ejecuta
java -jar launcher_mialu.jar
```

### 6. Configuración del Sistema Linux/Mac
```bash
# En ~/.bashrc o ~/.zshrc
export MIALU_MS_CLIENT_ID="your-custom-client-id"
export MIALU_API_PORT="7878"

# Luego ejecuta en nuevo terminal
java -jar launcher_mialu.jar
```

---

## Verificación

Para verificar que las variables se están usando correctamente:

```bash
# Mostrar variables de entorno actuales
echo $MIALU_MS_CLIENT_ID
echo $MIALU_API_PORT

# Iniciar launcher con verbose
java -Xms256m -Xmx1024m -jar launcher_mialu.jar
```

El launcher mostrará en la consola qué valores está usando.

---

## Seguridad

⚠️ **Importante:**
- Nunca incluyas el `MIALU_MS_CLIENT_SECRET` en repositorios públicos
- Usa secretos del sistema operativo o gestores de secretos
- En producción, usa variables de entorno del sistema, no hardcodeadas

```bash
# ❌ MAL (No hagas esto)
java -Dmialu.ms.clientSecret="actual-secret" -jar launcher_mialu.jar

# ✅ BIEN (Usa variables de entorno)
export MIALU_MS_CLIENT_SECRET="from-secure-source"
java -jar launcher_mialu.jar

# ✅ MEJOR (Usa gestores de secretos)
# Ansible, Docker Secrets, Vault, etc.
```

---

## Orden de Búsqueda

Para cada variable, el launcher busca en el siguiente orden:

1. Variable de entorno específica (`MIALU_*`)
2. Variable de entorno heredada (`FROSHY_*`)
3. Propiedad del sistema Java (`-D` flags)
4. Archivo de configuración (si existe)
5. Valor por defecto

**Ejemplo para MIALU_MS_CLIENT_ID:**
```
1. System.getenv("MIALU_MS_CLIENT_ID")
2. System.getenv("FROSHY_MS_CLIENT_ID")
3. System.getProperty("mialu.ms.clientId")
4. Valor hardcodeado: "04b07795-8ddb-461a-bbee-02f9e1bf7b46"
```

---

## Troubleshooting

### Variable no se aplica
1. Verificar que se estableció correctamente: `echo $MIALU_API_PORT`
2. Reiniciar terminal después de cambiar variables de sistema
3. Usar comillas si el valor contiene espacios: `MIALU_MS_CLIENT_ID="value with spaces"`

### Conflicto entre variable heredada y nueva
- La variable nueva (`MIALU_*`) tiene prioridad
- Si necesitas usar la variable heredada, desdefine la nueva: `unset MIALU_MS_CLIENT_ID`

### Variables no se heredan en subprocesos
- Usa `export` en Linux/Mac: `export MIALU_API_PORT=8080`
- En Windows, usa `setx` para persistencia entre sesiones

---

**Última actualización:** 31/03/2026
**Versión del Launcher:** launcher_mialu 1.0+

