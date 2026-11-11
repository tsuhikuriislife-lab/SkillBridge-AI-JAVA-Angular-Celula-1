# Guía Leonardo: KAN-58 / HU-NOT03, webhooks de Resend, auditoría de envíos y monitoreo de salud

> **Jira:** [KAN-58](https://skillbridgec3.atlassian.net/browse/KAN-58) (épica [KAN-49](https://skillbridgec3.atlassian.net/browse/KAN-49))
> **Responsable:** Leonardo Ayala
> **Rama:** `feature/notificaciones-webhooks-audit` (sale de `feature/notificaciones` **después** de que Dylan suba la base)
> **Antes de empezar lee:** [CONTRACT.md](./CONTRACT.md), la sección "Estructura" de la [guía de Dylan](./GUIA-DYLAN-HU-NOT01-KAN-56.md), [PLAN.md](./PLAN.md) y [CONFIGURACION §9 (webhooks)](./CONFIGURACION-DOMINIO-Y-SECRETOS.md#9-webhooks-de-resend-kan-58)
> **Requisitos:** Node.js 22, Docker y `cloudflared` o `ngrok` (para probar webhooks en local).

## Qué cubre esta guía (criterios de aceptación de KAN-58)

| CA | Qué | Dónde |
|---|---|---|
| CA 3.1 | `POST /webhooks/resend` con verificación de la firma Svix | Paso 5 |
| CA 3.2 | Procesa `email.sent`, `email.delivered`, `email.bounced` y `email.complained` (además de `delivery_delayed` y `failed`) | Paso 4 |
| CA 3.3 | Persistencia de `NotificationLog`: destinatario, plantilla, id de Resend, estado y fechas | Pasos 1-3 |
| CA 3.4 | `GET /health` con Terminus: RabbitMQ, Redis, PostgreSQL y API de Resend | Paso 6 |
| Tarea 3 | Swagger/OpenAPI | Paso 7 |
| Extra (operación) | Prometheus, CI y Makefile | Paso 8 |

## Tus archivos (no edites otros)

```
src/domain/delivery-status-transition.ts (+ .spec)
src/application/process-delivery-event.use-case.ts (+ .spec)
src/infrastructure/persistence/**          ← reemplazas el módulo en memoria por TypeORM
src/infrastructure/webhooks/**
src/infrastructure/observability/**        ← agregas /health completo
src/infrastructure/http/swagger.ts  +  2 líneas en src/main.ts
ops/postgres/init/*, ops/prometheus/prometheus.yml, .github/workflows/ci.yml, Makefile
docker-compose.yml → SÓLO el volumen del servicio postgres (Paso 1)
```

## Orden recomendado
1. **Bloque 1** (no depende de nadie): Pasos 1-5 (base de datos, log, webhooks).
2. **Bloque 2** (después de que se mergee el PR de **Kevin**, porque usas su `MessagingModule`): Pasos 6-8 (health, Swagger, Prometheus y CI).

## Paso 0: Rama

```bash
cd ~/Projects/SkillBridge-AI-JAVA-Angular-Celula-1
git fetch origin
git checkout feature/notificaciones && git pull origin feature/notificaciones
git checkout -b feature/notificaciones-webhooks-audit
cd notifications-service && npm install
```

---

# BLOQUE 1: Auditoría y webhooks

## Paso 1: Base de datos propia `notifications`

📄 **Crear** `ops/postgres/init/10-notifications-db.sql`

```sql
-- Crea la base de datos del microservicio de notificaciones si no existe.
-- Postgres ejecuta este archivo SOLO al crear el volumen por primera vez;
-- para volúmenes existentes usa: make db-notifications
SELECT 'CREATE DATABASE notifications'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'notifications')\gexec
```

📝 **Modificar** `docker-compose.yml`, **sólo** el servicio `postgres`. Agrega una línea en `volumes:`:

```yaml
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./ops/postgres/init:/docker-entrypoint-initdb.d:ro
```

📝 **Modificar** `Makefile`. Agrega `db-notifications test-notifications logs-notifications` a la línea `.PHONY` y estos targets al final (**indentación con TAB**):

```makefile
db-notifications:
	docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$$POSTGRES_USER" -d "$$POSTGRES_DB"' < ops/postgres/init/10-notifications-db.sql

test-notifications:
	cd notifications-service && npm ci && npm run lint:check && npm test

logs-notifications:
	docker compose logs -f --tail=200 notifications
```

```bash
# Crea la BD en tu volumen actual (sólo hace falta una vez por máquina)
docker compose up -d postgres
make db-notifications
docker compose exec postgres psql -U skillbridge -l | grep notifications   # debe aparecer
```

**Explicación:**
- **Base propia dentro del mismo contenedor** (decisión del equipo): cada servicio es dueño de sus datos. El backend sigue usando `skillbridge` con Flyway y el microservicio usa `notifications` con migraciones TypeORM. Ninguno toca las tablas del otro.
- `\gexec` ejecuta el resultado del `SELECT` como SQL. Así el script es **idempotente**: si la base ya existe, no hace nada.
- Los scripts de `/docker-entrypoint-initdb.d` **sólo** corren cuando el volumen está vacío. Por eso existe `make db-notifications` para quien ya tiene datos.
- En `Makefile`, `$$` se convierte en `$`, de modo que la variable la expande el `sh` del contenedor.

## Paso 2 (CA 3.3): Entidad y migración

📄 **Crear** `notifications-service/src/infrastructure/persistence/notification-log.entity.ts`

```ts
import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

/** Tabla notification_log (CA 3.3) en la base de datos PROPIA del microservicio: "notifications". */
@Entity({ name: 'notification_log' })
export class NotificationLogEntity {
  @PrimaryGeneratedColumn('uuid')
  id!: string;

  @Column({ name: 'event_id', type: 'uuid' })
  eventId!: string;

  @Column({ name: 'event_type', type: 'varchar', length: 60 })
  eventType!: string;

  @Column({ type: 'varchar', length: 60 })
  template!: string;

  @Column({ type: 'varchar', length: 254 })
  recipient!: string;

  @Column({
    name: 'provider_message_id',
    type: 'varchar',
    length: 100,
    nullable: true,
    unique: true,
  })
  providerMessageId!: string | null;

  @Column({ type: 'varchar', length: 30 })
  status!: string;

  @Column({ type: 'text', nullable: true })
  error!: string | null;

  @Column({ name: 'last_event_at', type: 'timestamptz' })
  lastEventAt!: Date;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt!: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt!: Date;
}
```

📄 **Crear** `notifications-service/src/infrastructure/persistence/migrations/1762880000000-create-notification-log.ts`

```ts
import type { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Migración inmutable (igual que Flyway en el backend): NUNCA se edita una vez mergeada.
 * Cambios futuros = nueva migración con timestamp mayor.
 */
export class CreateNotificationLog1762880000000 implements MigrationInterface {
  name = 'CreateNotificationLog1762880000000';

  async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      CREATE TABLE notification_log (
        id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        event_id            uuid         NOT NULL,
        event_type          varchar(60)  NOT NULL,
        template            varchar(60)  NOT NULL,
        recipient           varchar(254) NOT NULL,
        provider_message_id varchar(100) UNIQUE,
        status              varchar(30)  NOT NULL
          CHECK (status IN ('sent','delivery_delayed','delivered','bounced','complained','failed')),
        error               text,
        last_event_at       timestamptz  NOT NULL DEFAULT now(),
        created_at          timestamptz  NOT NULL DEFAULT now(),
        updated_at          timestamptz  NOT NULL DEFAULT now()
      )
    `);
    await queryRunner.query(
      `CREATE INDEX ix_notification_log_event_id ON notification_log (event_id)`,
    );
    await queryRunner.query(
      `CREATE INDEX ix_notification_log_status ON notification_log (status)`,
    );
  }

  async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS notification_log`);
  }
}
```

