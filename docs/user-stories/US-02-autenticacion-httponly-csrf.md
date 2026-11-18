# US-02: Autenticación Stateless, Cookies HttpOnly y Protección CSRF

**Estado:** `[✅] Terminada`

---

## 1. Descripción y Objetivo de Ingeniería
Proveer registro e inicio de sesión seguro para clientes y proveedores mediante tokens JWT firmados con HMAC-SHA256, contraseñas hasheadas con BCrypt y transporte seguro vía Cookies HttpOnly inmunes a ataques XSS y CSRF.

---

## 2. Arquitectura de la Solución (Seguridad & Hexagonal)
1. **Flujo de Autenticación:** `POST /api/auth/login` valida credenciales y escribe el token JWT en la cookie `access_token` con directivas `HttpOnly`, `SameSite=Lax` y `Secure` condicional.
2. **Protección Anti-CSRF:** Endpoint `GET /api/auth/csrf` emite token que el cliente Angular reenvía en la cabecera `X-XSRF-TOKEN`.

---

## 3. Componentes Técnicos y Referencias de Implementación
- Controlador de autenticación:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AuthController.java` línea 20:65)*.
- Servicio de tokens JWT:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/configuration/security/JwtService.java` línea 25:70)*.
- Interceptor Angular de credenciales:
  *(Ver `frontend/src/app/core/interceptors/auth.interceptor.ts` línea 10:35)*.
