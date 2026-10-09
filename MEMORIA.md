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
- **Validación:** `make test-back-unit` pasó (9 pruebas). La suite completa compila, pero la integración Testcontainers no se ejecuta en este equipo: el cliente 
Java solicita API 1.32 y el daemon exige 1.44. El cambio de `DOCKER_API_VERSION` no modificó ese resultado.

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

## [2026-10-08] Comportamiento Estricto de Expiración de Token
- **Agente:** Antigravity
- **Contexto:** Al expirar el token, el usuario debe ver un modal a pantalla completa que bloquee cualquier otra interacción, en lugar de un toast descartable.
- **Cambios realizados:**
  - `AuthService`: Agregado el estado `sessionExpired` y el método `forceLogout()` para limpiar la sesión y redirigir al `/login`.
  - `auth.interceptor.ts`: Al recibir 401/403, se llama a `triggerSessionExpired()` en lugar del `ToastService`.
  - `app.component.ts`: Se añadió el overlay modal con el estado de expiración, con un alto `z-index` y fondo opaco, forzando el botón "Ir al login" para recuperar el estado limpio.
  - Reconstrucción de `frontend` y reinicio del contenedor `skillbridge-ai-frontend-1`.
## [2026-10-08] Corrección de Paginación en Notificaciones
- **Agente:** Antigravity
- **Contexto:** La vista de mis notificaciones mostraba "NaN de NaN" en el indicador de paginación.
- **Cambios realizados:**
  - Se identificó que el backend (`BookingHistoryController`) estaba devolviendo la página actual bajo la propiedad `number` (debido a la estructura del `PageResult` de Java) en lugar de `page` que esperaba el frontend.
  - Se actualizó la interfaz `NotificationPage` en `frontend/src/app/core/notification.service.ts` para aceptar `number` o `page`.
  - Se modificó la asignación en `MyNotificationsComponent` (`my-notifications.component.ts`) para utilizar prioritariamente `result.number ?? result.page ?? 0`, asegurando que `currentPage` contenga un valor numérico y el cálculo visual se realice correctamente.

## [2026-10-08] Administrar servicios como proveedor (HU-10) — backend
- **Agente:** Claude (Sonnet 5.5)
- **Contexto:** Un proveedor puede crear, editar y activar/desactivar sus propios servicios. Adaptado al nuevo esquema de base de datos del equipo (tablas `services` y `categories`), conservando los nombres existentes (`Offering`, `/api/offerings`).
- **Cambios realizados:**
  - Dominio: `Offering` ampliado (código, categoría, precio, descripciones, capacidad, estado y creador), enum `OfferingStatus` (ACTIVE/INACTIVE) y `ForbiddenOperationException`.
  - Persistencia: `OfferingEntity` mapeada a `services`; consultas paginadas por proveedor con orden fijo (nombre y fecha de creación, ascendente/descendente); `CategoryPort` y `CategoryPersistenceAdapter` (existencia y listado de categorías activas).
  - Aplicación: `ProviderOfferingService` con validaciones, verificación de propiedad, código automático `SRV-XXXXXXXX` e invalidación del caché de Redis después de guardar; `CategoryService` para listar categorías.
  - REST: `ProviderOfferingController` en `/api/provider/offerings` (POST, PUT, PATCH estado, GET paginado), `CategoryController` en `/api/categories`, DTOs con validación, regla `hasRole("PROVIDER")` en `SecurityConfiguration` y mapeo de 403 en `GlobalExceptionHandler`.
  - Seguridad de datos: el catálogo público ahora responde con `OfferingPublicResponse`, sin `createdBy` ni `status`.
  - Se ajustaron `BookingService` (comparación con `OfferingStatus.ACTIVE`) y `GeminiAiAdapter` (usa `name` y `shortDescription`) al nuevo modelo.
  - Documentación: `docs/API.md` actualizado con los endpoints y reglas del proveedor.
