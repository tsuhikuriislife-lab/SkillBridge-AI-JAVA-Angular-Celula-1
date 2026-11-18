# US-USER-01: Vista y Gestión Reactiva del Perfil de Usuario

**Estado:** `[ ] Por hacer`

---

## 1. Descripción y Objetivo de Ingeniería

Construir la experiencia integral de perfil de usuario en el frontend de SkillBridge AI. Permite al usuario autenticado visualizar su información personal, rol, historial de matriculas y sesiones activas, así como actualizar datos editables (nombre, teléfono), gestionar preferencias de aprendizaje y modificar su contraseña de forma segura con validaciones reactivas y Angular Signals.

---

## 2. Arquitectura de la Solución (Angular 20 & Hexagonal REST)

1. **Frontend (Angular 20 Standalone):**
   - Nuevo componente Standalone `UserProfileComponent` (`frontend/src/app/features/profile/profile.component.ts`), enlazado a la ruta protegida `/profile` con `authGuard`.
   - Utilización de **Angular Signals** (`userProfile = signal<UserAccountOut | null>(null)`) para reactividad fina y `ReactiveFormsModule` para los formularios de edición de datos y cambio de contraseña.
   - Pestaña de historial y servicios matriculados que consume `EnrollmentService` y `HistoryService`.
2. **Backend (Endpoints Inbound Existentes y Adaptación):**
   - Consumo de `GET /api/users/me` expuesto por `UserAccountController` para obtención del perfil del usuario autenticado a partir de la identidad de Spring Security.
   - Consumo de `PUT /api/users/{id}` para actualización de datos y nuevo endpoint `PUT /api/users/me/password` para cambio de credenciales verificando previamente la contraseña actual.
   - Integración con `UserPreferenceController` para actualización de intereses y áreas de aprendizaje.

---

## 3. Componentes Técnicos y Referencias de Implementación

- Controlador de cuentas de usuario:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/UserAccountController.java` línea 40:60)*.
- Casos de uso de usuario:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/application/service/UserAccountService.java` línea 25:70)*.
- Enrutador principal de Angular:
  *(Ver `frontend/src/app/app.routes.ts` línea 13:35)*.
- Servicio de autenticación en Angular:
  *(Ver `frontend/src/app/core/auth.service.ts` línea 30:75)*.

---

## 4. Criterios de Aceptación Técnicos

- **CA-USER-01.1:** La ruta `/profile` debe ser inaccesible para usuarios no autenticados, redirigiendo automáticamente a `/login?returnUrl=/profile` a través de `authGuard`.
- **CA-USER-01.2:** La modificación de contraseña exige el ingreso obligatorio de la contraseña actual, validada con BCrypt en el backend.
- **CA-USER-01.3:** Los cambios de perfil deben reflejarse de inmediato en el encabezado global de la aplicación (`NavbarComponent`) sin requerir recarga total del navegador (F5), actualizando la señal del usuario en `AuthService`.
