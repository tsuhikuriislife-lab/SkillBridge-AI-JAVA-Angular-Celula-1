# Guía Persona B: Núcleo del microservicio NestJS (mensajería, política, métricas)

> **Rama:** `feature/notificaciones-nest-core` (sale de `feature/notificaciones`)
> **Tiempo estimado:** Bloque 0 (45 min, **base compartida**) + Bloques 1 y 2 (4 h). Ver [PLAN](./PLAN.md).
> **Antes de empezar lee:** [CONTRACT.md](./CONTRACT.md)
> **Requisitos:** Node.js 22 (`node -v`), npm 10+, Docker.

## Qué vas a construir

El esqueleto completo de `notification-service/` y todo lo que pasa **entre** RabbitMQ y "enviar un correo":

```
notification-service/src/
├── main.ts / app.module.ts              ← arranque y cableado
├── config/env.validation.ts             ← valida variables de entorno al arrancar
├── domain/                              ← tipos y reglas puras (sin Nest)
│   ├── user-event.ts                    ← forma del evento (contrato)
│   ├── notification.ts                  ← tipos de correo / plantillas
│   └── notification-policy.ts           ← QUIÉN recibe QUÉ correo
├── application/
│   ├── ports/email-sender.port.ts       ← puerto: "enviar correo" (C lo implementa con Resend)
│   ├── ports/template-renderer.port.ts  ← puerto: "renderizar plantilla" (C lo implementa)
│   └── handle-user-event.use-case.ts    ← caso de uso
├── infrastructure/
│   ├── messaging/                       ← RabbitMQ: consumidor, DTO, reintentos, DLQ
│   ├── email/                           ← LogEmailAdapter (falso) + módulo (C agrega Resend)
│   └── observability/                   ← /health y /metrics (Prometheus)
├── templates/                           ← renderer básico (C lo reemplaza por el definitivo)
├── shared/mask-email.ts
└── test-support/load-fixture.ts         ← lee docs/notifications/contract/*.json en las pruebas
```

> **Hexagonal también en Nest:** `domain` no importa nada de Nest ni de RabbitMQ. `application` sólo conoce puertos (clases abstractas). Toda la tecnología vive en `infrastructure`.

### División del trabajo con la Persona C
- **Tú (B)** haces el **Bloque 0** (Pasos 0 a 9) lo antes posible y lo subes a `feature/notificaciones`. C **depende** de esa base.
- Después C trabaja **sólo** en `src/infrastructure/email/resend-*`, `src/templates/*` (excepto `escape-html.ts`), `Dockerfile`, compose y CI. Tú sigues con mensajería, política, métricas y pruebas. Así no se pisan archivos.

---

# BLOQUE 0: Base compartida (45 min)

## Paso 0: Ramas

```bash
cd ~/Projects/SkillBridge-AI-JAVA-Angular-Celula-1
git fetch origin
git checkout feature/notificaciones        # la creó Dylan
git pull origin feature/notificaciones
```

## Paso 1: Crear el proyecto NestJS

```bash
# Genera el proyecto con NestJS 11, npm y TypeScript estricto (sin inicializar otro repo git)
npx @nestjs/cli@11 new notification-service --package-manager npm --skip-git --strict

cd notification-service

# Dependencias de ejecución:
#  @nestjs/config             → lectura de variables de entorno
#  joi                        → validación de esas variables
#  @golevelup/nestjs-rabbitmq → integración RabbitMQ (exchanges, colas, suscripciones con decorador)
#  class-validator / class-transformer → validar el JSON que llega
#  prom-client                → métricas para Prometheus
#  ⚠️ Las versiones van FIJADAS a propósito (ver explicación abajo)
npm install @nestjs/config@^4 joi@^18 @golevelup/nestjs-rabbitmq@^6 class-validator class-transformer prom-client

# Tipos de amqplib (la librería AMQP que usa golevelup por debajo)
npm install -D @types/amqplib

# Borra los archivos de ejemplo que trae el generador (no se usan)
rm src/app.controller.ts src/app.service.ts src/app.controller.spec.ts test/app.e2e-spec.ts

# Fija la versión de Node para todo el equipo
echo "22" > .nvmrc
```

**Por qué se fijan versiones (verificado el 2026-11-11):**
- `npx @nestjs/cli@11` genera un proyecto **NestJS 11 en CommonJS**. Pero `npm install` sin versión trae lo último:
  - `@nestjs/config` **12.x** es **sólo ESM** (pensado para Nest 12). Jest falla con `Must use import to load ES Module`. La línea para Nest 11 es la **4.x**.
  - `@golevelup/nestjs-rabbitmq` **7+** depende de `amqp-connection-manager@5` → `promise-breaker@7` (ESM), y Jest falla igual. La **6.x** usa dependencias CommonJS y tiene la misma API que usa esta guía.
- Con `@nestjs/config@4.0.4`, `@golevelup/nestjs-rabbitmq@6.1.0`, `joi@18`, `resend@6` y `prom-client@15`, el código de las guías B y C **compila, pasa ESLint y pasa las 38 pruebas**.

**Por qué `@golevelup/nestjs-rabbitmq` y no `@nestjs/microservices`:** el transporte RMQ oficial de Nest espera mensajes con la forma `{ "pattern": ..., "data": ... }`. Spring publica JSON plano en un exchange *topic*, y golevelup trabaja exactamente así: exchange, routing key y cola.

📝 **Modificar** el `.gitignore` de la **raíz del repo**. Agrega al final:

```gitignore
# Notification service (NestJS)
notification-service/node_modules/
notification-service/dist/
notification-service/coverage/
```

**Explicación:** el `.gitignore` raíz sólo ignora `frontend/node_modules`. Sin esto se subirían miles de archivos. `.env` ya está ignorado globalmente.

## Paso 2: Validación de variables de entorno

📄 **Crear** `notification-service/src/config/env.validation.ts`

```ts
import * as Joi from 'joi';

/**
 * Si falta una variable obligatoria o tiene un formato inválido, el servicio
 * NO arranca y muestra exactamente qué está mal (falla rápido).
 */
export const envValidationSchema = Joi.object({
  NODE_ENV: Joi.string()
    .valid('development', 'production', 'test')
    .default('development'),
  PORT: Joi.number().port().default(3100),

  // RabbitMQ
  RABBITMQ_URL: Joi.string()
    .uri({ scheme: ['amqp', 'amqps'] })
    .required(),
  RABBITMQ_PREFETCH: Joi.number().integer().min(1).default(5),
  NOTIFICATIONS_MAX_RETRIES: Joi.number().integer().min(0).max(10).default(3),
  NOTIFICATIONS_RETRY_DELAY_MS: Joi.number().integer().min(1000).default(30000),

  // Correo
  EMAIL_DRIVER: Joi.string().valid('log', 'resend').default('log'),
  RESEND_API_KEY: Joi.when('EMAIL_DRIVER', {
    is: 'resend',
    then: Joi.string().pattern(/^re_/).required(),
    otherwise: Joi.string().allow('').default(''),
  }),
  MAIL_FROM: Joi.string().default('SkillBridge AI <onboarding@resend.dev>'),
  MAIL_REPLY_TO: Joi.string().allow('').default(''),
  MAIL_REDIRECT_TO: Joi.string().allow('').default(''),
  FRONTEND_URL: Joi.string().uri().default('http://localhost:8088'),
});
```

