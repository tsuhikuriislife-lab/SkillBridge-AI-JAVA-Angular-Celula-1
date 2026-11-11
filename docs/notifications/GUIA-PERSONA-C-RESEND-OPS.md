# Guía Persona C: Resend, plantillas de correo y operación (Docker, CI, Prometheus)

> **Rama:** `feature/notificaciones-resend-ops` (sale de `feature/notificaciones` **cuando B suba la base**, Bloque 0)
> **Tiempo estimado:** 4 h (Bloques 1 y 2). Ver [PLAN](./PLAN.md).
> **Antes de empezar lee:** [CONTRACT.md](./CONTRACT.md) y [CONFIGURACION-DOMINIO-Y-SECRETOS.md](./CONFIGURACION-DOMINIO-Y-SECRETOS.md)
> **Requisitos:** Node.js 22, Docker y acceso a la cuenta de Resend del equipo.

## Qué vas a construir

```
notification-service/
├── src/infrastructure/email/
│   ├── resend-email.adapter.ts        ← implementa EmailSenderPort con el SDK de Resend
│   ├── resend-email.adapter.spec.ts
│   └── email.module.ts                ← (modificar) elige "log" o "resend" según EMAIL_DRIVER
├── src/templates/
│   ├── labels.ts                      ← textos en español (roles, estados, fechas)
│   ├── layout.ts                      ← HTML base con la marca + versión texto plano
│   ├── user-templates.ts              ← las 8 plantillas
│   ├── spanish-template.renderer.ts   ← implementa TemplateRendererPort
│   ├── templates.spec.ts
│   └── templates.module.ts            ← (modificar) usa SpanishTemplateRenderer
├── Dockerfile / .dockerignore
docker-compose.yml, .env.example, Makefile           ← (modificar) raíz del repo
ops/prometheus/prometheus.yml                        ← (modificar) scrape del servicio
.github/workflows/ci.yml                             ← (modificar) job de CI
```

> **Regla de convivencia con B:** tú **no** tocas `src/domain`, `src/application`, `src/infrastructure/messaging` ni `src/infrastructure/observability`. B no toca tus archivos. El único archivo compartido es `package.json` (tú agregas `resend` y un script). Si hay conflicto, conserva **ambos** cambios.

---

## Paso 0: Mientras B termina la base (primeros 45 min)

Mientras B arma el scaffold, adelanta lo que **no** depende del código:
1. Sigue [CONFIGURACION-DOMINIO-Y-SECRETOS.md](./CONFIGURACION-DOMINIO-Y-SECRETOS.md) junto con Dylan: cuenta de Resend, dominio en Hostinger, registros DNS y API key. La propagación DNS tarda, así que **es lo primero**.
2. Lee la tabla de plantillas del contrato (§3) y el Paso 3 de esta guía.

Cuando B avise que la base está lista:

```bash
cd ~/Projects/SkillBridge-AI-JAVA-Angular-Celula-1
git fetch origin
git checkout feature/notificaciones
git pull origin feature/notificaciones
git checkout -b feature/notificaciones-resend-ops

cd notification-service
npm install            # instala lo que B dejó en package.json
npm install resend@^6  # SDK oficial de Resend (verificado con 6.38)
```

---

## Paso 1: Adaptador de Resend

📄 **Crear** `notification-service/src/infrastructure/email/resend-email.adapter.ts`

```ts
import { Logger } from '@nestjs/common';
import type { ConfigService } from '@nestjs/config';
import { Resend } from 'resend';
import type { OutgoingEmail } from '../../domain/notification';
import {
  EmailSenderPort,
  PermanentEmailError,
  RetryableEmailError,
  type SentEmail,
} from '../../application/ports/email-sender.port';
import { maskEmail } from '../../shared/mask-email';

/** Errores de Resend que vale la pena reintentar (https://resend.com/docs/api-reference/errors). */
const RETRYABLE_ERRORS = new Set<string>([
  'rate_limit_exceeded',
  'concurrent_idempotent_requests',
  'application_error',
  'internal_server_error',
]);

export interface ResendAdapterOptions {
  from: string;
  replyTo?: string;
  /** Sólo desarrollo: si se define, TODOS los correos van a esta dirección. */
  redirectTo?: string;
}

type ResendClient = Pick<Resend, 'emails'>;

export class ResendEmailAdapter implements EmailSenderPort {
  private readonly logger = new Logger(ResendEmailAdapter.name);

  constructor(
    private readonly client: ResendClient,
    private readonly options: ResendAdapterOptions,
  ) {
    if (options.redirectTo) {
      this.logger.warn(`MAIL_REDIRECT_TO activo: todos los correos irán a ${maskEmail(options.redirectTo)}`);
    }
  }

  static fromConfig(config: ConfigService): ResendEmailAdapter {
    const optional = (key: string): string | undefined => config.get<string>(key) || undefined;
    return new ResendEmailAdapter(new Resend(config.getOrThrow<string>('RESEND_API_KEY')), {
      from: config.getOrThrow<string>('MAIL_FROM'),
      replyTo: optional('MAIL_REPLY_TO'),
      redirectTo: optional('MAIL_REDIRECT_TO'),
    });
  }

  async send(email: OutgoingEmail): Promise<SentEmail> {
    const redirect = this.options.redirectTo;
    const to = redirect ?? email.to;
    const subject = redirect ? `[para: ${email.to}] ${email.subject}` : email.subject;

    let response: Awaited<ReturnType<ResendClient['emails']['send']>>;
    try {
      response = await this.client.emails.send(
        {
          from: this.options.from,
          to: [to],
          subject,
          html: email.html,
          text: email.text,
          replyTo: this.options.replyTo,
          tags: email.tags,
        },
        { idempotencyKey: email.idempotencyKey },
      );
    } catch (error) {
      // Timeout, DNS o conexión rechazada: el problema es la red, no los datos.
      throw new RetryableEmailError(
        `Error de red con Resend: ${error instanceof Error ? error.message : String(error)}`,
      );
    }

    if (response.error) {
      const detail = `${response.error.name}: ${response.error.message}`;
      if (RETRYABLE_ERRORS.has(response.error.name)) {
        throw new RetryableEmailError(detail);
      }
      throw new PermanentEmailError(detail);
    }

    if (!response.data) {
      throw new RetryableEmailError('Resend respondió sin id de correo');
    }

    return { id: response.data.id };
  }
}
```

