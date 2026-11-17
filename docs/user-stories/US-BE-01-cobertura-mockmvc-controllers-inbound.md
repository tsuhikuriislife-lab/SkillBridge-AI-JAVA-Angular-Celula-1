# US-BE-01: Cobertura de Pruebas en Controladores Inbound REST con MockMvc

**Estado:** `[ ] Por hacer`

---

## 1. Descripción y Objetivo de Ingeniería
Cerrar la brecha de cobertura en la capa de entrada HTTP del backend (actualmente en 20.4%). Implementar suites de prueba `@WebMvcTest` para los 11 controladores desprovistos de validación y elevar la cobertura del manejador global de excepciones.

---

## 2. Criterios de Aceptación y Arquitectura
1. **Pruebas MockMvc en Controladores Clave:** Crear pruebas unitarias con mock de seguridad y servicios para `MeetingController`, `ServiceManagementController`, `EnrollmentController`, `OfferingController` y `AiController`.
2. **Cobertura de Respuestas y Errores:** Validar serialización JSON, códigos HTTP (200, 201, 400, 403, 404, 500) y formatos de respuesta según el estándar `GlobalExceptionHandler`.

---

## 3. Componentes Técnicos y Referencias de Implementación
- Controladores REST de la capa de infraestructura:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/MeetingController.java` línea 20:65)*.
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/EnrollmentController.java` línea 25:70)*.
- Manejador centralizado de excepciones:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java` línea 20:80)*.