**Explicación:**
- **`EMAIL_DRIVER`:** con `log` (por defecto) los correos **no se envían**, sólo se imprimen en el log. Así cualquiera del equipo, y el CI, puede levantar el servicio **sin** API key de Resend.
- **`Joi.when(...)`:** la API key sólo es obligatoria con `EMAIL_DRIVER=resend`, y debe empezar por `re_` (formato de Resend). Así se detecta enseguida si alguien pegó otra cosa.
- **`NOTIFICATIONS_*`:** controlan los reintentos (Paso 12).
- **`MAIL_REDIRECT_TO`:** la usa C. En desarrollo manda **todos** los correos a una sola casilla, para no escribirle a usuarios de prueba que no existen.

## Paso 3: Dominio (tipos puros)

📄 **Crear** `notification-service/src/domain/user-event.ts`

```ts
/** Contrato v1 — docs/notifications/CONTRACT.md */
export const USER_EVENT_TYPES = [
  'user.registered',
  'user.created',
  'user.status.changed',
  'user.role.changed',
  'user.deleted',
] as const;

export type UserEventType = (typeof USER_EVENT_TYPES)[number];
export type Role = 'CUSTOMER' | 'PROVIDER' | 'ADMIN';
export type AccountStatus = 'ACTIVE' | 'INACTIVE';

export interface EventActor {
  id: string | null;
  email: string;
  role: Role | null;
}

export interface EventSubject {
  id: string;
  name: string;
  email: string;
  role: Role;
  status: AccountStatus;
}

export interface AdminRecipient {
  email: string;
  name: string;
}

export interface UserEvent {
  eventId: string;
  eventType: UserEventType;
  version: number;
  occurredAt: string;
  actor: EventActor | null;
  subject: EventSubject;
  admins: AdminRecipient[];
  data: Record<string, string>;
}
```

📄 **Crear** `notification-service/src/domain/notification.ts`

```ts
export const TEMPLATE_KEYS = [
  'welcome',
  'admin.new-registration',
  'account-created',
  'status-changed',
  'admin.status-changed',
  'role-changed',
  'account-deleted',
  'admin.account-deleted',
] as const;

export type TemplateKey = (typeof TEMPLATE_KEYS)[number];

/** "Hay que mandar ESTA plantilla a ESTA persona" (todavía sin HTML). */
export interface NotificationIntent {
  template: TemplateKey;
  to: string;
  recipientName: string;
  idempotencyKey: string;
}

export interface RenderedEmail {
  subject: string;
  html: string;
  text: string;
}

export interface EmailTag {
  name: string;
  value: string;
}

export interface OutgoingEmail extends RenderedEmail {
  to: string;
  idempotencyKey: string;
  tags: EmailTag[];
}
```

**Explicación:**
- `as const` + `(typeof X)[number]` genera un *union type* (`'user.registered' | ...`) **y** un arreglo usable en runtime, que después se usa para validar. Una sola fuente, sin duplicar.
- `NotificationIntent` separa **la decisión** (a quién y qué plantilla) **del render** (HTML). Así la política se prueba sin plantillas.
- `idempotencyKey` viaja hasta Resend para que el mismo correo nunca se envíe dos veces.

## Paso 4: Puertos de aplicación

📄 **Crear** `notification-service/src/application/ports/email-sender.port.ts`

```ts
import type { OutgoingEmail } from '../../domain/notification';

export interface SentEmail {
  id: string;
}

/** Puerto de salida. Implementaciones: LogEmailAdapter (dev/test) y ResendEmailAdapter (C). */
export abstract class EmailSenderPort {
  abstract send(email: OutgoingEmail): Promise<SentEmail>;
}

/** Error transitorio (red, 429, 5xx): vale la pena reintentar. */
export class RetryableEmailError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'RetryableEmailError';
  }
}

/** Error permanente (datos inválidos, cuota agotada): reintentar no sirve → DLQ. */
export class PermanentEmailError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'PermanentEmailError';
  }
}
```

📄 **Crear** `notification-service/src/application/ports/template-renderer.port.ts`

```ts
import type { RenderedEmail, TemplateKey } from '../../domain/notification';
import type { UserEvent } from '../../domain/user-event';

/** Puerto de salida. Implementación final: SpanishTemplateRenderer (C). */
export abstract class TemplateRendererPort {
  abstract render(
    template: TemplateKey,
    event: UserEvent,
    recipientName: string,
  ): RenderedEmail;
}
```

**Explicación:**
- **¿Por qué `abstract class` y no `interface`?** En TypeScript las interfaces desaparecen al compilar, y Nest necesita un **token en runtime** para la inyección de dependencias. Una clase abstracta sirve de contrato **y** de token: `constructor(private sender: EmailSenderPort)`.
- **Los dos tipos de error** son la clave del sistema de reintentos: el consumidor (Paso 12) decide reintentar o mandar a la DLQ según la clase del error.

## Paso 5: Adaptador de correo falso + módulo

📄 **Crear** `notification-service/src/shared/mask-email.ts`

```ts
/** "ana.gomez@gmail.com" → "a***@gmail.com" (para logs sin datos personales). */
export function maskEmail(email: string): string {
  const [user, domain] = email.split('@');
  if (!user || !domain) return '***';
  return `${user[0]}***@${domain}`;
}
```

📄 **Crear** `notification-service/src/infrastructure/email/log-email.adapter.ts`

```ts
import { Injectable, Logger } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import type { OutgoingEmail } from '../../domain/notification';
import { EmailSenderPort, type SentEmail } from '../../application/ports/email-sender.port';
import { maskEmail } from '../../shared/mask-email';

/**
 * EMAIL_DRIVER=log → no envía nada, sólo registra. Útil en desarrollo, CI y pruebas.
 * Guarda los correos en memoria (`sent`) para poder inspeccionarlos en tests.
 */
@Injectable()
export class LogEmailAdapter implements EmailSenderPort {
  private readonly logger = new Logger(LogEmailAdapter.name);
  readonly sent: OutgoingEmail[] = [];

  send(email: OutgoingEmail): Promise<SentEmail> {
    this.sent.push(email);
    this.logger.log(
      `[EMAIL_DRIVER=log] to=${maskEmail(email.to)} subject="${email.subject}" key=${email.idempotencyKey}`,
    );
    return Promise.resolve({ id: `log-${randomUUID()}` });
  }
}
```

📄 **Crear** `notification-service/src/infrastructure/email/email.module.ts`

```ts
import { Module } from '@nestjs/common';
import { EmailSenderPort } from '../../application/ports/email-sender.port';
import { LogEmailAdapter } from './log-email.adapter';

/** C reemplazará este provider por una factory que elige entre "log" y "resend". */
@Module({
  providers: [{ provide: EmailSenderPort, useClass: LogEmailAdapter }],
  exports: [EmailSenderPort],
})
export class EmailModule {}
```

**Explicación:**
- `implements EmailSenderPort`: TypeScript permite "implementar" una clase abstracta, y te obliga a escribir `send` con la firma correcta.
- `send` **no** es `async` y devuelve `Promise.resolve(...)`: la configuración de ESLint de Nest 11 (`require-await`) marca error en un `async` sin `await`.
- `{ provide: EmailSenderPort, useClass: LogEmailAdapter }`: "cuando alguien pida `EmailSenderPort`, dale un `LogEmailAdapter`". Para cambiar a Resend sólo se toca **esta línea** (lo hace C).
- `maskEmail`: los logs no deben guardar correos completos (datos personales).

## Paso 6: Renderer básico (temporal)

📄 **Crear** `notification-service/src/templates/escape-html.ts`