**Explicación:**
- **La entidad** mapea la tabla `notification_log` con los datos que pide el CA 3.3: `recipient`, `template`, `provider_message_id` (el id de Resend), `status`, `last_event_at`, `created_at` y `updated_at`. Además guarda `event_id` y `event_type` para rastrear de qué evento vino.
- **`provider_message_id` es `UNIQUE`:** el webhook busca por ese campo, así que necesita índice y no puede repetirse.
- **El `CHECK (status IN …)`** impide guardar estados inventados, igual que hace el backend en sus migraciones (`V8`, `V15`).
- **La migración es inmutable**, como la regla de Flyway en `AGENTS.md`. Si mañana hace falta una columna, se crea **otra** migración con un timestamp mayor.
- `gen_random_uuid()` viene incluido en PostgreSQL 13+ (usamos la 17).

## Paso 3 (CA 3.3): Adaptador TypeORM y módulo

📄 **Crear** `notifications-service/src/infrastructure/persistence/typeorm-notification-log.adapter.ts`

```ts
import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import type { DeliveryStatus } from '../../domain/delivery-status';
import {
  NotificationLogPort,
  type FailedNotification,
  type SentNotification,
} from '../../application/ports/notification-log.port';
import { NotificationLogEntity } from './notification-log.entity';

@Injectable()
export class TypeOrmNotificationLogAdapter implements NotificationLogPort {
  constructor(
    @InjectRepository(NotificationLogEntity)
    private readonly repository: Repository<NotificationLogEntity>,
  ) {}

  async recordSent(entry: SentNotification): Promise<void> {
    await this.repository.insert({
      ...entry,
      status: 'sent',
      error: null,
      lastEventAt: new Date(),
    });
  }

  async recordFailed(entry: FailedNotification): Promise<void> {
    await this.repository.insert({
      ...entry,
      error: entry.error.slice(0, 2000),
      providerMessageId: null,
      status: 'failed',
      lastEventAt: new Date(),
    });
  }

  async findStatus(providerMessageId: string): Promise<DeliveryStatus | null> {
    const row = await this.repository.findOne({
      where: { providerMessageId },
      select: { status: true },
    });
    return row ? (row.status as DeliveryStatus) : null;
  }

  async updateDeliveryStatus(
    providerMessageId: string,
    status: DeliveryStatus,
    occurredAt: Date,
  ): Promise<void> {
    await this.repository.update(
      { providerMessageId },
      { status, lastEventAt: occurredAt },
    );
  }
}
```

📝 **Reemplazar** `notifications-service/src/infrastructure/persistence/persistence.module.ts`

