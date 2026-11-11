# Conectar Jira a Claude Code y mapear la épica

## 1. Conectar Jira (Atlassian) a Claude Code

En esta sesión ya hay disponible un conector de **Atlassian**, pero falta autenticarlo. Elige una opción:

### Opción A: desde la terminal de Claude Code (recomendada)
1. En el prompt de Claude Code escribe:
   ```
   /mcp
   ```
2. En la lista, selecciona el servidor **atlassian** (puede aparecer como `plugin:design:atlassian`).
3. Elige **Authenticate**. Se abre el navegador con el login de Atlassian.
4. Inicia sesión con la cuenta que tiene acceso al tablero, elige el **sitio** (`tu-equipo.atlassian.net`) y acepta los permisos.
5. Vuelve a la terminal. El servidor debe quedar como **connected**.

### Opción B: desde claude.ai
1. Entra a <https://claude.ai> → **Settings → Connectors**.
2. Busca **Atlassian (Jira & Confluence)** → **Connect** y autoriza el sitio.
3. Reinicia la sesión de Claude Code (`/exit` y `claude` de nuevo) para que tome el conector.

> Si tu organización de Atlassian exige aprobación de un admin para apps de terceros, pídele a quien administre el sitio que apruebe "Claude" o "Anthropic".

## 2. Qué compartir después

Con Jira conectado, pásame **uno** de estos datos:
- La **URL del tablero** (por ejemplo, `https://tu-equipo.atlassian.net/jira/software/projects/KAN/boards/1`), o
- La **clave de la épica** (por ejemplo, `KAN-30`).

Con eso puedo:
1. Leer la épica y sus criterios de aceptación y compararlos con este plan.
2. Proponer las historias y subtareas de abajo. **Sólo las creo si tú lo apruebas.**
3. Enlazar cada subtarea con su guía (`docs/notifications/GUIA-*.md`).

> Según `MEMORIA.md`, el proyecto de Jira usa la clave **KAN**.

## 3. Desglose propuesto para la épica

| Tipo | Resumen | Responsable | Estimación | Guía |
|---|---|---|---|---|
| Historia | Contrato de eventos de usuario v1 | Equipo | 0,5 h | [CONTRACT](./CONTRACT.md) |
| Tarea | Comprar dominio en Hostinger + verificar en Resend + API keys | Dylan + C | 0,5 h (+ propagación) | [CONFIGURACIÓN](./CONFIGURACION-DOMINIO-Y-SECRETOS.md) |
| Historia | Backend publica eventos de cuenta en RabbitMQ | Dylan (A) | 4 h | [Guía A](./GUIA-PERSONA-A-JAVA.md) |
| ↳ Subtarea | Evento de dominio + puerto + `UserEventEmitter` | A | 1 h | Pasos 1-4 |
| ↳ Subtarea | Adaptador RabbitMQ + exchange `skillbridge.events` | A | 1 h | Pasos 5-6 |
| ↳ Subtarea | Publicar en Auth/Admin/UserAccount + actor en controllers | A | 1 h | Pasos 7-11 |
| ↳ Subtarea | Pruebas unitarias y de contrato | A | 1 h | Paso 12 |
| Historia | Núcleo de `notification-service` (NestJS) | B | 4,5 h | [Guía B](./GUIA-PERSONA-B-NEST-CORE.md) |
| ↳ Subtarea | Scaffold, configuración, puertos (base compartida) | B | 0,75 h | Pasos 0-9 |
| ↳ Subtarea | Política de destinatarios + caso de uso | B | 1 h | Pasos 10-11 |
| ↳ Subtarea | Consumidor RabbitMQ con retry y DLQ | B | 1,5 h | Pasos 12-13 |
| ↳ Subtarea | Métricas, health y pruebas | B | 1,25 h | Pasos 12.4, 14-15 |
| Historia | Envío con Resend, plantillas y operación | C | 4 h | [Guía C](./GUIA-PERSONA-C-RESEND-OPS.md) |
| ↳ Subtarea | Adaptador Resend + pruebas | C | 1 h | Pasos 1-2 |
| ↳ Subtarea | 8 plantillas HTML/texto en español | C | 1,5 h | Paso 3 |
| ↳ Subtarea | Docker, compose, Makefile y `.env.example` | C | 0,75 h | Pasos 4-5 |
| ↳ Subtarea | Prometheus y CI | C | 0,75 h | Pasos 6-7 |
| Tarea | Integración E2E y PR a `develop` | Equipo | 1 h | [PLAN §6](./PLAN.md) |
| Bug | Autorización en `PATCH/DELETE /api/users/{id}`, ADMIN no desactivable, login de INACTIVE rechazado | A | 0,5 h | Guía A, Paso 13 |
| Historia (backlog, fuera de la épica) | Botón activar/desactivar usuario en el panel admin de Angular | — | — | — |
