# SkillBridge AI

> Plataforma profesional de mentorías y servicios técnicos orientada a la excelencia en ingeniería de software. Diseñada con **Arquitectura Hexagonal (Ports & Adapters)**, **Event-Driven Architecture (EDA)** y procesamiento distribuido de datos.

---

## 1. Stack Tecnológico Principal

- **Backend:** Java 21 LTS, Spring Boot 3.5.x, Spring Security (JWT + RBAC), Spring Data JPA, Flyway.
- **Frontend:** Angular 20 (Standalone Components, Signals, Reactive Forms), TypeScript 5.9, Nginx.
- **Persistencia & Caché:** PostgreSQL 17 (System of Record), Redis 8 (Patrón Cache-Aside y almacenamiento de Feature Store).
- **Mensajería Asíncrona:** RabbitMQ 4 (AMQP 0-9-1) con estrategias de Reintento, Exponencial Backoff y Dead Letter Queue (DLQ).
- **Data & Analytics Engine:** Python 3.11, Apache Spark 3.5.6, PySpark, PyArrow, exportación columnar Parquet (Snappy).
- **Streaming WebRTC:** LiveKit SFU Server v1.8 con Redis para estado efímero de salas en tiempo real.
- **Inteligencia Artificial:** Spring AI con Google Gemini Developer API (`gemini-3.1-flash-lite`).
- **Observabilidad & Monitoreo:** Prometheus v3.5.0, Grafana 12.1.1 y Spring Boot Actuator con Micrometer.
- **Infraestructura & CI/CD:** Docker Compose multi-stage y GitHub Actions.

---

## 2. Centro de Documentación y Trazabilidad

Para profundizar en el diseño, contratos, estado actual e historias de usuario implementadas:

