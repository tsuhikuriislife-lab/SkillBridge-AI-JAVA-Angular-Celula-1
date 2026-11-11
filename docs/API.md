# Especificación Completa de la API REST de SkillBridge AI

**Versión:** 1.2.0  
**Base Path:** `/api`  
**Formato de Intercambio:** JSON (`application/json;charset=UTF-8`)  
**Autenticación:** Bearer Token (JSON Web Token - RFC 7519) en la cabecera `Authorization: Bearer <token>`  
**Protección CSRF:** Activada para peticiones mutables mediante la cabecera `X-XSRF-TOKEN` y cookie `XSRF-TOKEN`.

---

## 1. Convenciones y Manejo Global de Errores

Todas las respuestas de error siguen el estándar **RFC 7807 (Problem Details)**:

```json
{
  "timestamp": "2026-11-09T21:45:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Detalle explicativo del error de validación o regla de negocio",
  "path": "/api/services"
}
```

### Códigos de Estado Principales
- `200 OK`: Petición procesada exitosamente.
- `201 Created`: Recurso creado exitosamente.
- `204 No Content`: Petición procesada exitosamente sin cuerpo de respuesta.
- `400 Bad Request`: Parámetros inválidos, validación de DTO fallida o formato JSON incorrecto.
- `401 Unauthorized`: Token ausente, expirado o firma criptográfica inválida.
- `403 Forbidden`: El usuario autenticado no posee los privilegios requeridos (`ROLE_CUSTOMER`, `ROLE_PROVIDER`, `ROLE_ADMIN`).
- `404 Not Found`: Recurso o entidad no encontrada.
- `409 Conflict`: Conflicto de estado (ej. usuario ya registrado, cupo agotado).
- `422 Unprocessable Entity`: Violación de regla de negocio (ej. reserva con menos de 48 horas de anticipación).
- `429 Too Many Requests`: Cuota de peticiones o tokens de IA excedida.
- `503 Service Unavailable`: Servicio externo no disponible (Gemini API, Redis, LiveKit).

---

## 2. Autenticación y Autorización (`/api/auth`)

### 2.1. Registro de Usuario
Crea una nueva cuenta de usuario en el sistema con el rol `CUSTOMER` por defecto.

- **Método:** `POST`
- **Ruta:** `/auth/register`
- **Acceso:** Público
- **Cuerpo de Solicitud:**
  ```json
  {
    "fullName": "Carlos Gómez",
    "email": "carlos.gomez@example.com",
    "password": "Password123!"
  }
  ```
- **Respuesta Exitosa (`201 Created`):**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "type": "Bearer",
    "id": "e9b5f54e-9d21-4f18-bb6a-a1b7e3e9d801",
    "fullName": "Carlos Gómez",
    "email": "carlos.gomez@example.com",
    "roles": ["ROLE_CUSTOMER"]
  }
  ```

### 2.2. Inicio de Sesión
Autentica las credenciales del usuario y emite un JWT firmado.

- **Método:** `POST`
- **Ruta:** `/auth/login`
- **Acceso:** Público
- **Cuerpo de Solicitud:**
  ```json
  {
    "email": "carlos.gomez@example.com",
    "password": "Password123!"
  }
  ```
- **Respuesta Exitosa (`200 OK`):**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "type": "Bearer",
    "id": "e9b5f54e-9d21-4f18-bb6a-a1b7e3e9d801",
    "fullName": "Carlos Gómez",
    "email": "carlos.gomez@example.com",
    "roles": ["ROLE_CUSTOMER"]
  }
  ```

---

## 3. Perfil de Usuario y Preferencias (`/api/users`)

### 3.1. Obtener Perfil Actual
Devuelve la identidad y roles del usuario resuelto desde el token JWT.