```ts
import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { NotificationLogPort } from '../../application/ports/notification-log.port';
import { CreateNotificationLog1762880000000 } from './migrations/1762880000000-create-notification-log';
import { NotificationLogEntity } from './notification-log.entity';
import { TypeOrmNotificationLogAdapter } from './typeorm-notification-log.adapter';

@Module({
  imports: [
    TypeOrmModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        type: 'postgres' as const,
        url: config.getOrThrow<string>('NOTIFICATIONS_DATABASE_URL'),
        entities: [NotificationLogEntity],
        migrations: [CreateNotificationLog1762880000000],
        migrationsRun: true, // aplica las migraciones pendientes al arrancar
        synchronize: false, // NUNCA true: el esquema sólo cambia con migraciones
      }),
    }),
    TypeOrmModule.forFeature([NotificationLogEntity]),
  ],
  providers: [
    { provide: NotificationLogPort, useClass: TypeOrmNotificationLogAdapter },
  ],
  exports: [NotificationLogPort],
})
export class PersistenceModule {}
```

**Explicación:**
- **El adaptador** implementa el `NotificationLogPort` de la base. Kevin lo llama sin saber que hay PostgreSQL detrás: eso es arquitectura hexagonal.
- **`migrationsRun: true`:** al arrancar se aplican las migraciones pendientes. TypeORM guarda el historial en su tabla `migrations`.
- **`synchronize: false` siempre:** con `true`, TypeORM modificaría la tabla según la entidad y podría **borrar datos**.
- **No borres** `in-memory-notification-log.adapter.ts`: lo usan las pruebas de Kevin y las tuyas.

## Paso 4 (CA 3.2): Procesar los eventos de entrega

📄 **Crear** `notifications-service/src/domain/delivery-status-transition.ts`

```ts
import type { DeliveryStatus } from './delivery-status';

/**
 * Orden del ciclo de vida. Los webhooks pueden llegar DESORDENADOS
 * (p. ej. "delivered" antes que "sent"): nunca se retrocede a un estado anterior.
 */
const RANK: Record<DeliveryStatus, number> = {
  sent: 0,
  delivery_delayed: 1,
  delivered: 2,
  bounced: 3,
  complained: 3,
  failed: 3,
};

export function canTransition(
  current: DeliveryStatus,
  next: DeliveryStatus,
): boolean {
  return next !== current && RANK[next] >= RANK[current];
}
```

📄 **Crear** `notifications-service/src/application/process-delivery-event.use-case.ts`

```ts
import { Injectable, Logger } from '@nestjs/common';
import type { DeliveryStatus } from '../domain/delivery-status';
import { canTransition } from '../domain/delivery-status-transition';
import { NotificationLogPort } from './ports/notification-log.port';

/** Forma mínima que nos interesa de un webhook de Resend. */
export interface DeliveryWebhookEvent {
  type: string;
  created_at: string;
  data?: { email_id?: string };
}

export type DeliveryOutcome = 'updated' | 'ignored' | 'not_found' | 'stale';

/** Eventos del ciclo de vida que se registran (CA 3.2). El resto (opened, clicked…) se ignora. */
const STATUS_BY_EVENT: Record<string, DeliveryStatus> = {
  'email.sent': 'sent',
  'email.delivery_delayed': 'delivery_delayed',
  'email.delivered': 'delivered',
  'email.bounced': 'bounced',
  'email.complained': 'complained',
  'email.failed': 'failed',
};

@Injectable()
export class ProcessDeliveryEventUseCase {
  private readonly logger = new Logger(ProcessDeliveryEventUseCase.name);

  constructor(private readonly notificationLog: NotificationLogPort) {}

  async execute(event: DeliveryWebhookEvent): Promise<DeliveryOutcome> {
    const next = STATUS_BY_EVENT[event.type];
    const emailId = event.data?.email_id;
    if (!next || !emailId) return 'ignored';

    const current = await this.notificationLog.findStatus(emailId);
    if (!current) {
      this.logger.warn(
        `Webhook ${event.type} de un correo que no está en el log: ${emailId}`,
      );
      return 'not_found';
    }
    if (!canTransition(current, next)) return 'stale';

    const occurredAt = new Date(event.created_at);
    await this.notificationLog.updateDeliveryStatus(
      emailId,
      next,
      Number.isNaN(occurredAt.getTime()) ? new Date() : occurredAt,
    );
    return 'updated';
  }
}
```

**Explicación:**
- **Los webhooks pueden llegar desordenados** (por ejemplo, `delivered` antes que `sent`). `canTransition` asigna un **rango** a cada estado y nunca permite retroceder: un `sent` tardío no pisa un `delivered`.
- `bounced`, `complained` y `failed` son estados **finales**.
- **`STATUS_BY_EVENT`** cubre los 4 eventos del CA 3.2, más `delivery_delayed` y `failed`. Los demás (`opened`, `clicked`) se **ignoran**: el tracking está desactivado por diseño (ver la guía de configuración).
- **Resultados posibles:** `updated`, `ignored`, `not_found` (por ejemplo, un correo enviado desde el dashboard de Resend y no por nosotros) o `stale` (llegó tarde).

## Paso 5 (CA 3.1): Endpoint del webhook con verificación de firma

📄 **Crear** `notifications-service/src/infrastructure/webhooks/resend-webhook.controller.ts`

