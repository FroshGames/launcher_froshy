# 🎮 Guía de Instalación - Launcher_Mialu v1.0

## ✨ Novedades en esta Versión

**✅ Error de Login Microsoft SOLUCIONADO**

El launcher ahora:
- Abre el navegador automáticamente
- Procesa el login Premium sin errores
- Sincroniza tu sesión instantáneamente
- ¡Sin configuración requerida!

---

## 📥 Instalación

### Opción 1: Descarga el JAR Compilado
```bash
# Ubicación: target/launcher_mialu.jar
# Tamaño: 2.7 MB
# Requisito: Java 11+
```

### Opción 2: Compilar desde Código Fuente
```bash
# Clona o descarga el repositorio
cd launcher_froshy

# Compila y empaqueta
mvn clean package -DskipTests

# El JAR estará en: target/launcher_mialu.jar
```

---

## 🚀 Ejecución

### Windows
```cmd
java -jar launcher_mialu.jar
```

O doble clic en el JAR si Java está instalado.

### Linux/Mac
```bash
java -jar launcher_mialu.jar
```

O
```bash
chmod +x launcher_mialu.jar
./launcher_mialu.jar
```

---

## 🎯 Primer Uso: Login con Microsoft

1. **Abre el Launcher**
   ```bash
   java -jar launcher_mialu.jar
   ```

2. **Haz clic en "Login con Microsoft"**
   - El navegador se abre automáticamente
   - Se muestra la pantalla de login de Microsoft

3. **Inicia sesión**
   - Usa tu cuenta de Microsoft
   - La que está asociada a tu Minecraft Java

4. **Autoriza el acceso**
   - Permite que el launcher acceda a tu información
   - El navegador mostrará una confirmación

5. **¡Listo!**
   - El launcher automatiza todo el resto
   - Tu sesión Premium se sincroniza
   - Ya estás listo para jugar

---

## ⚙️ Configuración (Si es Necesaria)

### Puerto Ocupado (Puerto 3000)

Si ves el error "Puerto 3000 ya está en uso":

**Opción 1: Cierra lo que usa el puerto**
```bash
# Windows (PowerShell)
Get-Process | Where-Object {$_.Handles -like "*3000*"} | Stop-Process -Force

# Linux/Mac
lsof -i :3000 | grep LISTEN | awk '{print $2}' | xargs kill -9
```

**Opción 2: Usa otro puerto**
```bash
# Windows
set MIALU_API_PORT=8080
java -jar launcher_mialu.jar

# Linux/Mac
export MIALU_API_PORT=8080
java -jar launcher_mialu.jar
```

### Client ID Personalizado

Si necesitas usar un Client ID de Azure personalizado:

**Windows (PowerShell):**
```powershell
$env:MIALU_MS_CLIENT_ID = "tu-client-id"
java -jar launcher_mialu.jar
```

**Windows (CMD):**
```cmd
set MIALU_MS_CLIENT_ID=tu-client-id
java -jar launcher_mialu.jar
```

**Linux/Mac:**
```bash
export MIALU_MS_CLIENT_ID="tu-client-id"
java -jar launcher_mialu.jar
```

---

## 🆘 Problemas Comunes

### "No se abre el navegador automáticamente"
**Solución:**
1. La URL aparecerá en la consola
2. Cópiala y pégala en tu navegador
3. Inicia sesión normalmente
4. El callback se procesará automáticamente

### "Tiempo de espera agotado"
**Solución:**
1. Tienes 5 minutos para completar el login
2. Intenta nuevamente desde el principio
3. Verifica tu conexión a Internet

### "Error: Invalid redirect_uri"
**Solución:**
1. Verifica que el puerto 3000 está disponible
2. O configura un Client ID personalizado
3. Consulta `MICROSOFT_LOGIN_CONFIG.md`

### "Error: Access Denied"
**Solución:**
1. Asegúrate de usar tu cuenta de Microsoft
2. Que tenga licencia de Minecraft Java
3. Que no esté vinculada a otra cuenta del launcher

---

## 📚 Documentación

Para más información, consulta:

