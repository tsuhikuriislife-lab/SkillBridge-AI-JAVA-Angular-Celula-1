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


## [2026-10-06] Implementación de Detalle de Servicio y Toast de Autenticación
- **Agente:** Antigravity
- **Contexto:** HU/Tarea - Implementar la vista de Detalles de Servicio y lógica de "Inscribirse".
- **Cambios realizados:**
  - Creación del componente standalone `ServiceDetailsComponent` en frontend integrando HTML/CSS de diseños (`DetalleServicio.html`).
  - Lógica añadida en `HomeComponent` para validar el estado de sesión antes de redirigir a `/service/:id` al presionar "Inscribirse".
  - Se agregó un "Toast" modal en `home.component.html` (y estilos) que pide al usuario iniciar sesión si no está autenticado, con botones para cancelar o ir a Login.
  - Se agregó el método `getById(id)` en `OfferingService` del frontend.
  - Se agregó el endpoint `GET /api/offerings/{id}` en `OfferingController.java` del backend, y configuración correspondiente en `SecurityConfiguration.java` para permitir acceso público.
  - Reconstrucción de contenedores de backend y frontend.
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
## [2026-10-07] Implementación de Diagnóstico Técnico Previo con IA (Mini-Assessment)
- **Agente:** Antigravity (Gemini 3.8 Flash)
- **Contexto:** Se implementó la funcionalidad de diagnóstico previo a la reserva para evaluar prerrequisitos mediante IA generativa.
- **Cambios realizados:**
  - **Dominio:** Creación de modelos puros `AssessmentQuestion`, `TechnicalAssessment`, `AssessmentQuestionResult` y `AssessmentEvaluation`.
  - **Aplicación:** Creación de puertos de entrada `GenerateAssessmentUseCase`, `EvaluateAssessmentUseCase` y puertos de salida `AiAssessmentPort`, `AssessmentSessionCachePort`. Implementación de la orquestación en `AssessmentService` con lógica de calificación, umbral de aprobación y recomendaciones personalizadas.
  - **Infraestructura:**
    - `GeminiAiAssessmentAdapter`: Adaptador que utiliza Spring AI `ChatClient` para consultar a Google Gemini y parsear preguntas y explicaciones en formato JSON estructurado.
    - `RedisAssessmentCacheAdapter`: Adaptador de almacenamiento temporal en Redis (con fallback en memoria) para guardar la sesión del diagnóstico con TTL configurable.
    - `AssessmentController`: Endpoints REST `POST /api/assessments/offerings/{offeringId}` y `POST /api/assessments/{assessmentId}/submit`.
    - `SecurityConfiguration`: Habilitación de acceso público a `/api/assessments/**`.
    - Pruebas unitarias: Creación de `AssessmentServiceTest` (5 casos de prueba pasando al 100%).
  - **Frontend:**
    - Creación de `AssessmentService` en Angular.
    - Integración en `ServiceDetailsComponent`: Callout en la sección de prerrequisitos, botón en la tarjeta de compra y modal interactivo paso a paso con barra de progreso, selección de opciones y pantalla de resultados con feedback y explicaciones pedagógicas por pregunta.
## [2026-10-07] Corrección de Cuota y Manejo de Excepciones en Gemini
- **Agente:** Antigravity (Gemini 3.8 Flash)
- **Contexto:** Se diagnosticó y corrigió el fallo al generar el diagnóstico con Gemini tras actualizar la API Key.
- **Causa Raíz:**
  1. El modelo configurado por defecto (`gemini-3.8-flash`) tiene un límite gratuito restrictivo de 20 peticiones al día en la API de Google, lo que arrojaba `ClientException: 429 You exceeded your current quota... limit: 20`.
  2. Spring AI envolvía la excepción de Google en `NonTransientAiException: Failed to generate content`. Como el mensaje de nivel superior no contenía "429" ni "quota", no se clasificaba como `AiQuotaExceededException` y se lanzaba como `BusinessRuleException` genérica con "RuntimeException".
- **Cambios realizados:**
  - Actualización del modelo en `.env`, `application.yml` y `docker-compose.yml` a `gemini-3.1-flash-lite`, el cual cuenta con alta cuota en el tier gratuito y tiempos de respuesta óptimos.
  - Mejora en `GeminiAiAssessmentAdapter.java` y `GeminiAiAdapter.java` agregando el método `extractErrorDetails()` que recorre recursivamente toda la cadena de causas (`ex.getCause()`) para detectar correctamente códigos 429, 401, 403, 503 y cuotas agotadas.
  - Reconstrucción y despliegue de contenedores Docker (`docker compose up -d --build backend frontend`).
- **Validación:**
  - Petición directa a `POST /api/assessments/offerings/11111111-1111-1111-1111-111111111111` generó exitosamente 3 preguntas con sus opciones.
  - Petición a `POST /api/assessments/{id}/submit` evaluó con éxito las respuestas devolviendo puntaje 3/3, aprobación y explicaciones pedagógicas.


## [2026-10-07] Implementación de Vista de Checkout (Placeholder)
- **Agente:** Antigravity (Gemini 3.1 Pro)
- **Contexto:** Tarea - Implementar la vista de Checkout en el frontend desde el diseño base.
- **Cambios realizados:**
  - Creación del componente standalone `CheckoutComponent` (`checkout.component.ts`, `checkout.component.html`, `checkout.component.css`) en `frontend/src/app/features/`.
  - Extracción de HTML y CSS de la sección superior de `CheckoutYRegistros.html` (ignorando "Mis registros").
  - Mapeo de la lógica básica en TS para el cambio visual entre opciones de pago y vista de éxito, sin funcionalidad real transaccional.
  - Copia de las imágenes del diseño a la carpeta `frontend/public/images/` y actualización del placeholder de imagen en el componente para usar una de ellas.
  - Inclusión de la ruta `/checkout` en `app.routes.ts`.
  - Inclusión del CDN de `boxicons-brands` en `index.html` para soportar el ícono de PayPal.

## [2026-10-07] Conexión de Checkout
- **Agente:** Antigravity (Gemini 3.1 Pro)
- **Contexto:** Tarea - Conectar la vista de Checkout desde el botón "Inscribirse ahora" de un servicio específico.
- **Cambios realizados:**
  - Actualización del componente `ServiceDetailsComponent` (`service-details.component.html`) añadiendo `routerLink="/checkout"` al botón "Inscribirse Ahora" para redirigir a la nueva vista.

## [2026-10-07] Funcionalidad Real de Reserva en Checkout
- **Agente:** Antigravity (Gemini 3.1 Pro)
- **Contexto:** Conectar la vista de Checkout con el backend real para ejecutar la reserva.
- **Cambios realizados:**
  - `BookingService`: Se agregó el método `create()` para consumir el endpoint `POST /api/bookings`.
  - `app.routes.ts`: Se cambió la ruta a `/checkout/:id` para recibir el ID del servicio.
  - `CheckoutComponent`: Ahora lee el `:id` de la ruta, carga los datos del servicio con `OfferingService` (mostrando su título, categoría y precio en la tarjeta) y al hacer clic en "Pagar", llama a `BookingService.create()`.
  - Muestra un estado de "Procesando..." y, si la respuesta es exitosa (200/201), avanza a la vista de éxito.
