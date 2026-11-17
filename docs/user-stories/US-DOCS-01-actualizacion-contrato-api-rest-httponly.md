# US-DOCS-01: Actualización de Contrato de API REST para Autenticación HttpOnly y CSRF

**Estado:** `[ ] Por hacer`

---

## 1. Descripción y Objetivo de Ingeniería
Actualizar exhaustivamente `docs/API.md` para reflejar el esquema real de seguridad implementado en el backend y frontend (HU-14). Reemplazar la especificación obsoleta de Bearer token en cuerpo JSON por el uso de Cookies HttpOnly (`access_token`) y documentar los endpoints faltantes.

---

## 2. Criterios de Aceptación y Arquitectura
1. **Corrección de Contratos de Sesión:** Documentar el comportamiento de `Set-Cookie` en `/api/auth/login` y `/api/auth/register`, y el uso de `X-XSRF-TOKEN`.
2. **Documentación de Endpoints Faltantes:** Añadir las especificaciones de `POST /api/auth/logout`, `GET /api/auth/csrf`, `/api/catalog/**` y el módulo completo de administración `/api/admin/**`.

---

## 3. Componentes Técnicos y Referencias de Implementación
- Archivo central de especificación de API:
  *(Ver `docs/API.md` línea 45:110)*.
- Controlador de autenticación con cookies y CSRF:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AuthController.java` línea 25:90)*.