- **Método:** `GET`
- **Ruta:** `/users/me`
- **Acceso:** Autenticado (`CUSTOMER`, `PROVIDER`, `ADMIN`)
- **Respuesta Exitosa (`200 OK`):**
  ```json
  {
    "id": "e9b5f54e-9d21-4f18-bb6a-a1b7e3e9d801",
    "fullName": "Carlos Gómez",
    "email": "carlos.gomez@example.com",
    "roles": ["ROLE_CUSTOMER"]
  }
  ```

### 3.2. Consultar Preferencias de Usuario
- **Método:** `GET`
- **Ruta:** `/users/{userId}/preferences`
- **Acceso:** Autenticado (El propio usuario o `ADMIN`)

### 3.3. Actualizar Preferencias de Usuario
- **Método:** `PUT`
- **Ruta:** `/users/{userId}/preferences`
- **Acceso:** Autenticado

---

## 4. Catálogo Público de Servicios (`/api/services`, `/api/catalog`)

Respaldado por el patrón **Cache-Aside en Redis** para alta concurrencia y mínima latencia.

### 4.1. Listar Catálogo Activo Paginado
- **Método:** `GET`
- **Ruta:** `/services`
- **Acceso:** Público
- **Parámetros de Consulta:**
  - `page` (int, default: 0): Índice de página base 0.
  - `size` (int, default: 10, máx: 100): Elementos por página.
  - `sort` (string, default: "name,asc"): Campo y dirección (`name,asc`, `name,desc`, `price,asc`, `price,desc`, `createdAt,desc`).
- **Respuesta Exitosa (`200 OK`):**
  ```json
  {
    "content": [
      {
        "id": "11111111-1111-1111-1111-111111111111",
        "code": "SRV-JAVA01",
        "name": "Arquitectura Hexagonal con Spring Boot 3",
        "shortDescription": "Diseño robusto desacoplado con puertos y adaptadores.",
        "detail": "Curso avanzado que aborda dominio puro, TDD, Clean Architecture...",
        "price": 120.00,
        "capacity": 20,
        "categoryId": "22222222-2222-2222-2222-222222222222",
        "status": "ACTIVE",
        "photoUrl": "https://images.unsplash.com/photo-..."
      }
    ],
    "totalPages": 3,
    "totalElements": 25,
    "number": 0
  }
  ```

### 4.2. Búsqueda y Filtrado Avanzado
- **Método:** `GET`
- **Ruta:** `/services/search`
- **Acceso:** Público
- **Parámetros:** `name` (string), `category` (UUID), `free` (boolean), `page`, `size`, `sort`.

### 4.3. Obtener Detalle de un Servicio
- **Método:** `GET`
- **Ruta:** `/services/{id}`
- **Acceso:** Público

### 4.4. Listar Horarios Disponibles de un Servicio
- **Método:** `GET`
- **Ruta:** `/services/{id}/schedules`
- **Acceso:** Público
- **Respuesta Exitosa (`200 OK`):**
  ```json
  [
    {
      "id": "33333333-3333-3333-3333-333333333333",
      "serviceId": "11111111-1111-1111-1111-111111111111",
      "startDay": "MONDAY",
      "startTime": "18:00:00",
      "endTime": "20:00:00",
      "sessionDuration": 120
    }
  ]
  ```

---

## 5. Gestión de Servicios para Proveedores (`/api/provider/services`)

Exclusivo para usuarios con rol `ROLE_PROVIDER`.

### 5.1. Crear Nuevo Servicio Formativo
- **Método:** `POST`
- **Ruta:** `/provider/services`
- **Acceso:** Requiere `ROLE_PROVIDER`
- **Cuerpo de Solicitud:**
  ```json
  {
    "name": "Microservicios Event-Driven con RabbitMQ",
    "shortDescription": "Aprende mensajería asíncrona, DLQ y resiliencia.",
    "detail": "Guía práctica de diseño de eventos distribuidos...",
    "price": 95.00,
    "capacity": 15,
    "categoryId": "22222222-2222-2222-2222-222222222222",
    "photoUrl": "https://example.com/image.png"
  }
  ```

