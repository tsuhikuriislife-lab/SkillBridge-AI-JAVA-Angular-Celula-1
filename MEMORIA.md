# Historial de Cambios (Memoria de Agentes)

Este documento guarda el historial de tareas, decisiones técnicas y modificaciones realizadas en el código fuente por agentes de Inteligencia Artificial a lo largo del desarrollo de **SkillBridge AI**.

## [2026-10-05] Inicialización de Memoria y Contexto
- **Agente:** Antigravity (Gemini 3.1 Pro)
- **Contexto:** Inicialización de archivos de control de IA.
- **Cambios realizados:**
  - Análisis completo de la documentación del proyecto (`README.md`, `ARCHITECTURE.md`, `BACKLOG.md`, `API.md`, `DEPLOYMENT-CHECKLIST.md`, `INSTRUCTOR-GUIDE.md`).
  - Creación del archivo `AGENTS.md` para estipular las reglas de arquitectura hexagonal y protección de secretos (API Keys) que debe seguir la IA.
  - Creación de `MEMORIA.md` para asentar el inicio del track de cambios.
- **Próximos pasos posibles:** Iniciar el desarrollo del Sprint enfocado en HU-08 ("Consultar mis reservas") y HU-09 ("Cancelar una reserva propia").

## [2026-10-05] Implementación de Manejo de Errores IA (HU-12)
- **Agente:** Antigravity (Gemini 3.1 Pro)
- **Contexto:** Mejorar los mensajes de error al interactuar con Gemini.
- **Cambios realizados:**
  - Creación de excepciones de dominio: `AiConfigurationException`, `AiQuotaExceededException`, y `AiNetworkException`.
  - Mapeo de excepciones en `GlobalExceptionHandler` con los correspondientes códigos de estado HTTP (503 Service Unavailable y 429 Too Many Requests).
  - Actualización de `GeminiAiAdapter` para capturar `RuntimeException`, analizar el mensaje de error subyacente y lanzar las excepciones específicas (red, cuota, apiKey).
  - Mejora en el frontend (`ai.component.ts`) para mostrar mensajes de sesión expirada (401/403) y consumir el detalle provisto por el backend.

## [2026-10-05] Aviso al intentar abrir una vista protegida
- **Cambios realizados:** El componente de ingreso ahora escucha cambios en los parámetros de consulta y muestra el aviso de inicio de sesión cuando el guard redirige con `authRequired=true`, incluso si la vista de ingreso ya estaba abierta.
- **Validación:** `cd frontend && npm run build` completado correctamente.

## [2026-10-05] Listado paginado de reservas propias
- **Cambios realizados:** Se agregó `GET /api/bookings/me` con paginación desde base de datos, filtro por el usuario autenticado y nombre del servicio en el resultado. Angular incorpora la ruta protegida `/bookings`, la vista de reservas y controles de página/tamaño.
- **Validación:** `mvn -Dtest=BookingServiceTest test` pasó (3 pruebas) y `npm run build` del frontend completó. La suite completa del backend sigue bloqueada por la versión de API Docker requerida por el test de persistencia existente.

## [2026-10-05] Filtros y orden del listado de reservas
- **Cambios realizados:** El listado admite orden alfabético ascendente/descendente, fecha y precio ascendente/descendente, además de filtrar reservas activas (creadas/confirmadas) o no activas (canceladas/completadas). Los criterios se aplican en la consulta paginada del backend.
- **Validación:** `mvn -Dtest=BookingServiceTest test` pasó (3 pruebas) y `npm run build` del frontend completó.

## [2026-10-05] Ampliación de pruebas HU-07
- **Cambios realizados:** El target `make test-back` ahora ejecuta Maven desde el host y se agregó `make test-back-unit` para pruebas unitarias sin Docker. Se ampliaron reglas de reservas, cache miss del catálogo y persistencia/filtros/paginación con PostgreSQL Testcontainers.
- **Validación:** `make test-back-unit` pasó (9 pruebas). La suite completa compila, pero la integración Testcontainers no se ejecuta en este equipo: el cliente Java solicita API 1.32 y el daemon exige 1.44. El cambio de `DOCKER_API_VERSION` no modificó ese resultado.

## [2026-10-07] Notificaciones persistentes de reservas
- **Cambios realizados:** Se agregó la migración `V2__create_schedule_history.sql`, con una notificación ligada a `bookings.id`, estados/tipos validados, clave única por reserva/evento/canal y expiración. La FK usa el modelo real de esta aplicación (`bookings`), ya que `user_services` no existe en las migraciones actuales.
- **Cambios realizados:** El consumidor RabbitMQ ahora registra una notificación in-app idempotente; se agregó consulta paginada de las notificaciones del usuario autenticado, tarea diaria de limpieza con retención configurable (90 días por defecto), contadores/temporizador Micrometer y una vista Angular protegida para consultarlas.
- **Observabilidad:** Se aprovecha Prometheus/Grafana ya configurados. Loki y Jaeger no se añadieron porque no son necesarios para esta HU.
- **Validación:** Pasaron las pruebas unitarias enfocadas del servicio, consumidor, idempotencia del adaptador y reservas; `mvn -DskipTests package` y `npm --prefix frontend run build` completaron. Se validó el JSON del dashboard y `git diff --check`.

## [2026-10-07] Renombrado de persistencia de notificaciones
- **Cambios realizados:** Se renombraron la entidad JPA, el repositorio, la proyección y el adaptador a `BookingNotification*`, y la tabla a `booking_notifications`, manteniendo el modelo de dominio `BookingNotification`.
- **Migración:** Se añadió V3 para renombrar la tabla, restricciones e índices creados por V2; V2 se conserva inmutable para instalaciones que ya la ejecutaron.
- **Validación:** Las pruebas enfocadas de persistencia, notificaciones y reservas pasaron; `mvn -DskipTests package` y `git diff --check` completaron correctamente.
