# ADR-0005: Microservicio de notificaciones por correo (NestJS + Resend) desacoplado vía RabbitMQ

## Estado
Aceptado (2026-11-11).

## Contexto
La épica de notificaciones pide avisar por correo a usuarios y administradores cuando cambia el estado de una cuenta: registro, creación por un admin, activación/desactivación, cambio de rol y eliminación. Restricciones:
- El equipo debe implementarlo en **NestJS**, como un servicio aparte del monolito modular Java (ver ADR-0001).
- El proveedor de correo elegido es **Resend**.
- `AGENTS.md` exige arquitectura hexagonal, integraciones a través de puertos, cero secretos en el frontend y eventos asíncronos con retry y DLQ.
- El backend ya tenía RabbitMQ configurado (`spring-boot-starter-amqp`, retry de listener), pero sin publicadores ni consumidores.
- El backend no usa `@Transactional` en los casos de uso.

## Decisión
1. **Comunicación asíncrona por RabbitMQ.** El backend publica eventos de dominio `user.*` en un exchange topic nuevo, `skillbridge.events`, mediante el puerto `UserEventPublisherPort` y el adaptador `RabbitUserEventPublisher`. El contrato v1 está en `docs/notifications/CONTRACT.md`.
2. **`notification-service` (NestJS 11)** consume la cola `notifications.user-events` con `@golevelup/nestjs-rabbitmq` v6. También es hexagonal: dominio puro (política de destinatarios), puertos `EmailSenderPort` / `TemplateRendererPort` y adaptadores Resend / log.
3. **Reintentos con espera** mediante una cola `.retry` sin consumidores, con TTL por mensaje que vuelve a la cola principal por dead-lettering. Los mensajes inválidos, los errores permanentes y los reintentos agotados van a la `.dlq` con headers `x-failure-*`.
4. **Idempotencia sin base de datos:** caché en memoria de `eventId` + `idempotencyKey` de Resend (`eventId:plantilla:destinatario`, válida 24 h).
5. **Fallo de publicación tolerado en el backend:** si RabbitMQ no responde, el caso de uso continúa y se registra un WARN. Se acepta perder ese correo en v1.
6. **Java resuelve los destinatarios administradores** (`admins` en el evento), porque el microservicio no tiene acceso a la base de datos del backend.
7. **Driver de correo configurable** (`EMAIL_DRIVER=log|resend`): desarrollo y CI funcionan sin API key.

## Alternativas consideradas
- **HTTP síncrono de Java a Nest:** más simple, pero acopla la disponibilidad. Si Nest cae, se pierde el correo o falla la petición del usuario. Descartada.
- **`@nestjs/microservices` (transporte RMQ):** espera mensajes con el formato `{pattern, data}`, incompatible con el JSON plano que publica Spring en un exchange topic. Descartada.
- **Enviar los correos desde Java (`JavaMailSender` / SDK de Resend):** no cumple el requisito de un microservicio NestJS y mezcla responsabilidades. Descartada.
- **Transactional Outbox:** garantiza la entrega, pero requiere una tabla nueva, un publicador programado y transacciones que el backend hoy no usa. Se pospone como mejora.
- **Base de datos propia del microservicio (historial e idempotencia durable):** no es necesaria para v1. Se pospone.

## Consecuencias
- ✅ El backend no conoce a Resend. Cambiar de proveedor sólo toca un adaptador del microservicio.
- ✅ Las caídas del microservicio o de Resend no afectan a los usuarios, y los mensajes esperan en la cola.
- ✅ La observabilidad reutiliza el dashboard "SkillBridge - Notificaciones", que ya existía, gracias a métricas con los mismos nombres.
- ⚠️ Entrega "al menos una vez". Puede haber duplicados tras una caída: los mitiga la idempotencia de Resend (24 h).
- ⚠️ Si RabbitMQ está caído en el momento de publicar, el correo se pierde (no hay outbox).
- ⚠️ `notification-service` fija versiones CommonJS (`@nestjs/config@4`, `@golevelup/nestjs-rabbitmq@6`) porque las últimas son ESM y rompen Jest con Nest 11. Hay que revisarlo al migrar a Nest 12.
- ⚠️ Se agrega el parámetro `actorEmail` a `ChangeStatusUserAccountUseCase` y `DeleteUserAccountUseCase`.
