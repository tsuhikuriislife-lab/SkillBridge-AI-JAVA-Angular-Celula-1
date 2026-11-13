# STATE.md — Estado Técnico del Sistema

> **Última Actualización:** 2026-11-12  
> **Rama Actual:** `feat/upgraded_agent_memory_` (sincronizada con `origin/develop`)  
> **Último Commit Auditado:** `10da484` (`add: documentacion (#77)`)  
> **Versión de Migraciones Flyway:** `V18__feature_store.sql`  

---

## 1. Módulos y Servicios Activos

| Módulo / Servicio | Stack Técnico | Estado Operativo | Puerto / Endpoint Principal |
|---|---|---|---|
| **Backend** | Java 21, Spring Boot 3.5.x, Hexagonal (Ports & Adapters), Spring Security, Flyway | Activo / Operativo | `http://localhost:8081` (Docker) / `8080` (Local) |
| **Frontend** | Angular 20 Standalone Components, Signals, Nginx Reverse Proxy | Activo / Operativo | `http://localhost:8088` (Docker) / `4200` (Local) |
| **Persistencia Principal** | PostgreSQL 17 (System of Record, tablas relacionales) | Activo / Migrado a V18 | `localhost:5435` (Docker) / `5432` |
| **Caché & Feature Store** | Redis 8 (Cache-Aside para catálogo y embeddings/features) | Activo / Operativo | `redis:6379` (Docker interno) / `6379` |
| **Broker de Mensajería** | RabbitMQ 4 (AMQP 0-9-1) con Dead Letter Queue (DLQ) | Activo / Operativo | `localhost:5672` (AMQP) / `15672` (UI) |
| **WebRTC SFU** | LiveKit Server v1.8 + Redis-LiveKit para estado efímero | Activo / Operativo | `localhost:7880` (HTTP) / `7881` (RTC) |
| **Data Engine & Analytics** | Python 3.11, Apache Spark 3.5.6, PySpark, PyArrow | Activo (Perfil `data`) | Clúster Spark `localhost:7077` / UI `8080` |
| **Observabilidad** | Prometheus v3.5.0 + Grafana 12.1.1 (Micrometer Actuator) | Activo (Perfil `observability`) | Prometheus `9090` / Grafana `3000` |

---

## 2. Estado de Integraciones y Capacidades Técnicas

### 2.1. Arquitectura Hexagonal y Núcleo de Negocio
- **Estructura estricta:** `domain` (modelos puros, sin dependencias externas) -> `application` (casos de uso, puertos `in`/`out`) -> `infrastructure` (adaptadores web, mensajería, persistencia JPA, clientes REST/Redis).
- **Catálogo y Reservas:** Gestión de ofertas de mentoría y reservas con publicación asíncrona de eventos de dominio a RabbitMQ (`skillbridge.exchange`).
- **Autenticación y Seguridad:** JWT con firma HMAC-SHA256, contraseñas hasheadas con BCrypt, filtros stateless y control RBAC básico (`CUSTOMER`, `PROVIDER`, `ADMIN`).

### 2.2. Feature Store & Recomendaciones IA (HU-PS06, HU-PS02, HU-PS03)
- **Extracción y Limpieza Batch (HU-PS02):** Pipeline ETL en Apache Spark (`analytics-service/jobs/extract_and_clean_catalog.py`) que extrae vía JDBC particionado desde PostgreSQL, normaliza texto eliminando HTML/Markdown y escribe en formato columnar Parquet comprimido con Snappy.
- **Segmentación Semántica (HU-PS03):** Chunker semántico para descripciones de servicios en `analytics-service/jobs/text_chunker.py`.
- **Feature Store Redis + Fallback PostgreSQL (HU-PS06):**
  - Carga masiva con batch pipeline en Redis (`analytics-service/jobs/redis_batch_loader.py`).
  - Notificación asíncrona mediante evento `features.updated` en RabbitMQ.
  - Consumo en backend con `FeaturesUpdatedListener` y lectura en dos niveles (`FeatureStoreService`) consultando primero Redis y aplicando fallback resiliente a PostgreSQL si Redis falla o hay cold-start.
  - Registro de métricas de acierto/fallo y latencia con Micrometer.
- **Integración con Google Gemini:** `ChatClient` de Spring AI consumiendo `gemini-3.1-flash-lite` para diagnóstico de prerrequisitos y recomendaciones personalizadas sin exponer API keys en el cliente.

### 2.3. Videoconferencia WebRTC con LiveKit (HU-29.11)
- **Backend Adapter:** `LiveKitRestAdapter` utilizando el SDK oficial `io.livekit.server.AccessToken` con generación segura de tokens JWT, TTL de 120 minutos y asignación de permisos según el rol (`MeetingRole.PROVIDER` con `canPublish: true` y `roomAdmin: true`; `MeetingRole.PARTICIPANT` restringido).
- **Controlador de Webhooks:** `LiveKitWebhookController` para sincronizar eventos del ciclo de vida de salas (inicio, finalización, participantes conectados).
- **Frontend SPA:** Componente Standalone `MeetingRoomComponent` (`frontend/src/app/features/meeting-room`) con controles de audio, video y reconexión automática.

---

## 3. Historias de Usuario Documentadas y Trazables

Toda la documentación técnica detallada se encuentra en `/docs/user-stories/`:
- `[✅] Terminada` [US-PS06: Feature Store con Redis y Fallback PostgreSQL](docs/user-stories/US-PS06-feature-store.md)
- `[✅] Terminada` [US-PS02: Pipeline ETL de Limpieza y Normalización del Catálogo](docs/user-stories/US-PS02-etl-catalogo.md)
- `[✅] Terminada` [US-29.11: Videoconferencias en Tiempo Real con LiveKit](docs/user-stories/US-29.11-videoconferencias-livekit.md)

---

## 4. Pendientes Técnicos Inmediatos (Backlog Activo)

1. **Microservicio de Notificaciones (KAN-49 / HU-NOT00 a HU-NOT03):**
   - Implementar el consumidor NestJS para los eventos emitidos por el backend (`user.created`, `enrollment.created`, `booking.created`).
   - Integración con Resend para despacho de correos transaccionales bajo el dominio `skillbridgeai.online`.
   - Implementar firma y verificación de webhooks en `docs/notifications/CONTRACT.md`.
2. **Autorización Granular en Reservas:**
   - Exponer endpoints de consulta filtrada `GET /api/bookings/me` y cancelación validada `POST /api/bookings/{id}/cancel` con verificación estricta de propiedad de cuenta.
3. **Optimización de Pruebas de Integración:**
   - Homogeneizar suites con Testcontainers para Redis y LiveKit en pipelines headless de CI.

---

## 5. Auditoría Temporal Rápida (Comandos Git de Referencia)

Para consultar detalles de los últimos cambios sin recurrir a bitácoras masivas:
```bash
# Ver los últimos 10 commits con resumen de cambios
git log -n 10 --oneline --stat

# Ver qué cambió en los últimos 7 días
git log --since="7 days ago" --oneline

# Ver historial de cambios en un archivo específico
git log -p -n 3 -- <ruta/al/archivo>
```