**Explicación por partes:**
1. **`RETRYABLE_ERRORS`:** el SDK de Resend **no lanza excepciones** ante errores de la API. Devuelve `{ data, error }`, y `error.name` dice qué pasó:
   - `rate_limit_exceeded` (429) o `internal_server_error` (5xx) son **transitorios**: se lanza `RetryableEmailError` y el consumidor de B lo reintenta en 30 s.
   - `validation_error`, `invalid_from_address`, `daily_quota_exceeded`… son **permanentes**: reintentar no los arregla, así que el mensaje va directo a la DLQ con el detalle.
2. **El constructor recibe el cliente** (`client`) en vez de crearlo adentro. Así en las pruebas se pasa un cliente falso sin red ni API key (inyección de dependencias).
3. **`static fromConfig`:** crea el adaptador real leyendo `RESEND_API_KEY`, `MAIL_FROM`, etc. `getOrThrow` falla al arrancar si falta un valor.
4. **`idempotencyKey`:** si Resend recibe dos veces la misma llave en 24 h, **no** envía el segundo correo. Es lo que hace seguros los reintentos.
5. **`redirectTo` (`MAIL_REDIRECT_TO`):** en desarrollo los usuarios de prueba tienen correos falsos (por ejemplo, `admin@skillbridge.com` es un dominio **ajeno**). Escribirles daña la reputación del dominio. Con esta variable todo llega a tu casilla, y el asunto indica a quién iba dirigido.
6. **`try/catch` alrededor de `send`:** el SDK sí puede lanzar excepciones ante fallos de red (sin internet, DNS). Esos casos también son transitorios.

### 1.1 Pruebas del adaptador

📄 **Crear** `notification-service/src/infrastructure/email/resend-email.adapter.spec.ts`

```ts
import type { Resend } from 'resend';
import {
  PermanentEmailError,
  RetryableEmailError,
} from '../../application/ports/email-sender.port';
import type { OutgoingEmail } from '../../domain/notification';
import { ResendEmailAdapter } from './resend-email.adapter';

const email: OutgoingEmail = {
  to: 'delivered@resend.dev',
  subject: 'Hola',
  html: '<p>Hola</p>',
  text: 'Hola',
  idempotencyKey: 'evt-1:welcome:delivered@resend.dev',
  tags: [{ name: 'template', value: 'welcome' }],
};

function adapterWith(send: jest.Mock, redirectTo?: string): ResendEmailAdapter {
  const client = { emails: { send } } as unknown as Pick<Resend, 'emails'>;
  return new ResendEmailAdapter(client, { from: 'SkillBridge <no-reply@test.dev>', redirectTo });
}

describe('ResendEmailAdapter', () => {
  it('envía con la llave de idempotencia y devuelve el id', async () => {
    const send = jest.fn().mockResolvedValue({ data: { id: 're_123' }, error: null });
    await expect(adapterWith(send).send(email)).resolves.toEqual({ id: 're_123' });
    expect(send).toHaveBeenCalledWith(
      expect.objectContaining({ to: ['delivered@resend.dev'], subject: 'Hola' }),
      { idempotencyKey: email.idempotencyKey },
    );
  });

  it('redirige todos los correos cuando MAIL_REDIRECT_TO está activo', async () => {
    const send = jest.fn().mockResolvedValue({ data: { id: 're_1' }, error: null });
    await adapterWith(send, 'yo@equipo.dev').send(email);
    expect(send).toHaveBeenCalledWith(
      expect.objectContaining({ to: ['yo@equipo.dev'], subject: '[para: delivered@resend.dev] Hola' }),
      expect.anything(),
    );
  });

  it('convierte rate_limit_exceeded en error reintentable', async () => {
    const send = jest.fn().mockResolvedValue({
      data: null,
      error: { name: 'rate_limit_exceeded', message: 'Too many requests' },
    });
    await expect(adapterWith(send).send(email)).rejects.toBeInstanceOf(RetryableEmailError);
  });

  it('convierte validation_error en error permanente', async () => {
    const send = jest.fn().mockResolvedValue({
      data: null,
      error: { name: 'validation_error', message: 'Invalid `to` field' },
    });
    await expect(adapterWith(send).send(email)).rejects.toBeInstanceOf(PermanentEmailError);
  });

  it('trata un fallo de red como reintentable', async () => {
    const send = jest.fn().mockRejectedValue(new Error('ECONNRESET'));
    await expect(adapterWith(send).send(email)).rejects.toBeInstanceOf(RetryableEmailError);
  });
});
```

**Explicación:** se prueba la **clasificación de errores**, que es lo que decide entre reintentar y mandar a la DLQ. No se llama a Resend real: el cliente es un objeto con `send: jest.fn()`.

---

