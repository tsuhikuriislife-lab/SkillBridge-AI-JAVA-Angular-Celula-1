# SkillBridge AI - Backend API Service

Servicio backend monolítico modular para **SkillBridge AI**, implementado con **Java 21**, **Spring Boot 3.5.x** y **Arquitectura Hexagonal (Ports & Adapters)**.

---

## 1. Stack Tecnológico

| Componente | Tecnología | Versión | Propósito |
|---|---|---|---|
| **Lenguaje** | Java (OpenJDK) | 21 LTS | Runtime de alto rendimiento y Virtual Threads |
| **Framework** | Spring Boot | 3.5.6 | Núcleo de inyección de dependencias y configuración |
| **Inteligencia Artificial** | Spring AI (Google GenAI) | 1.1.8 | Abstracción para modelos Google Gemini (`gemini-3.1-flash-lite`) |
| **Seguridad** | Spring Security + JJWT | 0.12.6 | Autenticación stateless (JWT), RBAC y protección CSRF |
| **Persistencia** | Spring Data JPA / Hibernate | 6.x | Mapeo objeto-relacional en PostgreSQL 17 |
| **Migraciones** | Flyway | 11.x | Versionado inmutable del esquema de base de datos |
| **Caché Distribuido** | Spring Data Redis | 8.x Alpine | Cache-Aside para catálogo y sesiones de diagnóstico con TTL |
| **Broker de Mensajería**| Spring AMQP (RabbitMQ) | 4.x Alpine | Publicación asíncrona de eventos con reintentos y DLQ |
| **Tiempo Real (WebRTC)**| LiveKit Server SDK | v1.8 | Generación de tokens JWT de sala y webhooks |
| **Observabilidad** | Actuator + Micrometer | - | Métricas de rendimiento raspadas por Prometheus |

---

## 2. Arquitectura Hexagonal y Flujo de Dependencias

El backend separa estrictamente las reglas del negocio de los detalles técnicos y librerías externas:

```mermaid
flowchart TD
    subgraph AdaptadoresEntrada["ADAPTADORES DE ENTRADA (Inbound Adapters)"]
        REST["REST Controllers\n(@RestController)"]
        CONSUMER["RabbitMQ Consumer\n(@RabbitListener)"]
        WEBHOOK["LiveKit Webhook Controller"]
    end

    subgraph PuertosEntrada["PUERTOS DE ENTRADA (Use Cases)"]
        UC1["CatalogOfferingUseCase"]
        UC2["EnrollUserUseCase"]
        UC3["GenerateAssessmentUseCase"]
        UC4["ManageMeetingUseCase"]
    end

    subgraph DominioAplicacion["NÚCLEO DE APLICACIÓN Y DOMINIO"]
        SVC["Application Services\n(Orquestación & Transacciones)"]
        DOM["Domain Entities & Records\n(Reglas de Negocio Puras)"]
        EXC["Domain Exceptions\n(BusinessRuleException, etc.)"]
        SVC --> DOM
        SVC --> EXC
    end

    subgraph PuertosSalida["PUERTOS DE SALIDA (Outbound Ports)"]
        P_OFF["OfferingPort"]
        P_CACHE["OfferingCachePort"]
        P_AI["AiAssessmentPort / AiRecommendationPort"]
        P_SES["AssessmentSessionCachePort"]
        P_EVT["BookingEventPublisherPort"]
    end

    subgraph AdaptadoresSalida["ADAPTADORES DE SALIDA (Outbound Adapters)"]
        JPA["Spring Data JPA Repository\n(PostgreSQL 17)"]
        REDIS["StringRedisTemplate Adapter\n(Redis 8 + Fallback Local)"]
        GEMINI["Spring AI ChatClient\n(Google Gemini API)"]
        AMQP["RabbitTemplate Adapter\n(RabbitMQ Exchange)"]
        LK["LiveKit SDK Adapter\n(Token Generation)"]
    end

    REST --> UC1 & UC2 & UC3 & UC4
    CONSUMER --> SVC
    WEBHOOK --> SVC

    UC1 & UC2 & UC3 & UC4 --> SVC
    SVC --> P_OFF & P_CACHE & P_AI & P_SES & P_EVT

    P_OFF --> JPA
    P_CACHE & P_SES --> REDIS
    P_AI --> GEMINI
    P_EVT --> AMQP
```

---

## 3. Flujos Clave de Funcionamiento

### 3.1. Catálogo con Caché Cache-Aside e Invalidación

