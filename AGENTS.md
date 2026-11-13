# AGENTS.md

Este archivo contiene las directrices, el contexto técnico y las reglas obligatorias que los agentes de inteligencia artificial deben seguir al interactuar y modificar el código de **SkillBridge AI**.

---

## 1. Contexto del Proyecto

**SkillBridge AI** es una plataforma profesional de mentorías y servicios técnicos orientada a la excelencia en ingeniería de software.
- **Backend:** Java 21, Spring Boot 3.5.x, Spring Security (JWT + RBAC), Spring Data JPA, Redis, RabbitMQ (AMQP), Flyway.
- **Frontend:** Angular 20 (Standalone Components, Signals, Reactive Forms), Nginx.
- **Data & Analytics:** Python 3.11, Apache Spark / PySpark 3.5.x, PostgreSQL JDBC, Parquet.
- **Videoconferencia:** LiveKit SFU (WebRTC Audio/Video en tiempo real).
- **Infraestructura:** Docker Compose, PostgreSQL 17, Redis 8, RabbitMQ 4, Prometheus, Grafana, Google Gemini API.

---

## 2. Arquitectura y Patrones (OBLIGATORIO)

El backend implementa **Arquitectura Hexagonal (Ports & Adapters)** y sigue una estricta regla de dependencia unidireccional:

- **`domain`**: Modelos puros y excepciones de negocio. No contiene dependencias de frameworks ni infraestructura (sin referencias a PostgreSQL, JPA, Redis, RabbitMQ, Gemini, HTTP o Angular).
- **`application`**: Casos de uso (services) y los puertos de entrada (`in`) y salida (`out`).
- **`infrastructure`**: Implementación de puertos (adaptadores `in` como controllers REST/listeners de mensajería, y adaptadores `out` como repositorios JPA, clientes Redis/LiveKit/Gemini) junto con la configuración de Spring y seguridad.

**Patrones Activos:**
- **Cache-Aside:** Redis para acelerar lecturas frecuentes (catálogo público, feature store) con política de fallback transparente hacia la base de datos relacional.
- **Event-Driven Architecture (EDA):** RabbitMQ con esquemas de Retry, Exponential Backoff y Dead Letter Queue (DLQ) para desacoplamiento y consistencia eventual.
- **Observabilidad:** Métricas con Micrometer expuestas en `/actuator/prometheus` y dashboards en Grafana.

---

## 3. Reglas Técnicas de Desarrollo

1. **Regla de Dependencia:** Cualquier integración de base de datos, sistema externo, API de terceros o capa de caché DEBE realizarse a través de una interfaz (puerto) en la capa de aplicación, cuya implementación viva exclusivamente en la capa de infraestructura.
2. **Cero Secretos en el Frontend:** NUNCA filtres credenciales, API keys (`GEMINI_API_KEY`, `RESEND_API_KEY`, `LIVEKIT_API_SECRET`) o el `JWT_SECRET` hacia el código de Angular. Todas las operaciones seguras o llamadas a IA deben pasar por el backend.
3. **Migraciones de Base de Datos Inmutables:** Si se modifica el esquema, DEBES crear una nueva migración inmutable de Flyway en `backend/src/main/resources/db/migration` siguiendo la convención `V<numero>__<descripcion>.sql`. Nunca modifiques una migración ya existente.

---

## 4. Gestión de Memoria, Estado y Documentación

### 4.1. Prohibición de Registros Lineales Monolíticos
- **Queda estrictamente prohibido** continuar registrando eventos, bitácoras o notas de trabajo en archivos de texto de crecimiento lineal infinito (como `MEMORIA.md`). Los archivos masivos degradan el contexto y dificultan el mantenimiento.
- Los agentes **no deben** leer ni parsear archivos masivos de texto para buscar qué cambió en fechas o lapsos temporales específicos.

### 4.2. Delegación de Auditoría Temporal a Git
- Para cualquier auditoría de cambios históricos, trazabilidad temporal o inspección de commits entre fechas, sprints o ramas, los agentes deben apoyarse **exclusivamente en comandos nativos de Git**:
  - Historial por rango de fechas: `git log --since="YYYY-MM-DD" --until="YYYY-MM-DD" --oneline`
  - Inspección de cambios por archivo o commit: `git diff <commit>..<commit>`, `git show <commit>`, `git log -p -S "<termino>"`
  - Autores y confirmaciones recientes: `git log -n <N> --stat`

### 4.3. Estado Técnico Centralizado (`STATE.md`)
- La raíz del repositorio mantiene un único archivo **`STATE.md`** que refleja la fotografía del **estado técnico actual**.
- `STATE.md` contiene de forma concisa:
  1. Versión y módulos activos del sistema.
  2. Arquitectura y tecnologías en producción/desarrollo local.
  3. Estado de integración por servicio (Backend, Frontend, Data Engine, Analytics, Mensajería, Observabilidad).
  4. Pendientes técnicos inmediatos y deudas técnicas priorizadas.
- **Regla de Actualización:** Al finalizar una tarea técnica relevante, el agente debe actualizar `STATE.md` de manera incremental e idempotente, reemplazando el estado anterior para mantener el archivo siempre ligero y actualizado.

### 4.4. Trazabilidad de Historias de Usuario (US) en `/docs/user-stories/`
- Cada funcionalidad, tarea o historia de usuario debe contar con un documento técnico dedicado dentro del directorio `/docs/user-stories/`, siguiendo la nomenclatura `US-<CODIGO>-<nombre-corto>.md`.
- **Campo de Estado Obligatorio:** Todo documento de US debe iniciar con un campo de estado explícito:
  - `[ ] Por hacer`
  - `[🔄] En curso`
  - `[👀] En revisión`
  - `[✅] Terminada`
- **Enfoque Técnico Profundo:** La documentación de la US debe describir la solución técnica implementada: patrones arquitectónicos, puertos, adaptadores, eventos de mensajería, endpoints, entidades y flujos entre capas.
- **Prohibición de Bloques de Código Masivos:** **NO pegues código fuente largo** en los documentos de usuario. Las referencias a la implementación deben hacerse mediante la ruta relativa exacta y el rango de líneas:
  > *(Ej: `Ver backend/src/main/java/com/riwi/skillbridge/application/service/FeatureStoreService.java línea 32:55`)*