- **Validación:** Pasaron las pruebas unitarias de `ProviderOfferingServiceTest`, `ProviderOfferingControllerTest`, `OfferingServiceTest` y `BookingServiceTest`. `JpaOfferingRepositoryTest` y la prueba real contra la base quedan pendientes de la migración Flyway del nuevo esquema (tablas `services`/`categories`) que entrega otro integrante del equipo.
- **Pendiente:** Migración SQL del equipo (`services.code` UNIQUE, `created_by` → `app_users`) y adaptar reservas (`BookingEntity`, `JpaBookingRepository`) al nuevo esquema.

## [2026-10-08] Administrar servicios como proveedor (HU-10) — frontend y usuario actual
- **Agente:** Claude (Sonnet 5.5)
- **Contexto:** Pantalla para que el proveedor administre sus servicios y ajustes del backend necesarios para mostrarla.
- **Cambios realizados:**
  - Backend: `GET /api/users/me` (`CurrentUser`, `GetCurrentUserUseCase`, `CurrentUserService`, `UserController`) que devuelve nombre, correo y rol leído de la base de datos, sin contraseña. `GET /api/categories` pasó a ser público en `SecurityConfiguration`.
  - Angular: el catálogo (`home` y `booking`) se adaptó al nuevo formato de `Offering` (`name`, `shortDescription`, `categoryId`) y muestra la categoría por nombre. `AuthService` carga el perfil desde `/users/me` al abrir la app y después de ingresar; nuevo `providerGuard` que consulta el rol antes de entrar a la ruta.
  - Nueva pantalla `/provider/offerings` ("Mis servicios"): lista paginada con orden fijo (nombre y fecha, ascendente/descendente), formulario de crear y editar con validaciones, y activar/desactivar con confirmación. El enlace del menú solo aparece si el rol es PROVIDER, sin necesidad de cerrar sesión.
  - Servicios de Angular: `provider-offering.service.ts` y `category.service.ts`.
  - Documentación: `docs/API.md` actualizado.
- **Validación:** pasaron las pruebas unitarias de `CurrentUserServiceTest` y `UserControllerTest`, y `npm run build` del frontend completa sin errores. La prueba completa en el navegador queda pendiente de la migración Flyway del nuevo esquema (`services`/`categories`) que entrega otro integrante.
- **Pendiente:** Probar de punta a punta con la base de datos del equipo y adaptar reservas (`BookingEntity`, `JpaBookingRepository`) y `JpaOfferingRepositoryTest` al nuevo esquema.
## [2026-10-08] Fusión de rama feat/implementar-rol-proveedor
- **Agente:** Antigravity
- **Contexto:** Resolución de conflictos de la rama `feat/implementar-rol-proveedor`.
- **Cambios realizados:** 
  - Se completó la fusión priorizando el código de `fix/crud-completo` que ya tenía implementado el panel de proveedor (`ServiceManagementController`, Angular views en `provider/`, `OfferingCrudService`) y el nuevo esquema de la base de datos (`ServiceStatus`, UUID categoryId). 
  - Se descartaron las implementaciones duplicadas (`ProviderOfferingService`, `ProviderOfferingController`, etc.) para mantener la coherencia con la arquitectura hexagonal limpia introducida en HEAD.
  - La memoria de agentes fue unificada.

## 2026-10-08: Fixes para Provider y Checkout
- Se corrigió el bug del `[object Object]` en `CreateOfferingComponent`, mapeando correctamente el ID de la categoría seleccionada.
- Se agregó el manejo de `HttpMessageNotReadableException` en `GlobalExceptionHandler.java` (retorna 400 Bad Request) para evitar que el framework lance un 403 y desloguee falsamente al usuario.
- Se adaptó `CreateOfferingComponent` y `OfferingService` para recolectar campos del cronograma (`startDate`, `startDay`, `sessionDuration`, etc.) y encadenar la petición `/api/provider/services/{id}/schedule`.
- Se previno el acceso prematuro a la pasarela de pagos esperando la validación de `checkEnrollmentStatus` antes de habilitar la vista en `service-details.component.ts`.
- Se ajustó `CheckoutComponent` para que expulse inmediatamente al usuario (mediante `router.navigate`) si ingresa a la URL de checkout de un curso que ya tiene reservado, mostrando además el toast correspondiente.
- Se modificó `ServiceEnrollmentService.java` (`!isAfter` -> `isBefore`) para evitar errores 422 al reservar servicios que comienzan el mismo día.
- Se verificó la consistencia del modelo `NotificationPage` (retorna `PageResult` con `totalElements`), resolviendo la inconsistencia visual de "NaN de NaN".
- Se fusionó exitosamente la rama `feat/implementar-rol-proveedor`.