### 5.2. Listar Mis Servicios Creados
- **Método:** `GET`
- **Ruta:** `/provider/services`
- **Acceso:** Requiere `ROLE_PROVIDER`

### 5.3. Actualizar Servicio Existente
- **Método:** `PUT`
- **Ruta:** `/provider/services/{id}`
- **Acceso:** Requiere `ROLE_PROVIDER` (solo el propietario)

### 5.4. Cambiar Estado del Servicio (Activar / Pausar)
- **Método:** `PATCH`
- **Ruta:** `/provider/services/{id}/status`
- **Acceso:** Requiere `ROLE_PROVIDER`
- **Cuerpo de Solicitud:**
  ```json
  {
    "status": "INACTIVE"
  }
  ```

### 5.5. Crear Horario para un Servicio
- **Método:** `POST`
- **Ruta:** `/provider/services/{serviceId}/schedule`
- **Acceso:** Requiere `ROLE_PROVIDER`
- **Cuerpo de Solicitud:**
  ```json
  {
    "startDay": "TUESDAY",
    "startDate": "2026-11-20",
    "startTime": "19:00:00",
    "sessionDuration": 90
  }
  ```

### 5.6. Eliminar Horario de un Servicio
- **Método:** `DELETE`
- **Ruta:** `/provider/services/{serviceId}/schedule/{scheduleId}`
- **Acceso:** Requiere `ROLE_PROVIDER`

---

## 6. Inscripciones, Pagos y Reservas (`/api/services/{id}/enroll`, `/api/enrollments`)

### 6.1. Inscribirse a un Servicio
Registra la inscripción, valida cupos y publica el evento asíncrono a RabbitMQ (`BookingCreated`).

- **Método:** `POST`
- **Ruta:** `/services/{serviceId}/enroll`
- **Acceso:** Requiere `ROLE_CUSTOMER`
- **Respuesta Exitosa (`201 Created` o `200 OK`):**
  ```json
  {
    "userId": "e9b5f54e-9d21-4f18-bb6a-a1b7e3e9d801",
    "serviceId": "11111111-1111-1111-1111-111111111111",
    "enrollmentDate": "2026-11-09T21:50:00Z",
    "paymentStatus": "PAID"
  }
  ```

### 6.2. Listar Mis Inscripciones
- **Método:** `GET`
- **Ruta:** `/enrollments/me`
- **Acceso:** Requiere `ROLE_CUSTOMER`

### 6.3. Cancelar / Retirarse de una Inscripción
Aplica la política de negocio estricta de cancelación:
- Retiro con **más de 24 horas** de anticipación al inicio: `refund=true`.
- Retiro con **menos de 24 horas**: cancelación confirmada pero `refund=false`.

- **Método:** `DELETE`
- **Ruta:** `/enrollments/{serviceId}`
- **Acceso:** Requiere `ROLE_CUSTOMER`
- **Respuesta Exitosa (`200 OK`):**
  ```json
  {
    "serviceId": "11111111-1111-1111-1111-111111111111",
    "cancelled": true,
    "refundEligible": true,
    "message": "Inscripción cancelada con reembolso procesado satisfactoriamente."
  }
  ```

---

## 7. Historial y Notificaciones In-App (`/api/history/me`)

El consumidor asíncrono de RabbitMQ persiste eventos en `booking_notifications` y expone el historial al estudiante:

### 7.1. Consultar Mis Notificaciones e Historial
- **Método:** `GET`
- **Ruta:** `/history/me`
- **Acceso:** Autenticado
- **Respuesta Exitosa (`200 OK`):**
  ```json
  {
    "content": [
      {
        "id": "77777777-7777-7777-7777-777777777777",
        "type": "HISTORY",
        "message": "Tu inscripción ha sido confirmada exitosamente.",
        "status": "CONFIRMED",
        "createdAt": "2026-11-09T21:50:05Z",
        "serviceTitle": "Arquitectura Hexagonal con Spring Boot 3",
        "serviceId": "11111111-1111-1111-1111-111111111111"
      }
    ],
    "totalPages": 1,
    "totalElements": 1,
    "number": 0
  }
  ```