## Paso 2: Elegir el driver en `email.module.ts`

📝 **Reemplazar** `notification-service/src/infrastructure/email/email.module.ts`

```ts
import { Logger, Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { EmailSenderPort } from '../../application/ports/email-sender.port';
import { LogEmailAdapter } from './log-email.adapter';
import { ResendEmailAdapter } from './resend-email.adapter';

@Module({
  providers: [
    {
      provide: EmailSenderPort,
      inject: [ConfigService],
      useFactory: (config: ConfigService): EmailSenderPort => {
        const driver = config.get<string>('EMAIL_DRIVER', 'log');
        Logger.log(`Driver de correo: ${driver}`, 'EmailModule');
        return driver === 'resend' ? ResendEmailAdapter.fromConfig(config) : new LogEmailAdapter();
      },
    },
  ],
  exports: [EmailSenderPort],
})
export class EmailModule {}
```

**Explicación:**
- `useFactory` decide **en el arranque** qué implementación usar. El resto del código (caso de uso, consumidor) **no cambia**: esa es la ventaja del puerto.
- Con `EMAIL_DRIVER=log` (por defecto) **nunca** se crea el cliente de Resend, así que no hace falta API key en CI ni para los compañeros que no la tienen.

---

## Paso 3: Plantillas en español

### 3.1 Etiquetas y fechas

📄 **Crear** `notification-service/src/templates/labels.ts`

```ts
export const ROLE_LABELS: Record<string, string> = {
  CUSTOMER: 'Cliente',
  PROVIDER: 'Proveedor',
  ADMIN: 'Administrador',
};

export const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Activa',
  INACTIVE: 'Inactiva',
};

export function labelOf(map: Record<string, string>, key: string | null | undefined): string {
  if (!key) return '—';
  return map[key] ?? key;
}

const DATE_FORMAT = new Intl.DateTimeFormat('es-CO', {
  dateStyle: 'long',
  timeStyle: 'short',
  timeZone: 'America/Bogota',
});

/** "2026-11-11T15:10:00Z" → "11 de noviembre de 2026, 10:10 a. m." */
export function formatDate(isoDate: string): string {
  const date = new Date(isoDate);
  return Number.isNaN(date.getTime()) ? isoDate : DATE_FORMAT.format(date);
}

/** "Ana María Gómez" → "Ana" */
export function firstName(fullName: string): string {
  return fullName.trim().split(/\s+/)[0] || fullName;
}
```

**Explicación:** el backend envía `CUSTOMER`, `INACTIVE`, etc. Al usuario hay que mostrarle "Cliente" o "Inactiva". La fecha llega en UTC y se muestra en hora de Colombia. `Intl` viene incluido en Node 22 (con ICU completo), así que no hace falta otra librería.

### 3.2 Layout HTML + texto plano

📄 **Crear** `notification-service/src/templates/layout.ts`

```ts
import type { RenderedEmail } from '../domain/notification';
import { escapeHtml as e } from './escape-html';

/** Contenido de un correo como DATOS (nunca HTML crudo): el layout se encarga de escapar. */
export interface EmailContent {
  subject: string;
  preheader: string;
  heading: string;
  paragraphs: string[];
  details?: Array<[label: string, value: string]>;
  cta?: { label: string; url: string };
}

const BRAND_COLOR = '#4f46e5';

export function renderLayout(content: EmailContent): RenderedEmail {
  const paragraphsHtml = content.paragraphs
    .map((p) => `<p style="margin:0 0 14px;font-size:15px;line-height:1.6;color:#1f2937;">${e(p)}</p>`)
    .join('');

  const detailsHtml = content.details?.length
    ? `<table role="presentation" cellpadding="0" cellspacing="0" style="width:100%;margin:8px 0 20px;border:1px solid #e5e7eb;border-radius:6px;">
        ${content.details
          .map(
            ([label, value]) =>
              `<tr><td style="padding:8px 12px;font-size:13px;color:#6b7280;width:40%;">${e(label)}</td><td style="padding:8px 12px;font-size:13px;color:#111827;font-weight:bold;">${e(value)}</td></tr>`,
          )
          .join('')}
      </table>`
    : '';

  const ctaHtml = content.cta
    ? `<p style="margin:24px 0 8px;"><a href="${e(content.cta.url)}" style="display:inline-block;background:${BRAND_COLOR};color:#ffffff;text-decoration:none;padding:12px 22px;border-radius:6px;font-size:15px;font-weight:bold;">${e(content.cta.label)}</a></p>`
    : '';

  const html = `<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${e(content.subject)}</title>
</head>
<body style="margin:0;padding:0;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;">
<span style="display:none;max-height:0;overflow:hidden;opacity:0;">${e(content.preheader)}</span>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f4f5f7;padding:24px 12px;">
<tr><td align="center">
<table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%;background:#ffffff;border-radius:8px;overflow:hidden;">
<tr><td style="background:${BRAND_COLOR};padding:20px 32px;color:#ffffff;font-size:20px;font-weight:bold;">SkillBridge AI</td></tr>
<tr><td style="padding:32px;">
<h1 style="margin:0 0 18px;font-size:22px;color:#111827;">${e(content.heading)}</h1>
${paragraphsHtml}
${detailsHtml}
${ctaHtml}
</td></tr>
<tr><td style="padding:16px 32px;background:#f9fafb;font-size:12px;line-height:1.5;color:#6b7280;">
Recibes este correo porque tienes una cuenta en SkillBridge AI. Es un mensaje automático sobre la seguridad y el estado de tu cuenta.
</td></tr>
</table>
</td></tr>
</table>
</body>
</html>`;

  const textParts = [
    content.heading,
    '',
    ...content.paragraphs,
    ...(content.details?.length ? ['', ...content.details.map(([l, v]) => `${l}: ${v}`)] : []),
    ...(content.cta ? ['', `${content.cta.label}: ${content.cta.url}`] : []),
    '',
    '— SkillBridge AI',
  ];

  return { subject: content.subject, html, text: textParts.join('\n') };
}
```