```mermaid
sequenceDiagram
    autonumber
    actor Cliente as Cliente (Angular / HTTP)
    participant Ctrl as OfferingController
    participant Svc as OfferingService
    participant Cache as Redis (OfferingCachePort)
    participant DB as PostgreSQL (OfferingPort)

    Cliente->>Ctrl: GET /api/services?page=0&size=10
    Ctrl->>Svc: getActiveCatalog(page, size, sort)
    Svc->>Cache: get(cacheKey)
    alt Cache HIT
        Cache-->>Svc: Retorna lista serializada en Redis
    else Cache MISS
        Svc->>DB: findActiveServices(PageRequest)
        DB-->>Svc: Entidades de base de datos
        Svc->>Cache: put(cacheKey, data, TTL: 15 min)
    end
    Svc-->>Ctrl: PageResult<OfferingOut>
    Ctrl-->>Cliente: 200 OK (JSON)
```

### 3.2. Eventos Asíncronos con RabbitMQ y Dead Letter Queue (DLQ)

```mermaid
sequenceDiagram
    autonumber
    actor Alumno as Alumno Autenticado
    participant API as EnrollmentController
    participant Svc as ServiceEnrollmentService
    participant DB as PostgreSQL
    participant MQ as RabbitMQ Exchange
    participant Q as Cola: booking.created
    participant Cons as BookingEventConsumer
    participant DLQ as Dead Letter Queue (DLQ)

    Alumno->>API: POST /api/services/{id}/enroll
    API->>Svc: enroll(userId, serviceId)
    Svc->>DB: Guarda ServiceEnrollment (ACID)
    Svc->>MQ: Publica evento "BookingCreated"
    API-->>Alumno: 201 Created
    MQ->>Q: Enruta mensaje
    Q->>Cons: Entrega mensaje al consumidor
    alt Procesamiento Exitoso
        Cons->>DB: Registra notificación in-app (booking_notifications)
    else Falla Transitoria
        Cons-->>Q: Reintento con Backoff Exponencial (3 intentos)
    else Reintentos Agotados
        Q->>DLQ: Envía a Dead Letter Queue para inspección manual
    end
```

### 3.3. Diagnóstico Técnico con IA y Caché Anti-Trampas

```mermaid
sequenceDiagram
    autonumber
    actor Alumno as Alumno en Modal de Compra
    participant API as AssessmentController
    participant Svc as AssessmentService
    participant Gemini as Google Gemini (Spring AI)
    participant Cache as RedisAssessmentCacheAdapter
    
    Alumno->>API: POST /api/assessments/offerings/{offeringId}
    API->>Svc: generate(offeringId)
    Svc->>Gemini: prompt() evalúa prerrequisitos del curso
    Gemini-->>Svc: JSON: 3 preguntas + opciones + respuesta_correcta + explicacion
    Svc->>Cache: save(TechnicalAssessment completo, TTL: 30 min)
    Svc-->>API: TechnicalAssessment
    API-->>Alumno: ClientAssessmentResponse (SIN respuestas correctas)
    
    Note over Alumno: El usuario responde el test en el navegador
    
    Alumno->>API: POST /api/assessments/{assessmentId}/submit (answers)
    API->>Svc: evaluate(assessmentId, answers)
    Svc->>Cache: findById(assessmentId)
    Cache-->>Svc: TechnicalAssessment original
    Svc->>Svc: Califica (umbral 60%) y genera feedback pedagógico
    Svc-->>API: AssessmentEvaluation
    API-->>Alumno: 200 OK con puntaje, feedback y explicaciones
```

---

## 4. Estructura del Código

```text
backend/src/main/java/com/riwi/skillbridge/
├── domain/                               # NÚCLEO PURO (Cero dependencias externas)
│   ├── model/                            # Entidades de dominio y Value Objects (Records)
│   └── exception/                        # Excepciones de negocio (AiQuotaExceededException, etc.)
│
├── application/                          # CASOS DE USO Y ORQUESTACIÓN
│   ├── port/in/                          # Puertos de Entrada (Interfaces de casos de uso)
│   ├── port/out/                         # Puertos de Salida (Interfaces hacia persistencia, IA, etc.)
│   └── service/                          # Implementación de Casos de Uso (AssessmentService, etc.)
│
└── infrastructure/                       # DETALLES TÉCNICOS Y FRAMEWORK
    ├── adapter/in/rest/                  # Controladores REST Spring MVC y DTOs
    ├── adapter/out/ai/                   # Adaptadores Spring AI (GeminiAiAdapter, GeminiAiAssessmentAdapter)
    ├── adapter/out/cache/                # Adaptadores Redis y fallback en memoria
    ├── adapter/out/persistence/          # Adaptadores Spring Data JPA y Entidades
    ├── adapter/out/amqp/                 # Publicadores y Consumidores RabbitMQ
    ├── config/                           # Configuraciones de beans (RabbitMQ, Redis, OpenAPI, Security)
    └── security/                         # Filtros JWT, CSRF y UserDetailsService
```