## [2026-10-08] Inclusión de horario en creación de servicios y ajuste de sesión
- **Agente:** Antigravity
- **Contexto:** Se solicitaron correcciones en la vista de creación de servicios para incluir la hora, clarificar selectores, y evitar cierres de sesión abruptos por errores de permisos.
- **Cambios realizados:**
  - **Dominio y Persistencia (Backend):** Se amplió el modelo `ServiceSchedule` y sus DTOs (`ServiceScheduleRequest`, `ServiceScheduleOut`) para incluir `startTime` y `endTime` de tipo `LocalTime`. Se actualizó `ServiceScheduleEntity` y los adaptadores de persistencia, creando una nueva migración Flyway inmutable (`V11__add_time_to_schedules.sql`) con valores por defecto.
  - **Pruebas (Backend):** Se actualizaron `ServiceScheduleServiceTest` y `ServiceEnrollmentServiceTest` para proveer instancias válidas de `LocalTime`.
  - **Frontend:** Se modificó `CreateOfferingComponent` para incorporar dos nuevos inputs (`type="time"`) para `startTime` y `endTime`, mapeados apropiadamente en el formulario reactivo (`FormGroup`).
  - **Servicio Angular:** En `offering.service.ts` se ajustó la captura del `scheduleReq` para enviar los campos de hora formateados a Jackson (`HH:mm:00`). Se verificó que el selector `Día de la semana` ya envía valores ENUM funcionales (`MONDAY`, etc.).
  - **Autenticación (Frontend):** Se corrigió la lógica en `auth.interceptor.ts`. Anteriormente, ante un `403 Forbidden`, la app cerraba abruptamente la sesión. Ahora solo se expulsa la sesión (`triggerSessionExpired()`) con códigos `401 Unauthorized`, permitiendo al usuario con roles insuficientes permanecer en sesión y solo visualizar el mensaje de error de creación.

## [2026-10-08] Mejoras en la vista de creación de servicios (proveedor) y corrección del error 403
- **Agente:** Antigravity
- **Contexto:** El usuario reportó un error 403 al crear servicios y solicitó mejoras UI en la selección de días y categorías.
- **Cambios realizados:**
  - **Manejo de Excepciones:** Se agregó un `ExceptionHandler` en `GlobalExceptionHandler.java` para interceptar `DataIntegrityViolationException`. Esto previene que una excepción de base de datos no manejada (como desbordamiento numérico por precios/capacidades grandes) se propague a Spring Security y retorne erróneamente un 403, retornando en su lugar un 400 Bad Request estructurado.
  - **Frontend UI:** Se modificó `create-offering.component.ts` para usar checkboxes permitiendo la selección de múltiples días (`startDays`). Se ajustó el buscador de categorías para que ocupe la mitad de la pantalla y muestre la categoría seleccionada a la derecha.
  - **Lógica de Múltiples Horarios:** En `offering.service.ts`, el método `create` ahora itera sobre los días seleccionados. Por cada día, se calcula automáticamente la fecha de inicio correspondiente (usando `getNextDateForDay`) basada en la fecha base y el día objetivo, y se envían múltiples peticiones concurrentes `POST` a la ruta `/schedule` utilizando `forkJoin`.
  - **Eliminación de "Hora Fin":** Se eliminó el campo "hora fin" de la interfaz. En el backend, el `ServiceScheduleController` ya calcula automáticamente `endTime` usando `startTime().plusMinutes(req.sessionDuration())`, evitando que el frontend tenga que enviarlo.

## [2026-10-08] Fix para UUID de ServiceScheduleEntity
- **Agente:** Antigravity
- **Contexto:** Al intentar crear los horarios (schedules) en cadena con la creación del servicio, el endpoint devolvía un 500 Interno que Spring Security mapeaba como 403 Forbidden.
- **Cambio:** Se ajustó el método `createSchedule` en `ServiceScheduleService.java` para que genere explícitamente un `UUID.randomUUID()` en caso de que llegue nulo, dado que `ServiceScheduleEntity` no estaba configurada con generación automática (`@GeneratedValue`).