**Explicación:**
- **Las plantillas devuelven datos (`EmailContent`) y no HTML.** El layout es el **único** lugar que genera HTML y escapa **todo** con `escapeHtml`. Así un nombre como `<img onerror=...>` no puede inyectar código: es seguridad por diseño.
- **HTML con tablas y estilos inline:** clientes como Gmail y Outlook ignoran `<style>` y CSS moderno. Es la técnica estándar para correos.
- **`preheader`:** el texto gris que muestra Gmail junto al asunto en la bandeja de entrada.
- **Versión `text`:** clientes sin HTML y filtros antispam la valoran. Mejora la entregabilidad.

### 3.3 Las 8 plantillas

📄 **Crear** `notification-service/src/templates/user-templates.ts`

```ts
import type { TemplateKey } from '../domain/notification';
import type { UserEvent } from '../domain/user-event';
import { firstName, formatDate, labelOf, ROLE_LABELS, STATUS_LABELS } from './labels';
import type { EmailContent } from './layout';

export interface TemplateContext {
  event: UserEvent;
  recipientName: string;
  frontendUrl: string;
}

type TemplateBuilder = (ctx: TemplateContext) => EmailContent;

const actorLabel = (event: UserEvent): string => {
  if (!event.actor) return 'Tú (registro)';
  if (event.actor.email.toLowerCase() === event.subject.email.toLowerCase()) return 'Tú';
  return event.actor.role === 'ADMIN' ? 'Un administrador' : event.actor.email;
};

const subjectDetails = (event: UserEvent): Array<[string, string]> => [
  ['Nombre', event.subject.name],
  ['Correo', event.subject.email],
  ['Rol', labelOf(ROLE_LABELS, event.subject.role)],
  ['Fecha', formatDate(event.occurredAt)],
];

export const USER_TEMPLATES: Record<TemplateKey, TemplateBuilder> = {
  welcome: ({ event, frontendUrl }) => ({
    subject: `¡Bienvenido/a a SkillBridge AI, ${firstName(event.subject.name)}!`,
    preheader: 'Tu cuenta está lista. Explora mentorías y servicios profesionales.',
    heading: `Hola, ${firstName(event.subject.name)} 👋`,
    paragraphs: [
      'Tu cuenta en SkillBridge AI se creó correctamente. Ya puedes explorar servicios y mentorías, inscribirte y agendar tus sesiones.',
      'Si no fuiste tú quien creó esta cuenta, contacta al administrador de la plataforma.',
    ],
    cta: { label: 'Explorar servicios', url: frontendUrl },
  }),

  'admin.new-registration': ({ event, recipientName, frontendUrl }) => ({
    subject: `Nuevo usuario registrado: ${event.subject.name}`,
    preheader: `${event.subject.name} acaba de crear una cuenta.`,
    heading: 'Se registró un nuevo usuario',
    paragraphs: [`Hola ${firstName(recipientName)}, un nuevo usuario se registró en la plataforma.`],
    details: subjectDetails(event),
    cta: { label: 'Ir al panel de administración', url: `${frontendUrl}/admin` },
  }),

  'account-created': ({ event, frontendUrl }) => ({
    subject: 'Un administrador creó tu cuenta en SkillBridge AI',
    preheader: `Tu cuenta con rol ${labelOf(ROLE_LABELS, event.subject.role)} está lista.`,
    heading: `Hola, ${firstName(event.subject.name)}`,
    paragraphs: [
      `Un administrador creó una cuenta para ti en SkillBridge AI con el rol de ${labelOf(ROLE_LABELS, event.subject.role)}.`,
      'Por seguridad, este correo no incluye contraseñas. El administrador te compartirá tus credenciales por un canal seguro. Te recomendamos cambiarlas en tu primer ingreso.',
    ],
    cta: { label: 'Iniciar sesión', url: `${frontendUrl}/login` },
  }),

  'status-changed': ({ event, frontendUrl }) => {
    const deactivated = event.data.newStatus === 'INACTIVE';
    return {
      subject: deactivated
        ? 'Tu cuenta de SkillBridge AI fue desactivada'
        : 'Tu cuenta de SkillBridge AI fue reactivada',
      preheader: deactivated ? 'Tu cuenta ya no está activa.' : 'Ya puedes volver a usar la plataforma.',
      heading: deactivated ? 'Tu cuenta fue desactivada' : '¡Tu cuenta está activa de nuevo!',
      paragraphs: [
        `Hola ${firstName(event.subject.name)}, el estado de tu cuenta cambió de ${labelOf(STATUS_LABELS, event.data.previousStatus)} a ${labelOf(STATUS_LABELS, event.data.newStatus)}.`,
        deactivated
          ? 'Mientras tu cuenta esté inactiva no podrás usar los servicios de la plataforma. Si crees que se trata de un error, contacta al administrador.'
          : 'Ya puedes ingresar y usar la plataforma con normalidad.',
      ],
      details: [
        ['Realizado por', actorLabel(event)],
        ['Fecha', formatDate(event.occurredAt)],
      ],
      cta: deactivated ? undefined : { label: 'Iniciar sesión', url: `${frontendUrl}/login` },
    };
  },

  'admin.status-changed': ({ event, recipientName, frontendUrl }) => {
    const deactivated = event.data.newStatus === 'INACTIVE';
    return {
      subject: `${event.subject.name} ${deactivated ? 'desactivó' : 'reactivó'} su cuenta`,
      preheader: 'Cambio de estado de cuenta realizado por el propio usuario.',
      heading: 'Cambio de estado de una cuenta',
      paragraphs: [
        `Hola ${firstName(recipientName)}, ${event.subject.name} cambió el estado de su cuenta.`,
      ],
      details: [
        ...subjectDetails(event),
        ['Estado anterior', labelOf(STATUS_LABELS, event.data.previousStatus)],
        ['Estado nuevo', labelOf(STATUS_LABELS, event.data.newStatus)],
      ],
      cta: { label: 'Revisar en el panel', url: `${frontendUrl}/admin` },
    };
  },

  'role-changed': ({ event, frontendUrl }) => {
    const newRole = event.data.newRole;
    const hint: Record<string, string> = {
      PROVIDER: 'Ahora puedes publicar y gestionar tus propios servicios desde el panel de proveedor.',
      CUSTOMER: 'Puedes explorar los servicios disponibles e inscribirte en ellos.',
      ADMIN: 'Ahora tienes acceso al panel de administración de la plataforma.',
    };
    const destination: Record<string, string> = {
      PROVIDER: `${frontendUrl}/provider`,
      ADMIN: `${frontendUrl}/admin`,
    };
    return {
      subject: `Tu rol en SkillBridge AI cambió a ${labelOf(ROLE_LABELS, newRole)}`,
      preheader: `Nuevo rol: ${labelOf(ROLE_LABELS, newRole)}.`,
      heading: 'Tu rol fue actualizado',
      paragraphs: [
        `Hola ${firstName(event.subject.name)}, ${actorLabel(event).toLowerCase()} actualizó tu rol de ${labelOf(ROLE_LABELS, event.data.previousRole)} a ${labelOf(ROLE_LABELS, newRole)}.`,
        hint[newRole] ?? '',
      ].filter(Boolean),
      details: [['Fecha', formatDate(event.occurredAt)]],
      cta: { label: 'Ir a SkillBridge AI', url: destination[newRole] ?? frontendUrl },
    };
  },

  'account-deleted': ({ event }) => ({
    subject: 'Tu cuenta de SkillBridge AI fue eliminada',
    preheader: 'Confirmación de eliminación de cuenta.',
    heading: 'Tu cuenta fue eliminada',
    paragraphs: [
      `Hola ${firstName(event.subject.name)}, te confirmamos que la cuenta asociada a ${event.subject.email} fue eliminada${event.actor?.role === 'ADMIN' ? ' por un administrador' : ''}.`,
      'Tus credenciales ya no son válidas. Si no reconoces esta acción, contacta al administrador de inmediato.',
    ],
    details: [['Fecha', formatDate(event.occurredAt)]],
  }),

  'admin.account-deleted': ({ event, recipientName, frontendUrl }) => ({
    subject: `${event.subject.name} eliminó su cuenta`,
    preheader: 'Un usuario eliminó su cuenta.',
    heading: 'Un usuario eliminó su cuenta',
    paragraphs: [`Hola ${firstName(recipientName)}, ${event.subject.name} eliminó su cuenta de la plataforma.`],
    details: subjectDetails(event),
    cta: { label: 'Ir al panel de administración', url: `${frontendUrl}/admin` },
  }),
};
```

