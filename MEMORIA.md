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
- **Validación:** `make test-back-unit` pasó (9 pruebas). La suite completa compila, pero la integración Testcontainers no se ejecuta en este equipo: el cliente 
Java solicita API 1.32 y el daemon exige 1.44. El cambio de `DOCKER_API_VERSION` no modificó ese resultado.


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
- **Pendiente:** Frontend de HU-10 ("Mis servicios"), migración SQL del equipo (`services.code` UNIQUE, `created_by` → `app_users`) y adaptar reservas (`BookingEntity`, `JpaBookingRepository`) al nuevo esquema.

