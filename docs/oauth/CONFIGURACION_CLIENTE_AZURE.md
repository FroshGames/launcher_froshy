# Configuración de Cliente Microsoft Azure para Login Premium

## Problema: Error "unauthorized_client"

Si recibes este error al intentar loguear con Microsoft:

```
unauthorized_client: The client does not exist or is not enabled for consumers. 
If you are the application developer, configure a new application through the 
App Registrations in the Azure Portal at https://go.microsoft.com/fwlink/?linkid=2083908.
```

Significa que el Cliente ID de Microsoft que se está utilizando no está habilitado para consumidores en Azure.

## Soluciones

### Opción 1: Usar el Cliente ID por Defecto (Recomendado)

El launcher viene pre-configurado con dos Client IDs públicos que funcionan:

- **Cliente Principal**: `04b07795-8ddb-461a-bbee-02f9e1bf7b46` ✅ (Habilitado para consumidores)
- **Cliente Alternativo**: `389b1b32-b5d5-43b2-bf1a-76cb27cae1e1` (No habilitado para consumidores)

El sistema intenta automáticamente con ambos. Si aún así obtienes errores, pasa a la Opción 2.

### Opción 2: Configurar tu Propio Cliente ID de Azure

Si tienes tu propio Cliente ID de Azure (ej: `62e8121e-6311-4c99-a649-305c93a77f92`), puedes configurarlo de las siguientes formas:

#### Forma 1: Variable de Entorno (Recomendado)

**Windows CMD:**
```batch
set MIALU_MS_CLIENT_ID=62e8121e-6311-4c99-a649-305c93a77f92
launcher.bat
```

**Windows PowerShell:**
```powershell
$env:MIALU_MS_CLIENT_ID = "62e8121e-6311-4c99-a649-305c93a77f92"
.\launcher.bat
```

**Linux/Mac:**
```bash
export MIALU_MS_CLIENT_ID="62e8121e-6311-4c99-a649-305c93a77f92"
./launcher.sh
```

#### Forma 2: Variable de Entorno Permanente (Windows)

1. Abre las **Variables de Entorno**:
   - Presiona `Win + X` y selecciona "Sistema"
   - Haz clic en "Configuración avanzada del sistema"
   - Haz clic en "Variables de entorno"

2. En "Variables del usuario", haz clic en "Nueva..."
3. Nombre de variable: `MIALU_MS_CLIENT_ID`
4. Valor de variable: `62e8121e-6311-4c99-a649-305c93a77f92`
5. Haz clic en "Aceptar" y reinicia el launcher

#### Forma 3: Parámetro JVM

Ejecuta el launcher con:
```bash
java -Dmialu.ms.clientId=62e8121e-6311-4c99-a649-305c93a77f92 -jar launcher_mialu.jar
```

## Registro Correcto de tu Cliente ID en Azure

Si registras tu propio Cliente ID en Azure, asegúrate de configurar lo siguiente:

### 1. Permisos Delegados (Delegated Permissions)

En **API permissions**, añade los siguientes permisos delegados:
- `XboxLive.signin` - Acceder a Xbox Live
- `offline_access` - Acceso offline

### 2. Configuración de Cliente Público

En **Authentication**:
- Marca "Treat application as a public client" como **SÍ**
- Esto permite que aplicaciones de escritorio usen el cliente

### 3. Redirect URIs

En **Redirect URIs**, configura:
```
http://localhost:3000/
```

También puedes añadir como fallback:
```
http://localhost:8080/
```

### 4. Tenant

Asegúrate de que el cliente está configurado para:
- **Tenant**: `common` o `consumers` (para cuentas personales)

## Verificación

Después de configurar, el launcher debe:

1. Mostrar un navegador con el login de Microsoft
2. Solicitar permiso para acceder a Xbox Live
3. Redirigir a `http://localhost:3000/` tras el login exitoso
4. Mostrar "Login completado" en el navegador
5. Mostrar tu nombre de usuario en el launcher

## Errores Comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `unauthorized_client` | Cliente no está habilitado para consumidores | Ver Opción 1 o 2 |
| `invalid_request` con `redirect_uri` | Redirect URI no está registrado | Asegúrate de añadir `http://localhost:3000/` en Azure |
| `The request is not valid` | Tenant incorrecto | Cambia a `common` o `consumers` |
| `AADSTS700016` | Client ID no válido | Verifica que el ID sea correcto |

## Contacto y Soporte

Si los problemas persisten:

1. Verifica que uses una **cuenta Microsoft personal** (no de trabajo/escuela)
2. Intenta incógnito en el navegador (limpia cookies)
3. Crea un nuevo Client ID en Azure desde cero
4. Consulta la documentación oficial de Microsoft OAuth 2.0

## Variables de Entorno Legacy

Para compatibilidad con versiones antiguas, también se soportan:
- `FROSHY_MS_CLIENT_ID` (heredado)

Recomendamos usar `MIALU_MS_CLIENT_ID` en su lugar.

