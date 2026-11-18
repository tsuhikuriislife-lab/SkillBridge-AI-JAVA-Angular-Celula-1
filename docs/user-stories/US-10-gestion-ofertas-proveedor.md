# US-10: Gestión de Ofertas y Disponibilidad Horaria por Proveedores

**Estado:** `[✅] Terminada`

---

## 1. Descripción y Objetivo de Ingeniería
Permitir a los mentores y proveedores de servicios crear, editar y gestionar sus ofertas técnicas, así como definir franjas horarias de disponibilidad y tarifas por sesión.

---

## 2. Arquitectura de la Solución
1. **Control de Acceso RBAC:** Endpoints protegidos para rol `PROVIDER`.
2. **Resolución de Horarios:** `MeetingScheduleResolver` valida solapamientos y reglas de antelación mínima para reservas.

---

## 3. Componentes Técnicos y Referencias de Implementación
- Controlador de gestión de ofertas:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/ServiceManagementController.java` línea 20:65)*.
- Resolutor de agendas y disponibilidad:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/application/service/MeetingScheduleResolver.java` línea 20:50)*.
