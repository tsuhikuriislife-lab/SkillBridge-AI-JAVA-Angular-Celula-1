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

## [2026-10-08] Frontend de la Vista de Administración Completo
- **Agente:** Antigravity (Gemini 3.8 Flash)
- **Contexto:** Completar los elementos faltantes en el frontend de la vista de Administración antes de la integración con el backend.
- **Cambios realizados:**
  - **Seguridad y Enrutamiento:** Creación de `adminGuard` en `core/admin.guard.ts` para restringir el acceso a usuarios autenticados con rol `ADMIN`. Aplicación del guard a la ruta `/admin` en `app.routes.ts`.
  - **Navegación:** Inclusión del enlace "Panel Admin" en la barra de navegación superior (`app.component.ts`) condicionado a `auth.role() === 'ADMIN'`.
  - **Servicios:**
    - Ampliación de `UserService` (`core/userDto.service.ts`) con métodos completos CRUD (`create`, `update`, `delete`).
    - Creación de `ProviderService` (`core/provider.service.ts`) para consumir endpoints de gestión de proveedores (`getAll`, `getById`, `create`, `update`, `delete`).
    - Adición del método `delete` en `OfferingService` (`core/offering.service.ts`).
  - **UI / Componente Admin Dashboard (`AdminDashboardComponent`):**
    - Implementación de estado de carga (`isLoading` con spinner visual).
    - Integración de `ProviderService` con fallback defensivo a datos base mientras se crea el endpoint en el backend.
    - Conexión de acciones por fila:
      - Modal de visualización de detalles completos (`👁`).
      - Modal de confirmación y ejecución de eliminación (`✕`) con actualización reactiva en signals.
    - Implementación del modal dinámico `+ Nuevo Registro` adaptativo según la pestaña activa (`usuarios`, `proveedores`, `servicios`), validación y actualización en tiempo real con notificaciones `ToastService`.
  - **Estilos:** Agregados estilos CSS para modales, overlays con blur, diálogos de confirmación, formularios responsivos y spinner de carga en `admin-dashboard.component.css`.
- **Validación:** Compilación de producción con Angular CLI (`npm run build`) exitosa (0 errores).

## [2026-10-08] Backend para el Panel de Administración (Arquitectura Hexagonal)
- **Agente:** Antigravity (Gemini 3.8 Flash)
- **Contexto:** Implementación de los endpoints y lógica del backend para la gestión administrativa de usuarios, proveedores y servicios sin alterar el esquema de base de datos actual.
- **Cambios realizados:**
  - **Dominio:** Creación del modelo `ProviderSummary` para consolidar información de proveedores con conteo de servicios asociados.
  - **Aplicación:**
    - Puertos de Entrada: `AdminManageUsersUseCase`, `AdminManageProvidersUseCase`, `AdminManageOfferingsUseCase`.
    - Puertos de Salida: Ampliación de `UserRepositoryPort` (`findAll`, `findById`, `findByRole`, `deleteById`) y `OfferingRepositoryPort` (`countByProviderId`, `deleteById`).
    - Casos de Uso (Servicios):
      - `AdminUserService`: Gestión completa de usuarios (listado, búsqueda por ID, creación con contraseña hasheada vía `PasswordHasherPort`, actualización de datos y borrado con validación de unicidad de correo).
      - `AdminProviderService`: Listado de proveedores filtrando por `Role.PROVIDER` y agregando el conteo de servicios publicados, creación de nuevo proveedor y borrado.
      - `AdminOfferingService`: Eliminación de servicios con desalojo automático de caché Redis vía `OfferingCachePort`.
  - **Infraestructura:**
    - Seguridad: Configuración en `SecurityConfiguration.java` para exigir autoridad `hasRole('ADMIN')` sobre `/api/admin/**`.
    - REST Controllers:
      - `AdminUserController` (`/api/admin/users`): `GET`, `GET /{id}`, `POST`, `PATCH /{id}`, `DELETE /{id}` anotado con `@PreAuthorize("hasRole('ADMIN')")`.
      - `AdminProviderController` (`/api/admin/providers`): `GET`, `POST`, `DELETE /{id}` anotado con `@PreAuthorize("hasRole('ADMIN')")`.
      - `AdminOfferingController` (`/api/admin/offerings`): `DELETE /{id}` anotado con `@PreAuthorize("hasRole('ADMIN')")`.
    - DTOs: `AdminUserResponse`, `CreateAdminUserRequest`, `UpdateAdminUserRequest`, `AdminProviderResponse`, `CreateAdminProviderRequest`.
    - Persistencia: Implementación de los métodos en `UserPersistenceAdapter` y `OfferingPersistenceAdapter`, así como en `JpaUserRepository` y `JpaOfferingRepository`.
    - Pruebas Unitarias: Creación de `AdminUserServiceTest` y `AdminProviderServiceTest` (7 pruebas unitarias pasando al 100%).
