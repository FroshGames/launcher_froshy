# 📖 Referencias y API

**Contenido:** Referencia de API y documentación de referencia

---

## 📋 Documentos

- **API_REFERENCE.md** - Referencia completa de API interna

---

## 🎯 Endpoints Principales

### Perfil
- `GET /internal/v1/profiles` - Listar perfiles
- `POST /internal/v1/profiles` - Crear perfil

### Lanzamiento
- `POST /internal/v1/launch` - Lanzar Minecraft
- `GET /internal/v1/launch-prepared` - Estado

### Autenticación
- `POST /internal/v1/auth/microsoft/login/start` - Iniciar login
- `POST /internal/v1/auth/microsoft/login/complete` - Completar login
- `GET /internal/v1/auth/microsoft/session` - Estado sesión

### Actualización
- `GET /internal/v1/updates/check` - Verificar actualizaciones

---

## 📡 Formato

Todos los endpoints devuelven JSON con formato:
```json
{
  "status": "success|error",
  "data": {...},
  "timestamp": "2026-03-31T14:30:00Z"
}
```

---

**Última Actualización:** 31 de Marzo de 2026

