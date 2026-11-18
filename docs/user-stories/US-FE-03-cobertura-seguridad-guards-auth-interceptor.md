# US-FE-03: Cobertura de Pruebas en Seguridad Frontend: Guards y AuthInterceptor

**Estado:** `[ ] Por hacer`

---

## 1. Descripción y Objetivo de Ingeniería
Garantizar la protección de rutas y la integridad de las comunicaciones cliente-servidor mediante pruebas automatizadas para los tres guards de navegación y el interceptor de autenticación con cookies HttpOnly y protección CSRF.

---

## 2. Criterios de Aceptación y Arquitectura
1. **Pruebas de AuthInterceptor:** Crear `auth.interceptor.spec.ts` verificando con `HttpTestingController` la inyección de `withCredentials: true`, la inclusión de la cabecera `X-XSRF-TOKEN` y el redireccionamiento reactivo ante código de error 401.
2. **Pruebas de Guards de Navegación:** Crear suites unitarias para `authGuard`, `adminGuard` y `nonAdminGuard` validando el acceso condicional según el estado reactivo de sesión en `AuthService`.

---

## 3. Componentes Técnicos y Referencias de Implementación
- Interceptor HTTP de cookies y CSRF:
  *(Ver `frontend/src/app/core/interceptors/auth.interceptor.ts` línea 1:40)*.
- Guards funcionales de Angular:
  *(Ver `frontend/src/app/core/guards/auth.guard.ts` línea 1:25)*.
  *(Ver `frontend/src/app/core/guards/admin.guard.ts` línea 1:25)*.