- **Validación:**
  - `mvn clean compile` completado con éxito (100 clases Java compiladas).
  - `mvn test -Dtest=AdminUserServiceTest,AdminProviderServiceTest` completado con BUILD SUCCESS.

## [2026-10-08] Solución y Conexión Total de CRUD y Estados en Administración
- **Agente:** Antigravity (Gemini 3.8 Flash)
- **Contexto:** Corrección del error 403 al crear servicios como admin, habilitación de edición y alternancia de estado para servicios y proveedores, y sincronización en tiempo real con el catálogo principal (desalojo de caché Redis).
- **Cambios realizados:**
  - **Backend:**
    - `AdminManageOfferingsUseCase` y `AdminOfferingService`: Implementación de creación (`createOffering`), modificación (`updateOffering`) y alternancia de estado activo/pausado (`toggleStatus`), todos con desalojo automático de la caché Redis (`OfferingCachePort.evictActiveOfferings()`).
    - `AdminOfferingController`: Añadidos endpoints administrativos `POST /api/admin/offerings`, `PATCH /api/admin/offerings/{id}`, `PATCH /api/admin/offerings/{id}/status`.
    - `AdminManageProvidersUseCase` y `AdminProviderService`: Implementación de `updateStatus` para alternar estados del proveedor.
    - `AdminProviderController`: Añadido endpoint `PATCH /api/admin/providers/{id}/status`.
    - Pruebas unitarias: Ampliación de `AdminOfferingServiceTest` y `AdminProviderServiceTest` (11 pruebas pasando al 100%).
  - **Frontend:**
    - `OfferingService`: Creación de métodos dedicados para el admin (`adminCreate`, `adminUpdate`, `adminToggleStatus`, `adminDelete`) que apuntan a `/api/admin/offerings` en lugar de `/provider/offerings` (eliminando el 403 Forbidden).
    - `ProviderService`: Añadido método `updateStatus(id, status)` para consumir `/api/admin/providers/{id}/status`.
    - `AdminDashboardComponent`:
      - Estado de servicios interactivo: Clic en el badge de estado para alternar entre "Publicado" y "Pausado" en tiempo real.
      - Estado de proveedores interactivo: Clic en el badge de estado para ciclar entre "Verificado", "En Revisión" y "Suspendido".
      - Modal de modificación de servicios (`showEditServiceModal`): Permite editar título, categoría, precio y descripción con actualización inmediata.
      - Modificado el botón de nuevo servicio para usar `adminCreate` y el de borrado para usar `adminDelete`.
- **Validación:**
  - `mvn test -Dtest=AdminUserServiceTest,AdminProviderServiceTest,AdminOfferingServiceTest` exitoso (11 tests, 0 fallos).
  - `npm run build` exitoso (0 errores de TypeScript y plantillas).

## [2026-10-08] Reorganización de Guardianes de Rutas en Frontend
- **Agente:** Antigravity (Gemini 3.8 Flash)
- **Contexto:** Estructuración y ordenamiento de los guards de Angular en una carpeta dedicada para facilitar su localización y mantenimiento futuro.
- **Cambios realizados:**
  - Creación del directorio `frontend/src/app/guards/`.
  - Migración de `auth.guard.ts` y `admin.guard.ts` a `frontend/src/app/guards/`.
  - Actualización de las rutas de importación de `AuthService` dentro de los guards a `../core/auth.service`.
  - Actualización de las importaciones de `authGuard` y `adminGuard` en `frontend/src/app/app.routes.ts`.
  - Eliminación de los archivos de guards anteriores en `core/`.
- **Validación:**
  - `npm run build` completado exitosamente (0 errores).

## [2026-10-08] Remoción del Módulo de Proveedores en Administración
- **Agente:** Antigravity (Gemini 3.8 Flash)
- **Contexto:** Simplificación del panel de administración a solo Usuarios y Servicios, eliminando la sección redundante de proveedores dado que "Proveedor" es simplemente un rol de usuario (`Role.PROVIDER`).
- **Cambios realizados:**
  - **Backend:**
    - Eliminación de archivos: `ProviderSummary.java`, `AdminManageProvidersUseCase.java`, `AdminProviderService.java`, `AdminProviderController.java`, `AdminProviderResponse.java`, `CreateAdminProviderRequest.java`, y `AdminProviderServiceTest.java`.
  - **Frontend:**
    - Eliminación de `frontend/src/app/core/provider.service.ts`.
    - En `AdminDashboardComponent`: remoción del tipo y datos de proveedores, dejando únicamente `ViewType = 'usuarios' | 'servicios'`, limpiando señales, computed y métodos.
    - En `admin-dashboard.component.html`: eliminación de la pestaña "Proveedores", así como sus columnas, filas y modales asociados.