**Explicación:**
- **`Record<TemplateKey, TemplateBuilder>`:** TypeScript **obliga** a que existan las 8 plantillas. Si B agrega una `TemplateKey` nueva y aquí no se define, el proyecto no compila.
- **Cada plantilla** es una función pura `contexto → contenido`, fácil de probar.
- **Enlaces (`cta`)** apuntan a rutas reales del frontend Angular: `/login`, `/admin` y `/provider` existen en `frontend/src/app/app.routes.ts`.
- **`account-created` nunca incluye la contraseña** (ver la Guía A, Paso 9).
- **`actorLabel`** humaniza quién hizo el cambio: "Tú", "Un administrador", etc.

### 3.4 El renderer definitivo

📄 **Crear** `notification-service/src/templates/spanish-template.renderer.ts`

```ts
import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { TemplateRendererPort } from '../application/ports/template-renderer.port';
import type { RenderedEmail, TemplateKey } from '../domain/notification';
import type { UserEvent } from '../domain/user-event';
import { renderLayout } from './layout';
import { USER_TEMPLATES } from './user-templates';

@Injectable()
export class SpanishTemplateRenderer implements TemplateRendererPort {
  private readonly frontendUrl: string;

  constructor(config: ConfigService) {
    this.frontendUrl = config.get<string>('FRONTEND_URL', 'http://localhost:8088').replace(/\/+$/, '');
  }

  render(template: TemplateKey, event: UserEvent, recipientName: string): RenderedEmail {
    const build = USER_TEMPLATES[template];
    return renderLayout(build({ event, recipientName, frontendUrl: this.frontendUrl }));
  }
}
```

📝 **Reemplazar** `notification-service/src/templates/templates.module.ts`

```ts
import { Module } from '@nestjs/common';
import { TemplateRendererPort } from '../application/ports/template-renderer.port';
import { SpanishTemplateRenderer } from './spanish-template.renderer';

@Module({
  providers: [{ provide: TemplateRendererPort, useClass: SpanishTemplateRenderer }],
  exports: [TemplateRendererPort],
})
export class TemplatesModule {}
```