```ts
const HTML_ENTITIES: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

/** Evita inyección de HTML: un usuario llamado "<script>" no debe ejecutar nada en el correo. */
export function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (char) => HTML_ENTITIES[char] ?? char);
}
```

📄 **Crear** `notification-service/src/templates/basic-template.renderer.ts`

```ts
import { Injectable } from '@nestjs/common';
import type { RenderedEmail, TemplateKey } from '../domain/notification';
import type { UserEvent } from '../domain/user-event';
import { TemplateRendererPort } from '../application/ports/template-renderer.port';
import { escapeHtml } from './escape-html';

/** Renderer mínimo para poder probar el flujo completo. C lo sustituye por SpanishTemplateRenderer. */
@Injectable()
export class BasicTemplateRenderer implements TemplateRendererPort {
  render(template: TemplateKey, event: UserEvent, recipientName: string): RenderedEmail {
    const text = `Hola ${recipientName}: evento ${event.eventType} (${template}).`;
    return {
      subject: `[SkillBridge AI] ${template}`,
      text,
      html: `<p>${escapeHtml(text)}</p>`,
    };
  }
}
```

📄 **Crear** `notification-service/src/templates/templates.module.ts`

```ts
import { Module } from '@nestjs/common';
import { TemplateRendererPort } from '../application/ports/template-renderer.port';
import { BasicTemplateRenderer } from './basic-template.renderer';

@Module({
  providers: [{ provide: TemplateRendererPort, useClass: BasicTemplateRenderer }],
  exports: [TemplateRendererPort],
})
export class TemplatesModule {}
```

**Explicación:** con este renderer temporal tu flujo funciona de punta a punta desde hoy, y C trabaja las plantillas reales en paralelo sin bloquearte.

## Paso 7: Helper de fixtures para pruebas

📄 **Crear** `notification-service/src/test-support/load-fixture.ts`

```ts
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/** Ruta a docs/notifications/contract desde notification-service/src/test-support */
export const CONTRACT_DIR = join(__dirname, '..', '..', '..', 'docs', 'notifications', 'contract');

export const FIXTURE_NAMES = [
  'user.registered',
  'user.created',
  'user.status.changed',
  'user.role.changed',
  'user.deleted',
] as const;

export function loadFixture(name: (typeof FIXTURE_NAMES)[number]): unknown {
  return JSON.parse(readFileSync(join(CONTRACT_DIR, `${name}.json`), 'utf8')) as unknown;
}
```

**Explicación:** Java y Nest prueban contra **los mismos JSON**. Si alguien cambia el contrato sin avisar, fallan las pruebas de ambos lados.

## Paso 8: `app.module.ts` y `main.ts` (versión base, sin RabbitMQ)

📝 **Reemplazar** `notification-service/src/app.module.ts`

```ts
import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { envValidationSchema } from './config/env.validation';
import { EmailModule } from './infrastructure/email/email.module';
import { TemplatesModule } from './templates/templates.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      validationSchema: envValidationSchema,
    }),
    EmailModule,
    TemplatesModule,
  ],
})
export class AppModule {}
```

📝 **Reemplazar** `notification-service/src/main.ts`

```ts
import { Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule);
  app.enableShutdownHooks(); // cierra la conexión AMQP ordenadamente al detener el contenedor
  const port = Number(app.get(ConfigService).get('PORT', 3100));
  await app.listen(port);
  Logger.log(`notification-service escuchando en el puerto ${port}`, 'Bootstrap');
}

void bootstrap();
```

📄 **Crear** `notification-service/.env.example`

```dotenv
# Copia este archivo a .env para correr el servicio fuera de Docker (npm run start:dev)
PORT=3100
RABBITMQ_URL=amqp://guest:guest@localhost:5672
EMAIL_DRIVER=log
RESEND_API_KEY=
MAIL_FROM=SkillBridge AI <onboarding@resend.dev>
MAIL_REPLY_TO=
MAIL_REDIRECT_TO=
FRONTEND_URL=http://localhost:8088
NOTIFICATIONS_MAX_RETRIES=3
NOTIFICATIONS_RETRY_DELAY_MS=30000
```

**Explicación:**
- `ConfigModule.forRoot({ isGlobal: true, validationSchema })`: lee `.env` y `process.env`, valida con Joi y deja `ConfigService` disponible en todos los módulos.
- `enableShutdownHooks()`: cuando Docker detiene el contenedor (SIGTERM), Nest cierra las conexiones. Así no quedan mensajes "a medio procesar".
- `void bootstrap()`: le dice a ESLint que ignoramos la promesa a propósito.

## Paso 9: Verificar y subir la base (para desbloquear a C)

```bash
# Da formato al código pegado (Prettier). Las guías tienen líneas largas por legibilidad,
# así que SIEMPRE ejecuta esto después de pegar código; si no, ESLint marca errores de formato.
npm run format

# Compila y corre las pruebas (aún no hay pruebas propias; debe compilar sin errores)
npm run build
npm test -- --passWithNoTests

# Vuelve a la raíz y sube la base directo a la rama de integración
cd ..
git checkout -b feature/notificaciones-nest-core   # tu rama de trabajo
git add .gitignore notification-service
git commit -m "feat(notificaciones): scaffold de notification-service (NestJS) con puertos y adaptador de log"
git push -u origin feature/notificaciones-nest-core

# PR rápido hacia la rama de integración (se auto-mergea cuando pasa el CI)
gh pr create --base feature/notificaciones --head feature/notificaciones-nest-core \
  --title "feat(notificaciones): base de notification-service" \
  --body "Scaffold NestJS 11, validación de entorno, dominio, puertos, LogEmailAdapter y renderer básico. Base para que la persona C trabaje Resend y plantillas."
```

> **Avisa en el chat del equipo:** "Base de notification-service lista en `feature/notificaciones`". C hace `git pull` y arranca.
> Si el auto-merge tarda, C puede hacer `git checkout feature/notificaciones-nest-core && git checkout -b feature/notificaciones-resend-ops`.

**Después del merge**, sigue en tu misma rama: `git pull origin feature/notificaciones`.

---

# BLOQUE 1: Mensajería y caso de uso

## Paso 10: Política de notificación (la regla de negocio)

📄 **Crear** `notification-service/src/domain/notification-policy.ts`

```ts
import type { NotificationIntent, TemplateKey } from './notification';
import type { UserEvent } from './user-event';

/**
 * Decide qué correos genera un evento (ver tabla en docs/notifications/CONTRACT.md §3).
 * Función pura: mismo evento → mismo resultado. Fácil de probar.
 */
export function resolveNotifications(event: UserEvent): NotificationIntent[] {
  const toUser = (template: TemplateKey): NotificationIntent => ({
    template,
    to: event.subject.email,
    recipientName: event.subject.name,
    idempotencyKey: `${event.eventId}:${template}:${event.subject.email}`,
  });

  const toAdmins = (template: TemplateKey): NotificationIntent[] =>
    event.admins
      .filter((admin) => admin.email.toLowerCase() !== event.subject.email.toLowerCase())
      .map((admin) => ({
        template,
        to: admin.email,
        recipientName: admin.name,
        idempotencyKey: `${event.eventId}:${template}:${admin.email}`,
      }));

  const actorIsAdmin = event.actor?.role === 'ADMIN';

  switch (event.eventType) {
    case 'user.registered':
      return [toUser('welcome'), ...toAdmins('admin.new-registration')];
    case 'user.created':
      return [toUser('account-created')];
    case 'user.status.changed':
      return [toUser('status-changed'), ...(actorIsAdmin ? [] : toAdmins('admin.status-changed'))];
    case 'user.role.changed':
      return [toUser('role-changed')];
    case 'user.deleted':
      return [toUser('account-deleted'), ...(actorIsAdmin ? [] : toAdmins('admin.account-deleted'))];
    default:
      return assertNever(event.eventType);
  }
}

function assertNever(value: never): never {
  throw new Error(`Tipo de evento no soportado: ${String(value)}`);
}
```

