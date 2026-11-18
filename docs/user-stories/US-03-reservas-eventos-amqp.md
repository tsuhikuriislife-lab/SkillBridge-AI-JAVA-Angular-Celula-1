# US-03: Gestión de Reservas y Desacoplamiento Asíncrono con RabbitMQ

**Estado:** `[✅] Terminada`

---

## 1. Descripción y Objetivo de Ingeniería
Permitir a los clientes reservar sesiones de mentoría en fechas y horas válidas, desacoplando la transacción relacional de los efectos secundarios (notificaciones, auditoría) mediante publicación de eventos asíncronos en RabbitMQ.

---

## 2. Arquitectura de la Solución (EDA)
1. **Transacción de Negocio:** `ServiceEnrollmentService` valida disponibilidad del proveedor y persiste la reserva en estado `PENDING`.
2. **Publicación de Eventos:** Se emite el evento de dominio `BookingCreated` hacia el topic exchange `skillbridge.events` con clave de enrutamiento `booking.created`.

---

## 3. Componentes Técnicos y Referencias de Implementación
- Caso de uso de reservas e inscripciones:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/application/service/ServiceEnrollmentService.java` línea 30:80)*.
- Adaptador de publicación RabbitMQ:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitMqBookingEventPublisher.java` línea 20:55)*.