---

## 8. Subsistema de Inteligencia Artificial (`/api/ai`, `/api/assessments`)

### 8.1. Recomendación Semántica Basada en Catálogo
Genera recomendaciones ancladas al catálogo activo con enlaces directos en Markdown (`/service/:id`).

- **Método:** `POST`
- **Ruta:** `/ai/recommendations`
- **Acceso:** Autenticado (`CUSTOMER`, `PROVIDER`)
- **Cuerpo de Solicitud:**
  ```json
  {
    "goal": "Quiero aprender a construir arquitecturas reactivas y dominar Spring Boot y RabbitMQ para un empleo senior."
  }
  ```
- **Respuesta Exitosa (`200 OK`):**
  ```json
  {
    "recommendation": "Para alcanzar tu objetivo senior en arquitecturas reactivas y desacopladas, te recomiendo:\n\n1. [Ver curso: Arquitectura Hexagonal con Spring Boot 3](/service/11111111-1111-1111-1111-111111111111)\n2. [Ver curso: Microservicios Event-Driven con RabbitMQ](/service/44444444-4444-4444-4444-444444444444)\n\n---\n*Haz clic en el enlace para ir directamente a la vista del curso sin tener que buscarlo manualmente por su nombre.*"
  }
  ```

### 8.2. Generar Diagnóstico Técnico de Prerrequisitos
Solicita a Google Gemini la creación de 3 preguntas orientadas a evaluar prerrequisitos formativos. Almacena la sesión en Redis (TTL 30 min) y remueve las respuestas correctas de la respuesta al cliente para evitar trampas.

- **Método:** `POST`
- **Ruta:** `/assessments/offerings/{offeringId}`
- **Acceso:** Público (o autenticado)
- **Respuesta Exitosa (`200 OK`):**
  ```json
  {
    "id": "88888888-8888-8888-8888-888888888888",
    "offeringId": "11111111-1111-1111-1111-111111111111",
    "offeringTitle": "Arquitectura Hexagonal con Spring Boot 3",
    "questions": [
      {
        "id": "q1",
        "question": "¿Cuál es la principal ventaja de utilizar la inversión de dependencias en una arquitectura limpia?",
        "options": [
          "El dominio no depende de la base de datos ni de frameworks externos.",
          "Las consultas SQL se ejecutan un 50% más rápido.",
          "Evita la necesidad de escribir pruebas unitarias.",
          "Obliga a que todo el código esté dentro de un solo paquete."
        ]
      },
      {
        "id": "q2",
        "question": "¿Qué representa un Puerto en la Arquitectura Hexagonal?",
        "options": [
          "Un número de puerto TCP en el servidor Linux.",
          "Una interfaz de software que define un contrato de entrada o salida.",
          "Un componente de interfaz gráfica de Angular.",
          "Un servicio de mensajería asíncrono."
        ]
      },
      {
        "id": "q3",
        "question": "¿Cómo se comunican las entidades de dominio con los adaptadores de infraestructura?",
        "options": [
          "Invocando directamente las librerías de persistencia.",
          "A través de llamadas HTTP directas.",
          "A través de abstracciones (interfaces/puertos) implementadas en infraestructura.",
          "Mediante variables globales compartidas."
        ]
      }
    ]
  }
  ```

### 8.3. Enviar Respuestas y Obtener Calificación Pedagógica
Evalúa las respuestas del usuario contra la sesión en caché.

- **Método:** `POST`
- **Ruta:** `/assessments/{assessmentId}/submit`
- **Acceso:** Público (o autenticado)
- **Cuerpo de Solicitud:**
  ```json
  {
    "answers": {
      "q1": 0,
      "q2": 1,
      "q3": 2
    }
  }
  ```