## [2026-10-08] Refinamiento de la UX y manejo de errores en la vista de Proveedor
- **Agente:** Antigravity
- **Contexto:** Repaso exhaustivo sobre el manejo de excepciones y alertas en las funcionalidades de creación, edición y visualización de servicios del proveedor.
- **Cambios en Frontend:**
  - `create-offering.component.ts`: Se implementó un validador personalizado (`minDateValidator`) para asegurar que la fecha de inicio tenga al menos 48 horas de anticipación, coincidiendo con la regla de negocio del backend y evitando llamadas API fallidas.
  - `create-offering.component.ts`: Se removió el atributo `[disabled]="form.invalid"` del botón de submit para permitir que el usuario intente enviarlo y el sistema pueda marcar los campos como tocados (`markAllAsTouched()`) y desplegar los mensajes de error correspondientes.
  - Se capturó adecuadamente el mensaje de error del backend (`err.error.detail`) en todas las peticiones (creación, edición, listar), de manera que si ocurre un problema de base de datos o regla de negocio, el usuario pueda ver el motivo real.
  - `my-offerings.component.ts`: Se añadió lógica completa para mostrar alertas de éxito y de error usando una función `showMessage` compartida con un `setTimeout` a 5 segundos para su limpieza automática.
  - Estilización: Se añadió una clase `.msg.error` a las plantillas HTML para pintar de rojo y con fondo claro las notificaciones de fallo en ambas pantallas.

## [2026-10-08] Corrección del mensaje de éxito en la creación de servicio
- **Agente:** Antigravity
- **Contexto:** Al crear un servicio, el mensaje de éxito ("Servicio creado exitosamente!") no se mostraba porque la función `cancel()` (encargada de limpiar el formulario) era llamada *después* de asignar el mensaje de éxito, y dentro de su lógica también blanqueaba la variable `msg`.
- **Cambio en Frontend (`create-offering.component.ts`):** Se invirtió el orden de ejecución en el callback `next` del observable, ejecutando `this.cancel()` antes de asignar `this.msg = 'Servicio creado exitosamente!'` para evitar que la limpieza sobrescriba el mensaje.

## [2026-10-08] UX Visual para errores de validación de formulario
- **Agente:** Antigravity
- **Contexto:** Identificación visual de los campos obligatorios o inválidos durante la creación y actualización de servicios.
  - Se añadieron estilos CSS (`.ng-invalid.ng-touched`) en los componentes `create-offering.component.ts` y `my-offerings.component.ts`. Cuando el usuario intenta enviar el formulario o sale de un campo dejándolo inválido, los bordes del input (o la caja contenedora de los checkboxes/selector personalizado de categoría) se vuelven rojos para indicarle exactamente dónde debe corregir.

## [2026-10-08] Validación de fecha máxima para evitar desbordamientos
- **Agente:** Antigravity
- **Contexto:** Al intentar ingresar valores excesivamente grandes en la fecha de inicio (ej. el año 275760), el sistema colapsaba porque el backend no podía deserializarlo (HttpMessageNotReadableException), resultando en un error confuso para el usuario.
- **Cambios realizados:**
  - **Frontend (`create-offering.component.ts`):** Se añadió el atributo HTML `max="2099-12-31"` al input de fecha para restringir fechas absurdas desde el navegador. Además, se actualizó el validador personalizado (ahora llamado `dateValidator`) para detectar si el año supera el 2099 y arrojar un error específico (`maxDate`) que muestra un mensaje amigable al usuario.
  - **Backend (`GlobalExceptionHandler.java`):** Se mejoró el manejador de la excepción `HttpMessageNotReadableException` para inspeccionar la causa raíz (`InvalidFormatException`). Si el fallo ocurre al parsear un `LocalDate`, ahora retorna un mensaje amigable indicando que el formato o el límite de la fecha no es válido, en lugar de exponer el error interno de Jackson al frontend.