**Explicación:**
- **`toUser` / `toAdmins`:** generan las "intenciones". `toAdmins` **filtra** al admin cuando el evento es sobre él mismo.
- **`actorIsAdmin`:** si el admin hizo el cambio, no se le avisa de su propia acción. Si lo hizo el usuario (por ejemplo, se desactivó a sí mismo), sí.
- **`idempotencyKey = eventId:plantilla:destinatario`:** un evento puede generar 2 correos, y cada uno necesita su propia llave. Si se reintenta el evento completo, Resend descarta los que ya salieron.
- **`assertNever`:** si mañana se agrega un tipo nuevo a `USER_EVENT_TYPES` y no se maneja aquí, **TypeScript no compila**. Es un seguro contra olvidos.

## Paso 11: Caso de uso

📄 **Crear** `notification-service/src/application/handle-user-event.use-case.ts`

```ts
import { Injectable, Logger } from '@nestjs/common';
import { resolveNotifications } from '../domain/notification-policy';
import type { UserEvent } from '../domain/user-event';
import { maskEmail } from '../shared/mask-email';
import { EmailSenderPort } from './ports/email-sender.port';
import { TemplateRendererPort } from './ports/template-renderer.port';

const toTagValue = (value: string): string => value.replace(/[^a-zA-Z0-9_-]/g, '_');

@Injectable()
export class HandleUserEventUseCase {
  private readonly logger = new Logger(HandleUserEventUseCase.name);

  constructor(
    private readonly sender: EmailSenderPort,
    private readonly renderer: TemplateRendererPort,
  ) {}

  /** Envía todos los correos del evento. Devuelve cuántos se enviaron. Si uno falla, lanza el error. */
  async execute(event: UserEvent): Promise<number> {
    const intents = resolveNotifications(event);

    for (const intent of intents) {
      const rendered = this.renderer.render(intent.template, event, intent.recipientName);
      const result = await this.sender.send({
        ...rendered,
        to: intent.to,
        idempotencyKey: intent.idempotencyKey,
        tags: [
          { name: 'event_type', value: toTagValue(event.eventType) },
          { name: 'template', value: toTagValue(intent.template) },
        ],
      });
      this.logger.log(
        `eventId=${event.eventId} template=${intent.template} to=${maskEmail(intent.to)} providerId=${result.id}`,
      );
    }

    return intents.length;
  }
}
```

**Explicación:**
- Orquesta el flujo **política → render → envío**, y sólo conoce puertos: no sabe si detrás hay Resend o un log.
- **Envío secuencial (`for...of` con `await`):** el plan gratuito de Resend limita a ~2 peticiones por segundo, y en secuencia es más fácil razonar los fallos.
- **`tags`:** Resend permite filtrar correos por etiqueta en su dashboard. Sólo acepta letras, números, `_` y `-`, por eso `toTagValue` reemplaza los puntos.
- Si un envío falla, el error **sube** al consumidor, que decide si reintentar.

## Paso 12: Mensajería RabbitMQ

### 12.1 Constantes de topología

📄 **Crear** `notification-service/src/infrastructure/messaging/rabbit.constants.ts`

```ts
/** Ver docs/notifications/CONTRACT.md §1 */
export const EVENTS_EXCHANGE = 'skillbridge.events';
export const USER_EVENTS_ROUTING_KEY = 'user.#';
export const USER_EVENTS_QUEUE = 'notifications.user-events';
export const USER_EVENTS_RETRY_QUEUE = 'notifications.user-events.retry';
export const USER_EVENTS_DLQ = 'notifications.user-events.dlq';
export const RETRY_COUNT_HEADER = 'x-retry-count';
```

### 12.2 Validación del mensaje (DTO)

📄 **Crear** `notification-service/src/infrastructure/messaging/user-event.dto.ts`

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
import { USER_EVENT_TYPES, type UserEvent } from '../../domain/user-event';

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

export class UserEventDto {
  @IsUUID('all') eventId!: string;
  @IsIn([...USER_EVENT_TYPES]) eventType!: string;
  @IsInt() @IsIn([1]) version!: number;
  @IsISO8601({ strict: true }) occurredAt!: string;

  @IsOptional() @ValidateNested() @Type(() => ActorDto)
  actor!: ActorDto | null;

  @IsDefined() @ValidateNested() @Type(() => SubjectDto)
  subject!: SubjectDto;

  @IsArray() @ValidateNested({ each: true }) @Type(() => AdminRecipientDto)
  admins!: AdminRecipientDto[];

  @IsObject() data!: Record<string, string>;
}

export class InvalidUserEventError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'InvalidUserEventError';
  }
}

/** Convierte el JSON recibido en un UserEvent válido o lanza InvalidUserEventError. */
export async function parseUserEvent(payload: unknown): Promise<UserEvent> {
  if (typeof payload !== 'object' || payload === null || Array.isArray(payload)) {
    throw new InvalidUserEventError('El mensaje no es un objeto JSON');
  }

  const dto = plainToInstance(UserEventDto, payload);
  const errors = await validate(dto);
  if (errors.length > 0) {
    throw new InvalidUserEventError(formatErrors(errors));
  }

  const invalidData = Object.entries(dto.data).find(([, value]) => typeof value !== 'string');
  if (invalidData) {
    throw new InvalidUserEventError(`data.${invalidData[0]} debe ser string`);
  }

  return dto as unknown as UserEvent;
}

function formatErrors(errors: ValidationError[], parent = ''): string {
  return errors
    .flatMap((error) => {
      const path = parent ? `${parent}.${error.property}` : error.property;
      const own = Object.values(error.constraints ?? {}).map((msg) => `${path}: ${msg}`);
      const nested = error.children?.length ? [formatErrors(error.children, path)] : [];
      return [...own, ...nested];
    })
    .join('; ');
}
```

**Explicación:**
- **Nunca confíes en lo que llega por la cola.** Un bug en Java podría mandar un email vacío. Validar aquí convierte un error confuso de Resend en un mensaje claro ("subject.email: must be an email") que va a la DLQ.
- `plainToInstance` convierte el objeto plano en instancias de las clases DTO, y así los decoradores (`@IsEmail`…) pueden validar. `@Type(() => SubjectDto)` le indica la clase para los objetos anidados.
- Los campos **desconocidos se ignoran** a propósito: si Java agrega un campo nuevo, Nest no se rompe (compatibilidad hacia adelante).
- `import 'reflect-metadata'` (primera línea): los decoradores `@Type` leen metadatos de TypeScript. En la app, Nest ya carga esa librería, pero en las pruebas unitarias no, y sin esta línea falla con `Reflect.getMetadata is not a function`.
- Las propiedades usan `string` en vez de los *union types*: con `isolatedModules` + `emitDecoratorMetadata` (configuración por defecto de Nest 11), los tipos importados en propiedades decoradas pueden dar error TS1272. Después de validar, se convierte a `UserEvent`.

### 12.3 Memoria de eventos ya procesados

📄 **Crear** `notification-service/src/infrastructure/messaging/recent-event-ids.ts`

```ts
import { Injectable } from '@nestjs/common';

