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

