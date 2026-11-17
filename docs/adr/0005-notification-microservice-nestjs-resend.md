# ADR-0005: Microservicio de notificaciones por correo (NestJS + Resend) desacoplado vía RabbitMQ

## Estado
Aceptado (2026-11-11). Épica Jira KAN-49 (historias KAN-56, KAN-57, KAN-58 y KAN-59).

## Contexto
La épica pide avisar por correo a usuarios, administradores y proveedores cuando:
- cambia el estado de una cuenta (registro, creación por un admin, activación/desactivación, cambio de rol, eliminación);
- se confirma una reserva.

Restricciones:
- Debe ser un **microservicio NestJS** aparte del monolito modular Java (ADR-0001), con **Resend** como proveedor.
- `AGENTS.md` exige: arquitectura hexagonal, integraciones a través de puertos, cero secretos en el frontend, eventos asíncronos con retry y DLQ, y migraciones inmutables.
- Jira exige: idempotencia en Redis (KAN-57), auditoría persistente con webhooks firmados (KAN-58), Terminus y Swagger.
- El backend ya tenía RabbitMQ configurado, pero sin publicadores ni consumidores, y no usa `@Transactional` en los casos de uso.

## Decisión
1. **Comunicación asíncrona por RabbitMQ.** El backend publica eventos en el exchange topic `skillbridge.events` (puerto `UserEventPublisherPort`, adaptador `RabbitUserEventPublisher`). El contrato v1 está en `docs/notifications/CONTRACT.md`, con 6 tipos de evento: `user.*` y `enrollment.created`.
2. **`notifications-service` (NestJS 11, CommonJS)**, hexagonal:
   - dominio puro: política de destinatarios y transición de estados de entrega;
   - puertos: `EmailSenderPort`, `TemplateRendererPort`, `IdempotencyPort` y `NotificationLogPort`;
   - un módulo de Nest por responsable, y `app.module.ts` congelado desde la base.
3. **Consumo con `@golevelup/nestjs-rabbitmq` v6.** `@nestjs/microservices` se descarta porque exige el formato `{pattern, data}`.
4. **Reintentos exponenciales** con 3 colas de espera de TTL fijo (5 s, 30 s y 2 min) que devuelven el mensaje a `notifications.events` por dead-lettering. Hay una cola por nivel para evitar el bloqueo *head-of-line* que produce el TTL por mensaje. Al agotarse, el mensaje va a `dlq.notifications` con headers `x-failure-*`.
5. **Idempotencia por correo** (`eventId:plantilla:destinatario`) en **Redis base 1** con `SET NX` (`processing`, luego `sent` durante 7 días), más la `idempotencyKey` de Resend (24 h).
6. **Auditoría en una base de datos propia `notifications`** (mismo contenedor PostgreSQL, migraciones TypeORM inmutables). Cada servicio es dueño de sus datos; el backend y su Flyway no cambian.
7. **Webhooks de Resend** verificados con la firma Svix (`resend.webhooks.verify` sobre el cuerpo crudo). Los estados avanzan sin retroceder.
8. **Plantillas Handlebars** (`.hbs`, layout + parciales, modo `strict`, escape automático), copiadas a `dist` como *assets*.
9. **Fallo de publicación tolerado en el backend:** si RabbitMQ no responde, el caso de uso continúa y se registra un WARN (sin outbox en v1).
10. **Java resuelve los destinatarios** (admins activos y proveedor de la reserva), porque el microservicio no accede a la base del backend.
11. **`EMAIL_DRIVER=log|resend`** y **`MAIL_REDIRECT_TO`** para trabajar en desarrollo y en CI sin enviar correos reales.

## Alternativas consideradas
- **HTTP síncrono de Java a Nest:** acopla la disponibilidad de ambos servicios. Descartada.
- **`@nestjs/microservices` (RMQ):** formato incompatible con el JSON de Spring. Descartada.
- **Tabla de auditoría en la base del backend (Flyway V16):** acopla los dos servicios a un mismo esquema. Descartada a favor de una base propia.
- **Sólo Redis para la auditoría:** el historial sería temporal y difícil de consultar. Descartada.
- **TTL por mensaje en una única cola de reintento:** produce bloqueo *head-of-line* con esperas exponenciales. Descartada.
- **Transactional Outbox:** se pospone. Requiere transacciones y un publicador programado que el backend hoy no tiene.

## Consecuencias
- ✅ El backend no conoce a Resend, y las caídas del microservicio o de Resend no afectan a los usuarios.
- ✅ Entrega "al menos una vez" sin duplicados visibles (Redis + Resend), y trazabilidad completa en `notification_log`.
- ✅ Reutiliza el dashboard de Grafana "SkillBridge - Notificaciones" (los nombres de las métricas coinciden).
- ⚠️ Infraestructura extra: una base de datos más (`notifications`, creada con `make db-notifications`) y un túnel para probar webhooks en local.
- ✅ El check de Resend en `/health` funciona con una llave de *Sending access* (mínimo privilegio): el error `restricted_api_key` cuenta como "up" (2026-11-12).
- ⚠️ Sin outbox: si RabbitMQ está caído en el momento de publicar, ese correo se pierde.
- ⚠️ Versiones fijadas (`@nestjs/config@4`, `@golevelup/nestjs-rabbitmq@6`, `typeorm@0.3`) por compatibilidad CommonJS con Jest. Hay que revisarlas al migrar a Nest 12.
- ⚠️ Se agrega el parámetro `actorEmail` a `ChangeStatusUserAccountUseCase` y `DeleteUserAccountUseCase`, junto con las reglas de autorización (403).