const CAPACITY = 5000;

/**
 * Recuerda los últimos eventIds procesados con éxito (en memoria, por instancia).
 * Primera línea de defensa contra duplicados; la segunda es la idempotencia de Resend (24 h).
 */
@Injectable()
export class RecentEventIds {
  private readonly ids = new Set<string>();

  has(eventId: string): boolean {
    return this.ids.has(eventId);
  }

  add(eventId: string): void {
    this.ids.add(eventId);
    if (this.ids.size > CAPACITY) {
      const oldest = this.ids.values().next().value as string | undefined;
      if (oldest !== undefined) this.ids.delete(oldest);
    }
  }
}
```

**Explicación:** RabbitMQ garantiza entrega "**al menos una vez**", así que un mensaje puede llegar 2 veces (por ejemplo, si el servicio se cae justo antes del ACK). Un `Set` recuerda el orden de inserción, así que borrar el primero equivale a borrar el más viejo. No se usa BD a propósito (ver [ADR 0005](../adr/0005-notification-microservice-nestjs-resend.md)).

### 12.4 Métricas (Prometheus)

📄 **Crear** `notification-service/src/infrastructure/observability/metrics.service.ts`

```ts
import { Injectable } from '@nestjs/common';
import { collectDefaultMetrics, Counter, Histogram, Registry } from 'prom-client';

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
    help: 'Eventos descartados por eventId repetido',
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
      collectDefaultMetrics({ register: this.registry, prefix: 'notification_service_' });
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

**Explicación:**
- El repo **ya tiene** un dashboard de Grafana para notificaciones (era de una versión vieja), y estos nombres coinciden con sus consultas. Por eso el dashboard funciona sin tocarlo.
- Se usa un `Registry` propio (no el global) para que cada prueba cree su instancia sin el error "metric already registered".
- `collectDefaultMetrics` agrega CPU, memoria y *event loop lag* de Node. Se desactiva en tests para que Jest no quede con *handles* abiertos.

📄 **Crear** `notification-service/src/infrastructure/observability/metrics.controller.ts`

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

📄 **Crear** `notification-service/src/infrastructure/observability/health.controller.ts`

```ts
import { AmqpConnection } from '@golevelup/nestjs-rabbitmq';
import { Controller, Get, ServiceUnavailableException } from '@nestjs/common';

@Controller('health')
export class HealthController {
  constructor(private readonly amqp: AmqpConnection) {}

  @Get()
  check(): { status: string; rabbitmq: string } {
    if (!this.amqp.managedConnection.isConnected()) {
      throw new ServiceUnavailableException({ status: 'down', rabbitmq: 'down' });
    }
    return { status: 'ok', rabbitmq: 'up' };
  }
}
```

**Explicación:**
- `/metrics` devuelve el texto que lee Prometheus. Se usa `@Res()` para fijar el `Content-Type` exacto que exige prom-client.
- `/health` responde **503** si se perdió la conexión con RabbitMQ. Docker (healthcheck que configura C) lo usa para marcar el contenedor como *unhealthy*.
- `import type { Response }`: con `isolatedModules` y decoradores, los tipos de parámetros decorados deben importarse con `import type`.

### 12.5 Topología de reintentos y DLQ

📄 **Crear** `notification-service/src/infrastructure/messaging/rabbit-topology.service.ts`

```ts
import { AmqpConnection } from '@golevelup/nestjs-rabbitmq';
import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { USER_EVENTS_DLQ, USER_EVENTS_QUEUE, USER_EVENTS_RETRY_QUEUE } from './rabbit.constants';

/**
 * Declara las colas auxiliares (idempotente: si ya existen con los mismos argumentos, no hace nada).
 *
 *  notifications.user-events ──(fallo)──► consumidor publica en .retry con expiration
 *  notifications.user-events.retry ──(expira)──► vuelve a notifications.user-events
 *  notifications.user-events.dlq  ◄── inválidos / permanentes / reintentos agotados
 */
@Injectable()
export class RabbitTopologyService implements OnModuleInit {
  private readonly logger = new Logger(RabbitTopologyService.name);

  constructor(private readonly amqp: AmqpConnection) {}

  async onModuleInit(): Promise<void> {
    await this.amqp.channel.assertQueue(USER_EVENTS_DLQ, { durable: true });
    await this.amqp.channel.assertQueue(USER_EVENTS_RETRY_QUEUE, {
      durable: true,
      arguments: {
        'x-dead-letter-exchange': '',
        'x-dead-letter-routing-key': USER_EVENTS_QUEUE,
      },
    });
    this.logger.log('Colas de reintento y DLQ declaradas');
  }
}
```

**Explicación del patrón *retry con espera*:**
1. La cola `.retry` **no tiene consumidores**. Los mensajes esperan ahí hasta que vence su `expiration` (por ejemplo, 30 s).
2. Al vencer, RabbitMQ los "da por muertos" y los reenvía a su *dead-letter exchange* `''` (el exchange por defecto, que enruta por nombre de cola) con la routing key `notifications.user-events`. Es decir, **vuelven a la cola principal**.
3. Así se espera entre intentos **sin bloquear** el consumidor ni usar `setTimeout`.
- El TTL va **por mensaje** (`expiration`) y no en la cola: si después cambian `NOTIFICATIONS_RETRY_DELAY_MS`, no hay que borrar la cola. Cambiar los argumentos de una cola existente provoca el error `PRECONDITION_FAILED`.

### 12.6 El consumidor

📄 **Crear** `notification-service/src/infrastructure/messaging/user-events.consumer.ts`

