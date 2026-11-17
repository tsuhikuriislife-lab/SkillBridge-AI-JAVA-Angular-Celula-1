# US-FE-01: Corrección de Tests en DisplayBookings y Limpieza de Spec AdminDashboard

**Estado:** `[ ] Por hacer`

---

## 1. Descripción y Objetivo de Ingeniería
Solucionar los 3 tests unitarios fallando en Karma para `DisplayBookingsComponent` por inyección incompleta de dependencias (`MeetingService` / `HttpClient`). Corregir el error de tipado TypeScript `TS2345` y renombrar el archivo huérfano de pruebas de `AdminDashboardComponent`.

---

## 2. Criterios de Aceptación y Arquitectura
1. **Mocking de Dependencias en DisplayBookings:** Proveer `provideHttpClientTesting()` o un espía simulado de `MeetingService` en `src/app/features/display-bookings/display-bookings.spec.ts` para que los 3 tests pasen en verde.
2. **Renombrado de Spec:** Renombrar `admin-dashboard.componentspec.ts` a `admin-dashboard.component.spec.ts` para que sea reconocido por Karma y `tsconfig.spec.json`.
3. **Corrección de Tipado TS2345:** Ajustar el valor `'proveedores'` en el spec del admin a los valores válidos de la interfaz `ViewType` (`'usuarios' | 'servicios'`).

---

## 3. Componentes Técnicos y Referencias de Implementación
- Pruebas de visualización de reservas:
  *(Ver `frontend/src/app/features/display-bookings/display-bookings.spec.ts` línea 10:35)*.
- Archivo de pruebas del panel de administración:
  *(Ver `frontend/src/app/features/admin-dashboard/admin-dashboard.componentspec.ts` línea 1:45)*.
- Componente y tipos del dashboard:
  *(Ver `frontend/src/app/features/admin-dashboard/admin-dashboard.component.ts` línea 15:40)*.
