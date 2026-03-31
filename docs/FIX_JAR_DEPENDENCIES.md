# 🔧 Fix: JAR con Dependencias Incluidas

## ✅ Estado Final

Ahora **`launcher.jar` es el JAR ejecutable** que incluye todas las dependencias automáticamente.

```bash
# El comando que quería
java -jar target/launcher_mialu.jar  ✅ FUNCIONA
```

## Problema Inicial

Al ejecutar el primer JAR generado:
```bash
java -jar target/launcher_mialu.jar
```

Se obtenía error:
```
NoClassDefFoundError: com/fasterxml/jackson/databind/ObjectMapper
```

## Solución Implementada

Se configuró **maven-shade-plugin** en el `pom.xml` para:

1. **Crear un "fat JAR"** que incluye todas las dependencias
2. **Reemplazar el JAR original** para que sea directamente ejecutable
3. **Simplificar la experiencia**: Un solo JAR para usar

## Configuración Maven Final

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <createDependencyReducedPom>false</createDependencyReducedPom>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>am.froshy.mialu.launcher.LauncherUiApplication</mainClass>
                    </transformer>
                </transformers>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Resultado

```
target/
├── launcher.jar                (2.38 MB) ✅ Usar este - contiene todo
└── original-launcher.jar       (38 KB)   - JAR original sin deps, ignorar
```

## Comandos Finales

```bash
# Compilar y empaquetar
mvn clean package

# Ejecutar (simple y directo)
java -jar target/launcher_mialu.jar
```

## Documentación Actualizada

Se actualizaron estos archivos:
- ✅ README.md (raíz)
- ✅ docs/QUICKSTART.md (todos los comandos)
- ✅ docs/HOWTOREAD.md

---

**Fecha del Fix**: 2026-03-04  
**Estado**: ✅ **Resuelto y simplificado - Un único JAR ejecutable**