```ts
import { AmqpConnection, RabbitSubscribe } from '@golevelup/nestjs-rabbitmq';
import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { ConsumeMessage } from 'amqplib';
import { HandleUserEventUseCase } from '../../application/handle-user-event.use-case';
import { PermanentEmailError } from '../../application/ports/email-sender.port';
import type { UserEvent } from '../../domain/user-event';
import { MetricsService } from '../observability/metrics.service';
import {
  EVENTS_EXCHANGE,
  RETRY_COUNT_HEADER,
  USER_EVENTS_DLQ,
  USER_EVENTS_QUEUE,
  USER_EVENTS_RETRY_QUEUE,
  USER_EVENTS_ROUTING_KEY,
} from './rabbit.constants';
import { RecentEventIds } from './recent-event-ids';
import { parseUserEvent } from './user-event.dto';

type FailureReason = 'invalid_payload' | 'permanent_error' | 'max_retries_exceeded';

const errorMessage = (error: unknown): string =>
  error instanceof Error ? error.message : String(error);

@Injectable()
export class UserEventsConsumer {
  private readonly logger = new Logger(UserEventsConsumer.name);
  private readonly maxRetries: number;
  private readonly retryDelayMs: number;

  constructor(
    private readonly handleUserEvent: HandleUserEventUseCase,
    private readonly amqp: AmqpConnection,
    private readonly metrics: MetricsService,
    private readonly recentEvents: RecentEventIds,
    config: ConfigService,
  ) {
    this.maxRetries = Number(config.get('NOTIFICATIONS_MAX_RETRIES', 3));
    this.retryDelayMs = Number(config.get('NOTIFICATIONS_RETRY_DELAY_MS', 30000));
  }

  @RabbitSubscribe({
    exchange: EVENTS_EXCHANGE,
    routingKey: USER_EVENTS_ROUTING_KEY,
    queue: USER_EVENTS_QUEUE,
    queueOptions: {
      durable: true,
      arguments: {
        'x-dead-letter-exchange': '',
        'x-dead-letter-routing-key': USER_EVENTS_DLQ,
      },
    },
  })
  async onUserEvent(payload: unknown, amqpMsg: ConsumeMessage): Promise<void> {
    const stopTimer = this.metrics.startProcessingTimer();
    const attempt = this.retryCountOf(amqpMsg) + 1;

    try {
      let event: UserEvent;
      try {
        event = await parseUserEvent(payload);
      } catch (error) {
        this.metrics.failedAttempt('invalid_payload');
        this.logger.error(`Mensaje inválido → DLQ: ${errorMessage(error)}`);
        await this.sendToDlq(payload, amqpMsg, 'invalid_payload', errorMessage(error));
        return;
      }

      if (this.recentEvents.has(event.eventId)) {
        this.metrics.duplicate(event.eventType);
        this.logger.warn(`Evento duplicado ignorado eventId=${event.eventId}`);
        return;
      }

      try {
        const emails = await this.handleUserEvent.execute(event);
        this.recentEvents.add(event.eventId);
        this.metrics.processed(event.eventType);
        this.logger.log(
          `Procesado eventId=${event.eventId} type=${event.eventType} correos=${emails} intento=${attempt}`,
        );
      } catch (error) {
        this.metrics.failedAttempt(event.eventType);
        const permanent = error instanceof PermanentEmailError;

        if (!permanent && attempt <= this.maxRetries) {
          this.logger.warn(
            `Fallo transitorio eventId=${event.eventId} (reintento ${attempt}/${this.maxRetries} en ${this.retryDelayMs} ms): ${errorMessage(error)}`,
          );
          await this.sendToRetry(payload, amqpMsg, attempt);
        } else {
          const reason: FailureReason = permanent ? 'permanent_error' : 'max_retries_exceeded';
          this.logger.error(`eventId=${event.eventId} → DLQ (${reason}): ${errorMessage(error)}`);
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
    return Number.isFinite(count) ? count : 0;
  }

  private baseHeaders(msg: ConsumeMessage): Record<string, unknown> {
    const headers: Record<string, unknown> = { ...(msg.properties.headers ?? {}) };
    delete headers['x-death']; // lo agrega RabbitMQ al hacer dead-letter; no se reenvía
    return headers;
  }

  private async sendToRetry(payload: unknown, msg: ConsumeMessage, attempt: number): Promise<void> {
    await this.amqp.publish('', USER_EVENTS_RETRY_QUEUE, payload, {
      persistent: true,
      messageId: msg.properties.messageId as string | undefined,
      expiration: String(this.retryDelayMs),
      headers: { ...this.baseHeaders(msg), [RETRY_COUNT_HEADER]: attempt },
    });
  }

  private async sendToDlq(
    payload: unknown,
    msg: ConsumeMessage,
    reason: FailureReason,
    detail: string,
  ): Promise<void> {
    await this.amqp.publish('', USER_EVENTS_DLQ, payload, {
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

**Explicación por partes:**
1. **`@RabbitSubscribe`:** golevelup crea (si no existe) la cola `notifications.user-events`, la enlaza al exchange `skillbridge.events` con `user.#` (todos los eventos `user.*`, incluso los que tengan más puntos) y registra este método como consumidor.
   - El `arguments` de la cola configura que, si un mensaje se rechaza (*nack*) por un error inesperado, vaya a la DLQ en vez de perderse.
2. **Flujo dentro de `onUserEvent`:**
   - **Validar.** Si es inválido va a la DLQ **sin reintentar** (reintentar un JSON malo no lo arregla).
   - **Deduplicar.** Si ya se procesó, se ignora y se suma la métrica `duplicate`.
   - **Ejecutar el caso de uso.** Si todo sale bien, se marca como procesado.
   - **Si falla:**
     - Error **transitorio** con intentos disponibles: se republica en `.retry` con `x-retry-count + 1`.
     - Error **permanente** o intentos agotados: se publica en la DLQ con el motivo en headers (`x-failure-reason`). Así se puede leer en la UI de RabbitMQ por qué falló.
3. **¿Y el ACK?** Cuando el método termina **sin lanzar** error, golevelup hace ACK automático. Como siempre republicamos (en `.retry` o en la DLQ) antes de terminar, el mensaje original se puede borrar sin perder nada.
4. **`finally { stopTimer() }`:** mide la duración de **todos** los casos para el histograma.
5. **`attempt <= maxRetries`:** con `maxRetries=3` hay 1 intento original + 3 reintentos y después DLQ.

## Paso 13: Cablear todo en `app.module.ts`

📝 **Reemplazar** `notification-service/src/app.module.ts`

```ts
import { MessageHandlerErrorBehavior, RabbitMQModule } from '@golevelup/nestjs-rabbitmq';
import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { HandleUserEventUseCase } from './application/handle-user-event.use-case';
import { envValidationSchema } from './config/env.validation';
import { EmailModule } from './infrastructure/email/email.module';
import { EVENTS_EXCHANGE } from './infrastructure/messaging/rabbit.constants';
import { RabbitTopologyService } from './infrastructure/messaging/rabbit-topology.service';
import { RecentEventIds } from './infrastructure/messaging/recent-event-ids';
import { UserEventsConsumer } from './infrastructure/messaging/user-events.consumer';
import { HealthController } from './infrastructure/observability/health.controller';
import { MetricsController } from './infrastructure/observability/metrics.controller';
import { MetricsService } from './infrastructure/observability/metrics.service';
import { TemplatesModule } from './templates/templates.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      validationSchema: envValidationSchema,
    }),
    RabbitMQModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        uri: config.getOrThrow<string>('RABBITMQ_URL'),
        exchanges: [{ name: EVENTS_EXCHANGE, type: 'topic', options: { durable: true } }],
        prefetchCount: Number(config.get('RABBITMQ_PREFETCH', 5)),
        defaultSubscribeErrorBehavior: MessageHandlerErrorBehavior.NACK,
        connectionInitOptions: { wait: true, timeout: 30000 },
      }),
    }),
    EmailModule,
    TemplatesModule,
  ],
  controllers: [HealthController, MetricsController],
  providers: [
    MetricsService,
    RecentEventIds,
    RabbitTopologyService,
    HandleUserEventUseCase,
    UserEventsConsumer,
  ],
})
export class AppModule {}
```

**Explicación:**
- **`exchanges`:** Nest **también** declara `skillbridge.events` con los mismos parámetros que Java (topic, durable). Declarar es idempotente, así que no importa quién arranque primero.
- **`prefetchCount: 5`:** RabbitMQ entrega como máximo 5 mensajes sin ACK a la vez. Evita saturar Resend (≈2 req/s en el plan gratis).
- **`defaultSubscribeErrorBehavior: NACK`:** red de seguridad. Si algo lanza un error que no atrapamos (o el JSON ni siquiera se puede parsear), el mensaje se rechaza **sin reencolar** y la cola lo manda a la DLQ. Si no, podría quedar en un bucle infinito.
- **`connectionInitOptions.wait: true`:** el servicio espera hasta 30 s a que RabbitMQ esté listo antes de arrancar.

> ⚠️ **Compatibilidad de versión:** verificado con `@golevelup/nestjs-rabbitmq@6.1.0`. Comprueba con `npm ls @golevelup/nestjs-rabbitmq`. Si alguien instaló la 7 o superior, las pruebas fallan con `Must use import to load ES Module` (ver Paso 1).