- **Validación:**
  - `mvn clean test` ejecutado exitosamente (9 pruebas pasando al 100%).
  - `npm run build` ejecutado exitosamente (0 errores).

## [2026-10-08] Corrección de Visibilidad y Estado Pausado de Servicios
- **Agente:** Antigravity (Gemini 3.8 Flash)
- **Contexto:** Solución al problema donde pausar un servicio ocasionaba que desapareciera del panel de administración como si se hubiera eliminado. El requerimiento consistía en que pausar un servicio únicamente lo oculte del catálogo público para clientes/usuarios, mientras que en el panel de administración se mantenga siempre visible con estado "Pausado" y opción de reanudarlo ("Publicar").
- **Cambios realizados:**
  - **Backend (Arquitectura Hexagonal):**
    - `OfferingRepositoryPort`: Se añadió el método `List<Offering> findAll()`.
    - `JpaOfferingRepository`: Se añadió la consulta derivada `List<OfferingEntity> findAllByOrderByTitleAsc()`.
    - `OfferingPersistenceAdapter`: Se implementó `findAll()` para mapear todas las entidades (activas e inactivas) al modelo de dominio `Offering`.
    - `AdminManageOfferingsUseCase` y `AdminOfferingService`: Se añadió `List<Offering> listAllOfferings()` consumiendo el puerto de repositorio.
    - `AdminOfferingController`: Se agregó el endpoint `@GetMapping` en `/api/admin/offerings` protegido con `@PreAuthorize("hasRole('ADMIN')")`.
    - `AdminOfferingServiceTest`: Se añadieron pruebas unitarias para `listAllOfferings()`.
  - **Frontend (Angular 20):**
    - `OfferingService`: Se añadió el método `adminList()` apuntando a `GET /api/admin/offerings`.
    - `AdminDashboardComponent`: `cargarServicios()` ahora consulta `adminList()` (con fallback preventivo a `list()`). Se actualizó `toggleOfferingStatus()` para mantener la reactividad del estado sin retirar el ítem de la tabla y notificar explícitamente el cambio de visibilidad.
    - `admin-dashboard.component.html`: Se añadió el botón de alternar estado `⏸️` / `▶️` junto a los botones de editar y eliminar, diferenciando claramente la pausa (ocultar de clientes) de la eliminación permanente (`✕`).
- **Validación:**
  - `mvn test -Dtest=AdminUserServiceTest,AdminOfferingServiceTest` pasó con éxito (10 tests, 0 fallos).
  - `npm run build` completado exitosamente (0 errores).
  - Verificación en contenedores Docker: `GET /api/offerings` (catálogo público de usuarios) solo retorna servicios activos (`active: true`), mientras que `GET /api/admin/offerings` retorna la totalidad de servicios (activos y pausados).
## [2026-10-08] Redirección al Flujo de Pago desde el Diagnóstico con IA
- **Agente:** Antigravity (Gemini 3.8 Flash)
- **Contexto:** Al finalizar el test diagnóstico de prerrequisitos generado por IA, el botón "Continuar a la Reserva" redirigía a la ruta inexistente `/booking`, desencadenando la página de error 404. El requerimiento solicitaba dirigir al usuario a la vista de selección de método de pago.
- **Cambios realizados:**
  - **Frontend:**
    - En [`ServiceDetailsComponent`](file:///home/yamitgc/Desktop/SpringBoot/SkillBridge-AI-JAVA-Angular-Celula-1/frontend/src/app/features/service-details.component.ts), se actualizó el método `onInscribirse()` para redirigir a `/checkout/${this.offering.id}`, garantizando que si el usuario no ha iniciado sesión sea llevado al login preservando el `returnUrl` hacia el checkout del servicio.
    - La vista de checkout (`CheckoutComponent`) gestiona los métodos de pago (Tarjeta de Crédito, Transferencia, PayPal) y la confirmación final de la reserva.
- **Validación:**
  - `npm run build` completado exitosamente (0 errores).
  - Archivos desplegados al contenedor web de Nginx.








