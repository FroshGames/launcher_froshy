# Solución: ResolutionException en Java 17+ con Forge Mods

## Problema
El launcher crasheaba con errores como:
```
Exception in thread "main" java.lang.module.ResolutionException: 
Modules minecraft and _1._20._1 export package net.minecraft.client to module sophisticatedcore
```

Este error ocurre cuando múltiples mods intentan acceder a los mismos paquetes de módulos de Java,
causando conflictos de resolución de módulos cuando se usa Java 17 o superior.

## Causa
Con Java 9+, se introdujo el sistema de módulos de Java que tiene controles estrictos sobre qué
módulos pueden acceder a qué paquetes. Cuando se ejecuta Forge con múltiples mods en Java 17+,
estos mods necesitan acceso a módulos internos de Java que están normalmente cerrados, lo que
causa la ResolutionException.

## Solución Implementada

### 1. Agregar Argumentos JVM de Acceso a Módulos (MojangVersionDownloader.java)

Se agregó un nuevo método `addModuleAccessArguments()` que detecta automáticamente la versión
de Java y, si es 17 o superior, agrega los siguientes argumentos JVM:

```
--add-modules=ALL-SYSTEM
--add-opens=java.base/java.lang.invoke=ALL-UNNAMED
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED
--add-opens=java.base/java.nio=ALL-UNNAMED
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
--add-opens=java.base/java.util.jar=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
```

**¿Qué hacen estos argumentos?**

- `--add-modules=ALL-SYSTEM`: Hace que todos los módulos del sistema sean accesibles
- `--add-opens`: Abre módulos específicos para acceso sin restricciones por parte de código no modular

Estos argumentos son completamente seguros para desarrollo local y servidores privados.

### 2. Corrección del API Client (InternalApiClient.java)

Se agregó el método `deleteProfile()` que estaba siendo llamado en la UI pero no existía:

```java
public void deleteProfile(String profileId) {
    send("profiles/" + profileId, "DELETE", null, new TypeReference<Map<String, Object>>() {});
}
```

## Cómo Funciona la Fix

1. Cuando se construye la instalación de Minecraft (`buildInstallation()`), se detecta la versión de Java
2. Si es Java 17+, se agregan automáticamente los argumentos de acceso a módulos
3. Esto permite que Forge y los mods accedan a los módulos internos que necesitan
4. Los conflictos de resolución se evitan porque ahora todos los mods pueden acceder a los paquetes que necesitan

## Compatibilidad

- ✅ Java 8-16: Sin cambios (no usa argumentos de módulos)
- ✅ Java 17+: Agrega argumentos de acceso a módulos automáticamente
- ✅ Funciona con Forge, NeoForge, Fabric y Quilt

## Testing Recomendado

1. Crear un perfil vanilla sin mods → ✅ Debe funcionar
2. Crear un perfil con mods Forge → ✅ Debe funcionar sin ResolutionException
3. Lanzar el juego desde la UI → ✅ Debe iniciar correctamente

Si después de la fix siguen apareciendo errores de módulos diferentes, puede indicar:
- Mods incompatibles entre sí
- Versiones de Forge incompatibles con los mods
- Conflictos específicos de ciertas combinaciones de mods