---

# BLOQUE 2: Pruebas y prueba de punta a punta

## Paso 14: Pruebas unitarias

📄 **Crear** `notification-service/src/domain/notification-policy.spec.ts`

```ts
import { loadFixture } from '../test-support/load-fixture';
import { resolveNotifications } from './notification-policy';
import type { UserEvent } from './user-event';

const fixture = (name: Parameters<typeof loadFixture>[0]): UserEvent => loadFixture(name) as UserEvent;

describe('resolveNotifications', () => {
  it.each([
    ['user.registered', ['welcome', 'admin.new-registration']],
    ['user.created', ['account-created']],
    ['user.status.changed', ['status-changed', 'admin.status-changed']],
    ['user.role.changed', ['role-changed']],
    ['user.deleted', ['account-deleted', 'admin.account-deleted']],
  ] as const)('%s genera las plantillas correctas', (name, expected) => {
    const templates = resolveNotifications(fixture(name)).map((n) => n.template);
    expect(templates).toEqual(expected);
  });

  it('no avisa a los admins si el cambio de estado lo hizo un admin', () => {
    const event = fixture('user.status.changed');
    event.actor = { id: null, email: 'admin@test.com', role: 'ADMIN' };
    expect(resolveNotifications(event).map((n) => n.template)).toEqual(['status-changed']);
  });

  it('no envía a un admin el aviso sobre sí mismo', () => {
    const event = fixture('user.registered');
    event.admins = [{ email: event.subject.email.toUpperCase(), name: 'Yo' }];
    expect(resolveNotifications(event)).toHaveLength(1);
  });

  it('genera llaves de idempotencia únicas por correo', () => {
    const keys = resolveNotifications(fixture('user.registered')).map((n) => n.idempotencyKey);
    expect(new Set(keys).size).toBe(keys.length);
  });
});
```

📄 **Crear** `notification-service/src/infrastructure/messaging/user-event.dto.spec.ts`

```ts
import { FIXTURE_NAMES, loadFixture } from '../../test-support/load-fixture';
import { InvalidUserEventError, parseUserEvent } from './user-event.dto';

describe('parseUserEvent', () => {
  it.each(FIXTURE_NAMES)('acepta el fixture del contrato %s', async (name) => {
    await expect(parseUserEvent(loadFixture(name))).resolves.toMatchObject({ eventType: name });
  });

  it('rechaza un email inválido en subject', async () => {
    const bad = loadFixture('user.created') as { subject: { email: string } };
    bad.subject.email = 'no-es-un-correo';
    await expect(parseUserEvent(bad)).rejects.toThrow(/subject\.email/);
  });

  it('rechaza un eventType desconocido', async () => {
    const bad = { ...(loadFixture('user.created') as object), eventType: 'user.hacked' };
    await expect(parseUserEvent(bad)).rejects.toBeInstanceOf(InvalidUserEventError);
  });

  it('rechaza algo que no es un objeto', async () => {
    await expect(parseUserEvent('hola')).rejects.toBeInstanceOf(InvalidUserEventError);
  });
});
```

📄 **Crear** `notification-service/src/application/handle-user-event.use-case.spec.ts`

```ts
import { LogEmailAdapter } from '../infrastructure/email/log-email.adapter';
import { parseUserEvent } from '../infrastructure/messaging/user-event.dto';
import { BasicTemplateRenderer } from '../templates/basic-template.renderer';
import { loadFixture } from '../test-support/load-fixture';
import { HandleUserEventUseCase } from './handle-user-event.use-case';
import { RetryableEmailError } from './ports/email-sender.port';

describe('HandleUserEventUseCase', () => {
  it('envía un correo al usuario y otro al admin en un registro', async () => {
    const sender = new LogEmailAdapter();
    const useCase = new HandleUserEventUseCase(sender, new BasicTemplateRenderer());

    const sent = await useCase.execute(await parseUserEvent(loadFixture('user.registered')));

    expect(sent).toBe(2);
    expect(sender.sent.map((e) => e.to)).toEqual(['delivered@resend.dev', 'delivered+admin@resend.dev']);
    expect(sender.sent[0].tags).toContainEqual({ name: 'event_type', value: 'user_registered' });
  });

  it('propaga el error del proveedor para que el consumidor decida reintentar', async () => {
    const sender = new LogEmailAdapter();
    jest.spyOn(sender, 'send').mockRejectedValueOnce(new RetryableEmailError('429'));
    const useCase = new HandleUserEventUseCase(sender, new BasicTemplateRenderer());

    await expect(useCase.execute(await parseUserEvent(loadFixture('user.created')))).rejects.toBeInstanceOf(
      RetryableEmailError,
    );
  });
});
```

📄 **Crear** `notification-service/src/infrastructure/messaging/user-events.consumer.spec.ts`

```ts
/* eslint-disable @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-member-access */
import type { AmqpConnection } from '@golevelup/nestjs-rabbitmq';
import { ConfigService } from '@nestjs/config';
import type { ConsumeMessage } from 'amqplib';
import type { HandleUserEventUseCase } from '../../application/handle-user-event.use-case';
import { PermanentEmailError, RetryableEmailError } from '../../application/ports/email-sender.port';
import { loadFixture } from '../../test-support/load-fixture';
import { MetricsService } from '../observability/metrics.service';
import { USER_EVENTS_DLQ, USER_EVENTS_RETRY_QUEUE } from './rabbit.constants';
import { RecentEventIds } from './recent-event-ids';
import { UserEventsConsumer } from './user-events.consumer';

const message = (headers: Record<string, unknown> = {}): ConsumeMessage =>
  ({ properties: { headers, messageId: 'msg-1' } }) as unknown as ConsumeMessage;

describe('UserEventsConsumer', () => {
  const event = loadFixture('user.registered');
  let publish: jest.Mock;
  let execute: jest.Mock;
  let consumer: UserEventsConsumer;

  beforeEach(() => {
    publish = jest.fn().mockResolvedValue(true);
    execute = jest.fn().mockResolvedValue(2);
    consumer = new UserEventsConsumer(
      { execute } as unknown as HandleUserEventUseCase,
      { publish } as unknown as AmqpConnection,
      new MetricsService(),
      new RecentEventIds(),
      new ConfigService({ NOTIFICATIONS_MAX_RETRIES: 3, NOTIFICATIONS_RETRY_DELAY_MS: 1000 }),
    );
  });

  it('procesa un evento válido sin republicar', async () => {
    await consumer.onUserEvent(event, message());
    expect(execute).toHaveBeenCalledTimes(1);
    expect(publish).not.toHaveBeenCalled();
  });

  it('ignora un eventId repetido', async () => {
    await consumer.onUserEvent(event, message());
    await consumer.onUserEvent(event, message());
    expect(execute).toHaveBeenCalledTimes(1);
  });

  it('manda a la cola de reintentos ante un error transitorio', async () => {
    execute.mockRejectedValueOnce(new RetryableEmailError('429 rate limit'));
    await consumer.onUserEvent(event, message());
    expect(publish).toHaveBeenCalledWith(
      '',
      USER_EVENTS_RETRY_QUEUE,
      event,
      expect.objectContaining({
        expiration: '1000',
        headers: expect.objectContaining({ 'x-retry-count': 1 }),
      }),
    );
  });

  it('manda a la DLQ cuando se agotan los reintentos', async () => {
    execute.mockRejectedValueOnce(new RetryableEmailError('500'));
    await consumer.onUserEvent(event, message({ 'x-retry-count': 3 }));
    expect(publish).toHaveBeenCalledWith(
      '',
      USER_EVENTS_DLQ,
      event,
      expect.objectContaining({
        headers: expect.objectContaining({ 'x-failure-reason': 'max_retries_exceeded' }),
      }),
    );
  });

  it('manda a la DLQ sin reintentar un error permanente', async () => {
    execute.mockRejectedValueOnce(new PermanentEmailError('validation_error'));
    await consumer.onUserEvent(event, message());
    expect(publish).toHaveBeenCalledTimes(1);
    expect(publish.mock.calls[0][1]).toBe(USER_EVENTS_DLQ);
  });

  it('manda a la DLQ un mensaje que no cumple el contrato', async () => {
    await consumer.onUserEvent({ foo: 'bar' }, message());
    expect(execute).not.toHaveBeenCalled();
    expect(publish.mock.calls[0][1]).toBe(USER_EVENTS_DLQ);
  });
});
```

