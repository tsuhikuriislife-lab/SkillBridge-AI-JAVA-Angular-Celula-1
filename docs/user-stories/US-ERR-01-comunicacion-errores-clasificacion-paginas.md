# US-ERR-01: Catálogo Estandarizado de Códigos de Error y Páginas de Fallo

**Estado:** `[ ] Por hacer`

---

## 1. Descripción y Objetivo de Ingeniería

Mejorar radicalmente la comunicación de errores en SkillBridge AI, eliminando mensajes genéricos o crípticos y proporcionando trazabilidad técnica profesional. Se implementará una taxonomía estructurada de códigos de error de negocio en el backend (`ERR-<DOMINIO>-<NUMERO>`) basada en la norma **RFC 7807 (Problem Details for HTTP APIs)**, un interceptor reactivo en el frontend para traducirlos a feedback amigable y componentes dedicados para estados de fallo HTTP (403 Prohibido, 404 No Encontrado, 500 Error Interno y 503 En Mantenimiento).

> [!NOTE] Relación con el Backlog:
> Esta historia incorpora y amplía el alcance de la candidata `HU-12 (Explicar errores del proveedor de IA)`, integrando los fallos de Gemini (`ERR-AI-001`, `ERR-AI-002`) dentro de un sistema unificado de clasificación para todo el dominio (Autenticación, Servicios, Validaciones y Sistema).

---

## 2. Arquitectura de la Solución (RFC 7807 & Frontend Routing)

1. **Catálogo de Códigos de Dominio en Backend:**
   - Creación de un enumerador/registro formal `ErrorCode`:
     - `ERR-AUTH-001`: Credenciales inválidas.
     - `ERR-AUTH-002`: Token expirado o revocado.
     - `ERR-AUTH-003`: Verificación CAPTCHA fallida o ausente.
     - `ERR-SRV-001`: Cupo de servicio agotado.
     - `ERR-SRV-002`: Conflicto de horario en sesión de mentoría.
     - `ERR-AI-001`: Cuota de proveedor de IA excedida.
     - `ERR-AI-002`: Timeout en procesamiento de modelo generativo.
     - `ERR-SYS-001`: Error interno de base de datos o broker de mensajería.
   - Refactor de `GlobalExceptionHandler` para inyectar en cada `ProblemDetail`: el código (`errorCode`), la traza (`traceId` de Micrometer / MDC), la estampa de tiempo (`timestamp`) y el enlace a la documentación técnica del error (`type: "https://skillbridge.ai/errors/ERR-..."`).
2. **Interceptor y Enrutamiento en Frontend (Angular 20):**
   - Creación de `HttpErrorInterceptor` que captura los códigos RFC 7807 y emite mensajes contextuales mediante `ToastService`.
   - Vistas independientes y estilizadas para páginas de error:
     - `ForbiddenComponent` (`/forbidden` o trigger por código 403).
     - `NotFoundComponent` (`/**` para rutas inexistentes con botón de retorno a historial seguro vía `HistoryService`).
     - `ServerErrorComponent` (`/error` ante caídas 500 con opción de reintento).

---

## 3. Componentes Técnicos y Referencias de Implementación

- Manejador global de excepciones REST:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java` línea 15:75)*.
- Excepciones de negocio en el dominio:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/domain/exception/BusinessRuleException.java`)*.
- Servicio de historial de navegación en frontend:
  *(Ver `frontend/src/app/core/history.service.ts` línea 10:40)*.
- Componente de ruta no encontrada:
  *(Ver `frontend/src/app/features/not-found.component.ts`)*.

---

## 4. Criterios de Aceptación Técnicos

- **CA-ERR-01.1:** Toda respuesta con código de estado HTTP $4xx$ o $5xx$ generada por el backend debe seguir el esquema RFC 7807 con el atributo `errorCode` explícito.
- **CA-ERR-01.2:** Los errores 500 nunca deben exponer trazas de stack (stacktraces) de Java o SQL en el JSON retornado al cliente por motivos de seguridad; el detalle completo debe registrarse exclusivamente en los logs del servidor con su `traceId`.
- **CA-ERR-01.3:** Cuando ocurra un fallo de red o caída 503, la UI de Angular debe mostrar una vista amigable de mantenimiento con opción de "Reintentar conexión" sin perder el estado local del formulario si el usuario estaba editando datos.
