# Historial de Cambios y Versiones (Release Changelog)

Todas las modificaciones notables de **SkillBridge AI** quedan documentadas en este archivo según el estándar [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/) y siguiendo [Semantic Versioning](https://semver.org/).

Para el registro diario continuo de ejecución de tareas por agentes inteligentes, consulte también [MEMORIA.md](../MEMORIA.md).

---

## [v1.3.0] - 2026-10-21

### Añadido
- **Aulas Virtuales y Clases en Vivo con LiveKit WebRTC:**
  - `MeetingController`: Iniciar, unirse, finalizar y cancelar reuniones con validación de roles y de inscripciones activas.
  - Generación segura en backend de tokens JWT de LiveKit con permisos diferenciados para mentores (publicador de audio/video/pantalla) y estudiantes (suscriptor/interactor).
  - Webhooks de ciclo de vida (`/api/webhooks/livekit`) para procesar eventos `room_finished`, `participant_joined` y `participant_left`.
  - Suite de pruebas de simulación de streaming WebRTC sintético (`scripts/livekit-stream-test/stream-test.js` y target `make test-livekit-stream`) verificando criterios de aceptación CA 7.1 (latencia video < 250 ms, audio < 60 ms), CA 7.2 (tasa media 190-200 kbps sin pérdida de paquetes) y CA 7.3 (desconexión limpia y tolerancia a caídas).
  - Documentación de monitoreo y SLAs en `docs/LIVEKIT-MONITORING.md`.

---

## [v1.2.0] - 2026-10-08

### Añadido
- **Subsistema de Inteligencia Artificial (Google Gemini):**
  - Adaptador `GeminiAiAdapter` para recomendaciones contextuales del catálogo con directrices anti-alucinación (*Grounding*) y auto-adición de enlaces Markdown directos (`/service/:id`).
  - Adaptador `GeminiAiAssessmentAdapter` para generación dinámica de diagnósticos técnicos de prerrequisitos (*Mini-Assessments*) de 3 preguntas de opción múltiple con respuestas y explicaciones pedagógicas.
  - Caché de sesiones de diagnóstico en dos niveles (`RedisAssessmentCacheAdapter`) con TTL de 30 minutos y conmutación automática por fallo hacia `ConcurrentHashMap` en memoria local.
  - Proyección segura `ClientAssessmentResponse` que excluye respuestas correctas en la entrega al cliente para evitar trampas.
  - Motor de calificación en `AssessmentService` con umbral del 60%, feedback cualitativo y retroalimentación pedagógica por pregunta.
  - Componente modal interactivo en Angular con barra de progreso, selección de opciones y pantalla de resultados con feedback pedagógico.

### Corregido
- **Gobernanza de Cuotas y Errores de IA:**
  - Migración del modelo por defecto a `gemini-3.1-flash-lite` para evitar las cuotas restrictivas del nivel gratuito de modelos experimentales.
  - Implementación del extractor recursivo de causas (`extractErrorDetails`) para capturar excepciones anidadas de Spring AI (`NonTransientAiException`) y mapear adecuadamente códigos 429 (Too Many Requests), 401/403 (Configuración/API Key) y 503 (Red/Timeout).

---

## [v1.1.0] - 2026-10-07

### Añadido
- **Panel de Gestión de Servicios para Proveedores (HU-10):**
  - Endpoints REST `/api/provider/services` para creación, edición, listado paginado y conmutación de estado activo/inactivo.
  - Gestión avanzada de horarios semanales (`ServiceSchedule`) con cálculo automático de horas de inicio/fin y soporte para múltiples días simultáneos.
  - Migración Flyway inmutable `V11__add_time_to_schedules.sql`.
  - Vistas de Angular para creación (`create-offering`) y gestión de catálogo del mentor (`my-offerings`) con validaciones reactivas de fechas mínimas (48 horas) y máximas (año 2099).
- **Flujo de Checkout y Reservas:**
  - Componente `CheckoutComponent` con precarga del servicio, cálculo de tarifas y conexión con `POST /api/bookings`.
  - Política de retiro y cancelación: derecho a reembolso antes de las 24 horas del inicio del servicio.
- **Notificaciones In-App Persistentes:**
  - Migraciones Flyway `V2__create_schedule_history.sql` y `V3` (`booking_notifications`).
  - Consumidor RabbitMQ idempotente que persiste estados y cambios de reserva.
  - Endpoint paginado `/api/history/me` y vista protegida en Angular.

### Seguridad
- Expulsión estricta de sesión (modal bloqueante a pantalla completa) ante respuestas `401 Unauthorized`.
- Prevención de deslogueo erróneo por `403 Forbidden` cuando se deben a permisos insuficientes o violaciones de integridad de datos (`DataIntegrityViolationException`).

---

## [v1.0.0] - 2026-10-05

### Añadido
- **Línea Base del Proyecto e Infraestructura Inicial:**
  - Monolito modular en Spring Boot 3.5.x con Java 21 y Arquitectura Hexagonal.
  - Frontend SPA con Angular 20 (Standalone Components) y Nginx como proxy reverso.
  - Persistencia de usuarios y servicios en PostgreSQL 17 gestionada con Flyway.
  - Caché de catálogo público en Redis 8 bajo el patrón Cache-Aside.
  - Mensajería asíncrona en RabbitMQ 4 con Dead Letter Queue (DLQ) para eventos `BookingCreated`.
  - Autenticación stateless con Spring Security, JWT y protección CSRF para Single Page Applications.
  - Observabilidad integral con Spring Boot Actuator, Micrometer, Prometheus y Grafana.
  - Directrices estrictas para agentes de IA en `AGENTS.md` y registro en `MEMORIA.md`.