| Recurso / Documento | Ubicación | Descripción |
|---|---|---|
| 📋 **Historias de Usuario (US)** | [`docs/user-stories/`](docs/user-stories/) | Trazabilidad técnica, criterios de aceptación y referencias exactas de código por funcionalidad. |
| 📍 **Estado Técnico Actual** | [`STATE.md`](STATE.md) | Fotografía viva del sistema, servicios activos, integraciones y pendientes inmediatos. |
| 🤖 **Reglas para Agentes IA** | [`AGENTS.md`](AGENTS.md) | Directrices arquitectónicas, regla de dependencia, manejo de memoria y auditoría vía Git. |
| 🏛️ **Arquitectura Global** | [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Puertos y adaptadores, modelos de dominio, flujo de eventos y diagramas de secuencia. |
| 🔌 **Especificación REST API** | [`docs/API.md`](docs/API.md) | Catálogo de endpoints REST, DTOs de entrada/salida y códigos de estado HTTP. |
| 🧠 **Feature Store & Analytics** | [`docs/FEATURE-STORE.md`](docs/FEATURE-STORE.md) | Arquitectura de embeddings vectoriales, Redis Cache-Aside, fallback PostgreSQL y batch ETL. |
| ⚡ **Guía Apache Spark** | [`docs/SPARK-GUIDE.md`](docs/SPARK-GUIDE.md) | Comandos de ejecución distribuida en Spark y pruebas analíticas en PySpark. |
| 📹 **Monitoreo WebRTC LiveKit** | [`docs/LIVEKIT-MONITORING.md`](docs/LIVEKIT-MONITORING.md) | Configuración de streaming WebRTC, latencias y telemetría de videoconferencias. |
| 📬 **Microservicio Notificaciones** | [`docs/notifications/`](docs/notifications/) | Especificación de contratos de eventos, plan de arquitectura NestJS y correos con Resend. |
| ⚖️ **Architecture Decision Records**| [`docs/adr/`](docs/adr/) | Registro formal de decisiones técnicas (ADR 0001 a 0005). |
| ☕ **Guía Técnica de Backend** | [`backend/README.md`](backend/README.md) | Comandos Maven, testing con Testcontainers y estructura de paquetes Spring Boot. |
| 🅰️ **Guía Técnica de Frontend** | [`frontend/README.md`](frontend/README.md) | Estructura Angular Standalone, Signals, interceptores HTTP y compilación Nginx. |

---

## 3. Guía Rápida de Ejecución con Docker Compose

### 3.1. Prerrequisitos
- Docker Engine 24+ y Docker Compose v2+.
- Make (opcional, para atajos de consola).

### 3.2. Paso a Paso

1. **Configurar Variables de Entorno:**
   ```bash
   cp .env.example .env
   # Configura tu GEMINI_API_KEY y un JWT_SECRET de al menos 32 caracteres
   ```

2. **Levantar el Stack Completo en Segundo Plano:**
   ```bash
   make up
   # O directamente con Docker Compose:
   docker compose up --build -d
   ```

3. **Verificar Estado de los Contenedores:**
   ```bash
   docker compose ps
   ```

4. **Monitorear Logs en Tiempo Real:**
   ```bash
   docker compose logs -f --tail=100
   ```

5. **Detener los Servicios:**
   ```bash
   make down
   # O: docker compose down
   ```

### 3.3. Perfiles Opcionales

El archivo `docker-compose.yml` incluye perfiles modulares para ahorrar recursos cuando sea necesario:

```bash
# Levantar el entorno analítico de Apache Spark / PySpark:
docker compose --profile data up -d

# Levantar el stack de observabilidad (Prometheus + Grafana):
docker compose --profile observability up -d
```

### 3.4. Modo Desarrollo Local Híbrido (Fast Feedback)

Para desarrollar con recarga en caliente sin reconstruir imágenes en cada cambio:

```bash
# 1. Levantar únicamente servicios de infraestructura
docker compose up -d postgres redis rabbitmq livekit redis-livekit

# 2. Ejecutar el backend (Spring Boot en http://localhost:8080)
cd backend && mvn spring-boot:run

# 3. Ejecutar el frontend (Angular en http://localhost:4200)
cd frontend && npm install && npm start
```

---

## 4. Puertos y Direcciones Locales de Acceso

| Servicio | Modo Docker Compose | Modo Desarrollo Local Host | Credenciales por Defecto |
|---|---|---|---|
| **Frontend Web (SPA)** | `http://localhost:8088` | `http://localhost:4200` | - |
| **Backend API REST** | `http://localhost:8081` | `http://localhost:8080` | Bearer JWT en cabecera |
| **Swagger UI (OpenAPI)** | `http://localhost:8081/swagger-ui.html` | `http://localhost:8080/swagger-ui.html` | Pública |
| **Health Check (Actuator)** | `http://localhost:8081/actuator/health` | `http://localhost:8080/actuator/health` | Pública |
| **PostgreSQL 17** | `localhost:5435` | `localhost:5432` | `skillbridge` / `skillbridge_dev` |
| **RabbitMQ Management** | `http://localhost:15672` | `http://localhost:15672` | `guest` / `guest` |
| **LiveKit SFU Server** | `http://localhost:7880` | `http://localhost:7880` | `devkey` / `secret` |
| **Prometheus** (perfil `observability`) | `http://localhost:9090` | `http://localhost:9090` | Sin autenticación |
| **Grafana** (perfil `observability`) | `http://localhost:3000` | `http://localhost:3000` | `admin` / `admin` |
| **Spark Master UI** (perfil `data`) | `http://localhost:8080` | `http://localhost:8080` | Sin autenticación |

---

## 5. Estructura del Repositorio

```text
SkillBridge-AI-JAVA-Angular-Celula-1/
├── AGENTS.md                  # Reglas estrictas y lineamientos para agentes IA
├── STATE.md                   # Fotografía técnica viva del estado actual del sistema
├── README.md                  # Entrada principal y guía ejecutiva del proyecto
├── docker-compose.yml         # Orquestación de contenedores y servicios
├── Makefile                   # Comandos rápidos de ciclo de vida (up, down, test)
│
├── backend/                   # Microservicio Spring Boot 3.5.x (Arquitectura Hexagonal)
│   ├── src/main/java/.../
│   │   ├── domain/            # Modelos puros y reglas de negocio
│   │   ├── application/       # Casos de uso y puertos (in/out)
│   │   └── infrastructure/    # Adaptadores (REST, JPA, Redis, RabbitMQ, LiveKit)
│   └── src/main/resources/db/migration/ # Migraciones inmutables Flyway
│
├── frontend/                  # SPA Angular 20 (Standalone Components + Signals)
│   └── src/app/
│       ├── core/              # Servicios singleton, interceptores y modelos
│       ├── features/          # Vistas (catálogo, reservas, videoconferencias LiveKit)
│       └── shared/            # Componentes reutilizables y layout
│
├── analytics-service/         # Procesamiento analítico y jobs de Machine Learning
│   └── jobs/                  # ETL Spark, batch loader Redis y segmentador semántico
│
├── data-engine/               # Contenedor aislado para PySpark y drivers JDBC
├── docs/                      # Centro de documentación técnica especializada
│   ├── user-stories/          # Fichas técnicas de Historias de Usuario (US)
│   ├── notifications/         # Contratos y diseño del microservicio de notificaciones
│   └── adr/                   # Architecture Decision Records
│
└── ops/                       # Aprovisionamiento de Prometheus y Grafana
```

---

## 6. Licencia y Uso Académico

Este proyecto está bajo la licencia MIT. Diseñado como proyecto integrador para la formación práctica en arquitectura de software, metodologías ágiles e integración de sistemas distribuidos con IA.