---

## 5. Variables de Entorno del Backend

El backend se configura mediante variables de entorno en su archivo `.env` o en el entorno del sistema:

| Variable | Descripción | Valor por Defecto |
|---|---|---|
| `PORT` | Puerto HTTP del servidor | `8080` |
| `DATABASE_URL` | URL JDBC de conexión a PostgreSQL | `jdbc:postgresql://localhost:5432/skillbridge` |
| `DATABASE_USER` | Usuario de base de datos | `skillbridge` |
| `DATABASE_PASSWORD` | Contraseña de base de datos | `skillbridge_dev` |
| `REDIS_URL` | URL de conexión al servidor Redis | `redis://localhost:6379` |
| `RABBITMQ_URL` | URL AMQP de conexión a RabbitMQ | `amqp://guest:guest@localhost:5672` |
| `JWT_SECRET` | Clave secreta HMAC para firmar tokens JWT (min 256 bits) | *Requerida* |
| `GEMINI_API_KEY` | Clave de API de Google AI Studio | *Requerida para IA* |
| `GEMINI_MODEL` | Modelo de lenguaje de Google Gemini | `gemini-3.1-flash-lite` |
| `LIVEKIT_API_KEY` | Clave pública del servidor LiveKit WebRTC | *Requerida para videollamadas* |
| `LIVEKIT_API_SECRET` | Clave privada del servidor LiveKit WebRTC | *Requerida para videollamadas* |
| `LIVEKIT_URL` | URL del servidor LiveKit | `http://localhost:7880` |
| `CORS_ALLOWED_ORIGINS` | Orígenes autorizados en cabeceras CORS | `http://localhost:8088,http://localhost:4200` |

---

## 6. Comandos de Terminal para Ejecutar el Backend

### 6.1. Requisitos Previos
- **Java 21 LTS** (`java -version`).
- **Apache Maven 3.9+** (`mvn -v`) o usar el wrapper `./mvnw`.
- Contenedores de soporte (PostgreSQL, Redis, RabbitMQ) activos.

---

### 6.2. Levantar Solo las Dependencias de Infraestructura (Recomendado para Dev Local)
Para desarrollar en el backend localmente sin tener que compilar el contenedor Docker de Spring en cada cambio:

```bash
# Levantar PostgreSQL, Redis y RabbitMQ en background
docker compose up -d postgres redis rabbitmq
```

---

### 6.3. Ejecutar el Backend Localmente con Maven

```bash
cd backend

# Exportar variables mínimas necesarias
export JWT_SECRET="un_secreto_muy_seguro_y_largo_de_al_menos_32_caracteres_12345"
export GEMINI_API_KEY="AIzaSyTuClaveRealDeGoogleAiStudioAqui"
export GEMINI_MODEL="gemini-3.1-flash-lite"
export LIVEKIT_API_KEY="devkey"
export LIVEKIT_API_SECRET="secret"

# Compilar y arrancar la aplicación
mvn spring-boot:run
```

*La API estará escuchando en `http://localhost:8080/api` y la documentación interactiva en `http://localhost:8080/swagger-ui.html`.*

---

### 6.4. Ejecución de Pruebas Automatizadas

```bash
cd backend

# Ejecutar toda la suite de pruebas
mvn test

# Ejecutar únicamente pruebas unitarias de casos de uso (sin necesidad de Docker)
mvn -Dtest=AssessmentServiceTest,BookingServiceTest,CurrentUserServiceTest test

# Generar reporte de cobertura con JaCoCo
mvn test jacoco:report
# El reporte HTML estará disponible en: target/site/jacoco/index.html
```

---

### 6.5. Compilación y Empaquetado de Producción (JAR)

```bash
cd backend

# Generar el archivo ejecutable optimizado
mvn clean package -DskipTests

# Ejecutar el fat JAR resultante
java -jar target/skillbridge-api-0.0.1-SNAPSHOT.jar
```

---

### 6.6. Monitoreo y Verificación de Salud
Con el backend en ejecución, puedes inspeccionar sus puntos de telemetría:

```bash
# Comprobar estado de salud y dependencias vivas
curl -s http://localhost:8080/actuator/health | jq .

# Inspeccionar métricas expuestas para Prometheus
curl -s http://localhost:8080/actuator/prometheus | head -n 30
```
