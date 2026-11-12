# Jira: épica KAN-49 (tablero `skillbridgec3.atlassian.net`, proyecto KAN)

## Historias de la épica

| Clave | Historia | Responsable | Guía |
|---|---|---|---|
| [KAN-49](https://skillbridgec3.atlassian.net/browse/KAN-49) | **Épica:** Microservicio de notificaciones por email con NestJS y Resend | Dylan | [PLAN.md](./PLAN.md) |
| [KAN-59](https://skillbridgec3.atlassian.net/browse/KAN-59) | HU-NOT00: Publicación de eventos desde el backend Java + seguridad *(creada el 2026-11-11)* | Dylan | [GUIA-DYLAN-HU-NOT00-JAVA.md](./GUIA-DYLAN-HU-NOT00-JAVA.md) |
| [KAN-56](https://skillbridgec3.atlassian.net/browse/KAN-56) | HU-NOT01: Scaffolding NestJS, SDK de Resend y motor de plantillas | Dylan | [GUIA-DYLAN-HU-NOT01-KAN-56.md](./GUIA-DYLAN-HU-NOT01-KAN-56.md) |
| [KAN-57](https://skillbridgec3.atlassian.net/browse/KAN-57) | HU-NOT02: Consumidor RabbitMQ, idempotencia y reintentos (DLQ) | Kevin Mercado | [GUIA-KEVIN-HU-NOT02-KAN-57.md](./GUIA-KEVIN-HU-NOT02-KAN-57.md) |
| [KAN-58](https://skillbridgec3.atlassian.net/browse/KAN-58) | HU-NOT03: Webhooks de Resend, auditoría y salud | Leonardo Ayala | [GUIA-LEONARDO-HU-NOT03-KAN-58.md](./GUIA-LEONARDO-HU-NOT03-KAN-58.md) |

## Cambios hechos en Jira el 2026-11-11 (aprobados por Dylan)

- **Se creó KAN-59**, porque ninguna historia cubría la publicación de eventos desde Java. Incluye las correcciones de seguridad.
- **Criterios de aceptación ajustados** (marcados "actualizado 2026-11-11" en cada historia):
  - **KAN-56, CA 1.3:** motor Handlebars; plantillas de bienvenida, **confirmación de reserva** y avisos de cuenta. El **recordatorio de sesión en vivo** pasa a una 2ª iteración.
  - **KAN-57, CA 2.1:** exchange `skillbridge.events`, cola `notifications.events`, routing keys `user.#` y `enrollment.#`. La tarea 1 usa `@golevelup/nestjs-rabbitmq` en lugar de `@nestjs/microservices` (incompatible con el JSON plano de Spring).
  - **KAN-58, CA 3.3:** `NotificationLog` en una **base de datos propia `notifications`** con migraciones TypeORM. **CA 3.4** suma PostgreSQL a `/health`.
- **Comentario** en KAN-56, 57, 58 y 59 con el enlace a la guía de cada uno.
- **No se modificaron** responsables, estados ni prioridades.

## Flujo de estados sugerido

`Por hacer` → `En curso` (al crear la rama) → `En revisión` (al abrir el PR) → `Finalizada` (al mergear en `feature/notificaciones` y pasar el checklist E2E de [PLAN §6](./PLAN.md#6-checklist-de-integración-e2e-los-3-juntos)).

## Reconectar el conector de Atlassian (aviso)

Claude Code está conectado al endpoint **V1**, que es obsoleto y se retira el **2027-03-01**:
- **Conector oficial:** quítalo y vuelve a agregarlo desde el directorio de conectores de Claude.
- **Conector propio:** actualízalo a `https://mcp.atlassian.com/v2/mcp` y vuelve a autorizarlo.
- **Conector administrado por tu organización:** pídele el cambio al admin.

Para autenticar de nuevo en la terminal: `/mcp` → **atlassian** → **Authenticate**.