**Explicación de la estrategia de pruebas:**
- **Política:** se prueba la tabla del contrato con los fixtures reales. Es la regla de negocio más importante.
- **DTO:** los 5 fixtures deben ser **válidos**. Si Java y Nest no están de acuerdo, aquí se nota.
- **Caso de uso:** se usa el `LogEmailAdapter` **real** (es un *fake* en memoria), sin mocks complicados.
- **Consumidor:** se simulan `AmqpConnection` y el caso de uso con `jest.fn()` para comprobar **a qué cola** se republica en cada escenario. No hace falta RabbitMQ real. El comentario `eslint-disable` de la primera línea es necesario porque `expect.objectContaining(...)` y `mock.calls` devuelven `any`, y la configuración estricta de ESLint de Nest los marca como error. Sólo se desactiva en este archivo de prueba.

```bash
cd notification-service
npm run format           # Prettier: deja el código pegado con el formato del proyecto (OBLIGATORIO antes de lint)
npm test                 # todas las pruebas
npm run test:cov         # con cobertura (reporte en notification-service/coverage)
npm run lint             # ESLint + Prettier (corrige formato automáticamente)
npm run build
```

## Paso 15: Prueba de punta a punta (local)

```bash
# Terminal 1: sólo RabbitMQ en Docker (expone 5672 temporalmente para tu máquina)
docker run --rm -d --name rabbit-dev -p 5672:5672 -p 15673:15672 rabbitmq:4-management-alpine

# Terminal 2: el servicio en modo desarrollo (recarga al guardar)
cd notification-service
cp .env.example .env
npm run start:dev
```

1. Abre `http://localhost:15673` (`guest` / `guest`). En **Queues** deben aparecer las 3 colas: `notifications.user-events`, `.retry` y `.dlq`.
2. Publica `docs/notifications/contract/user.registered.json` siguiendo [CONTRACT.md §6](./CONTRACT.md#6-publicar-un-evento-a-mano-para-probar-sin-java). En el log deben aparecer 2 líneas `[EMAIL_DRIVER=log]` y una `Procesado eventId=...`.
3. Publica **el mismo** mensaje otra vez. Debe salir `Evento duplicado ignorado`.
4. Publica `{"hola":"mundo"}`. El mensaje debe aparecer en `notifications.user-events.dlq` con el header `x-failure-reason: invalid_payload`.
5. `curl localhost:3100/health` debe responder `{"status":"ok","rabbitmq":"up"}`, y `curl -s localhost:3100/metrics | grep skillbridge_` debe mostrar los contadores.

```bash
# Al terminar, apaga el Rabbit temporal
docker stop rabbit-dev
```

## Paso 16: Subir tu trabajo

```bash
cd ~/Projects/SkillBridge-AI-JAVA-Angular-Celula-1
git pull origin feature/notificaciones          # trae lo de A y C para evitar conflictos
git add notification-service/src notification-service/package.json notification-service/package-lock.json
git status                                       # revisa que NO aparezca .env ni node_modules
git commit -m "feat(notificaciones): consumidor RabbitMQ con reintentos, DLQ, política de destinatarios y métricas"
git push
gh pr create --base feature/notificaciones --head feature/notificaciones-nest-core \
  --title "feat(notificaciones): núcleo de mensajería de notification-service" \
  --body "Consumidor de user.* en skillbridge.events, validación del contrato, política de destinatarios, reintentos con cola .retry, DLQ, deduplicación, /health y /metrics. Pruebas con fixtures del contrato."
```

> Si ya abriste el PR en el Paso 9 y se mergeó, este es un **PR nuevo** desde la misma rama. Si sigue abierto, el `git push` lo actualiza solo y **no** hace falta `gh pr create`.

---

## Definition of Done (Persona B)

- [ ] `npm test` y `npm run build` en verde.
- [ ] Las 3 colas se crean solas al arrancar.
- [ ] Mensaje válido: se procesa. Duplicado: se ignora. Inválido: va a la DLQ. Error transitorio: va a `.retry` y vuelve. Reintentos agotados: va a la DLQ.
- [ ] `/health` y `/metrics` responden.
- [ ] Ningún log imprime emails completos.
- [ ] Entrada en `MEMORIA.md`.

## Problemas comunes

| Síntoma | Causa / solución |
|---|---|
| `PRECONDITION_FAILED - inequivalent arg 'x-dead-letter-exchange'` | La cola ya existía con otros argumentos. En la UI de RabbitMQ: **Queues → la cola → Delete**, y reinicia el servicio. |
| `Config validation error: "RABBITMQ_URL" is required` | Falta el `.env` (`cp .env.example .env`) o la variable en compose. |
| `TS1272: A type referenced in a decorated signature...` | Usa `import type { X }` para tipos usados en parámetros de métodos decorados. |
| Jest: `Must use import to load ES Module ... @nestjs/config` o `... promise-breaker` | Se instaló una versión ESM. Ejecuta `npm install @nestjs/config@^4 @golevelup/nestjs-rabbitmq@^6`. |
| Jest: `Reflect.getMetadata is not a function` | Falta `import 'reflect-metadata';` en la primera línea de `user-event.dto.ts`. |
| ESLint: muchos errores `prettier/prettier` | Ejecuta `npm run format` (o `npm run lint`, que corrige solo). |
| El servicio se queda esperando al arrancar | RabbitMQ no responde en `RABBITMQ_URL`. Dentro de Docker el host es `rabbitmq`; fuera de Docker es `localhost`. |

## Prompt para otra IA

```text
Trabajo en notification-service, un microservicio NestJS 11 (TypeScript estricto, Node 22) del proyecto
SkillBridge AI. Consume eventos de RabbitMQ (exchange topic "skillbridge.events", routing keys user.*) con
@golevelup/nestjs-rabbitmq y envía correos mediante el puerto EmailSenderPort. Arquitectura hexagonal:
src/domain (puro), src/application (casos de uso + puertos como clases abstractas), src/infrastructure
(RabbitMQ, email, observabilidad). Contrato: docs/notifications/CONTRACT.md; fixtures en
docs/notifications/contract/*.json. Guía: docs/notifications/GUIA-PERSONA-B-NEST-CORE.md.
Reglas: no exponer secretos, no hacer git commit/push, registrar en MEMORIA.md.
Estoy en el Paso <N> y obtengo: <error / comportamiento>. Explícame la causa y dame el código corregido
bloque por bloque con su explicación.
```