- **Respuesta Exitosa (`200 OK`):**
  ```json
  {
    "assessmentId": "88888888-8888-8888-8888-888888888888",
    "offeringId": "11111111-1111-1111-1111-111111111111",
    "offeringTitle": "Arquitectura Hexagonal con Spring Boot 3",
    "score": 3,
    "totalQuestions": 3,
    "passed": true,
    "feedback": "¡Excelente desempeño! Demuestras un dominio sólido de los conceptos y prerrequisitos clave para este servicio.",
    "recommendation": "Estás completamente preparado para aprovechar esta mentoría al 100%. Te recomendamos proceder con la inscripción.",
    "questions": [
      {
        "id": "q1",
        "question": "¿Cuál es la principal ventaja de utilizar la inversión de dependencias en una arquitectura limpia?",
        "options": [
          "El dominio no depende de la base de datos ni de frameworks externos.",
          "Las consultas SQL se ejecutan un 50% más rápido.",
          "Evita la necesidad de escribir pruebas unitarias.",
          "Obliga a que todo el código esté dentro de un solo paquete."
        ],
        "userAnswer": 0,
        "correctOptionIndex": 0,
        "correct": true,
        "explanation": "La inversión de dependencias desacopla las reglas de negocio puras de los detalles técnicos de la infraestructura."
      }
    ]
  }
  ```

---

## 9. Clases Virtuales en Vivo con LiveKit WebRTC (`/api/meetings`)

Control de sesiones síncronas de video/audio, emisión de tokens JWT de sala y webhooks.

### 9.1. Iniciar Reunión de Mentoría
- **Método:** `POST`
- **Ruta:** `/meetings/service/{serviceId}/start`
- **Acceso:** Requiere `ROLE_PROVIDER` (solo el mentor del servicio)
- **Respuesta Exitosa (`200 OK`):**
  ```json
  {
    "meetingId": "99999999-9999-9999-9999-999999999999",
    "roomName": "room-srv-11111111",
    "livekitUrl": "ws://localhost:7880",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
  ```

### 9.2. Unirse a una Reunión en Vivo
- **Método:** `POST`
- **Ruta:** `/meetings/{meetingId}/join`
- **Acceso:** Autenticado (el proveedor propietario o un alumno con inscripción activa)
- **Respuesta Exitosa (`200 OK`):** Devuelve el token JWT de LiveKit con permisos de sala.

### 9.3. Finalizar Reunión
- **Método:** `POST`
- **Ruta:** `/meetings/{meetingId}/finish`
- **Acceso:** Requiere `ROLE_PROVIDER`

### 9.4. Webhook de Eventos LiveKit
- **Método:** `POST`
- **Ruta:** `/webhooks/livekit`
- **Acceso:** Público (Validado criptográficamente con `LIVEKIT_API_KEY` y `LIVEKIT_API_SECRET`)

---

## 10. Endpoints Administrativos (`/api/admin`)

Exclusivos para usuarios con rol `ROLE_ADMIN`.

- `GET /api/admin/users`: Listado de todos los usuarios registrados con estado y roles.
- `PATCH /api/admin/users/{id}/role`: Ascender o modificar rol de usuario.
- `GET /api/admin/offerings`: Auditoría y supervisión global del catálogo.
- `PATCH /api/admin/offerings/{id}/status`: Moderación y suspensión de servicios.
- `GET /api/admin/notifications`: Log unificado de notificaciones y eventos.

---

## 11. Observabilidad y Métricas (`/actuator`)

- `GET /actuator/health`: Estado de salud de componentes (PostgreSQL, Redis, RabbitMQ, espacio en disco).
- `GET /actuator/prometheus`: Scraping de métricas de Micrometer para Prometheus.
- `GET /actuator/metrics`: Exploración de métricas individuales de JVM, tiempos de respuesta HTTP y contadores.