**Explicación:** `replace(/\/+$/, '')` quita la `/` final de `FRONTEND_URL` para no generar `https://app.com//login`. El `BasicTemplateRenderer` de B se **deja** en el repo: lo usan sus pruebas.

### 3.5 Pruebas de plantillas

📄 **Crear** `notification-service/src/templates/templates.spec.ts`

```ts
import { ConfigService } from '@nestjs/config';
import { TEMPLATE_KEYS } from '../domain/notification';
import { resolveNotifications } from '../domain/notification-policy';
import type { UserEvent } from '../domain/user-event';
import { FIXTURE_NAMES, loadFixture } from '../test-support/load-fixture';
import { SpanishTemplateRenderer } from './spanish-template.renderer';
import { USER_TEMPLATES } from './user-templates';

const renderer = new SpanishTemplateRenderer(new ConfigService({ FRONTEND_URL: 'https://app.test/' }));

describe('Plantillas de correo', () => {
  it('existe una plantilla por cada TemplateKey', () => {
    expect(Object.keys(USER_TEMPLATES).sort()).toEqual([...TEMPLATE_KEYS].sort());
  });

  it.each(FIXTURE_NAMES)('renderiza todos los correos del evento %s', (name) => {
    const event = loadFixture(name) as UserEvent;
    for (const intent of resolveNotifications(event)) {
      const email = renderer.render(intent.template, event, intent.recipientName);
      expect(email.subject.length).toBeGreaterThan(5);
      expect(email.html).toContain('<!doctype html>');
      expect(email.text).not.toMatch(/<[a-z]/i);
      expect(email.html).not.toContain('//login');
    }
  });

  it('escapa HTML en datos del usuario (anti-inyección)', () => {
    const event = loadFixture('user.registered') as UserEvent;
    event.subject.name = '<script>alert(1)</script>';
    const email = renderer.render('welcome', event, event.subject.name);
    expect(email.html).not.toContain('<script>');
    expect(email.html).toContain('&lt;script&gt;');
  });

  it('el correo de cuenta desactivada no incluye botón de login', () => {
    const event = loadFixture('user.status.changed') as UserEvent;
    const email = renderer.render('status-changed', event, event.subject.name);
    expect(email.subject).toContain('desactivada');
    expect(email.html).not.toContain('/login');
  });

  it('nunca menciona contraseñas concretas en account-created', () => {
    const event = loadFixture('user.created') as UserEvent;
    const email = renderer.render('account-created', event, event.subject.name);
    expect(email.text).not.toMatch(/TemporaryPass/i);
  });
});
```

**Explicación:**
- Se renderizan **todos** los correos que puede producir cada fixture, así ninguna plantilla queda sin probar.
- Hay pruebas de seguridad explícitas: escape de HTML y que nunca aparezca una contraseña.

### 3.6 Ver los correos en el navegador (opcional, muy útil)

```bash
cd notification-service
mkdir -p ../.preview-correos
npx ts-node -T -e "
const { SpanishTemplateRenderer } = require('./src/templates/spanish-template.renderer');
const { resolveNotifications } = require('./src/domain/notification-policy');
const { ConfigService } = require('@nestjs/config');
const fs = require('fs');
const r = new SpanishTemplateRenderer(new ConfigService({ FRONTEND_URL: 'http://localhost:8088' }));
for (const n of ['user.registered','user.created','user.status.changed','user.role.changed','user.deleted']) {
  const ev = JSON.parse(fs.readFileSync('../docs/notifications/contract/' + n + '.json','utf8'));
  for (const i of resolveNotifications(ev)) fs.writeFileSync('../.preview-correos/' + i.template + '.html', r.render(i.template, ev, i.recipientName).html);
}
console.log('Listo: abre .preview-correos/*.html');
"
xdg-open ../.preview-correos/welcome.html   # en macOS: open
```

> No subas `.preview-correos/` a git. Bórrala al terminar (`rm -rf ../.preview-correos`).

---

## Paso 4: Docker

📄 **Crear** `notification-service/Dockerfile`

```dockerfile
# ---------- Etapa 1: compilar TypeScript ----------
FROM public.ecr.aws/docker/library/node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci --no-audit --no-fund
COPY tsconfig*.json nest-cli.json ./
COPY src ./src
RUN npm run build

# ---------- Etapa 2: imagen final liviana ----------
FROM public.ecr.aws/docker/library/node:22-alpine
ENV NODE_ENV=production
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev --no-audit --no-fund && npm cache clean --force
COPY --from=builder /app/dist ./dist
USER node
EXPOSE 3100
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=5 \
  CMD wget -qO- http://127.0.0.1:3100/health >/dev/null || exit 1
CMD ["node", "dist/main.js"]
```

📄 **Crear** `notification-service/.dockerignore`

```
node_modules
dist
coverage
.env
*.log
test
```

**Explicación:**
- **Dos etapas (*multi-stage*):** la primera instala TODO (TypeScript, Jest…) y compila. La segunda sólo lleva `dist/` y las dependencias de producción, así la imagen pesa mucho menos y tiene menos superficie de ataque.
- **`public.ecr.aws/docker/library/node:22-alpine`:** es el mismo registro que ya usan `backend/Dockerfile` y `frontend/Dockerfile`, y evita el límite de descargas de Docker Hub.
- **`USER node`:** el proceso no corre como root (buena práctica de seguridad, igual que el backend con `spring`).
- **`HEALTHCHECK`:** llama al `/health` de B. `wget` viene en Alpine, así que no hace falta instalar `curl`.
- **`.dockerignore`:** evita copiar `node_modules` y, sobre todo, `.env`, para que **los secretos no queden dentro de la imagen**.