```ts
import {
  BadRequestException,
  Controller,
  Headers,
  HttpCode,
  Inject,
  Logger,
  Post,
  Req,
  ServiceUnavailableException,
  UnauthorizedException,
  type RawBodyRequest,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  ApiHeader,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';
import type { Request } from 'express';
import type { Resend } from 'resend';
import {
  ProcessDeliveryEventUseCase,
  type DeliveryWebhookEvent,
} from '../../application/process-delivery-event.use-case';
import { RESEND_CLIENT } from '../email/resend.module';

@ApiTags('webhooks')
@Controller('webhooks')
export class ResendWebhookController {
  private readonly logger = new Logger(ResendWebhookController.name);
  private readonly webhookSecret: string;

  constructor(
    @Inject(RESEND_CLIENT) private readonly resend: Resend,
    private readonly processDelivery: ProcessDeliveryEventUseCase,
    config: ConfigService,
  ) {
    this.webhookSecret = config.get<string>('RESEND_WEBHOOK_SECRET', '');
  }

  @Post('resend')
  @HttpCode(200)
  @ApiOperation({
    summary:
      'Recibe eventos de entrega de Resend (sent, delivered, bounced, complained…)',
    description:
      'Verifica la firma Svix con RESEND_WEBHOOK_SECRET y actualiza NotificationLog.',
  })
  @ApiHeader({ name: 'svix-id', required: true })
  @ApiHeader({ name: 'svix-timestamp', required: true })
  @ApiHeader({ name: 'svix-signature', required: true })
  @ApiOkResponse({ description: 'Evento recibido (aunque se ignore).' })
  async handle(
    @Req() req: RawBodyRequest<Request>,
    @Headers('svix-id') id?: string,
    @Headers('svix-timestamp') timestamp?: string,
    @Headers('svix-signature') signature?: string,
  ): Promise<{ received: boolean; result: string }> {
    if (!this.webhookSecret) {
      throw new ServiceUnavailableException(
        'RESEND_WEBHOOK_SECRET no está configurado',
      );
    }
    const payload = req.rawBody?.toString('utf8');
    if (!payload || !id || !timestamp || !signature) {
      throw new BadRequestException('Faltan el cuerpo o las cabeceras svix-*');
    }

    let event: DeliveryWebhookEvent;
    try {
      event = this.resend.webhooks.verify({
        payload,
        headers: { id, timestamp, signature },
        webhookSecret: this.webhookSecret,
      }) as unknown as DeliveryWebhookEvent;
    } catch {
      this.logger.warn(`Webhook rechazado: firma inválida (svix-id=${id})`);
      throw new UnauthorizedException('Firma del webhook inválida');
    }

    const result = await this.processDelivery.execute(event);
    this.logger.log(
      `Webhook ${event.type} email_id=${event.data?.email_id ?? '-'} → ${result}`,
    );
    return { received: true, result };
  }
}
```

📝 **Reemplazar** `notifications-service/src/infrastructure/webhooks/webhooks.module.ts`

```ts
import { Module } from '@nestjs/common';
import { ProcessDeliveryEventUseCase } from '../../application/process-delivery-event.use-case';
import { PersistenceModule } from '../persistence/persistence.module';
import { ResendWebhookController } from './resend-webhook.controller';

@Module({
  imports: [PersistenceModule],
  controllers: [ResendWebhookController],
  providers: [ProcessDeliveryEventUseCase],
})
export class WebhooksModule {}
```

**Explicación:**
1. **¿Por qué verificar la firma?** La URL del webhook es pública. Sin la firma, **cualquiera** podría mandar `{"type":"email.delivered"}` y falsear la auditoría.
2. Resend firma cada webhook con **Svix**:
   - `svix-id`: id único del mensaje;
   - `svix-timestamp`: momento del envío (evita *replays* viejos);
   - `svix-signature`: HMAC-SHA256 del contenido, hecho con tu secreto `whsec_…`.
3. **`resend.webhooks.verify(...)`** (del SDK que encapsula `ResendModule`) recalcula la firma y **lanza un error** si no coincide. En ese caso respondemos **401**.
4. **Se verifica el cuerpo CRUDO** (`req.rawBody`, gracias a `rawBody: true` en `main.ts`). Si se usara el JSON ya parseado, un solo espacio de diferencia invalidaría la firma.
5. **Responde 200 incluso si el evento se ignora:** si respondemos 4xx o 5xx, Resend **reintenta** el webhook muchas veces.
6. Sin `RESEND_WEBHOOK_SECRET` configurado responde **503**: falla cerrado, nunca acepta webhooks sin verificar.

### 5.1 Pruebas

📄 **Crear** `notifications-service/src/domain/delivery-status-transition.spec.ts`

```ts
import { canTransition } from './delivery-status-transition';

describe('canTransition', () => {
  it.each([
    ['sent', 'delivered', true],
    ['sent', 'bounced', true],
    ['delivery_delayed', 'delivered', true],
    ['delivered', 'complained', true],
    ['delivered', 'sent', false],
    ['bounced', 'delivered', false],
    ['delivered', 'delivered', false],
  ] as const)('%s → %s = %s', (current, next, expected) => {
    expect(canTransition(current, next)).toBe(expected);
  });
});
```

📄 **Crear** `notifications-service/src/application/process-delivery-event.use-case.spec.ts`

