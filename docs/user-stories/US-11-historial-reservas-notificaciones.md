# US-11: Historial de Reservas y Notificaciones In-App para Usuarios

**Estado:** `[✅] Terminada`

---

## 1. Descripción y Objetivo de Ingeniería
Facilitar a los usuarios clientes y proveedores consultar el historial completo de sus sesiones pasadas y futuras, así como recibir notificaciones internas sobre el estado de sus reservas.

---

## 2. Arquitectura de la Solución
1. **Consultas Históricas:** `BookingHistoryService` recupera registros paginados filtrados por propietario.
2. **Consumo de Eventos In-App:** Consumidor RabbitMQ que persiste avisos transaccionales en la tabla `booking_notifications`.

---

## 3. Componentes Técnicos y Referencias de Implementación
- Controlador de historial:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingHistoryController.java` línea 20:55)*.
- Servicio de notificaciones internas:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/application/service/BookingHistoryService.java` línea 20:60)*.
