# AGENTS.md

Este archivo contiene las directrices, el contexto y las reglas que los agentes de inteligencia artificial deben seguir al interactuar y modificar el código de **SkillBridge AI**.

## 1. Contexto del Proyecto
**SkillBridge AI** es una plataforma de mentorías y servicios profesionales diseñada como proyecto integrador.
- **Backend:** Java 21, Spring Boot 3.5.x, Spring Security, Spring Data JPA/Redis/AMQP, Flyway.
- **Frontend:** Angular 20 (Standalone Components), Nginx.
- **Infraestructura:** Docker Compose, PostgreSQL 17, Redis 8, RabbitMQ 4, Prometheus, Grafana, IA Gemini API.

## 2. Arquitectura y Patrones (OBLIGATORIO)
El backend implementa **Arquitectura Hexagonal (Ports & Adapters)** y sigue una estricta regla de dependencia:

- **`domain`**: Contiene modelos y excepciones de negocio. No debe saber que existen PostgreSQL, JPA, Redis, RabbitMQ, Gemini, HTTP o Angular.
- **`application`**: Contiene los casos de uso (services) y los puertos de entrada (`in`) y salida (`out`). 
- **`infrastructure`**: Contiene la implementación de los puertos (adaptadores `in` y `out`), la configuración de Spring y seguridad.

**Patrones Activos:**
- Cache-Aside (Redis) para lecturas (ej. catálogo público).
- Eventos Asíncronos (RabbitMQ) con Retry y Dead Letter Queue (DLQ).

## 3. Reglas de Desarrollo para Agentes
1. **Respeta la Regla de Dependencia:** Cualquier integración de base de datos, sistema externo o caché DEBE hacerse a través de una interfaz (puerto) en la capa de aplicación, cuya implementación viva en infraestructura.
2. **Cero Secretos en el Frontend:** NUNCA filtres la `GEMINI_API_KEY` o el `JWT_SECRET` hacia el código de Angular. Las llamadas a IA deben pasar por el backend.
3. **Migraciones de Base de Datos:** Si se modifica el esquema, DEBES crear una nueva migración inmutable de Flyway en `backend/src/main/resources/db/migration`.
4. **Registro Continuo:** Cada tarea completada por el agente debe ser registrada y descrita en `MEMORIA.md`.

