# Solución del Error de Login con Microsoft - 31/03/2026

## Problema Identificado

El error `Invalid_request: The provided value for the input parameter 'redirect_uri' is not valid` ocurría porque:

1. El Client ID público de Microsoft (`04b07795-8ddb-461a-bbee-02f9e1bf7b46`) está registrado con `redirect_uri = http://localhost:3000/`
2. El launcher intentaba usar `http://localhost:7878/` (el puerto del API interno)
3. Microsoft rechazaba el redirect_uri porque no coincidía

## Soluciones Implementadas

### 1. Servidor OAuth Callback Temporal (Puerto 3000)
Se creó `MicrosoftOAuthCallbackServer.java` que:
- Abre un servidor HTTP temporal en el puerto 3000
- Recibe el callback de Microsoft con el código de autorización
- Se inicializa automáticamente cuando se usa el Client ID público

### 2. Lógica Inteligente de Redirect URI
En `MicrosoftAuthService.java`:
- Si usas el **Client ID público** (por defecto): usa `http://localhost:3000/`
- Si usas un **Client ID personalizado**: usa `http://localhost:7878/` (o el puerto configurado)

### 3. Flujo de Autenticación Automático
- Usuario hace clic en "Login con Microsoft"
- Se abre el navegador automáticamente
- Microsoft redirige a `http://localhost:3000/` con el código
- El servidor captura el callback
- El launcher completa la autenticación automáticamente
- La sesión Premium se sincroniza

## Cómo Usar

### Opción 1: Client ID Público (Recomendado - Sin configuración)
El launcher usa automáticamente el Client ID público de Microsoft. No requiere configuración:
```bash
# Simplemente ejecuta el launcher
java -jar launcher_mialu.jar
```

### Opción 2: Client ID Personalizado (Avanzado)
Si quieres usar un Client ID personalizado registrado en Azure:

```bash
# Windows (PowerShell)
$env:MIALU_MS_CLIENT_ID="tu-client-id-aqui"
java -jar launcher_mialu.jar

# Linux/Mac
export MIALU_MS_CLIENT_ID="tu-client-id-aqui"
java -jar launcher_mialu.jar

# O usa variables de entorno del sistema
setx MIALU_MS_CLIENT_ID "tu-client-id-aqui"
```

## Cambios en el Código

### Archivos Modificados
- `MicrosoftAuthService.java`: Lógica de redirect_uri inteligente y procesamiento de callbacks
- `LauncherConfig.java`: Sin cambios (compatible)
- `InternalApiServer.java`: Sin cambios (compatible)

### Archivos Creados
- `MicrosoftOAuthCallbackServer.java`: Servidor temporal para callbacks OAuth

## Pruebas Realizadas

✅ Compilación: Exitosa sin errores
✅ Flujo de Login: Validado
✅ Redirect URI: Configurado correctamente para el Client ID público

## Características

- ✅ Navegador se abre automáticamente
- ✅ Callback se procesa automáticamente en puerto 3000
- ✅ Sincronización de sesión Premium
- ✅ Soporte para Client IDs personalizados
- ✅ Manejo de errores completo
- ✅ Compatible con ambientes offline

## Notas

- El puerto 3000 debe estar disponible. Si está en uso, configura un Client ID personalizado
- Si el navegador no se abre automáticamente, puedes copiar la URL de la consola
- El callback server se ejecuta durante el tiempo de espera del login (5 minutos por defecto)

## Troubleshooting

### "Puerto 3000 ya está en uso"
```bash
# Opción 1: Registra un Client ID personalizado con otro puerto
# Opción 2: Cierra la aplicación que usa el puerto 3000
```

### "La sesión expira sin completar el login"
- Verifica tu conexión a internet
- El timeout predeterminado es de 5 minutos
- Intenta nuevamente

### "No se abre el navegador automáticamente"
- El launcher mostrará la URL en la consola
- Cópiala y pégala manualmente en tu navegador
- El callback se procesará automáticamente

## Actualización Futura

Para mayor seguridad, se recomienda:
1. Registrar un Application ID personalizado en Azure
2. Configurarlo con MIALU_MS_CLIENT_ID
3. Usar un puerto diferente según tus necesidades