```ts
import { InMemoryNotificationLogAdapter } from '../infrastructure/persistence/in-memory-notification-log.adapter';
import { ProcessDeliveryEventUseCase } from './process-delivery-event.use-case';

describe('ProcessDeliveryEventUseCase', () => {
  let log: InMemoryNotificationLogAdapter;
  let useCase: ProcessDeliveryEventUseCase;

  beforeEach(async () => {
    log = new InMemoryNotificationLogAdapter();
    useCase = new ProcessDeliveryEventUseCase(log);
    await log.recordSent({
      eventId: 'evt-1',
      eventType: 'user.registered',
      template: 'welcome',
      recipient: 'delivered@resend.dev',
      providerMessageId: 're_123',
    });
  });

  const webhook = (type: string, emailId = 're_123') => ({
    type,
    created_at: '2026-11-11T15:00:05Z',
    data: { email_id: emailId },
  });

  it('actualiza a delivered', async () => {
    await expect(useCase.execute(webhook('email.delivered'))).resolves.toBe(
      'updated',
    );
    expect(log.records[0].status).toBe('delivered');
  });

  it('registra un rebote', async () => {
    await useCase.execute(webhook('email.bounced'));
    expect(log.records[0].status).toBe('bounced');
  });

  it('no retrocede si llega "sent" después de "delivered"', async () => {
    await useCase.execute(webhook('email.delivered'));
    await expect(useCase.execute(webhook('email.sent'))).resolves.toBe('stale');
    expect(log.records[0].status).toBe('delivered');
  });

  it('ignora eventos que no son de entrega (email.opened)', async () => {
    await expect(useCase.execute(webhook('email.opened'))).resolves.toBe(
      'ignored',
    );
  });

  it('informa cuando el correo no está en el log', async () => {
    await expect(
      useCase.execute(webhook('email.delivered', 're_999')),
    ).resolves.toBe('not_found');
  });
});
```

📄 **Crear** `notifications-service/src/infrastructure/webhooks/resend-webhook.controller.spec.ts`

```ts
import { UnauthorizedException, type RawBodyRequest } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Request } from 'express';
import type { Resend } from 'resend';
import type { ProcessDeliveryEventUseCase } from '../../application/process-delivery-event.use-case';
import { ResendWebhookController } from './resend-webhook.controller';

describe('ResendWebhookController', () => {
  const body = JSON.stringify({
    type: 'email.delivered',
    data: { email_id: 're_1' },
  });
  const req = { rawBody: Buffer.from(body) } as RawBodyRequest<Request>;
  let verify: jest.Mock;
  let execute: jest.Mock;
  let controller: ResendWebhookController;

  beforeEach(() => {
    verify = jest.fn().mockReturnValue(JSON.parse(body));
    execute = jest.fn().mockResolvedValue('updated');
    controller = new ResendWebhookController(
      { webhooks: { verify } } as unknown as Resend,
      { execute } as unknown as ProcessDeliveryEventUseCase,
      new ConfigService({ RESEND_WEBHOOK_SECRET: 'whsec_test' }),
    );
  });

  it('verifica la firma con el cuerpo CRUDO y procesa el evento', async () => {
    await expect(
      controller.handle(req, 'msg_1', '1731337200', 'v1,abc'),
    ).resolves.toEqual({
      received: true,
      result: 'updated',
    });
    expect(verify).toHaveBeenCalledWith({
      payload: body,
      headers: { id: 'msg_1', timestamp: '1731337200', signature: 'v1,abc' },
      webhookSecret: 'whsec_test',
    });
  });

  it('responde 401 si la firma no es válida', async () => {
    verify.mockImplementation(() => {
      throw new Error('No matching signature found');
    });
    await expect(
      controller.handle(req, 'msg_1', '1', 'v1,mala'),
    ).rejects.toBeInstanceOf(UnauthorizedException);
    expect(execute).not.toHaveBeenCalled();
  });

  it('responde 400 si faltan cabeceras svix', async () => {
    await expect(
      controller.handle(req, undefined, '1', 'v1,abc'),
    ).rejects.toThrow(/svix/);
  });
});
```

**Explicación:** se simula `resend.webhooks.verify` para comprobar 3 cosas: que se use el cuerpo **crudo** con las 3 cabeceras, que una firma inválida dé **401** sin tocar la base de datos, y que falten cabeceras dé **400**. El caso de uso se prueba con el adaptador en memoria.

```bash
npm run format && npm test && npm run lint:check && npm run build
```

### 5.2 Subir el Bloque 1

```bash
cd ~/Projects/SkillBridge-AI-JAVA-Angular-Celula-1
git add ops/postgres Makefile docker-compose.yml \
        notifications-service/src/domain/delivery-status-transition* \
        notifications-service/src/application/process-delivery-event.use-case* \
        notifications-service/src/infrastructure/persistence notifications-service/src/infrastructure/webhooks
git commit -m "feat(notificaciones): NotificationLog en PostgreSQL y webhook de Resend con verificación Svix (KAN-58)"
git push -u origin feature/notificaciones-webhooks-audit
gh pr create --base feature/notificaciones --head feature/notificaciones-webhooks-audit \
  --title "feat(notificaciones): auditoría + webhooks Resend (KAN-58, parte 1)" \
  --body "CA 3.1–3.3: BD propia notifications, migración notification_log, adaptador TypeORM, POST /webhooks/resend con verificación de firma y transiciones de estado sin retroceso."
```

---

# BLOQUE 2: Salud, Swagger y operación (después del merge de Kevin)

```bash
git checkout feature/notificaciones && git pull
git checkout -b feature/notificaciones-health-ops
```

## Paso 6 (CA 3.4): `/health` con Terminus

📄 **Crear** `notifications-service/src/infrastructure/observability/indicators/rabbitmq.health.ts`

```ts
import { AmqpConnection } from '@golevelup/nestjs-rabbitmq';
import { Injectable } from '@nestjs/common';
import { HealthIndicatorService } from '@nestjs/terminus';

@Injectable()
export class RabbitMqHealthIndicator {
  constructor(
    private readonly amqp: AmqpConnection,
    private readonly health: HealthIndicatorService,
  ) {}

  check(key = 'rabbitmq') {
    const indicator = this.health.check(key);
    return this.amqp.managedConnection.isConnected()
      ? indicator.up()
      : indicator.down({ message: 'Sin conexión con RabbitMQ' });
  }
}
```

