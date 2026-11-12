# Arquitectura del Sistema - SkillBridge AI

## 1. Visión General del Sistema

**SkillBridge AI** es una plataforma integral de mentorías técnicas y servicios profesionales que combina una arquitectura monolítica modular desacoplada con capacidades avanzadas de **Inteligencia Artificial Generativa (Google Gemini)** y **comunicación audiovisual en tiempo real (LiveKit WebRTC)**.

El backend está diseñado siguiendo estrictamente los principios de **Arquitectura Hexagonal (Ports & Adapters)** y **Clean Architecture**, asegurando que las reglas del negocio permanezcan completamente aisladas de los detalles de infraestructura, frameworks y proveedores en la nube.

---

## 2. Diagrama de Contenedores y Topología

```text
                                 [ Usuario / Navegador ]
                                            │
                                            ▼
                           +─────────────────────────────────+
                           │          Nginx (Proxy)          │
                           │  - Angular 20 Standalone SPA    │
                           │  - Enrutamiento /api/* a Backend│
                           +─────────────────────────────────+
                                            │
                                            ▼
                           +─────────────────────────────────+
                           │  Spring Boot 3.5.x Monolith     │
                           │  - Arquitectura Hexagonal       │
                           │  - Spring Security (JWT + CSRF) │
                           │  - Spring AI (Gemini ChatClient)│
                           │  - Actuator & Micrometer        │
                           +─────────────────────────────────+
                              /      |         |          \
                             /       |         |           \
                            v        v         v            v
                 +------------+ +--------+ +----------+ +-------------+
                 │ PostgreSQL │ │ Redis  │ │ RabbitMQ │ │ Google      │
                 │ 17 (DB)    │ │ 8      │ │ 4 (AMQP) │ │ Gemini API  │
                 │ Record of  │ │ Cache- │ │ Eventos  │ │ (LLM)       │
                 │ Truth      │ │ Aside  │ │ & DLQ    │ │             │
                 +------------+ +--------+ +----------+ +-------------+
                                               │
                                               ▼
                                      +-----------------+
                                      │ Async Consumer  │
                                      │ Notificaciones  │
                                      +-----------------+

    [ Servicios Adicionales en la Topología ]
    - LiveKit SFU Server: Servidor WebRTC para salas virtuales de clase y mentoría.
    - Prometheus & Grafana: Monitoreo operativo, scraping de métricas y dashboards.
```

---

## 3. Arquitectura Hexagonal (Ports & Adapters)

El backend de SkillBridge AI rechaza la arquitectura tradicional en capas fuertemente acoplada a la base de datos (Database-Driven Architecture) y adopta **Hexagonal Architecture**:

```text
               ADAPTADORES DE ENTRADA (Inbound Adapters)
         ┌──────────────────────────────────────────────────┐
         │  REST Controllers (Spring MVC)                   │
         │  RabbitMQ Event Listeners (Message Consumers)    │
         │  LiveKit Webhook Receiver                        │
         └─────────────────────────┬────────────────────────┘
                                   │
                                   ▼ Invocan
         ┌──────────────────────────────────────────────────┐
         │  PUERTOS DE ENTRADA (Inbound Ports / Use Cases)  │
         │  - CatalogOfferingUseCase, EnrollUserUseCase     │
         │  - GenerateAssessmentUseCase, JoinMeetingUseCase │
         └─────────────────────────┬────────────────────────┘
                                   │
                                   ▼ Implementan
         ┌──────────────────────────────────────────────────┐
         │  SERVICIOS DE APLICACIÓN (Application Services)  │
         │  - Orquestación de Casos de Uso                  │
         │  - Transacciones de Aplicación                   │
         │  - Coordinación de Puertos de Salida             │
         └─────────────────────────┬────────────────────────┘
                                   │
                                   ▼ Depende de
         ┌──────────────────────────────────────────────────┐
         │  NÚCLEO DE DOMINIO (Domain Layer)                │
         │  - Entidades y Records Inmutables                │
         │  - Excepciones de Negocio Puras                  │
         │  - Reglas de Aprobación, Estados y Políticas     │
         │  * CERO DEPENDENCIAS de Frameworks o DB *       │
         └─────────────────────────▲────────────────────────┘
                                   │
                                   │ Utiliza contratos
         ┌─────────────────────────┴────────────────────────┐
         │  PUERTOS DE SALIDA (Outbound Ports)              │
         │  - OfferingPort, UserAccountPort                 │
         │  - AiAssessmentPort, AiRecommendationPort        │
         │  - AssessmentSessionCachePort, MeetingPort       │
         └─────────────────────────▲────────────────────────┘
                                   │
                                   │ Implementan
         ┌─────────────────────────┴────────────────────────┐
         │  ADAPTADORES DE SALIDA (Outbound Adapters)       │
         │  - Spring Data JPA Repositories (PostgreSQL)     │
         │  - StringRedisTemplate Adapters (Redis 8)        │
         │  - RabbitTemplate Event Publishers (RabbitMQ)    │
         │  - Spring AI Gemini Adapters (Google LLM)        │
         │  - LiveKit Server SDK (LiveKit Rooms & Tokens)   │
         └──────────────────────────────────────────────────┘
```

