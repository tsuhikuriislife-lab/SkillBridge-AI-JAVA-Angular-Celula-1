# Lista de Verificación para Despliegue en Producción (Deployment Checklist)

## 1. Verificaciones Previas al Despliegue (Pre-flight Checks)

### Seguridad y Gestión de Secretos
- [ ] Ningún secreto, archivo `.env` o clave privada está commiteada en el repositorio Git.
- [ ] `JWT_SECRET` es una cadena criptográficamente segura, aleatoria y de al menos 256 bits (32+ caracteres).
- [ ] `GEMINI_API_KEY` está configurada únicamente en las variables de entorno del contenedor backend (nunca expuesta al frontend ni a logs).
- [ ] `GEMINI_MODEL` está configurado en `gemini-3.1-flash-lite` para garantizar estabilidad de cuotas y baja latencia.
- [ ] `LIVEKIT_API_KEY` y `LIVEKIT_API_SECRET` están configuradas en el backend y sincronizadas con el servidor LiveKit SFU.
- [ ] `CORS_ALLOWED_ORIGINS` contiene de forma estricta el dominio o dominios autorizados de la SPA frontend (sin comodines `*` en producción).
- [ ] La variable `app.security.cookie.secure` y `app.security.require-https` están activadas (`true`) en el perfil de producción (`prod`).
- [ ] La interfaz Swagger UI (`/swagger-ui.html`) está debidamente asegurada o deshabilitada en producción.

### Datos y Persistencia
- [ ] La base de datos PostgreSQL de producción utiliza conexiones cifradas mediante TLS (`sslmode=require`).
- [ ] Todas las migraciones Flyway (`backend/src/main/resources/db/migration`) se ejecutaron sin errores ni advertencias de checksum.
- [ ] El pool de conexiones de HikariCP (`maximum-pool-size`) está dimensionado adecuadamente según los recursos del servidor de BD.

### Caché y Mensajería
- [ ] La conexión a Redis 8 utiliza autenticación por contraseña y TLS si el proveedor cloud lo requiere (`rediss://`).
- [ ] La conexión a RabbitMQ 4 utiliza credenciales de usuario dedicado con permisos restringidos al vhost de la aplicación.
- [ ] Las colas principales y la Dead Letter Queue (DLQ) están creadas y enrutadas correctamente.

### Pruebas Automatizadas y Calidad
- [ ] La suite de pruebas unitarias y de integración pasa al 100%:
  ```bash
  mvn test
  ```
- [ ] La compilación de producción de Angular completa sin advertencias críticas:
  ```bash
  cd frontend && npm run build -- --configuration production
  ```

---

## 2. Verificaciones Posteriores al Despliegue (Smoke & Sanity Testing)

- [ ] **Salud General:** `/actuator/health` retorna `{"status":"UP"}` confirmando conexión viva a PostgreSQL, Redis y RabbitMQ.
- [ ] **Flujo de Autenticación:** Registro de un nuevo usuario cliente (`POST /api/auth/register`) e inicio de sesión (`POST /api/auth/login`) emiten un JWT válido.
- [ ] **Caché del Catálogo:** 
  - Primera petición a `GET /api/services` (Cache Miss) consulta PostgreSQL y puebla Redis.
  - Segunda petición inmediata a `GET /api/services` (Cache Hit) responde desde Redis con latencia < 20 ms.
- [ ] **Recomendación Semántica con IA:** Petición a `POST /api/ai/recommendations` con token Bearer genera recomendaciones válidas con enlaces `/service/:id` sin errores 429.
- [ ] **Diagnóstico Técnico Previo con IA:** 
  - `POST /api/assessments/offerings/{offeringId}` genera 3 preguntas sin exponer las respuestas correctas.
  - Verificación de la clave `assessment:session:{id}` en Redis con TTL de 30 minutos.
  - `POST /api/assessments/{assessmentId}/submit` califica las respuestas y retorna el puntaje y la retroalimentación.
- [ ] **Inscripción y Mensajería Asíncrona:** 
  - `POST /api/services/{id}/enroll` crea la reserva y publica el evento en RabbitMQ.
  - El consumidor asíncrono procesa el evento y registra la notificación en `booking_notifications` (visible en `GET /api/history/me`).
- [ ] **Aulas Virtuales WebRTC:** 
  - Proveedor inicia sala con `POST /api/meetings/service/{id}/start` y recibe token JWT de LiveKit.
  - Alumno se une con `POST /api/meetings/{meetingId}/join`.
  - El servidor LiveKit procesa el tráfico WebRTC y los webhooks notifican al backend.
- [ ] **Auditoría de Logs:** Los logs de producción no contienen contraseñas, hashes, tokens JWT ni llamadas completas con la API Key de Gemini.
- [ ] **Telemetría y Métricas:** `/actuator/prometheus` está siendo raspado exitosamente por Prometheus y los tableros de Grafana muestran tráfico activo.