📄 **Crear** `notifications-service/src/infrastructure/observability/indicators/redis.health.ts`

```ts
import { Inject, Injectable } from '@nestjs/common';
import { HealthIndicatorService } from '@nestjs/terminus';
import type Redis from 'ioredis';
import { REDIS_CLIENT } from '../../messaging/redis/redis.provider';

@Injectable()
export class RedisHealthIndicator {
  constructor(
    @Inject(REDIS_CLIENT) private readonly redis: Redis,
    private readonly health: HealthIndicatorService,
  ) {}

  async check(key = 'redis') {
    const indicator = this.health.check(key);
    try {
      const pong = await this.redis.ping();
      return pong === 'PONG'
        ? indicator.up()
        : indicator.down({ message: pong });
    } catch (error) {
      return indicator.down({
        message: error instanceof Error ? error.message : String(error),
      });
    }
  }
}
```

📄 **Crear** `notifications-service/src/infrastructure/observability/indicators/resend.health.ts`

```ts
import { Inject, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { HealthIndicatorService } from '@nestjs/terminus';
import type { Resend } from 'resend';
import { RESEND_CLIENT } from '../../email/resend.module';

/** Resend limita las peticiones: se consulta como máximo una vez por minuto. */
const CACHE_MS = 60_000;

@Injectable()
export class ResendHealthIndicator {
  private lastCheck: { at: number; ok: boolean; message: string } | null = null;
  private readonly driver: string;

  constructor(
    @Inject(RESEND_CLIENT) private readonly resend: Pick<Resend, 'domains'>,
    private readonly health: HealthIndicatorService,
    config: ConfigService,
  ) {
    this.driver = config.get<string>('EMAIL_DRIVER', 'log');
  }

  async check(key = 'resend') {
    const indicator = this.health.check(key);
    if (this.driver !== 'resend') {
      return indicator.up({ mode: 'log (sin envío real)' });
    }
    if (!this.lastCheck || Date.now() - this.lastCheck.at > CACHE_MS) {
      try {
        const { error } = await this.resend.domains.list();
        this.lastCheck = {
          at: Date.now(),
          ok: !error,
          message: error?.message ?? 'ok',
        };
      } catch (error) {
        this.lastCheck = {
          at: Date.now(),
          ok: false,
          message: error instanceof Error ? error.message : String(error),
        };
      }
    }
    return this.lastCheck.ok
      ? indicator.up()
      : indicator.down({ message: this.lastCheck.message });
  }
}
```

📄 **Crear** `notifications-service/src/infrastructure/observability/health.controller.ts`

```ts
import { Controller, Get } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import {
  HealthCheck,
  HealthCheckService,
  TypeOrmHealthIndicator,
} from '@nestjs/terminus';
import { RabbitMqHealthIndicator } from './indicators/rabbitmq.health';
import { RedisHealthIndicator } from './indicators/redis.health';
import { ResendHealthIndicator } from './indicators/resend.health';

@ApiTags('health')
@Controller('health')
export class HealthController {
  constructor(
    private readonly health: HealthCheckService,
    private readonly database: TypeOrmHealthIndicator,
    private readonly rabbitmq: RabbitMqHealthIndicator,
    private readonly redis: RedisHealthIndicator,
    private readonly resend: ResendHealthIndicator,
  ) {}

  @Get()
  @HealthCheck()
  @ApiOperation({
    summary: 'Estado de RabbitMQ, Redis, PostgreSQL y Resend (CA 3.4)',
  })
  check() {
    return this.health.check([
      () => this.rabbitmq.check(),
      () => this.redis.check(),
      () => this.database.pingCheck('database', { timeout: 1500 }),
      () => this.resend.check(),
    ]);
  }
}
```

📝 **Reemplazar** `notifications-service/src/infrastructure/observability/observability.module.ts`

```ts
import { Module } from '@nestjs/common';
import { TerminusModule } from '@nestjs/terminus';
import { MessagingModule } from '../messaging/messaging.module';
import { HealthController } from './health.controller';
import { RabbitMqHealthIndicator } from './indicators/rabbitmq.health';
import { RedisHealthIndicator } from './indicators/redis.health';
import { ResendHealthIndicator } from './indicators/resend.health';
import { LivenessController } from './liveness.controller';

@Module({
  imports: [TerminusModule, MessagingModule],
  controllers: [LivenessController, HealthController],
  providers: [
    RabbitMqHealthIndicator,
    RedisHealthIndicator,
    ResendHealthIndicator,
  ],
})
export class ObservabilityModule {}
```

📄 **Crear** `notifications-service/src/infrastructure/observability/indicators/resend.health.spec.ts`