### Reglas de Dependencia
1. **Regla de Dependencia Estricta:** Las dependencias del código fuente apuntan exclusivamente hacia adentro. La capa `domain` no sabe que existen PostgreSQL, JPA, Redis, RabbitMQ, Gemini, LiveKit, HTTP o Spring.
2. **Independencia de Frameworks:** Los casos de uso y las entidades no extienden clases base del framework.
3. **Sustituibilidad de Infraestructura:** El motor de base de datos, el proveedor de IA o el broker de mensajería pueden ser sustituidos implementando una nueva clase en `infrastructure` sin modificar una sola línea de código en `application` o `domain`.

---

## 4. Patrones de Diseño Estratégicos

### 4.1. Cache-Aside en Redis con Invalidación Selectiva
- **Lectura:** El catálogo público de servicios (`/api/services`) se consulta prioritariamente en Redis antes de golpear la base de datos PostgreSQL.
- **Escritura e Invalidación:** Cuando un proveedor crea (`POST`), edita (`PUT`) o altera el estado (`PATCH`) de un servicio, el servicio de aplicación emite una señal de invalidación a través de `OfferingCachePort` para purgar las claves obsoletas del catálogo.

### 4.2. Dual-Tier Session Cache para IA con Fallback Local
- Las sesiones de diagnóstico técnico generadas por Google Gemini (`TechnicalAssessment`) se almacenan de forma distribuida en Redis con un TTL de 30 minutos.
- Si Redis no está disponible o sufre una desconexión transitoria, el adaptador `RedisAssessmentCacheAdapter` conmuta de inmediato y de forma transparente a una caché local concurrente (`ConcurrentHashMap`), asegurando que ningún alumno pierda su sesión de evaluación.

### 4.3. Eventos Asíncronos con Retry y Dead Letter Queue (DLQ)
- Cuando una inscripción o reserva se confirma, `EnrollmentService` persiste la transacción en PostgreSQL y publica el evento `BookingCreated` en el exchange de RabbitMQ `skillbridge.exchange`.
- El consumidor procesa el evento de forma desacoplada para:
  1. Registrar la notificación persistente in-app en `booking_notifications`.
  2. Disparar integraciones futuras (envío de correos, facturación, auditoría).
- Si el procesamiento falla:
  - Se ejecutan reintentos automáticos con backoff exponencial.
  - Al agotar los reintentos, el mensaje se redirige a la **Dead Letter Queue (DLQ)** para evitar envenenamiento de cola (*poison messages*) y permitir inspección post-mortem sin pérdida de datos.
- **Idempotencia:** La tabla `booking_notifications` cuenta con una restricción única por `(booking_id, event_type, channel)` garantizando que procesamientos duplicados no generen notificaciones repetidas.