---

## Paso 5: `docker-compose.yml`, `.env.example` y `Makefile` (raíz del repo)

📝 **Modificar** `docker-compose.yml`: agrega este servicio **después** del servicio `frontend` (mismo nivel de indentación que `frontend:`):

```yaml
  notifications:
    build:
      context: ./notification-service
    environment:
      NODE_ENV: production
      PORT: 3100
      RABBITMQ_URL: amqp://guest:guest@rabbitmq:5672
      EMAIL_DRIVER: ${EMAIL_DRIVER:-log}
      RESEND_API_KEY: ${RESEND_API_KEY:-}
      MAIL_FROM: ${MAIL_FROM:-SkillBridge AI <onboarding@resend.dev>}
      MAIL_REPLY_TO: ${MAIL_REPLY_TO:-}
      MAIL_REDIRECT_TO: ${MAIL_REDIRECT_TO:-}
      FRONTEND_URL: ${FRONTEND_URL:-http://localhost:8088}
      NOTIFICATIONS_MAX_RETRIES: ${NOTIFICATIONS_MAX_RETRIES:-3}
      NOTIFICATIONS_RETRY_DELAY_MS: ${NOTIFICATIONS_RETRY_DELAY_MS:-30000}
    depends_on:
      rabbitmq:
        condition: service_healthy
    ports:
      - "127.0.0.1:3100:3100"
    healthcheck:
      test: [ "CMD-SHELL", "wget -qO- http://127.0.0.1:3100/health >/dev/null || exit 1" ]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 20s
    restart: unless-stopped
    networks: [ skillbridge ]
```

**Explicación:**
- **`${VAR:-valor}`:** toma la variable del `.env` de la raíz y, si no existe, usa el valor por defecto. Así `make up` funciona **aunque no tengas API key** (driver `log`).
- **`RABBITMQ_URL` con host `rabbitmq`:** dentro de la red `skillbridge` los contenedores se encuentran por el nombre del servicio. Es el mismo valor que usa el backend.
- **`depends_on: service_healthy`:** no arranca hasta que RabbitMQ responde al ping.
- **`127.0.0.1:3100`:** el puerto sólo se expone a tu máquina (para `/health` y `/metrics`), igual que el resto de servicios internos del compose.

📝 **Modificar** `.env.example` (raíz). Agrega al final:

```dotenv
# ---------- Notificaciones por correo (notification-service) ----------
# log = no envía correos, sólo los escribe en el log (por defecto). resend = envío real.
EMAIL_DRIVER=log
# API key de Resend (https://resend.com/api-keys). Permiso "Sending access". NUNCA la subas a git.
RESEND_API_KEY=
# Remitente. Debe pertenecer a un dominio VERIFICADO en Resend. Sin dominio: SkillBridge AI <onboarding@resend.dev>
MAIL_FROM=SkillBridge AI <notificaciones@mail.tudominio.com>
MAIL_REPLY_TO=
# Sólo desarrollo: si tiene valor, TODOS los correos se redirigen a esta dirección.
MAIL_REDIRECT_TO=
# URL pública del frontend (para los botones de los correos)
FRONTEND_URL=http://localhost:8088
```

📝 **Modificar** `Makefile`:
- Agrega `test-notifications logs-notifications` al final de la línea `.PHONY: ...`.
- Agrega los targets al final del archivo (**la indentación debe ser TAB, no espacios**):

```makefile
test-notifications:
	cd notification-service && npm ci && npm run lint:check && npm test

logs-notifications:
	docker compose logs -f --tail=200 notifications
```

📝 **Modificar** `notification-service/package.json`. Dentro de `"scripts"`, agrega:

```json
    "lint:check": "eslint \"{src,test}/**/*.ts\"",
```

**Explicación:** el script `lint` que trae Nest usa `--fix` (modifica archivos). En CI conviene sólo **verificar** (`lint:check`), sin corregir.

---

## Paso 6: Prometheus

📝 **Modificar** `ops/prometheus/prometheus.yml`. Agrega al final de `scrape_configs` (misma indentación que `- job_name: livekit`):

```yaml
  - job_name: notification-service
    metrics_path: /metrics
    static_configs:
      - targets: ["notifications:3100"]
```

**Explicación:** Prometheus lee `/metrics` cada 15 s. El dashboard **"SkillBridge - Notificaciones"**, que ya existe en Grafana (`ops/grafana/provisioning/dashboards/notifications.json`), usa exactamente los nombres de métrica que expone B, así que empieza a mostrar datos sin cambios.

```bash
# Levantar con observabilidad
make observability
# Prometheus → http://localhost:9090/targets → "notification-service" debe estar UP
# Grafana    → http://localhost:3000 (admin/admin) → carpeta SkillBridge → "SkillBridge - Notificaciones"
```

> ℹ️ Hallazgo aparte (no bloquea): `prometheus.yml` referencia `/etc/prometheus/rules/livekit-rules.yml`, pero compose **no** monta la carpeta `ops/prometheus/rules`. Coméntalo en el equipo de LiveKit.

---

## Paso 7: CI (GitHub Actions)

📝 **Modificar** `.github/workflows/ci.yml`

**7.1** Agrega este job después del job `frontend` (mismo nivel que `frontend:`):

