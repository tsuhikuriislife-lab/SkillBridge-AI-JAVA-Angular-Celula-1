# Contrato de eventos v1: Backend Java → `notification-service`

> **Fuente única de verdad.** Si algo de este documento cambia, se avisa a los 3 integrantes **antes** de tocar código.
> Los ejemplos ejecutables están en [`contract/`](./contract) y los usan las pruebas de Java y de NestJS.

## 1. Transporte

| Elemento | Valor | Dueño |
|---|---|---|
| Broker | RabbitMQ 4 (`rabbitmq` en `docker-compose.yml`) | Infra |
| Exchange | `skillbridge.events`, tipo **topic**, `durable=true`, `autoDelete=false` | Java (lo declara). Nest también lo declara con los mismos parámetros, así no importa cuál arranque primero. |
| Routing keys | `user.registered`, `user.created`, `user.status.changed`, `user.role.changed`, `user.deleted` | Java |
| Cola principal | `notifications.user-events`, binding `user.#`, durable, DLX `""` y DLRK `notifications.user-events.dlq` | Nest |
| Cola de reintentos | `notifications.user-events.retry`, durable, DLX `""` y DLRK `notifications.user-events` (TTL por mensaje) | Nest |
| Cola de mensajes muertos (DLQ) | `notifications.user-events.dlq`, durable | Nest |
| Content-Type | `application/json`, UTF-8 | Java |
| Headers AMQP | `message_id = eventId`, `x-event-version = 1`, `delivery_mode = 2` (persistente) | Java |

> Spring también agrega el header `__TypeId__` con el nombre de la clase Java. NestJS lo ignora. **No hay que depender de él.**

## 2. Envelope (cuerpo JSON)

```json
{
  "eventId": "uuid v4",
  "eventType": "user.status.changed",
  "version": 1,
  "occurredAt": "2026-11-11T15:10:00Z",
  "actor":   { "id": "uuid|null", "email": "string", "role": "CUSTOMER|PROVIDER|ADMIN|null" },
  "subject": { "id": "uuid", "name": "string", "email": "string", "role": "CUSTOMER|PROVIDER|ADMIN", "status": "ACTIVE|INACTIVE" },
  "admins":  [ { "email": "string", "name": "string" } ],
  "data":    { "clave": "valor (siempre string)" }
}
```

| Campo | Obligatorio | Regla |
|---|---|---|
| `eventId` | Sí | UUID único por evento. Es la base de la **idempotencia**: si llega dos veces, se envía un solo correo. |
| `eventType` | Sí | Igual a la routing key. |
| `version` | Sí | Entero, hoy `1`. Un cambio incompatible significa `version: 2` y un período de convivencia. |
| `occurredAt` | Sí | ISO-8601 en UTC (`Instant.toString()` en Java). |
| `actor` | No (`null`) | Quién provocó el cambio. Es `null` en el auto-registro. Si el correo del actor no existe en BD: `id=null`, `role=null`. |
| `subject` | Sí | Usuario afectado, ya con el estado **nuevo**. En `user.deleted` son los datos capturados **antes** de borrar. |
| `admins` | Sí (puede ser `[]`) | Administradores activos al momento del evento. Java los resuelve porque Nest **no** tiene acceso a la BD. |
| `data` | Sí (puede ser `{}`) | Datos propios de cada tipo (ver tabla). |

> **Prohibido** incluir contraseñas, hashes, tokens JWT o cualquier secreto en el evento.

## 3. Tipos de evento

| `eventType` | Dónde se publica (Java) | `data` | Correos que envía Nest |
|---|---|---|---|
| `user.registered` | `AuthService.register` | `{}` | Usuario: `welcome`. Admins: `admin.new-registration`. |
| `user.created` | `AdminUserService.createUser` | `{}` | Usuario: `account-created`. |
| `user.status.changed` | `UserAccountService.changeStatus` (sólo si el estado cambió) | `previousStatus`, `newStatus` | Usuario: `status-changed`. Admins: `admin.status-changed`, **sólo si el actor NO es ADMIN**. |
| `user.role.changed` | `AdminUserService.updateUser` (sólo si el rol cambió) | `previousRole`, `newRole` | Usuario: `role-changed`. |
| `user.deleted` | `AdminUserService.deleteUser`, `UserAccountService.deleteUserAccount` | `{}` | Usuario: `account-deleted`. Admins: `admin.account-deleted`, **sólo si el actor NO es ADMIN**. |

Reglas comunes:
- Nunca se envía a un admin el aviso de un cambio sobre sí mismo (se filtra cuando `admin.email == subject.email`).
- La llave de idempotencia de cada correo es `"{eventId}:{template}:{destinatario}"`.

## 4. Errores y reintentos (lado Nest)

| Situación | Acción |
|---|---|
| JSON inválido o envelope que no cumple el contrato | Va **directo a la DLQ**, sin reintentos. Es un bug del productor y reintentar no lo arregla. |
| Error transitorio (red, 429 y 5xx de Resend) | Va a la cola `retry` con `expiration = NOTIFICATIONS_RETRY_DELAY_MS` (por defecto 30 s) y header `x-retry-count + 1`. |
| Más de `NOTIFICATIONS_MAX_RETRIES` (por defecto 3) intentos | Va a la DLQ con header `x-failure-reason`. |
| Error permanente (4xx de validación de Resend, cuota agotada) | Va directo a la DLQ. |
| `eventId` ya procesado (en memoria) | Hace ACK y suma 1 a `skillbridge_notifications_duplicate_total`. |

**Reprocesar la DLQ:** en la UI de RabbitMQ (`http://localhost:15672`) abre la cola `notifications.user-events.dlq` y usa **Move messages** hacia `notifications.user-events`. Resend no duplica gracias a la llave de idempotencia (válida 24 h).

## 5. Ejemplos

| Archivo | Caso |
|---|---|
| [`contract/user.registered.json`](./contract/user.registered.json) | Auto-registro (`actor: null`) |
| [`contract/user.created.json`](./contract/user.created.json) | Admin crea un proveedor |
| [`contract/user.status.changed.json`](./contract/user.status.changed.json) | El usuario se desactiva a sí mismo, así que también se avisa al admin |
| [`contract/user.role.changed.json`](./contract/user.role.changed.json) | Admin cambia CUSTOMER → PROVIDER |
| [`contract/user.deleted.json`](./contract/user.deleted.json) | El usuario elimina su cuenta, así que también se avisa al admin |

Los ejemplos usan las direcciones de prueba de Resend (`delivered@resend.dev`, `delivered+admin@resend.dev`). Así, si alguien los publica a mano, no le llega correo a nadie real.

## 6. Publicar un evento a mano (para probar sin Java)

1. Abre `http://localhost:15672` e inicia sesión con `guest` / `guest`.
2. Ve a **Exchanges → `skillbridge.events` → Publish message**.
3. En **Routing key** escribe `user.registered`.
4. En **Properties** escribe `content_type = application/json`.
5. En **Payload** pega el contenido de `contract/user.registered.json`.
6. Haz clic en **Publish message** y revisa los logs: `docker compose logs -f notifications`.