| Documento | Propósito | Lectura |
|-----------|-----------|---------|
| `QUICK_MICROSOFT_LOGIN.md` | Inicio rápido | 2 min |
| `MICROSOFT_LOGIN_CONFIG.md` | Configuración completa | 15 min |
| `ENVIRONMENT_VARIABLES.md` | Variables de entorno | 10 min |
| `MICROSOFT_LOGIN_FIX.md` | Detalles técnicos | 10 min |

---

## 🔐 Seguridad

- ✅ Tus credenciales nunca se guardan en el launcher
- ✅ Solo se guardan tokens criptografados
- ✅ El launcher nunca tiene acceso a tu contraseña
- ✅ Todo se comunica con servidores oficiales de Microsoft/Mojang

---

## 🎮 Primeros Pasos Después de Instalar

1. **Configura tu perfil**
   - Selecciona la versión de Minecraft
   - Elige tu modpack (si aplica)

2. **Espera a que se prepare**
   - El launcher descargará recursos necesarios
   - Primera vez toma más tiempo

3. **¡Juega!**
   - Haz clic en "Launch Minecraft"
   - ¡Disfruta del juego!

---

## 💾 Archivos del Sistema

El launcher almacena archivos en:

- **Windows:** `C:\Users\tu_usuario\.mialu-launcher\`
- **Linux:** `~/.mialu-launcher/`
- **macOS:** `~/.mialu-launcher/`

Contenido:
- Perfiles
- Configuración
- Tokens criptografados
- Cache de descargas

---

## 🔄 Actualizar el Launcher

### Método Manual
1. Descarga el nuevo JAR
2. Reemplaza el anterior
3. Ejecuta normalmente
4. Tus perfiles se conservan

### Método Automático (Si está configurado)
- El launcher verificará actualizaciones automáticamente
- Te notificará cuando haya una versión nueva

---

## 🆘 Soporte

### Si algo no funciona

1. **Revisa los logs en la consola**
   - Los mensajes de error te ayudarán

2. **Intenta estos pasos**
   - Reinicia el launcher
   - Verifica tu conexión a Internet
   - Reinicia tu computadora

3. **Consulta la documentación**
   - Busca tu error en los documentos
   - Revisa los troubleshooting

4. **Reporte de problema**
   - Describe exactamente qué hiciste
   - Incluye el mensaje de error
   - Especifica tu sistema operativo

---

## ✅ Verificación de Instalación

Para verificar que todo está correcto:

```bash
# 1. Verifica Java
java -version
# Debe ser 11 o superior

# 2. Verifica el JAR
java -jar launcher_mialu.jar --help
# Debe mostrar información del launcher

# 3. Verifica el puerto
netstat -ano | findstr :3000
# En Windows: debería estar disponible
```

---

## 🎓 Primeros Login: Paso a Paso

```
1. Abre el launcher
        ↓
2. Haz clic en "Login con Microsoft"
        ↓
3. Se abre tu navegador
        ↓
4. Inicia sesión con tu cuenta de Microsoft
        ↓
5. Autoriza el acceso del launcher
        ↓
6. Ves el mensaje "Login completado"
        ↓
7. El navegador se cierra automáticamente
        ↓
8. El launcher muestra "Sesión Premium activa"
        ↓
9. ¡Estás listo para jugar!
```

---

## 🌍 Requisitos Mínimos

- **Java:** 11.0 o superior
- **RAM:** 256 MB para el launcher
- **Espacio en disco:** 20 GB para Minecraft
- **Conexión:** Internet (al menos para login)
- **Navegador:** Cualquiera con soporte HTTP

---

## 💡 Tips

- ✅ Deja el launcher abierto mientras juegas
- ✅ Es más rápido ejecutarlo desde el menú de inicio
- ✅ Puedes jugar offline después de la primera autenticación
- ✅ Los tokens se renuevan automáticamente
- ✅ Tu sesión persiste entre reinicios

---

## 📞 Contacto y Soporte

Para problemas:
1. Consulta `MICROSOFT_LOGIN_CONFIG.md`
2. Revisa los mensajes de la consola
3. Intenta los pasos de troubleshooting
4. Busca en la documentación

---

## 🎉 ¡Disfruta!

**Launcher_Mialu v1.0**
**Listo para usar ✅**

**¡Que disfrutes jugando Minecraft!** 🎮

---

**Última actualización:** 31 de Marzo de 2026
**Versión:** 1.0
**Estado:** LISTO PARA PRODUCCIÓN ✅

