# Guía del Instructor y Rúbrica de Evaluación

## 1. Secuencia de Aprendizaje Sugerida

### Sesión 1 — Arquitectura Hexagonal y Línea Base
- Explicar el caso de negocio de SkillBridge AI y los límites de contexto (*Bounded Contexts*).
- Identificar la separación entre adaptadores de entrada (`in`), casos de uso (`application`) y adaptadores de salida (`out`).
- Levantar el entorno con `docker compose up --build`.
- Inspeccionar contenedores, redes virtuales, volúmenes de persistencia y verificaciones de salud (*health checks*).

### Sesión 2 — Persistencia y Migraciones Evolutivas
- PostgreSQL como sistema de registro (*System of Record*).
- Mapeo entre entidades JPA de infraestructura y modelos puros de dominio.
- Evolución inmutable del esquema mediante **Flyway**.
- Ejercicio: Agregar un campo nuevo mediante una migración versionada `V...__...sql` sin alterar scripts previos.

### Sesión 3 — Estrategias de Caché Distribuido
- Medir latencia de respuesta en el catálogo de servicios.
- Inspeccionar claves y TTL en Redis con `redis-cli KEYS "*"`.
- Forzar un *Cache Miss* e invalidación tras operaciones de escritura del proveedor (`OfferingCachePort`).
- Estrategia de doble nivel (*Dual-Tier Caching*) para sesiones de diagnóstico con fallback en memoria local.

### Sesión 4 — Arquitectura Dirigida por Eventos (EDA)
- Crear una reserva e inspeccionar el evento `BookingCreated`.
- Inspeccionar exchange, colas vinculadas y routing keys en la consola de RabbitMQ (`http://localhost:15672`).
- Provocar deliberadamente un fallo en el consumidor asíncrono para observar los reintentos con backoff y el envío a la **Dead Letter Queue (DLQ)**.
- Discutir idempotencia en base de datos mediante claves compuestas únicas.

### Sesión 5 — Seguridad, Autenticación y Autorización
- Registro y login con generación de tokens JWT stateless.
- Inspeccionar claims del JWT y verificar control de acceso basado en roles (`CUSTOMER`, `PROVIDER`, `ADMIN`).
- Protección CSRF para Single Page Applications (Angular) con tokens de cookie SameSite Lax.
- Principio de Menor Privilegio: Cero secretos en frontend (`GEMINI_API_KEY`, `JWT_SECRET`, credenciales DB).

### Sesión 6 — Inteligencia Artificial Generativa y Resiliencia
- Justificar por qué la API Key y los SDKs de LLM deben residir exclusivamente en adaptadores de infraestructura (`AiRecommendationPort`, `AiAssessmentPort`).
- Analizar las técnicas de **Grounding** en el recomendador de catálogo para prevenir alucinaciones de cursos inexistentes.
- Inspeccionar la generación dinámica del **Diagnóstico Técnico Previo (*Mini-Assessment*)**:
  - Contrato de prompts y parseo tolerante a markdown para extraer JSON estructurado.
  - Proyección de seguridad anti-trampas: el cliente jamás recibe las respuestas correctas.
  - Análisis recursivo de causas (`extractErrorDetails`) para mapear errores 429 de cuota en Google Gemini a respuestas HTTP 429 limpias.
  - Elección del modelo `gemini-3.1-flash-lite` por latencia y cuota.

### Sesión 7 — Clases en Vivo con LiveKit WebRTC
- Comprender el rol del backend como emisor de tokens y el SFU de LiveKit como distribuidor de medios.
- Ejecutar pruebas sintéticas de tráfico WebRTC (`make test-livekit-stream`).
- Evaluar latencias de audio/video y métricas de desconexión limpia.

### Sesión 8 — Observabilidad y Monitoreo Operativo
- Exposición de métricas con Spring Boot Actuator y Micrometer.
- Generar tráfico en la API y consultar métricas en Prometheus (`/actuator/prometheus`).
- Visualizar latencias HTTP P95/P99, saturación de CPU/memoria y colas en los dashboards de Grafana.
- Definir un SLI (Indicador de Nivel de Servicio) y un SLO (Objetivo de Nivel de Servicio) para las llamadas a la IA.

---

## 2. Rúbrica y Criterios de Evaluación

| Dimensión Técnica | Ponderación | Criterios de Éxito |
|---|---:|---|
| **Arquitectura Hexagonal / Dominio** | 20% | El dominio permanece 100% puro sin anotaciones de JPA, Spring o librerías de IA. Los adaptadores dependen de puertos y no al revés. |
| **Funcionalidad y Casos de Negocio** | 15% | Catálogo público, reservas, horarios, gestión de proveedor y ciclo de vida de inscripción funcionando de punta a punta. |
| **Subsistema de IA (Gemini)** | 15% | Recomendaciones con catálogo anclado, generación de diagnósticos técnicos, protección anti-trampas, caché de sesión y manejo de cuotas 429. |
| **Seguridad y Cero Secretos** | 10% | Sin secretos en frontend, validación JWT, RBAC efectivo, contraseñas con BCrypt y manejo de CSRF. |
| **Persistencia y Migraciones** | 10% | PostgreSQL como sistema de registro, scripts Flyway inmutables, transacciones ACID correctas. |
| **Caché y Mensajería Asíncrona** | 10% | Cache-Aside en Redis con invalidación; RabbitMQ con reintentos, DLQ y consumidor idempotente. |
| **Videoconferencia WebRTC (LiveKit)** | 10% | Emisión de tokens de sala en backend, validación de inscripciones y pruebas de streaming superadas. |
| **Observabilidad y Pruebas** | 10% | Pruebas unitarias de servicios, Actuator, Prometheus y Grafana operativos. |

> **Nota para evaluadores:** No otorgar puntos de arquitectura únicamente por los nombres de las carpetas. Solicitar a los estudiantes reemplazar un adaptador o explicar el flujo de dependencias entre capas.

---

## 3. Banco de Preguntas para Sustentación Oral

1. **Arquitectura y Desacoplamiento:**
   - *¿Por qué `GeminiAiAssessmentAdapter` está en la capa de infraestructura y no en la capa de aplicación?*
   - *¿Qué cambios serían necesarios en el código si quisiéramos cambiar Google Gemini por Claude de Anthropic o un modelo local en Ollama?*
2. **Seguridad y Prevención de Trampas:**
   - *¿Por qué el endpoint `POST /api/assessments/offerings/{id}` no devuelve la opción correcta (`correctOptionIndex`) ni la explicación al navegador?*
   - *¿Cómo garantizamos que la API Key de Gemini nunca sea interceptada desde el frontend?*
3. **Resiliencia y Caché:**
   - *¿Qué ocurre con las evaluaciones diagnósticas si el contenedor de Redis se detiene o reinicia mientras un estudiante responde el examen?*
   - *¿Cómo maneja el backend el error 429 (Too Many Requests) cuando se satura la cuota gratuita de Gemini en Google AI Studio?*
4. **Mensajería y Consistencia:**
   - *¿Qué sucede si la base de datos confirma una reserva pero RabbitMQ se encuentra temporalmente caído?*
   - *¿Cómo se evita que un mensaje reintentado en RabbitMQ genere notificaciones duplicadas en la base de datos?*
5. **Comunicaciones en Tiempo Real:**
   - *¿Por qué el video de las clases en vivo no pasa a través del servidor Spring Boot y qué papel cumple LiveKit SFU?*