## [2026-10-08] Corrección Crítica en la Gestión de Servicios (Mis Servicios)
- **Agente:** Antigravity
- **Contexto:** La vista de "Mis Servicios" permitía editar y cambiar el estado, pero introducía bugs críticos de pérdida de datos y no permitía cambiar la categoría.
- **Cambios realizados (Frontend):**
  - **Fallo de Desactivación Permanente:** Se corrigió un bug en `offering.service.ts` donde la función `toggleStatus` enviaba siempre `{ status: "INACTIVE" }` al backend (incluso al intentar activarlo), y la función `update` hacía lo mismo porque el formulario no enviaba la propiedad `active`. Ahora `toggleStatus` envía el estado invertido correctamente y `update` preserva el estado actual del servicio al editarlo.
  - **Pérdida de Capacidad y Código:** Se corrigió la función `update` para que no sobrescriba la capacidad a `10` y el código del servicio a `SRV-XXXX` (lo cual destruía los datos originales). Ahora se agregó `capacity` y `code` a la interfaz `Offering` de Angular, se mapean desde la respuesta, y se reutilizan al hacer el PUT al backend.
  - **Edición de Categoría:** Se añadió el selector desplegable de categoría en el modal de edición de `my-offerings.component.ts`. Ahora el proveedor puede cambiar la categoría del servicio una vez creado.
  - **Validación Visual:** Se agregó la marcación roja automática para el selector de categoría (`select.ng-invalid`) y se invocó `markAllAsTouched()` en la función `update()` para que el usuario sepa qué falta.
  - **Mejora UI (Overflow de Textos):** En `my-offerings.component.ts`, se ajustó la disposición de las tarjetas (`.card`) para evitar desbordamientos visuales cuando el título o la descripción son muy largos. Se aplicó `white-space: nowrap`, `overflow: hidden` y `text-overflow: ellipsis`, utilizando Flexbox (`.title-row`) para garantizar que la etiqueta de estado ("Activo"/"Inactivo") nunca sea empujada fuera de la pantalla ni se oculte. Además, se añadió el atributo HTML `[title]` para permitir al usuario leer el texto completo al pasar el mouse por encima.
  - **Administración de Horarios (Schedules):** Se añadió la capacidad de gestionar los horarios desde el modal de edición de servicio (`my-offerings.component.ts`). Al abrir el modal, se hace una petición HTTP para cargar todos los horarios asociados. Se muestra una lista de los horarios activos indicando el día de la semana, hora y duración. Se agregó un botón para **eliminar** horarios específicos usando el endpoint `DELETE /api/provider/services/{id}/schedule/{scheduleId}`, conectando con la capa de persistencia a través de `offering.service.ts`.
  - **Campos Faltantes de la HU:** Se agregó la posibilidad de editar la capacidad por sesión y la URL de la imagen (photoUrl) en el formulario reactivo del modal, asegurando que se modifiquen en la base de datos al usar el PUT de la API.

## [2026-10-09] Enlace y Notificación Directa a Vista de Curso en Asistente IA
- **Agente:** Antigravity (Gemini 3.8 Flash)
- **Contexto:** Permitir que las recomendaciones del asistente IA incluyan enlaces directos y tarjetas de acceso inmediato a la vista de detalle del curso (`/service/:id`), evitando que el usuario deba buscarlo manualmente por nombre.
- **Cambios realizados:**
  - **Backend (`GeminiAiAdapter.java`):**
    - Se formateó el catálogo entregado al prompt para incluir de forma explícita el `ID` de la oferta y su ruta directa `/service/{id}` adaptado a la nueva estructura de `Offering` (`name`, `code`, `shortDescription`/`detail`).
    - Se agregaron instrucciones estrictas al prompt para exigir el formato Markdown `[Ver curso: NOMBRE](/service/ID)` e indicar al usuario que puede acceder directamente.
    - Se implementó un mecanismo de respaldo/seguridad que detecta si el modelo mencionó cursos del catálogo pero omitió los enlaces directos, anexando automáticamente una sección de enlaces al pie del mensaje.
    - Se unificó el manejo y extracción recursiva de detalles de error (`extractErrorDetails`).
    - Se crearon pruebas unitarias completas en `GeminiAiAdapterTest` validando enlaces directos, respaldo de seguridad y excepciones de configuración, cuota y red.
  - **Frontend (`AiComponent`):**
    - Se actualizó el modelo de mensaje `ChatMessage` para soportar `SafeHtml` y lista de `courses` recomendados.
    - Se implementó pre-carga de ofertas con `OfferingService` y extracción dinámica de IDs y cursos recomendados (`extractCourses`).
    - Se agregaron formateo de Markdown interactivo para enlaces `/service/:id` y captura de eventos de clic en la burbuja para navegar con el `Router` de Angular sin recargar la página.
    - Se agregó una tarjeta/notificación interactiva de cursos recomendados al pie del mensaje del asistente con badges, títulos y botones de acción "Ir al curso", junto a la indicación de acceso directo sin búsqueda manual.
    - Se añadieron estilos modernos en `ai.component.css` manteniendo la línea visual del proyecto.