### 4.4. Aislamiento Anti-Trampas en Evaluaciones IA
- `GeminiAiAssessmentAdapter` genera las preguntas con sus opciones, el índice de la opción correcta y explicaciones didácticas.
- Al exponer el recurso vía REST (`ClientAssessmentResponse`), se omiten intencionalmente la respuesta correcta y la explicación.
- La validación solo ocurre en el servidor cuando el cliente envía sus respuestas en `/api/assessments/{id}/submit`.

### 4.5. Integración de Video en Vivo con LiveKit SFU
- La plataforma no retransmite flujos de video a través del backend de Spring Boot.
- El backend actúa como **servidor de autorización y señalización**: valida las credenciales, verifica que el usuario esté inscrito y emite un token criptográfico de LiveKit con los permisos de sala adecuados (publicador para el tutor, suscriptor/interactor para el estudiante).
- El tráfico multimedia WebRTC fluye directamente entre los navegadores y el servidor SFU de LiveKit.

---

## 5. Arquitectura de Datos y Migraciones

### 5.1. Sistema de Registro (System of Record)
- **Motor:** PostgreSQL 17.
- **Principales Agregados:**
  - `app_users`: Usuarios, roles (`CUSTOMER`, `PROVIDER`, `ADMIN`), contraseñas cifradas en BCrypt.
  - `categories`: Taxonomía y categorías temáticas.
  - `services` (`offerings`): Servicios formativos, capacidad, precios, estado (`ACTIVE`/`INACTIVE`), mentor propietario.
  - `service_schedules`: Horarios semanales, días de clase, horas de inicio y duración.
  - `service_enrollments`: Inscripciones de alumnos, estado de pago y fechas de registro.
  - `booking_notifications` / `booking_history`: Historial inmutable de estados y notificaciones del estudiante.
  - `meetings`: Salas de videoconferencia WebRTC asociadas a servicios.

### 5.2. Evolución del Esquema con Flyway
- Todas las modificaciones de esquema son **inmutables y secuenciales**, ubicadas en `backend/src/main/resources/db/migration/`.
- Nunca se modifican scripts ya aplicados (`V1__...`, `V2__...`). Cualquier cambio de columnas, renombrado o nuevas restricciones requiere un nuevo script versionado (`V3__...`, `V11__...`).

---

## 6. Seguridad y Gobernanza de Acceso

1. **Tokens JWT Stateless:** Sesiones sin estado en el servidor; cada petición incluye el token en la cabecera `Authorization: Bearer <token>`.
2. **Defensa CSRF para SPA:** Integración de `CookieCsrfTokenRepository` con SameSite Lax y `SpaCsrfTokenRequestHandler` para proteger peticiones que mutan estado.
3. **Control de Acceso Basado en Roles (RBAC):**
   - Público: `/api/auth/**`, `/api/services/**`, `/api/catalog/**`, `/actuator/health`.
   - `CUSTOMER` / `PROVIDER`: `/api/bookings/**`, `/api/enrollments/**`, `/api/meetings/**`.
   - `PROVIDER` Exclusivo: `/api/provider/services/**`, `/api/meetings/service/{id}/start`.
   - `ADMIN` Exclusivo: `/api/admin/**`.
4. **Cero Secretos Expuestos:** Las claves `GEMINI_API_KEY`, `JWT_SECRET`, `LIVEKIT_API_SECRET` y contraseñas de bases de datos existen únicamente en las variables de entorno del backend y nunca se compilan en los artefactos de Angular.

---

## 7. Observabilidad y Monitoreo

- **Spring Boot Actuator:** Expone `/actuator/health`, `/actuator/info` y `/actuator/metrics`.
- **Micrometer + Prometheus:** Exporta métricas operativas (latencia HTTP percentiles P95/P99, conteo de errores 4xx/5xx, uso de memoria JVM, conexiones activas de HikariCP y métricas de colas RabbitMQ).
- **Grafana:** Dashboards preconfigurados para supervisar el rendimiento del monolito, el estado de los contenedores y el rendimiento de las sesiones WebRTC de LiveKit.
