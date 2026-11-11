# Guía Kevin: KAN-57 / HU-NOT02, consumidor RabbitMQ, idempotencia (Redis) y reintentos (DLQ)

> **Jira:** [KAN-57](https://skillbridgec3.atlassian.net/browse/KAN-57) (épica [KAN-49](https://skillbridgec3.atlassian.net/browse/KAN-49))
> **Responsable:** Kevin Mercado
> **Rama:** `feature/notificaciones-consumer` (sale de `feature/notificaciones` **después** de que Dylan suba la base)
> **Antes de empezar lee:** [CONTRACT.md](./CONTRACT.md), la sección "Estructura" de la [guía de Dylan](./GUIA-DYLAN-HU-NOT01-KAN-56.md) y [PLAN.md](./PLAN.md)
> **Requisitos:** Node.js 22, Docker.

## Qué cubre esta guía (criterios de aceptación de KAN-57)

| CA | Qué | Dónde |
|---|---|---|
| CA 2.1 | Transporte AMQP: escucha el exchange `skillbridge.events` con las colas de eventos (`user.registered`, `enrollment.created`…) | Pasos 6-8 |
| CA 2.2 | Idempotencia con **Redis**: la llave de cada correo evita duplicados ante re-entregas | Pasos 3-4 |
| CA 2.3 | Dead Letter + **3 reintentos exponenciales** (5 s, 30 s y 2 min) y luego `dlq.notifications` | Pasos 6-7 |
| CA 2.4 | Validación estricta del payload con DTOs de `class-validator` | Paso 2 |

> **Sobre la tarea técnica "usar `@nestjs/microservices`":** su transporte RMQ espera mensajes con forma `{ "pattern", "data" }`, y Spring publica **JSON plano** en un exchange *topic*. Por eso se usa `@golevelup/nestjs-rabbitmq`, que trabaja directo con exchange, routing key y cola. Ya quedó instalado por Dylan en la base (v6, la última CommonJS).
> **Sobre `ValidationPipe`:** se aplica a controladores HTTP (ya está activo de forma global en `main.ts`). En un consumidor AMQP se usa el **mismo motor** (`class-validator`), llamado directamente (Paso 2).

## Tus archivos (no edites otros)

```
src/domain/notification-policy.ts (+ .spec)
src/application/ports/idempotency.port.ts
src/application/handle-notification-event.use-case.ts (+ .spec)
src/infrastructure/messaging/**            ← todo lo de RabbitMQ, Redis y métricas
src/infrastructure/messaging/messaging.module.ts   ← reemplazas el módulo vacío de la base
```

## Paso 0: Rama

```bash
cd ~/Projects/SkillBridge-AI-JAVA-Angular-Celula-1
git fetch origin
git checkout feature/notificaciones && git pull origin feature/notificaciones
git checkout -b feature/notificaciones-consumer
cd notifications-service && npm install     # instala lo que Dylan dejó en package.json
```

---

## Paso 1: Política de notificación (quién recibe qué)

📄 **Crear** `notifications-service/src/domain/notification-policy.ts`

```ts
import type { NotificationIntent, TemplateKey } from './notification';
import type { NotificationEvent } from './notification-event';

/**
 * Decide qué correos genera un evento (tabla en docs/notifications/CONTRACT.md §3).
 * Función pura: mismo evento → mismo resultado.
 */
export function resolveNotifications(
  event: NotificationEvent,
): NotificationIntent[] {
  const subjectEmail = event.subject.email.toLowerCase();

  const intent = (
    template: TemplateKey,
    to: string,
    name: string,
  ): NotificationIntent => ({
    template,
    to,
    recipientName: name,
    idempotencyKey: `${event.eventId}:${template}:${to.toLowerCase()}`,
  });

  const toUser = (template: TemplateKey): NotificationIntent =>
    intent(template, event.subject.email, event.subject.name);

  const toAdmins = (template: TemplateKey): NotificationIntent[] =>
    event.admins
      .filter((admin) => admin.email.toLowerCase() !== subjectEmail)
      .map((admin) => intent(template, admin.email, admin.name));

  const toProvider = (template: TemplateKey): NotificationIntent[] => {
    const email = event.data.providerEmail;
    if (!email || email.toLowerCase() === subjectEmail) return [];
    return [intent(template, email, event.data.providerName || 'Proveedor')];
  };

  const actorIsAdmin = event.actor?.role === 'ADMIN';

  switch (event.eventType) {
    case 'user.registered':
      return [toUser('welcome'), ...toAdmins('admin.new-registration')];
    case 'user.created':
      return [toUser('account-created')];
    case 'user.status.changed':
      return [
        toUser('status-changed'),
        ...(actorIsAdmin ? [] : toAdmins('admin.status-changed')),
      ];
    case 'user.role.changed':
      return [toUser('role-changed')];
    case 'user.deleted':
      return [
        toUser('account-deleted'),
        ...(actorIsAdmin ? [] : toAdmins('admin.account-deleted')),
      ];
    case 'enrollment.created':
      return [
        toUser('booking-confirmed'),
        ...toProvider('provider.new-enrollment'),
      ];
    default:
      return assertNever(event.eventType);
  }
}

function assertNever(value: never): never {
  throw new Error(`Tipo de evento no soportado: ${String(value)}`);
}
```

**Explicación:**
- Es la tabla del [contrato §3](./CONTRACT.md#3-tipos-de-evento) convertida en código: **función pura**, sin Nest y fácil de probar.
- **`idempotencyKey = eventId:plantilla:destinatario`:** un evento puede generar varios correos (usuario, admin, proveedor) y **cada uno** necesita su propia llave.
- `toAdmins` **filtra** al admin cuando el evento es sobre él mismo. `toProvider` sólo avisa si llegó `providerEmail`.
- **`assertNever`:** si se agrega un tipo de evento nuevo al contrato y no se maneja aquí, **TypeScript no compila**.

## Paso 2 (CA 2.4): Validación del payload

📄 **Crear** `notifications-service/src/infrastructure/messaging/notification-event.dto.ts`

```ts
import 'reflect-metadata';
import { plainToInstance, Type } from 'class-transformer';
import {
  IsArray,
  IsDefined,
  IsEmail,
  IsIn,
  IsInt,
  IsISO8601,
  IsNotEmpty,
  IsObject,
  IsOptional,
  IsString,
  IsUUID,
  ValidateNested,
  validate,
  type ValidationError,
} from 'class-validator';
import {
  NOTIFICATION_EVENT_TYPES,
  type NotificationEvent,
} from '../../domain/notification-event';

const ROLES = ['CUSTOMER', 'PROVIDER', 'ADMIN'];
const STATUSES = ['ACTIVE', 'INACTIVE'];

class ActorDto {
  @IsOptional() @IsUUID('all') id!: string | null;
  @IsEmail() email!: string;
  @IsOptional() @IsIn(ROLES) role!: string | null;
}

class SubjectDto {
  @IsUUID('all') id!: string;
  @IsString() @IsNotEmpty() name!: string;
  @IsEmail() email!: string;
  @IsIn(ROLES) role!: string;
  @IsIn(STATUSES) status!: string;
}

class AdminRecipientDto {
  @IsEmail() email!: string;
  @IsString() name!: string;
}

export class NotificationEventDto {
  @IsUUID('all') eventId!: string;
  @IsIn([...NOTIFICATION_EVENT_TYPES]) eventType!: string;
  @IsInt() @IsIn([1]) version!: number;
  @IsISO8601({ strict: true }) occurredAt!: string;

  @IsOptional()
  @ValidateNested()
  @Type(() => ActorDto)
  actor!: ActorDto | null;

  @IsDefined()
  @ValidateNested()
  @Type(() => SubjectDto)
  subject!: SubjectDto;

  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => AdminRecipientDto)
  admins!: AdminRecipientDto[];

  @IsObject() data!: Record<string, string>;
}

export class InvalidNotificationEventError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'InvalidNotificationEventError';
  }
}

/** Convierte el JSON recibido en un NotificationEvent válido o lanza InvalidNotificationEventError. */
export async function parseNotificationEvent(
  payload: unknown,
): Promise<NotificationEvent> {
  if (
    typeof payload !== 'object' ||
    payload === null ||
    Array.isArray(payload)
  ) {
    throw new InvalidNotificationEventError('El mensaje no es un objeto JSON');
  }

  const dto = plainToInstance(NotificationEventDto, payload);
  const errors = await validate(dto);
  if (errors.length > 0) {
    throw new InvalidNotificationEventError(formatErrors(errors));
  }

  const invalidData = Object.entries(dto.data).find(
    ([, value]) => typeof value !== 'string',
  );
  if (invalidData) {
    throw new InvalidNotificationEventError(
      `data.${invalidData[0]} debe ser string`,
    );
  }

  if (dto.eventType === 'enrollment.created' && !dto.data.serviceName) {
    throw new InvalidNotificationEventError(
      'data.serviceName es obligatorio en enrollment.created',
    );
  }

  return dto as unknown as NotificationEvent;
}

function formatErrors(errors: ValidationError[], parent = ''): string {
  return errors
    .flatMap((error) => {
      const path = parent ? `${parent}.${error.property}` : error.property;
      const own = Object.values(error.constraints ?? {}).map(
        (msg) => `${path}: ${msg}`,
      );
      const nested = error.children?.length
        ? [formatErrors(error.children, path)]
        : [];
      return [...own, ...nested];
    })
    .join('; ');
}
```

**Explicación:**
- **Nunca confíes en lo que llega por la cola.** Un bug en Java podría mandar un email vacío. Aquí se convierte en un error claro (`subject.email: must be an email`) y el mensaje va **directo a la DLQ** sin reintentar.
- `import 'reflect-metadata'` (primera línea): los decoradores `@Type` leen metadatos. Nest ya carga esa librería en la app, pero en las pruebas unitarias no; sin esta línea falla con `Reflect.getMetadata is not a function`.
- Las propiedades se tipan como `string` y no como *union types*: con `isolatedModules` + `emitDecoratorMetadata` (configuración por defecto de Nest 11), los tipos importados en propiedades decoradas pueden dar el error TS1272.
- Los **campos desconocidos se ignoran**: si Java agrega un campo, Nest no se rompe (compatibilidad hacia adelante).
- Regla extra: `enrollment.created` **exige** `data.serviceName` (sin él, la plantilla de reserva no tiene sentido).

## Paso 3 (CA 2.2): Puerto de idempotencia

📄 **Crear** `notifications-service/src/application/ports/idempotency.port.ts`

```ts
/** Resultado de intentar "reservar" un envío. */
export type ClaimResult = 'claimed' | 'already_sent' | 'in_progress';

/**
 * Puerto de salida: control de idempotencia (CA 2.2).
 * Garantiza que cada correo (llave = eventId:plantilla:destinatario) se envíe UNA sola vez.
 */
export abstract class IdempotencyPort {
  /** Reserva la llave. 'claimed' = puedes enviar; 'already_sent' = ya se envió; 'in_progress' = otro proceso lo está enviando. */
  abstract claim(key: string): Promise<ClaimResult>;
  /** Marca la llave como enviada (se recuerda IDEMPOTENCY_TTL_SECONDS). */
  abstract markSent(key: string): Promise<void>;
  /** Libera la reserva si el envío falló, para que el reintento pueda volver a intentarlo. */
  abstract release(key: string): Promise<void>;
}
```

**Explicación:** RabbitMQ entrega "**al menos una vez**": si el servicio se cae justo antes del ACK, el mensaje llega de nuevo. El puerto define 3 operaciones:
- **`claim`:** "reservo este correo".
- **`markSent`:** "ya salió".
- **`release`:** "falló, libéralo para el reintento".

Con el estado intermedio `processing` se evita que **dos réplicas** del servicio envíen el mismo correo al mismo tiempo.

## Paso 4 (CA 2.2): Redis

📄 **Crear** `notifications-service/src/infrastructure/messaging/redis/redis.provider.ts`

```ts
import {
  Inject,
  Injectable,
  Logger,
  type OnApplicationShutdown,
  type Provider,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';

/** Token de inyección del cliente Redis. Uso: @Inject(REDIS_CLIENT) private readonly redis: Redis */
export const REDIS_CLIENT = Symbol('REDIS_CLIENT');

export const redisClientProvider: Provider = {
  provide: REDIS_CLIENT,
  inject: [ConfigService],
  useFactory: (config: ConfigService): Redis => {
    const client = new Redis(config.getOrThrow<string>('REDIS_URL'), {
      maxRetriesPerRequest: 3,
    });
    const logger = new Logger('Redis');
    client.on('error', (error: Error) =>
      logger.error(`Redis: ${error.message}`),
    );
    return client;
  },
};

/** Cierra la conexión a Redis al apagar el servicio (SIGTERM de Docker). */
@Injectable()
export class RedisShutdown implements OnApplicationShutdown {
  constructor(@Inject(REDIS_CLIENT) private readonly redis: Redis) {}

  async onApplicationShutdown(): Promise<void> {
    await this.redis.quit();
  }
}
```

📄 **Crear** `notifications-service/src/infrastructure/messaging/redis/redis-idempotency.adapter.ts`

```ts
import { Inject, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type Redis from 'ioredis';
import {
  IdempotencyPort,
  type ClaimResult,
} from '../../../application/ports/idempotency.port';
import { REDIS_CLIENT } from './redis.provider';

const PREFIX = 'notifications:idempotency:';
/** Si el proceso muere a mitad de un envío, la reserva expira sola en 5 min. */
const PROCESSING_TTL_SECONDS = 300;

@Injectable()
export class RedisIdempotencyAdapter implements IdempotencyPort {
  private readonly sentTtlSeconds: number;

  constructor(
    @Inject(REDIS_CLIENT) private readonly redis: Redis,
    config: ConfigService,
  ) {
    this.sentTtlSeconds = Number(config.get('IDEMPOTENCY_TTL_SECONDS', 604800));
  }

  async claim(key: string): Promise<ClaimResult> {
    const redisKey = PREFIX + key;
    // SET ... NX: sólo escribe si la llave NO existe. Es atómico: dos consumidores
    // a la vez nunca pueden "ganar" los dos.
    const result = await this.redis.set(
      redisKey,
      'processing',
      'EX',
      PROCESSING_TTL_SECONDS,
      'NX',
    );
    if (result === 'OK') return 'claimed';
    const current = await this.redis.get(redisKey);
    return current === 'sent' ? 'already_sent' : 'in_progress';
  }

  async markSent(key: string): Promise<void> {
    await this.redis.set(PREFIX + key, 'sent', 'EX', this.sentTtlSeconds);
  }

  async release(key: string): Promise<void> {
    await this.redis.del(PREFIX + key);
  }
}
```

📄 **Crear** `notifications-service/src/infrastructure/messaging/redis/in-memory-idempotency.adapter.ts`

```ts
import { Injectable } from '@nestjs/common';
import {
  IdempotencyPort,
  type ClaimResult,
} from '../../../application/ports/idempotency.port';

/** Implementación en memoria para pruebas unitarias (mismo comportamiento que Redis). */
@Injectable()
export class InMemoryIdempotencyAdapter implements IdempotencyPort {
  readonly keys = new Map<string, 'processing' | 'sent'>();

  claim(key: string): Promise<ClaimResult> {
    const current = this.keys.get(key);
    if (current === 'sent') return Promise.resolve('already_sent');
    if (current === 'processing') return Promise.resolve('in_progress');
    this.keys.set(key, 'processing');
    return Promise.resolve('claimed');
  }

  markSent(key: string): Promise<void> {
    this.keys.set(key, 'sent');
    return Promise.resolve();
  }

  release(key: string): Promise<void> {
    this.keys.delete(key);
    return Promise.resolve();
  }
}
```

**Explicación:**
- **`REDIS_CLIENT`** es un token para inyectar el cliente de `ioredis`. Leonardo lo reutiliza en `/health`.
- **`SET key 'processing' EX 300 NX`:** escribe **sólo si la llave no existe**, y Redis lo hace de forma **atómica**. Si dos consumidores lo intentan a la vez, sólo uno gana. Si el proceso muere en medio del envío, la reserva expira sola a los 5 min.
- **`markSent`** guarda `'sent'` durante `IDEMPOTENCY_TTL_SECONDS` (7 días por defecto). En ese tiempo, cualquier re-entrega del mismo evento se ignora.
- **Prefijo `notifications:idempotency:`** + **base 1 de Redis** (`redis://redis:6379/1`): nunca choca con la caché del backend.
- **`RedisShutdown`** cierra la conexión al apagar el servicio.
- **`InMemoryIdempotencyAdapter`** tiene el mismo comportamiento, pero en memoria. Es sólo para pruebas unitarias.

## Paso 5: Caso de uso (orquestación)

📄 **Crear** `notifications-service/src/application/handle-notification-event.use-case.ts`

```ts
import { Injectable, Logger } from '@nestjs/common';
import { resolveNotifications } from '../domain/notification-policy';
import type { NotificationIntent } from '../domain/notification';
import type { NotificationEvent } from '../domain/notification-event';
import { maskEmail } from '../shared/mask-email';
import {
  EmailSenderPort,
  PermanentEmailError,
  RetryableEmailError,
} from './ports/email-sender.port';
import { IdempotencyPort } from './ports/idempotency.port';
import { NotificationLogPort } from './ports/notification-log.port';
import { TemplateRendererPort } from './ports/template-renderer.port';

export interface HandleResult {
  sent: number;
  skipped: number;
}

const toTagValue = (value: string): string =>
  value.replace(/[^a-zA-Z0-9_-]/g, '_');

@Injectable()
export class HandleNotificationEventUseCase {
  private readonly logger = new Logger(HandleNotificationEventUseCase.name);

  constructor(
    private readonly sender: EmailSenderPort,
    private readonly renderer: TemplateRendererPort,
    private readonly idempotency: IdempotencyPort,
    private readonly notificationLog: NotificationLogPort,
  ) {}

  /** Envía todos los correos del evento. Si uno falla, lanza el error (el consumidor decide reintentar). */
  async execute(event: NotificationEvent): Promise<HandleResult> {
    const result: HandleResult = { sent: 0, skipped: 0 };

    for (const intent of resolveNotifications(event)) {
      const claim = await this.idempotency.claim(intent.idempotencyKey);
      if (claim === 'already_sent') {
        result.skipped++;
        continue;
      }
      if (claim === 'in_progress') {
        throw new RetryableEmailError(
          `Envío en curso en otro proceso: ${intent.idempotencyKey}`,
        );
      }

      try {
        await this.sendOne(event, intent);
        await this.idempotency.markSent(intent.idempotencyKey);
        result.sent++;
      } catch (error) {
        await this.idempotency.release(intent.idempotencyKey);
        if (error instanceof PermanentEmailError) {
          await this.safeLog(() =>
            this.notificationLog.recordFailed({
              eventId: event.eventId,
              eventType: event.eventType,
              template: intent.template,
              recipient: intent.to,
              error: error.message,
            }),
          );
        }
        throw error;
      }
    }

    return result;
  }

  private async sendOne(
    event: NotificationEvent,
    intent: NotificationIntent,
  ): Promise<void> {
    const rendered = this.renderer.render(
      intent.template,
      event,
      intent.recipientName,
    );
    const sent = await this.sender.send({
      ...rendered,
      to: intent.to,
      idempotencyKey: intent.idempotencyKey,
      tags: [
        { name: 'event_type', value: toTagValue(event.eventType) },
        { name: 'template', value: toTagValue(intent.template) },
      ],
    });
    this.logger.log(
      `eventId=${event.eventId} template=${intent.template} to=${maskEmail(intent.to)} providerId=${sent.id}`,
    );
    await this.safeLog(() =>
      this.notificationLog.recordSent({
        eventId: event.eventId,
        eventType: event.eventType,
        template: intent.template,
        recipient: intent.to,
        providerMessageId: sent.id,
      }),
    );
  }

  /** El registro de auditoría nunca debe provocar un reenvío del correo: sus errores sólo se loguean. */
  private async safeLog(write: () => Promise<void>): Promise<void> {
    try {
      await write();
    } catch (error) {
      this.logger.warn(
        `No se pudo escribir en NotificationLog: ${error instanceof Error ? error.message : String(error)}`,
      );
    }
  }
}
```

**Explicación (flujo por cada correo del evento):**
1. **`claim`:**
   - si ya se envió, se **omite** (cuenta como duplicado);
   - si está en curso en otro proceso, se lanza `RetryableEmailError` y se reintenta más tarde.
2. **Renderiza** la plantilla (Dylan) y **envía** (Resend).
3. **`markSent`** y registra en **NotificationLog** con el id de Resend (Leonardo lo actualiza después con los webhooks).
4. **Si falla:** `release` (para que el reintento pueda enviar) y, si el error es **permanente**, se registra como `failed`. El error sube al consumidor.
- **`safeLog`:** si la base de datos del log falla, **no** se debe reenviar el correo, porque el usuario ya lo recibió. Por eso sólo se escribe un WARN.
- **Envío secuencial:** el plan gratis de Resend permite unas 2 peticiones por segundo.

## Paso 6 (CA 2.1 y 2.3): Topología de RabbitMQ

📄 **Crear** `notifications-service/src/infrastructure/messaging/rabbit.constants.ts`

```ts
/** Topología RabbitMQ — docs/notifications/CONTRACT.md §1 */
export const EVENTS_EXCHANGE = 'skillbridge.events';
export const ROUTING_KEYS = ['user.#', 'enrollment.#'];
export const EVENTS_QUEUE = 'notifications.events';
export const DEAD_LETTER_QUEUE = 'dlq.notifications';
export const RETRY_COUNT_HEADER = 'x-retry-count';

/**
 * Reintentos exponenciales (CA 2.3): una cola de espera por intento, con TTL creciente.
 * Al vencer el TTL, RabbitMQ devuelve el mensaje a EVENTS_QUEUE.
 */
export const RETRY_QUEUES = [
  { name: 'notifications.retry.5s', ttlMs: 5_000 },
  { name: 'notifications.retry.30s', ttlMs: 30_000 },
  { name: 'notifications.retry.2m', ttlMs: 120_000 },
] as const;

export const MAX_RETRIES = RETRY_QUEUES.length;
```

📄 **Crear** `notifications-service/src/infrastructure/messaging/rabbit-topology.service.ts`

```ts
import { AmqpConnection } from '@golevelup/nestjs-rabbitmq';
import { Injectable, Logger, type OnModuleInit } from '@nestjs/common';
import {
  DEAD_LETTER_QUEUE,
  EVENTS_QUEUE,
  RETRY_QUEUES,
} from './rabbit.constants';

/**
 * Declara la DLQ y las colas de espera (idempotente: si ya existen igual, no hace nada).
 *
 *  notifications.events ──(fallo n)──► consumidor publica en notifications.retry.<n>
 *  notifications.retry.<n> ──(vence TTL)──► vuelve a notifications.events
 *  dlq.notifications ◄── inválidos / permanentes / reintentos agotados
 */
@Injectable()
export class RabbitTopologyService implements OnModuleInit {
  private readonly logger = new Logger(RabbitTopologyService.name);

  constructor(private readonly amqp: AmqpConnection) {}

  async onModuleInit(): Promise<void> {
    await this.amqp.channel.assertQueue(DEAD_LETTER_QUEUE, { durable: true });
    for (const retry of RETRY_QUEUES) {
      await this.amqp.channel.assertQueue(retry.name, {
        durable: true,
        arguments: {
          'x-message-ttl': retry.ttlMs,
          'x-dead-letter-exchange': '',
          'x-dead-letter-routing-key': EVENTS_QUEUE,
        },
      });
    }
    this.logger.log(
      `Colas listas: ${DEAD_LETTER_QUEUE}, ${RETRY_QUEUES.map((r) => r.name).join(', ')}`,
    );
  }
}
```

**Explicación: reintentos exponenciales sin bloquear el consumidor:**

```
notifications.events ──falla (1ª vez)──► notifications.retry.5s  ──(5 s)──┐
                     ──falla (2ª vez)──► notifications.retry.30s ──(30 s)─┤──► vuelve a notifications.events
                     ──falla (3ª vez)──► notifications.retry.2m  ──(2 min)┘
                     ──falla (4ª vez)──► dlq.notifications  (con x-failure-reason)
```

- Cada cola `retry.*` **no tiene consumidores** y tiene un TTL fijo (`x-message-ttl`). Cuando el mensaje vence, RabbitMQ lo "da por muerto" y lo reenvía a su *dead-letter exchange* `''` (el exchange por defecto, que enruta por **nombre de cola**), o sea, **de vuelta a `notifications.events`**.
- Hay **una cola por nivel de espera** en vez de un TTL por mensaje en una sola cola. RabbitMQ sólo expira el mensaje que está **al frente**: un mensaje de 2 min bloquearía a uno de 5 s que esté detrás.
- `assertQueue` es **idempotente**: si la cola ya existe con los mismos argumentos, no hace nada.
- ⚠️ Si cambias un TTL después de crear la cola, RabbitMQ responde `PRECONDITION_FAILED`. Tienes que borrar esa cola en la UI y reiniciar.

## Paso 7: El consumidor

📄 **Crear** `notifications-service/src/infrastructure/messaging/notification-events.consumer.ts`

```ts
import { AmqpConnection, RabbitSubscribe } from '@golevelup/nestjs-rabbitmq';
import { Injectable, Logger } from '@nestjs/common';
import type { ConsumeMessage } from 'amqplib';
import { HandleNotificationEventUseCase } from '../../application/handle-notification-event.use-case';
import { PermanentEmailError } from '../../application/ports/email-sender.port';
import type { NotificationEvent } from '../../domain/notification-event';
import { MetricsService } from './metrics/metrics.service';
import { parseNotificationEvent } from './notification-event.dto';
import {
  DEAD_LETTER_QUEUE,
  EVENTS_EXCHANGE,
  EVENTS_QUEUE,
  MAX_RETRIES,
  RETRY_COUNT_HEADER,
  RETRY_QUEUES,
  ROUTING_KEYS,
} from './rabbit.constants';

type FailureReason =
  'invalid_payload' | 'permanent_error' | 'max_retries_exceeded';

const errorMessage = (error: unknown): string =>
  error instanceof Error ? error.message : String(error);

@Injectable()
export class NotificationEventsConsumer {
  private readonly logger = new Logger(NotificationEventsConsumer.name);

  constructor(
    private readonly handleEvent: HandleNotificationEventUseCase,
    private readonly amqp: AmqpConnection,
    private readonly metrics: MetricsService,
  ) {}

  @RabbitSubscribe({
    exchange: EVENTS_EXCHANGE,
    routingKey: ROUTING_KEYS,
    queue: EVENTS_QUEUE,
    queueOptions: {
      durable: true,
      arguments: {
        'x-dead-letter-exchange': '',
        'x-dead-letter-routing-key': DEAD_LETTER_QUEUE,
      },
    },
  })
  async onEvent(payload: unknown, amqpMsg: ConsumeMessage): Promise<void> {
    const stopTimer = this.metrics.startProcessingTimer();
    const retries = this.retryCountOf(amqpMsg);

    try {
      let event: NotificationEvent;
      try {
        event = await parseNotificationEvent(payload);
      } catch (error) {
        this.metrics.failedAttempt('invalid_payload');
        this.logger.error(`Mensaje inválido → DLQ: ${errorMessage(error)}`);
        await this.sendToDlq(
          payload,
          amqpMsg,
          'invalid_payload',
          errorMessage(error),
        );
        return;
      }

      try {
        const result = await this.handleEvent.execute(event);
        if (result.sent === 0 && result.skipped > 0) {
          this.metrics.duplicate(event.eventType);
          this.logger.warn(
            `Re-entrega ignorada eventId=${event.eventId} (ya enviado)`,
          );
          return;
        }
        this.metrics.processed(event.eventType);
        this.logger.log(
          `Procesado eventId=${event.eventId} type=${event.eventType} enviados=${result.sent} omitidos=${result.skipped} reintentos=${retries}`,
        );
      } catch (error) {
        this.metrics.failedAttempt(event.eventType);
        const permanent = error instanceof PermanentEmailError;

        if (!permanent && retries < MAX_RETRIES) {
          const target = RETRY_QUEUES[retries];
          this.logger.warn(
            `Fallo transitorio eventId=${event.eventId} → ${target.name} (reintento ${retries + 1}/${MAX_RETRIES}): ${errorMessage(error)}`,
          );
          await this.sendToRetry(payload, amqpMsg, retries + 1, target.name);
        } else {
          const reason: FailureReason = permanent
            ? 'permanent_error'
            : 'max_retries_exceeded';
          this.logger.error(
            `eventId=${event.eventId} → DLQ (${reason}): ${errorMessage(error)}`,
          );
          await this.sendToDlq(payload, amqpMsg, reason, errorMessage(error));
        }
      }
    } finally {
      stopTimer();
    }
  }

  private retryCountOf(msg: ConsumeMessage): number {
    const value: unknown = msg.properties.headers?.[RETRY_COUNT_HEADER];
    const count = Number(value ?? 0);
    return Number.isFinite(count) && count > 0 ? count : 0;
  }

  private baseHeaders(msg: ConsumeMessage): Record<string, unknown> {
    const headers: Record<string, unknown> = {
      ...(msg.properties.headers ?? {}),
    };
    delete headers['x-death']; // lo agrega RabbitMQ al hacer dead-letter; no se reenvía
    return headers;
  }

  private async sendToRetry(
    payload: unknown,
    msg: ConsumeMessage,
    retryCount: number,
    queue: string,
  ): Promise<void> {
    await this.amqp.publish('', queue, payload, {
      persistent: true,
      messageId: msg.properties.messageId as string | undefined,
      headers: { ...this.baseHeaders(msg), [RETRY_COUNT_HEADER]: retryCount },
    });
  }

  private async sendToDlq(
    payload: unknown,
    msg: ConsumeMessage,
    reason: FailureReason,
    detail: string,
  ): Promise<void> {
    await this.amqp.publish('', DEAD_LETTER_QUEUE, payload, {
      persistent: true,
      messageId: msg.properties.messageId as string | undefined,
      headers: {
        ...this.baseHeaders(msg),
        'x-failure-reason': reason,
        'x-failure-detail': detail.slice(0, 500),
        'x-failed-at': new Date().toISOString(),
      },
    });
  }
}
```

**Explicación:**
1. **`@RabbitSubscribe`:** golevelup crea la cola `notifications.events`, la enlaza a `skillbridge.events` con `user.#` y `enrollment.#`, y registra el método como consumidor.
   - `arguments` de la cola: si un error inesperado provoca un *nack*, el mensaje va a `dlq.notifications` en vez de perderse.
2. **Flujo:**
   - **Validar.** Si es inválido, va a la DLQ.
   - **Ejecutar.** Si todo ya se había enviado, cuenta como duplicado.
   - **Si falla:**
     - Error transitorio con reintentos disponibles: se republica en la cola de espera que toca (`RETRY_QUEUES[retries]`) con `x-retry-count + 1`.
     - Error permanente o reintentos agotados: va a la DLQ con `x-failure-reason`, `x-failure-detail` y `x-failed-at` (se ven en la UI de RabbitMQ).
3. **ACK:** cuando el método termina sin lanzar error, golevelup hace ACK. Como siempre republicamos **antes** de terminar, no se pierde nada.
4. **`delete headers['x-death']`:** RabbitMQ agrega ese header al hacer dead-letter, y no se debe reenviar.

## Paso 8: Métricas y módulo

📄 **Crear** `notifications-service/src/infrastructure/messaging/metrics/metrics.service.ts`

```ts
import { Injectable } from '@nestjs/common';
import {
  collectDefaultMetrics,
  Counter,
  Histogram,
  Registry,
} from 'prom-client';

/**
 * Los nombres coinciden con el dashboard que YA existe en
 * ops/grafana/provisioning/dashboards/notifications.json ("SkillBridge - Notificaciones").
 */
@Injectable()
export class MetricsService {
  readonly registry = new Registry();

  private readonly processedTotal = new Counter({
    name: 'skillbridge_notifications_processed_total',
    help: 'Eventos procesados con éxito (todos sus correos enviados)',
    labelNames: ['event_type'],
    registers: [this.registry],
  });

  private readonly duplicateTotal = new Counter({
    name: 'skillbridge_notifications_duplicate_total',
    help: 'Eventos cuyos correos ya se habían enviado (re-entregas)',
    labelNames: ['event_type'],
    registers: [this.registry],
  });

  private readonly failedAttemptsTotal = new Counter({
    name: 'skillbridge_notifications_failed_attempts_total',
    help: 'Intentos fallidos (incluye mensajes inválidos)',
    labelNames: ['event_type'],
    registers: [this.registry],
  });

  private readonly processingSeconds = new Histogram({
    name: 'skillbridge_notifications_processing_seconds',
    help: 'Tiempo de procesamiento de un evento',
    buckets: [0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10],
    registers: [this.registry],
  });

  constructor() {
    if (process.env.NODE_ENV !== 'test') {
      collectDefaultMetrics({
        register: this.registry,
        prefix: 'notifications_service_',
      });
    }
  }

  processed(eventType: string): void {
    this.processedTotal.inc({ event_type: eventType });
  }

  duplicate(eventType: string): void {
    this.duplicateTotal.inc({ event_type: eventType });
  }

  failedAttempt(eventType: string): void {
    this.failedAttemptsTotal.inc({ event_type: eventType });
  }

  startProcessingTimer(): () => void {
    const end = this.processingSeconds.startTimer();
    return () => {
      end();
    };
  }
}
```

📄 **Crear** `notifications-service/src/infrastructure/messaging/metrics/metrics.controller.ts`

```ts
import { Controller, Get, Res } from '@nestjs/common';
import type { Response } from 'express';
import { MetricsService } from './metrics.service';

@Controller('metrics')
export class MetricsController {
  constructor(private readonly metrics: MetricsService) {}

  @Get()
  async scrape(@Res() res: Response): Promise<void> {
    res.setHeader('Content-Type', this.metrics.registry.contentType);
    res.send(await this.metrics.registry.metrics());
  }
}
```

📝 **Reemplazar** `notifications-service/src/infrastructure/messaging/messaging.module.ts`

```ts
import {
  MessageHandlerErrorBehavior,
  RabbitMQModule,
} from '@golevelup/nestjs-rabbitmq';
import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { HandleNotificationEventUseCase } from '../../application/handle-notification-event.use-case';
import { IdempotencyPort } from '../../application/ports/idempotency.port';
import { TemplatesModule } from '../../templates/templates.module';
import { EmailModule } from '../email/email.module';
import { PersistenceModule } from '../persistence/persistence.module';
import { MetricsController } from './metrics/metrics.controller';
import { MetricsService } from './metrics/metrics.service';
import { NotificationEventsConsumer } from './notification-events.consumer';
import { EVENTS_EXCHANGE } from './rabbit.constants';
import { RabbitTopologyService } from './rabbit-topology.service';
import { RedisIdempotencyAdapter } from './redis/redis-idempotency.adapter';
import {
  REDIS_CLIENT,
  redisClientProvider,
  RedisShutdown,
} from './redis/redis.provider';

@Module({
  imports: [
    RabbitMQModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        uri: config.getOrThrow<string>('RABBITMQ_URL'),
        exchanges: [
          { name: EVENTS_EXCHANGE, type: 'topic', options: { durable: true } },
        ],
        prefetchCount: Number(config.get('RABBITMQ_PREFETCH', 5)),
        defaultSubscribeErrorBehavior: MessageHandlerErrorBehavior.NACK,
        connectionInitOptions: { wait: true, timeout: 30000 },
      }),
    }),
    EmailModule,
    TemplatesModule,
    PersistenceModule,
  ],
  controllers: [MetricsController],
  providers: [
    redisClientProvider,
    RedisShutdown,
    { provide: IdempotencyPort, useClass: RedisIdempotencyAdapter },
    MetricsService,
    RabbitTopologyService,
    HandleNotificationEventUseCase,
    NotificationEventsConsumer,
  ],
  // Leonardo usa estos dos en /health (KAN-58)
  exports: [RabbitMQModule, REDIS_CLIENT],
})
export class MessagingModule {}
```

**Explicación:**
- **Métricas:** el repo **ya tiene** un dashboard de Grafana para notificaciones (`ops/grafana/provisioning/dashboards/notifications.json`), y estos nombres coinciden con sus consultas. Leonardo configura Prometheus para leer `/metrics`.
- **`MessagingModule`:**
  - `RabbitMQModule.forRootAsync`: conecta con `RABBITMQ_URL`, también declara el exchange `skillbridge.events` (igual que Java, es idempotente) y espera hasta 30 s a RabbitMQ.
  - `prefetchCount: 5`: como máximo 5 mensajes sin ACK a la vez, para no saturar Resend.
  - `defaultSubscribeErrorBehavior: NACK`: red de seguridad para que ningún mensaje quede en un bucle infinito.
  - Importa `EmailModule`, `TemplatesModule` y `PersistenceModule`, que son los puertos que usa el caso de uso.
  - **`exports: [RabbitMQModule, REDIS_CLIENT]`:** Leonardo los usa en `/health`. **No los quites.**

## Paso 9: Pruebas

📄 **Crear** `notifications-service/src/domain/notification-policy.spec.ts`

```ts
import { loadFixture, type FixtureName } from '../test-support/load-fixture';
import type { NotificationEvent } from './notification-event';
import { resolveNotifications } from './notification-policy';

const fixture = (name: FixtureName): NotificationEvent =>
  loadFixture(name) as NotificationEvent;

describe('resolveNotifications', () => {
  it.each([
    ['user.registered', ['welcome', 'admin.new-registration']],
    ['user.created', ['account-created']],
    ['user.status.changed', ['status-changed', 'admin.status-changed']],
    ['user.role.changed', ['role-changed']],
    ['user.deleted', ['account-deleted', 'admin.account-deleted']],
    ['enrollment.created', ['booking-confirmed', 'provider.new-enrollment']],
  ] as const)('%s genera las plantillas correctas', (name, expected) => {
    expect(resolveNotifications(fixture(name)).map((n) => n.template)).toEqual(
      expected,
    );
  });

  it('no avisa a los admins si el cambio de estado lo hizo un admin', () => {
    const event = fixture('user.status.changed');
    event.actor = { id: null, email: 'admin@test.com', role: 'ADMIN' };
    expect(resolveNotifications(event).map((n) => n.template)).toEqual([
      'status-changed',
    ]);
  });

  it('no envía a un admin el aviso sobre sí mismo', () => {
    const event = fixture('user.registered');
    event.admins = [{ email: event.subject.email.toUpperCase(), name: 'Yo' }];
    expect(resolveNotifications(event)).toHaveLength(1);
  });

  it('no avisa al proveedor si no viene su correo', () => {
    const event = fixture('enrollment.created');
    delete event.data.providerEmail;
    expect(resolveNotifications(event).map((n) => n.template)).toEqual([
      'booking-confirmed',
    ]);
  });

  it('genera llaves de idempotencia únicas por correo', () => {
    const keys = resolveNotifications(fixture('user.registered')).map(
      (n) => n.idempotencyKey,
    );
    expect(new Set(keys).size).toBe(keys.length);
  });
});
```

📄 **Crear** `notifications-service/src/infrastructure/messaging/notification-event.dto.spec.ts`

```ts
import { FIXTURE_NAMES, loadFixture } from '../../test-support/load-fixture';
import {
  InvalidNotificationEventError,
  parseNotificationEvent,
} from './notification-event.dto';

describe('parseNotificationEvent', () => {
  it.each(FIXTURE_NAMES)('acepta el fixture del contrato %s', async (name) => {
    await expect(
      parseNotificationEvent(loadFixture(name)),
    ).resolves.toMatchObject({
      eventType: name,
    });
  });

  it('rechaza un email inválido en subject', async () => {
    const bad = loadFixture('user.created') as { subject: { email: string } };
    bad.subject.email = 'no-es-un-correo';
    await expect(parseNotificationEvent(bad)).rejects.toThrow(/subject\.email/);
  });

  it('rechaza un eventType desconocido', async () => {
    const bad = {
      ...(loadFixture('user.created') as object),
      eventType: 'user.hacked',
    };
    await expect(parseNotificationEvent(bad)).rejects.toBeInstanceOf(
      InvalidNotificationEventError,
    );
  });

  it('exige serviceName en enrollment.created', async () => {
    const bad = loadFixture('enrollment.created') as {
      data: Record<string, string>;
    };
    delete bad.data.serviceName;
    await expect(parseNotificationEvent(bad)).rejects.toThrow(/serviceName/);
  });

  it('rechaza algo que no es un objeto', async () => {
    await expect(parseNotificationEvent('hola')).rejects.toBeInstanceOf(
      InvalidNotificationEventError,
    );
  });
});
```

📄 **Crear** `notifications-service/src/application/handle-notification-event.use-case.spec.ts`

```ts
import { LogEmailAdapter } from '../infrastructure/email/log-email.adapter';
import { parseNotificationEvent } from '../infrastructure/messaging/notification-event.dto';
import { InMemoryIdempotencyAdapter } from '../infrastructure/messaging/redis/in-memory-idempotency.adapter';
import { InMemoryNotificationLogAdapter } from '../infrastructure/persistence/in-memory-notification-log.adapter';
import { BasicTemplateRenderer } from '../templates/basic-template.renderer';
import { loadFixture } from '../test-support/load-fixture';
import { HandleNotificationEventUseCase } from './handle-notification-event.use-case';
import {
  PermanentEmailError,
  RetryableEmailError,
} from './ports/email-sender.port';

describe('HandleNotificationEventUseCase', () => {
  let sender: LogEmailAdapter;
  let idempotency: InMemoryIdempotencyAdapter;
  let log: InMemoryNotificationLogAdapter;
  let useCase: HandleNotificationEventUseCase;

  beforeEach(() => {
    sender = new LogEmailAdapter();
    idempotency = new InMemoryIdempotencyAdapter();
    log = new InMemoryNotificationLogAdapter();
    useCase = new HandleNotificationEventUseCase(
      sender,
      new BasicTemplateRenderer(),
      idempotency,
      log,
    );
  });

  it('envía al usuario y al admin, y registra ambos en NotificationLog', async () => {
    const event = await parseNotificationEvent(loadFixture('user.registered'));

    await expect(useCase.execute(event)).resolves.toEqual({
      sent: 2,
      skipped: 0,
    });
    expect(sender.sent.map((e) => e.to)).toEqual([
      'delivered@resend.dev',
      'delivered+admin@resend.dev',
    ]);
    expect(log.records.map((r) => r.status)).toEqual(['sent', 'sent']);
  });

  it('no reenvía si el mismo evento llega dos veces (idempotencia)', async () => {
    const event = await parseNotificationEvent(loadFixture('user.registered'));
    await useCase.execute(event);

    await expect(useCase.execute(event)).resolves.toEqual({
      sent: 0,
      skipped: 2,
    });
    expect(sender.sent).toHaveLength(2);
  });

  it('libera la llave si el envío falla, para poder reintentar', async () => {
    jest
      .spyOn(sender, 'send')
      .mockRejectedValueOnce(new RetryableEmailError('429'));
    const event = await parseNotificationEvent(loadFixture('user.created'));

    await expect(useCase.execute(event)).rejects.toBeInstanceOf(
      RetryableEmailError,
    );
    expect(idempotency.keys.size).toBe(0);
  });

  it('registra el error permanente como "failed"', async () => {
    jest
      .spyOn(sender, 'send')
      .mockRejectedValueOnce(new PermanentEmailError('validation_error'));
    const event = await parseNotificationEvent(loadFixture('user.created'));

    await expect(useCase.execute(event)).rejects.toBeInstanceOf(
      PermanentEmailError,
    );
    expect(log.records[0]).toMatchObject({
      status: 'failed',
      error: 'validation_error',
    });
  });

  it('si otro proceso está enviando el mismo correo, pide reintentar más tarde', async () => {
    const event = await parseNotificationEvent(loadFixture('user.created'));
    idempotency.keys.set(
      `${event.eventId}:account-created:${event.subject.email}`,
      'processing',
    );

    await expect(useCase.execute(event)).rejects.toBeInstanceOf(
      RetryableEmailError,
    );
  });
});
```

📄 **Crear** `notifications-service/src/infrastructure/messaging/redis/redis-idempotency.adapter.spec.ts`

```ts
import { ConfigService } from '@nestjs/config';
import type Redis from 'ioredis';
import { RedisIdempotencyAdapter } from './redis-idempotency.adapter';

describe('RedisIdempotencyAdapter', () => {
  const redis = { set: jest.fn(), get: jest.fn(), del: jest.fn() };
  const adapter = new RedisIdempotencyAdapter(
    redis as unknown as Redis,
    new ConfigService({ IDEMPOTENCY_TTL_SECONDS: 3600 }),
  );

  beforeEach(() => jest.resetAllMocks());

  it('reserva la llave con SET NX y prefijo propio', async () => {
    redis.set.mockResolvedValue('OK');
    await expect(adapter.claim('evt:welcome:a@b.co')).resolves.toBe('claimed');
    expect(redis.set).toHaveBeenCalledWith(
      'notifications:idempotency:evt:welcome:a@b.co',
      'processing',
      'EX',
      300,
      'NX',
    );
  });

  it('detecta un correo ya enviado', async () => {
    redis.set.mockResolvedValue(null);
    redis.get.mockResolvedValue('sent');
    await expect(adapter.claim('k')).resolves.toBe('already_sent');
  });

  it('detecta un envío en curso', async () => {
    redis.set.mockResolvedValue(null);
    redis.get.mockResolvedValue('processing');
    await expect(adapter.claim('k')).resolves.toBe('in_progress');
  });

  it('marca como enviado con el TTL configurado', async () => {
    await adapter.markSent('k');
    expect(redis.set).toHaveBeenCalledWith(
      'notifications:idempotency:k',
      'sent',
      'EX',
      3600,
    );
  });
});
```

📄 **Crear** `notifications-service/src/infrastructure/messaging/notification-events.consumer.spec.ts`

```ts
/* eslint-disable @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-member-access */
import type { AmqpConnection } from '@golevelup/nestjs-rabbitmq';
import type { ConsumeMessage } from 'amqplib';
import type { HandleNotificationEventUseCase } from '../../application/handle-notification-event.use-case';
import {
  PermanentEmailError,
  RetryableEmailError,
} from '../../application/ports/email-sender.port';
import { loadFixture } from '../../test-support/load-fixture';
import { MetricsService } from './metrics/metrics.service';
import { NotificationEventsConsumer } from './notification-events.consumer';
import { DEAD_LETTER_QUEUE, RETRY_QUEUES } from './rabbit.constants';

const message = (headers: Record<string, unknown> = {}): ConsumeMessage =>
  ({
    properties: { headers, messageId: 'msg-1' },
  }) as unknown as ConsumeMessage;

describe('NotificationEventsConsumer', () => {
  const event = loadFixture('user.registered');
  let publish: jest.Mock;
  let execute: jest.Mock;
  let consumer: NotificationEventsConsumer;

  beforeEach(() => {
    publish = jest.fn().mockResolvedValue(true);
    execute = jest.fn().mockResolvedValue({ sent: 2, skipped: 0 });
    consumer = new NotificationEventsConsumer(
      { execute } as unknown as HandleNotificationEventUseCase,
      { publish } as unknown as AmqpConnection,
      new MetricsService(),
    );
  });

  it('procesa un evento válido sin republicar', async () => {
    await consumer.onEvent(event, message());
    expect(execute).toHaveBeenCalledTimes(1);
    expect(publish).not.toHaveBeenCalled();
  });

  it.each([
    [0, RETRY_QUEUES[0].name],
    [1, RETRY_QUEUES[1].name],
    [2, RETRY_QUEUES[2].name],
  ])(
    'con %i reintentos previos manda el fallo transitorio a %s (backoff exponencial)',
    async (previous, queue) => {
      execute.mockRejectedValueOnce(new RetryableEmailError('429'));
      await consumer.onEvent(event, message({ 'x-retry-count': previous }));
      expect(publish).toHaveBeenCalledWith(
        '',
        queue,
        event,
        expect.objectContaining({
          headers: expect.objectContaining({ 'x-retry-count': previous + 1 }),
        }),
      );
    },
  );

  it('manda a la DLQ cuando se agotan los 3 reintentos', async () => {
    execute.mockRejectedValueOnce(new RetryableEmailError('500'));
    await consumer.onEvent(event, message({ 'x-retry-count': 3 }));
    expect(publish).toHaveBeenCalledWith(
      '',
      DEAD_LETTER_QUEUE,
      event,
      expect.objectContaining({
        headers: expect.objectContaining({
          'x-failure-reason': 'max_retries_exceeded',
        }),
      }),
    );
  });

  it('manda a la DLQ sin reintentar un error permanente', async () => {
    execute.mockRejectedValueOnce(new PermanentEmailError('validation_error'));
    await consumer.onEvent(event, message());
    expect(publish).toHaveBeenCalledTimes(1);
    expect(publish.mock.calls[0][1]).toBe(DEAD_LETTER_QUEUE);
  });

  it('manda a la DLQ un mensaje que no cumple el contrato', async () => {
    await consumer.onEvent({ foo: 'bar' }, message());
    expect(execute).not.toHaveBeenCalled();
    expect(publish.mock.calls[0][1]).toBe(DEAD_LETTER_QUEUE);
  });
});
```

**Explicación de la estrategia:**
- **Política:** la tabla del contrato contra los 6 fixtures reales.
- **DTO:** los 6 fixtures deben ser válidos. Si Java y Nest no están de acuerdo, aquí se nota.
- **Caso de uso:** se usan los adaptadores **en memoria** (log, idempotencia y NotificationLog) en lugar de mocks complicados. Se prueba la idempotencia de punta a punta: la segunda vez se envían 0 correos.
- **Redis:** se verifica el comando exacto (`SET … NX`) con un cliente simulado.
- **Consumidor:** se simula `AmqpConnection.publish` para comprobar **a qué cola** se republica en cada intento (5 s, 30 s, 2 min y DLQ).
- El `eslint-disable` de la primera línea del spec del consumidor es necesario porque `expect.objectContaining` y `mock.calls` devuelven `any`.

```bash
cd notifications-service
npm run format && npm test && npm run lint:check && npm run build
```

## Paso 10: Prueba local con RabbitMQ y Redis reales

```bash
# Desde la raíz: levanta sólo la infraestructura (Leonardo crea la BD con make db-notifications)
docker compose up -d rabbitmq redis postgres
docker compose up -d --build notifications
docker compose logs -f notifications
```

1. Abre `http://localhost:15672` (`guest` / `guest`) → **Queues**. Deben aparecer `notifications.events`, `notifications.retry.5s`, `notifications.retry.30s`, `notifications.retry.2m` y `dlq.notifications`.
2. Publica `docs/notifications/contract/user.registered.json` siguiendo [CONTRACT §6](./CONTRACT.md#6-publicar-un-evento-a-mano-para-probar-sin-java). En el log deben aparecer 2 líneas `[EMAIL_DRIVER=log]` y `Procesado …`.
3. Publica el **mismo** mensaje otra vez. Debe salir `Re-entrega ignorada`.
4. Publica `{"hola":"mundo"}`. Debe aparecer en `dlq.notifications` con `x-failure-reason: invalid_payload`.
5. Comprueba la idempotencia en Redis:
   ```bash
   docker compose exec redis redis-cli -n 1 --scan --pattern 'notifications:idempotency:*'
   ```

## Paso 11: Subir

```bash
cd ~/Projects/SkillBridge-AI-JAVA-Angular-Celula-1
git pull origin feature/notificaciones
git add notifications-service/src/domain/notification-policy* \
        notifications-service/src/application/ports/idempotency.port.ts \
        notifications-service/src/application/handle-notification-event.use-case* \
        notifications-service/src/infrastructure/messaging
git status
git commit -m "feat(notificaciones): consumidor RabbitMQ con idempotencia en Redis, reintentos exponenciales y DLQ (KAN-57)"
git push -u origin feature/notificaciones-consumer
gh pr create --base feature/notificaciones --head feature/notificaciones-consumer \
  --title "feat(notificaciones): consumidor RabbitMQ + idempotencia + DLQ (KAN-57)" \
  --body "Cierra CA 2.1–2.4 de KAN-57: suscripción a skillbridge.events (user.#, enrollment.#), validación con class-validator, idempotencia por correo con Redis (SET NX), reintentos 5s/30s/2m y dlq.notifications, métricas Prometheus."
```

## Definition of Done (KAN-57)

- [ ] `npm test` (incluye tus 5 specs), `npm run lint:check` y `npm run build` en verde.
- [ ] Se crean las 5 colas al arrancar.
- [ ] Válido: se envía. Duplicado: no se reenvía. Inválido: va a la DLQ. Error transitorio: pasa por 5 s, 30 s y 2 min, y luego DLQ. Permanente: va a la DLQ directo.
- [ ] Ningún log imprime emails completos.
- [ ] Entrada en `MEMORIA.md` y KAN-57 movida a "En revisión".

## Problemas comunes

| Síntoma | Solución |
|---|---|
| `PRECONDITION_FAILED - inequivalent arg …` | La cola existía con otros argumentos. Bórrala en la UI de RabbitMQ y reinicia `notifications`. |
| Jest: `Must use import to load ES Module` | Alguien instaló una versión ESM: `npm install @nestjs/config@^4 @golevelup/nestjs-rabbitmq@^6`. |
| `Reflect.getMetadata is not a function` | Falta `import 'reflect-metadata'` en `notification-event.dto.ts`. |
| `Nest can't resolve dependencies of HandleNotificationEventUseCase (…, IdempotencyPort, …)` | Falta `{ provide: IdempotencyPort, useClass: RedisIdempotencyAdapter }` en `MessagingModule`. |
| El servicio no arranca: `connect ECONNREFUSED …:5672` | RabbitMQ no está arriba. Dentro de Docker el host es `rabbitmq`; fuera, `localhost`. |

## Prompt para otra IA

```text
Trabajo en notifications-service (NestJS 11 CommonJS, TypeScript estricto) de SkillBridge AI, historia
Jira KAN-57: consumidor RabbitMQ con @golevelup/nestjs-rabbitmq@6 (exchange topic skillbridge.events,
routing keys user.# y enrollment.#), validación con class-validator, idempotencia en Redis (ioredis,
SET NX) y reintentos exponenciales con colas TTL (5s/30s/2m) + dlq.notifications. Arquitectura
hexagonal (domain / application con puertos / infrastructure). Contrato: docs/notifications/CONTRACT.md.
Guía: docs/notifications/GUIA-KEVIN-HU-NOT02-KAN-57.md. Reglas: no hacer git commit/push, no exponer
secretos, registrar en MEMORIA.md. Estoy en el Paso <N> y obtengo: <error>. Explícame la causa y dame
el código corregido bloque por bloque con su explicación.
```