```yaml
  notifications:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: notification-service
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: npm
          cache-dependency-path: notification-service/package-lock.json
      - name: Install
        run: npm ci --no-audit --no-fund
      - name: Lint
        run: npm run lint:check
      - name: Test
        run: npm test -- --ci
      - name: Build
        run: npm run build
```

**7.2** En el job `docker`, cambia `needs: [backend, frontend]` por:

```yaml
    needs: [backend, frontend, notifications]
```

y agrega este paso al final de sus `steps`:

```yaml
      - name: Build notifications image
        uses: docker/build-push-action@v6
        with:
          context: ./notification-service
          push: false
          tags: skillbridge-notifications:ci
```

**7.3** En el job `auto-merge-feature-pr`, cambia `needs: [backend, frontend, docker]` por:

```yaml
    needs: [backend, frontend, notifications, docker]
```

**Explicación:**
- **El job `notifications` no necesita la API key de Resend:** las pruebas usan clientes falsos y `EMAIL_DRIVER` es `log` por defecto. **No** se agrega ningún secreto a GitHub.
- `--ci` hace que Jest falle si faltan *snapshots* y no entre en modo *watch*.
- El auto-merge espera también a este job, así que no se mergea código del microservicio con pruebas rotas.

---

## Paso 8: Prueba integrada con Docker

```bash
cd ~/Projects/SkillBridge-AI-JAVA-Angular-Celula-1

# 1. En tu .env (raíz), para probar con envío real (ver guía de configuración):
#    EMAIL_DRIVER=resend
#    RESEND_API_KEY=re_xxx
#    MAIL_FROM=SkillBridge AI <onboarding@resend.dev>   (o tu dominio verificado)
#    MAIL_REDIRECT_TO=tu-correo-personal@gmail.com      (recomendado en desarrollo)

# 2. Construir y levantar todo
make up

# 3. Estado: "notifications" debe quedar (healthy)
make ps

# 4. Logs del microservicio
make logs-notifications
```

Pruebas:
1. Publica `docs/notifications/contract/user.registered.json` desde la UI de RabbitMQ ([CONTRACT §6](./CONTRACT.md#6-publicar-un-evento-a-mano-para-probar-sin-java)). Debe llegar el correo (a `delivered@resend.dev` si no hay redirect: Resend lo marca como *Delivered* en **Emails**).
2. **Error permanente:** cambia temporalmente `MAIL_FROM` a un dominio no verificado y reinicia (`docker compose up -d notifications`). El mensaje debe ir a la DLQ con `x-failure-reason: permanent_error`.
3. **Rebote simulado:** publica un evento con `subject.email = "bounced@resend.dev"`. En el dashboard de Resend debe figurar como *Bounced*.

---

## Paso 9: Subir tu trabajo

```bash
# Antes de subir: formato + lint + pruebas + build del microservicio
cd ~/Projects/SkillBridge-AI-JAVA-Angular-Celula-1/notification-service
npm run format && npm run lint:check && npm test && npm run build

cd ~/Projects/SkillBridge-AI-JAVA-Angular-Celula-1
git pull origin feature/notificaciones     # trae lo último de A y B
git add notification-service/src/infrastructure/email notification-service/src/templates \
        notification-service/Dockerfile notification-service/.dockerignore \
        notification-service/package.json notification-service/package-lock.json \
        docker-compose.yml .env.example Makefile ops/prometheus/prometheus.yml .github/workflows/ci.yml
git status                                  # verifica que NO aparezca .env
git commit -m "feat(notificaciones): adaptador Resend, plantillas en español, Docker, CI y scrape de Prometheus"
git push -u origin feature/notificaciones-resend-ops
gh pr create --base feature/notificaciones --head feature/notificaciones-resend-ops \
  --title "feat(notificaciones): Resend + plantillas + operación" \
  --body "Adaptador Resend con idempotencia y clasificación de errores, 8 plantillas HTML/texto en español, Dockerfile multi-stage, servicio en docker-compose, job de CI y scrape de Prometheus."
```

> **Conflicto en `package.json` / `package-lock.json`:** conserva las dependencias de ambos y después ejecuta `cd notification-service && npm install` para regenerar el lock. Luego `git add` y continúa.

---

## Definition of Done (Persona C)

- [ ] `npm test`, `npm run lint:check` y `npm run build` en verde.
- [ ] `make up` levanta `notifications` en estado *healthy* **sin** API key (driver `log`).
- [ ] Con `EMAIL_DRIVER=resend` llega un correo real y se ve en el dashboard de Resend.
- [ ] Las 8 plantillas se ven bien en Gmail (escritorio y móvil).
- [ ] CI con el job `notifications` en verde.
- [ ] `.env` **no** está en git, y la API key no aparece en ningún archivo versionado.
- [ ] Entrada en `MEMORIA.md`.

## Prompt para otra IA

```text
Trabajo en notification-service (NestJS 11, TypeScript estricto, Node 22) del proyecto SkillBridge AI.
Mi parte: implementar EmailSenderPort con el SDK oficial de Resend (idempotencyKey, clasificar errores
en RetryableEmailError/PermanentEmailError), las plantillas de correo HTML+texto en español
(TemplateRendererPort), el Dockerfile multi-stage, el servicio en docker-compose, el job de GitHub Actions
y el scrape de Prometheus. Contrato: docs/notifications/CONTRACT.md. Guía:
docs/notifications/GUIA-PERSONA-C-RESEND-OPS.md. Reglas: nunca exponer RESEND_API_KEY (ni en frontend
ni en git), no hacer git commit/push, registrar en MEMORIA.md.
Estoy en el Paso <N> y obtengo: <error>. Explícame la causa y dame el código corregido bloque por bloque.
```