- **Validación:**
  - Pruebas unitarias de backend pasando al 100%.
  - Compilación de Angular (`npx ng build`) completada con éxito.
  - Reconstrucción y despliegue en Docker Compose (`docker compose up -d --build backend frontend`) validando contenedores activos y saludables.


## [2026-10-09] Fusión de funcionalidades de la vista de proveedor
- **Agente:** Antigravity
- **Contexto:** La rama actual (`fix/frontend-details`) tenía una implementación muy básica y desactualizada de la vista de proveedor en comparación con la rama `develop`, la cual contenía funcionalidades avanzadas de la HU-13 (gestión de horarios, validaciones complejas de fechas, manejo de categorías por objeto, entre otras).
- **Acción:** Se realizó un merge (fusión) de los cambios de `origin/develop` hacia la rama actual, priorizando los cambios de `develop` (estrategia `-X theirs`) para resolver automáticamente los conflictos en favor de la implementación más completa.
- **Resultado:** La rama actual ahora tiene la versión más robusta de los componentes `create-offering.component.ts` y `my-offerings.component.ts`, alineada con las funcionalidades desarrolladas en `develop`.

## [2026-10-09] Mejora visual y descriptiva en notificaciones
- **Agente:** Antigravity
- **Contexto:** La vista de notificaciones en Angular estaba solo mostrando de manera estática el cambio de estado de la reserva, sin permitir ir al detalle del servicio ni comparar claramente el estado anterior.
- **Acción (Frontend):** Se modificó la plantilla de `my-notifications.component.ts` agregando un ancla `<a>` con la directiva `[routerLink]` que envuelve al título (`notification.title`). Ahora enlaza a `/service/:bookingId` usando clases para estilizar.
- **Acción (Backend):** En `BookingHistoryController.java` se mejoró la generación del texto de la notificación para que busque en el `BookingHistory` del usuario cuál era su estado anterior. Ahora ensambla dinámicamente el mensaje: `"El/la estado del servicio fue modificado. Antes: [antes], despues [despues]"`. Si es el primer evento en la historia, el "Antes" queda catalogado como "Ninguno".
- **Validación:** Se recompiló la imagen `backend` en Docker y se verificó que arranca satisfactoriamente.

## [2026-10-09] Corrección de Fallo de CI por Límite de Peticiones en Docker Hub
- **Agente:** Antigravity
- **Contexto:** Al empujar los últimos cambios, las pruebas en GitHub Actions (`docker/build-push-action`) fallaron con un error `429 Too Many Requests` proveniente de `registry-1.docker.io`. Esto ocurre porque las IPs públicas de GitHub Actions suelen sobrepasar el límite de descargas anónimas impuesto por Docker Hub para imágenes oficiales.
- **Acción:** Se modificaron los archivos `backend/Dockerfile` y `frontend/Dockerfile` para sustituir las imágenes base de Docker Hub (`maven`, `eclipse-temurin`, `node`, `nginx`) por sus réplicas oficiales en el registro público de Amazon ECR (`public.ecr.aws/docker/library/...`), el cual no impone límites estrictos de peticiones anónimas.
- **Resultado:** Los Dockerfiles mantienen las mismas versiones (ej. `node:22-alpine`, `maven:3.9.16-eclipse-temurin-21`), pero descargadas desde un espejo (mirror) confiable, lo que garantiza estabilidad en los pipelines de integración continua.