```ts
import { ConfigService } from '@nestjs/config';
import { HealthIndicatorService } from '@nestjs/terminus';
import { ResendHealthIndicator } from './resend.health';

describe('ResendHealthIndicator', () => {
  const health = new HealthIndicatorService();

  it('con EMAIL_DRIVER=log responde up sin llamar a Resend', async () => {
    const list = jest.fn();
    const indicator = new ResendHealthIndicator(
      { domains: { list } } as never,
      health,
      new ConfigService({ EMAIL_DRIVER: 'log' }),
    );
    await expect(indicator.check()).resolves.toMatchObject({
      resend: { status: 'up' },
    });
    expect(list).not.toHaveBeenCalled();
  });

  it('cachea la consulta a Resend durante 60 s', async () => {
    const list = jest
      .fn()
      .mockResolvedValue({ data: { data: [] }, error: null });
    const indicator = new ResendHealthIndicator(
      { domains: { list } } as never,
      health,
      new ConfigService({ EMAIL_DRIVER: 'resend' }),
    );
    await indicator.check();
    await indicator.check();
    expect(list).toHaveBeenCalledTimes(1);
  });

  it('responde down si la API key es inválida', async () => {
    const list = jest.fn().mockResolvedValue({
      data: null,
      error: { name: 'validation_error', message: 'API key is invalid' },
    });
    const indicator = new ResendHealthIndicator(
      { domains: { list } } as never,
      health,
      new ConfigService({ EMAIL_DRIVER: 'resend' }),
    );
    await expect(indicator.check()).resolves.toMatchObject({
      resend: { status: 'down' },
    });
  });
});
```

**Explicación:**
- Se usa la **API nueva de Terminus 11**: `HealthIndicatorService.check(key).up()` / `.down()`. Los indicadores son clases normales e inyectables, ya no hace falta heredar de `HealthIndicator`.
- **Qué revisa cada indicador:**
  - **RabbitMQ:** `managedConnection.isConnected()` de golevelup.
  - **Redis:** `PING`, que debe responder `PONG`.
  - **PostgreSQL:** `TypeOrmHealthIndicator.pingCheck`, incluido en Terminus.
  - **Resend:** `domains.list()`, que de paso valida la API key.
- **El check de Resend se cachea 60 s:** Docker y Prometheus consultan `/health` muy seguido, y Resend tiene límite de peticiones. Con `EMAIL_DRIVER=log` ni siquiera llama a Resend.
- **`/health`** devuelve 503 si algo cae (útil para monitoreo). **`/health/live`** (base) sólo dice "el proceso responde" y es el que usa Docker. Así un corte de Resend **no** marca el contenedor como *unhealthy*.
- `ObservabilityModule` importa `MessagingModule` (de Kevin), que exporta `RabbitMQModule` y `REDIS_CLIENT`.

Ejemplo de respuesta:

```json
{ "status": "ok",
  "info": { "rabbitmq": {"status":"up"}, "redis": {"status":"up"}, "database": {"status":"up"}, "resend": {"status":"up"} },
  "error": {}, "details": { "...": "..." } }
```

## Paso 7: Swagger / OpenAPI

📄 **Crear** `notifications-service/src/infrastructure/http/swagger.ts`

```ts
import type { INestApplication } from '@nestjs/common';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';

/** Documentación OpenAPI en http://localhost:3100/docs */
export function setupSwagger(app: INestApplication): void {
  const config = new DocumentBuilder()
    .setTitle('SkillBridge AI — Notifications Service')
    .setDescription(
      'Microservicio de notificaciones por correo (RabbitMQ → Resend). Webhooks de entrega y salud.',
    )
    .setVersion('1.0.0')
    .build();
  SwaggerModule.setup('docs', app, SwaggerModule.createDocument(app, config));
}
```

📝 **Reemplazar** `notifications-service/src/main.ts`

```ts
import { Logger, ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { setupSwagger } from './infrastructure/http/swagger';

async function bootstrap(): Promise<void> {
  // rawBody: true → guarda el cuerpo original de cada petición (necesario para verificar la firma de los webhooks de Resend)
  const app = await NestFactory.create(AppModule, { rawBody: true });
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }));
  app.enableShutdownHooks();
  setupSwagger(app);
  const port = Number(app.get(ConfigService).get('PORT', 3100));
  await app.listen(port);
  Logger.log(
    `notifications-service escuchando en el puerto ${port}`,
    'Bootstrap',
  );
}

void bootstrap();
```

**Explicación:** `SwaggerModule` lee los decoradores `@ApiTags`, `@ApiOperation` y `@ApiHeader` de los controladores y publica la documentación en **`http://localhost:3100/docs`**. En `main.ts` sólo se agregan el `import` y la línea `setupSwagger(app)`. El resto queda igual que en la base.

## Paso 8: Prometheus, CI

📝 **Modificar** `ops/prometheus/prometheus.yml`. Agrega al final de `scrape_configs` (misma indentación que `- job_name: livekit`):

```yaml
  - job_name: notifications-service
    metrics_path: /metrics
    static_configs:
      - targets: ["notifications:3100"]
```

📝 **Modificar** `.github/workflows/ci.yml`:

**8.1** Agrega este job después de `frontend` (al mismo nivel):

```yaml
  notifications:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: notifications-service
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: npm
          cache-dependency-path: notifications-service/package-lock.json
      - name: Install
        run: npm ci --no-audit --no-fund
      - name: Lint
        run: npm run lint:check
      - name: Test
        run: npm test -- --ci
      - name: Build
        run: npm run build
```

**8.2** En el job `docker`, cambia `needs: [backend, frontend]` por `needs: [backend, frontend, notifications]` y agrega al final de sus `steps`:

```yaml
      - name: Build notifications image
        uses: docker/build-push-action@v6
        with:
          context: ./notifications-service
          push: false
          tags: skillbridge-notifications:ci
```

**8.3** En `auto-merge-feature-pr`, cambia `needs: [backend, frontend, docker]` por `needs: [backend, frontend, notifications, docker]`.

