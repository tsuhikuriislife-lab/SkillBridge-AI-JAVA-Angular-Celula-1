# STATE.md — Estado Técnico del Sistema

> **Última Actualización:** 2026-11-17  
> **Rama Actual:** `feat/upgraded_agent_memory_` (sincronizada con `origin/develop`)  
> **Último Commit Auditado:** `b023eed` (`feat: Add comprehensive technical state documentation and user story tracking`)  
> **Versión de Migraciones Flyway:** `V18__feature_store.sql`  
> **Tablero Kanban Activo (GitHub Projects):** [SkillBridge AI - Célula 1](https://github.com/users/tsuhikuriislife-lab/projects/1)

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
| **Data Engine & Analytics** | Python 3.8 (imagen `apache/spark:3.5.1-python3`), Apache Spark 3.5.1, PySpark 3.5.1, JDBC PostgreSQL 42.7.3, tiktoken 0.7.0, pytest | Activo (Perfil `data`) | Clúster Spark `localhost:7077` / UI `8082` |
| **Observabilidad** | Prometheus v3.5.0 + Grafana 12.1.1 (Micrometer Actuator) | Activo (Perfil `observability`) | Prometheus `9090` / Grafana `3000` |

---

## 2. Estado de Integraciones y Capacidades Técnicas

### 2.1. Arquitectura Hexagonal y Núcleo de Negocio
- **Estructura estricta:** `domain` (modelos puros, sin dependencias externas) -> `application` (casos de uso, puertos `in`/`out`) -> `infrastructure` (adaptadores web, mensajería, persistencia JPA, clientes REST/Redis).
- **Catálogo y Reservas:** Gestión de ofertas de mentoría y reservas con publicación asíncrona de eventos de dominio a RabbitMQ (`skillbridge.exchange`).
- **Autenticación y Seguridad:** JWT con firma HMAC-SHA256, contraseñas hasheadas con BCrypt, cookies HttpOnly (`access_token`) y protección anti-CSRF (`X-XSRF-TOKEN`).

### 2.2. Feature Store & Recomendaciones IA (HU-PS06, HU-PS02, HU-PS03, HU-05)
- **Extracción y Limpieza Batch (HU-PS02):** Pipeline ETL en Apache Spark (`analytics-service/jobs/extract_and_clean_catalog.py`) que extrae vía JDBC particionado desde PostgreSQL, normaliza texto eliminando HTML/Markdown y escribe en formato columnar Parquet comprimido con Snappy.
- **Segmentación Semántica (HU-PS03):** Chunker semántico para descripciones de servicios en `analytics-service/jobs/text_chunker.py`, cubierto por `analytics-service/tests/test_catalog_chunking.py` (US-ANL-01).
- **Feature Store Redis + Fallback PostgreSQL (HU-PS06):**
  - Carga masiva con batch pipeline en Redis (`analytics-service/jobs/redis_batch_loader.py`).
  - Notificación asíncrona mediante evento `features.updated` en RabbitMQ.
  - Consumo en backend con `FeaturesUpdatedListener` y lectura en dos niveles (`FeatureStoreService`) consultando primero Redis y aplicando fallback resiliente a PostgreSQL si Redis falla o hay cold-start.
  - Registro de métricas de acierto/fallo y latencia con Micrometer.
- **Integración con Google Gemini:** `ChatClient` de Spring AI consumiendo oficialmente el modelo `gemini-3.8-flash` para diagnóstico de prerrequisitos y recomendaciones personalizadas sin exponer API keys en el cliente.

### 2.3. Videoconferencia WebRTC con LiveKit (HU-29.11)
- **Backend Adapter:** `LiveKitRestAdapter` utilizando el SDK oficial `io.livekit.server.AccessToken` con generación segura de tokens JWT, TTL de 120 minutos y asignación de permisos según el rol (`MeetingRole.PROVIDER` con `canPublish: true` y `roomAdmin: true`; `MeetingRole.PARTICIPANT` restringido).
- **Controlador de Webhooks:** `LiveKitWebhookController` para sincronizar eventos del ciclo de vida de salas (inicio, finalización, participantes conectados).
- **Frontend SPA:** Componente Standalone `MeetingRoomComponent` (`frontend/src/app/features/meeting-room`) con controles de audio, video y reconexión automática.

---

## 3. Historias de Usuario Documentadas y Trazables

Catálogo completo indexado en [`docs/user-stories/README.md`](docs/user-stories/README.md):
- `[✅] Terminada` [US-01: Catálogo Público de Servicios con Cache-Aside en Redis](docs/user-stories/US-01-catalogo-publico-redis.md)
- `[✅] Terminada` [US-02: Autenticación Stateless, Cookies HttpOnly y CSRF](docs/user-stories/US-02-autenticacion-httponly-csrf.md)
- `[✅] Terminada` [US-03: Gestión de Reservas y Desacoplamiento EDA con RabbitMQ](docs/user-stories/US-03-reservas-eventos-amqp.md)
- `[✅] Terminada` [US-05: Asistente de Recomendaciones Semánticas con Gemini](docs/user-stories/US-05-recomendaciones-gemini-spring-ai.md)
- `[✅] Terminada` [US-06: Diagnóstico Técnico Previo con IA y Prerrequisitos](docs/user-stories/US-06-diagnostico-previo-assessments.md)
- `[✅] Terminada` [US-10: Gestión de Ofertas y Disponibilidad por Proveedores](docs/user-stories/US-10-gestion-ofertas-proveedor.md)
- `[✅] Terminada` [US-11: Historial de Reservas y Notificaciones In-App](docs/user-stories/US-11-historial-reservas-notificaciones.md)
- `[✅] Terminada` [US-29.11: Videoconferencias en Tiempo Real con LiveKit](docs/user-stories/US-29.11-videoconferencias-livekit.md)
- `[✅] Terminada` [US-PS02: Pipeline ETL de Limpieza y Normalización del Catálogo](docs/user-stories/US-PS02-etl-catalogo.md)
- `[✅] Terminada` [US-PS06: Feature Store con Redis y Fallback Resiliente PostgreSQL](docs/user-stories/US-PS06-feature-store.md)

---

## 4. Pendientes Técnicos Inmediatos (Backlog Activo de Auditoría)

Historias registradas en el tablero de GitHub Projects en columna `Todo`:

1. **`US-INFRA-01` [Asignado: `@deleonguillermo87`]:**
   - Remapear `spark-worker` a `8082:8081` en `docker-compose.yml` para evitar colisión con `backend`.
   - Asignar `profiles: ["data"]` a `spark-master` y `spark-worker`.
   - Montar `./ops/prometheus/rules` en el contenedor `prometheus`.
2. **`US-ANL-01` [Asignado: `@DylanSrz`] — `[👀] En revisión`:**
   - `tiktoken==0.7.0` (último compatible con Python 3.8) en `requirements.txt`; la codificación `cl100k_base` queda cacheada en la imagen.
   - `test_catalog_chunking.py` convertido a suite pytest (18 casos) y `pytest.ini` creado. Suite Analytics: 37 tests en verde.
3. **`US-FE-01` [Asignado: `@KevinMercado1`]:**
   - Corregir los 3 tests fallando en `display-bookings.spec.ts` agregando mock de `MeetingService`.
   - Renombrar `admin-dashboard.componentspec.ts` a `admin-dashboard.component.spec.ts` y corregir tipo `TS2345`.
4. **`US-FE-02` [Asignado: `@juanvargasviloria2507-gif`]:**
   - Implementar tests unitarios para `MeetingRoomComponent` (LiveKit WebRTC) y `CheckoutComponent`.
5. **`US-FE-03` [Asignado: `@andresmbd`]:**
   - Implementar tests unitarios para `authInterceptor` (HttpOnly/CSRF) y guards (`authGuard`, `adminGuard`, `nonAdminGuard`).
6. **`US-BE-01` [Asignado: `@JesusLucena-png`]:**
   - Implementar suites `@WebMvcTest` para los 11 controladores REST actualmente sin pruebas y elevar cobertura de `GlobalExceptionHandler`.
7. **`US-BE-02` [Asignado: `@Va-cod`]:**
   - Implementar tests Mockito para servicios core (`AuthService`, `AssessmentService`, `AiRecommendationService`) y `@DataJpaTest` para adaptadores JPA.
8. **`US-BE-03` [Asignado: `@YamitGC`]:**
   - Saneamiento de `SecurityConfiguration.java` para eliminar beans duplicados de `UserDetailsService`.
9. **`US-DOCS-01` [Asignado: `@tsuhikuriislife-lab`]:**
   - Actualizar `docs/API.md` reflejando el contrato de autenticación HttpOnly y CSRF y documentando endpoints faltantes.
10. **`US-DOCS-02` [Asignado: `@leonardo-200224`]:**
    - Unificar el modelo oficial `gemini-3.8-flash` en `.env.example`, `README.md` y guías técnicas.
11. **`US-DOCS-03` [Asignado: `@deleonguillermo87`]:**
    - Mantenimiento y actualización del catálogo central de Historias de Usuario base en `docs/user-stories/`.

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