**Explicación:**
- Prometheus lee `/metrics` (de Kevin) cada 15 s. El dashboard **"SkillBridge - Notificaciones"**, que ya existía en Grafana, empieza a mostrar datos.
- El CI **no necesita secretos**: las pruebas usan clientes simulados y adaptadores en memoria.
- El auto-merge espera a este job, así que no entra código del microservicio con pruebas rotas.

## Paso 9: Probar el webhook de punta a punta en local

Resend necesita una URL **pública** para mandar webhooks. En local se usa un túnel:

```bash
# 1) Levanta todo
make up

# 2) Túnel público hacia tu puerto 3100 (elige uno)
cloudflared tunnel --url http://localhost:3100      # imprime https://xxxx.trycloudflare.com
# ó: ngrok http 3100

# 3) En Resend → Webhooks → Add Endpoint:
#    URL:    https://xxxx.trycloudflare.com/webhooks/resend
#    Events: email.sent, email.delivered, email.delivery_delayed, email.bounced, email.complained, email.failed
#    Copia el "Signing secret" (whsec_...) a RESEND_WEBHOOK_SECRET en tu .env y reinicia:
docker compose up -d notifications
```

4. Registra un usuario o publica `user.registered.json`, con `EMAIL_DRIVER=resend`.
5. Revisa el log de auditoría:
   ```bash
   docker compose exec postgres psql -U skillbridge -d notifications \
     -c "SELECT template, recipient, status, provider_message_id, last_event_at FROM notification_log ORDER BY created_at DESC LIMIT 10;"
   ```
   El estado debe pasar de `sent` a `delivered` en segundos.
6. Prueba un **rebote**: publica un evento con `subject.email = "bounced@resend.dev"`. El estado debe quedar en `bounced`.
7. Prueba la **seguridad**: `curl -X POST localhost:3100/webhooks/resend -H 'svix-id: x' -H 'svix-timestamp: 1' -H 'svix-signature: v1,falsa' -d '{}'` debe responder **401**.
8. `curl -s localhost:3100/health | jq` y abre `http://localhost:3100/docs`.

> ⚠️ La URL de `trycloudflare.com` cambia cada vez que reinicias el túnel: actualízala en Resend. En producción se usa el dominio real.

## Paso 10: Subir el Bloque 2

```bash
cd ~/Projects/SkillBridge-AI-JAVA-Angular-Celula-1
cd notifications-service && npm run format && npm test && npm run lint:check && npm run build && cd ..
git add notifications-service/src/infrastructure/observability notifications-service/src/infrastructure/http \
        notifications-service/src/main.ts ops/prometheus/prometheus.yml .github/workflows/ci.yml
git commit -m "feat(notificaciones): health con Terminus, Swagger, scrape de Prometheus y job de CI (KAN-58)"
git push -u origin feature/notificaciones-health-ops
gh pr create --base feature/notificaciones --head feature/notificaciones-health-ops \
  --title "feat(notificaciones): health + Swagger + CI (KAN-58, parte 2)" \
  --body "CA 3.4: GET /health (RabbitMQ, Redis, PostgreSQL, Resend) con Terminus, documentación en /docs, scrape de Prometheus y job notifications en CI."
```

## Definition of Done (KAN-58)

- [ ] La base `notifications` existe y la tabla `notification_log` se crea sola al arrancar.
- [ ] Un correo enviado aparece como `sent` y pasa a `delivered` o `bounced` con los webhooks.
- [ ] Una firma inválida da 401 y no escribe nada.
- [ ] `/health` muestra los 4 componentes; `/docs` muestra los endpoints.
- [ ] El job `notifications` del CI está en verde.
- [ ] Entrada en `MEMORIA.md` y KAN-58 movida a "En revisión".

## Problemas comunes

| Síntoma | Solución |
|---|---|
| `database "notifications" does not exist` | `make db-notifications` |
| Webhook siempre da 401 | El secreto no corresponde a ese endpoint, o algún *proxy* modifica el cuerpo. Copia de nuevo el `whsec_` del endpoint correcto. |
| Webhook da 400 "Faltan el cuerpo…" | Falta `rawBody: true` en `NestFactory.create` (`main.ts`). |
| `Nest can't resolve dependencies of RabbitMqHealthIndicator (AmqpConnection…)` | El PR de Kevin aún no está mergeado, o `MessagingModule` no exporta `RabbitMQModule`. |
| El estado se queda en `sent` | El túnel se cayó o cambió de URL; revisa Resend → Webhooks → *Event log*. |

## Prompt para otra IA

```text
Trabajo en notifications-service (NestJS 11 CommonJS, TypeScript estricto) de SkillBridge AI, historia
Jira KAN-58: webhooks de Resend (POST /webhooks/resend, verificación Svix con resend.webhooks.verify y
rawBody), auditoría NotificationLog en PostgreSQL (BD propia "notifications", TypeORM 0.3 con
migraciones), GET /health con @nestjs/terminus 11 (HealthIndicatorService) para RabbitMQ, Redis,
PostgreSQL y Resend, y Swagger. Arquitectura hexagonal. Guía:
docs/notifications/GUIA-LEONARDO-HU-NOT03-KAN-58.md. Reglas: no hacer git commit/push, no exponer
secretos (RESEND_WEBHOOK_SECRET, RESEND_API_KEY), registrar en MEMORIA.md.
Estoy en el Paso <N> y obtengo: <error>. Explícame la causa y dame el código corregido bloque por bloque.
```
