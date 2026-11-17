# Catálogo de Historias de Usuario (US)

Este directorio centraliza la especificación técnica y trazabilidad de todas las historias de usuario y tareas de ingeniería desarrolladas en **SkillBridge AI**.

---

## 1. Reglas de Documentación

Conforme a las directrices de `AGENTS.md`, cada documento debe cumplir estrictamente con los siguientes estándares:

1. **Nomenclatura de Archivo:**
   `US-<CODIGO>-<descripcion-corta>.md` (ej. `US-PS06-feature-store.md`).

2. **Campo de Estado Obligatorio:**
   Cada historia debe comenzar con uno de los siguientes estados:
   - `[ ] Por hacer`
   - `[🔄] En curso`
   - `[👀] En revisión`
   - `[✅] Terminada`

3. **Profundidad y Enfoque Técnico:**
   - Detallar la solución arquitectónica implementada (patrones hexagonales, puertos, adaptadores, eventos de dominio, consistencia de datos).
   - Indicar contratos de API, payloads de mensajería y estrategias de resiliencia (reintentos, DLQ, fallbacks).

4. **Prohibición de Pegado de Código Fuente Masivo:**
   - **NO** pegar bloques extensos de código.
   - Toda mención al código debe utilizar referencias puntuales relativas con rango de líneas exacto:
     > *(Ej: `Ver backend/src/main/java/com/riwi/skillbridge/application/service/FeatureStoreService.java línea 32:56`)*.

---

## 2. Índice de Historias de Usuario

### 2.1. Funcionalidades en Producción (Terminadas)

| Código | Título | Estado | Módulo Principal | Documento |
|---|---|---|---|---|
| **US-01** | Catálogo Público de Servicios con Cache-Aside en Redis | `[✅] Terminada` | Core / Backend | [`US-01-catalogo-publico-redis.md`](US-01-catalogo-publico-redis.md) |
| **US-02** | Autenticación Stateless, Cookies HttpOnly y CSRF | `[✅] Terminada` | Seguridad / Auth | [`US-02-autenticacion-httponly-csrf.md`](US-02-autenticacion-httponly-csrf.md) |
| **US-03** | Gestión de Reservas y Desacoplamiento EDA con RabbitMQ | `[✅] Terminada` | Core / EDA | [`US-03-reservas-eventos-amqp.md`](US-03-reservas-eventos-amqp.md) |
| **US-05** | Asistente de Recomendaciones Semánticas con Gemini | `[✅] Terminada` | Spring AI / Gemini | [`US-05-recomendaciones-gemini-spring-ai.md`](US-05-recomendaciones-gemini-spring-ai.md) |
| **US-06** | Diagnóstico Técnico Previo con IA y Prerrequisitos | `[✅] Terminada` | IA / Assessment | [`US-06-diagnostico-previo-assessments.md`](US-06-diagnostico-previo-assessments.md) |
| **US-10** | Gestión de Ofertas y Disponibilidad por Proveedores | `[✅] Terminada` | Proveedores / Agenda | [`US-10-gestion-ofertas-proveedor.md`](US-10-gestion-ofertas-proveedor.md) |
| **US-11** | Historial de Reservas y Notificaciones In-App | `[✅] Terminada` | Historial / In-App | [`US-11-historial-reservas-notificaciones.md`](US-11-historial-reservas-notificaciones.md) |
| **US-29.11**| Videoconferencias en Tiempo Real con LiveKit | `[✅] Terminada` | Backend / Frontend | [`US-29.11-videoconferencias-livekit.md`](US-29.11-videoconferencias-livekit.md) |
| **US-PS02** | Pipeline ETL de Extracción y Limpieza de Catálogo | `[✅] Terminada` | Analytics / PySpark | [`US-PS02-etl-catalogo.md`](US-PS02-etl-catalogo.md) |
| **US-PS06** | Feature Store con Redis y Fallback Resiliente PostgreSQL | `[✅] Terminada` | Backend / Analytics | [`US-PS06-feature-store.md`](US-PS06-feature-store.md) |

### 2.2. Historias Técnicas de Mitigación y Auditoría (Por Hacer)

| Código | Título | Estado | Asignado | Módulo | Documento |
|---|---|---|---|---|---|
| **US-INFRA-01** | Conflicto Puertos Spark Worker y Perfiles Compose | `[ ] Por hacer` | `@deleonguillermo87` | Infraestructura | [`US-INFRA-01-resolucion-conflicto-puertos-spark-compose.md`](US-INFRA-01-resolucion-conflicto-puertos-spark-compose.md) |
| **US-ANL-01** | Dependencias (`tiktoken`) y Suite Tests Analytics | `[ ] Por hacer` | `@DylanSrz` | Analytics / PySpark | [`US-ANL-01-estabilizacion-dependencias-tests-analytics.md`](US-ANL-01-estabilizacion-dependencias-tests-analytics.md) |
| **US-FE-01** | Fix Tests DisplayBookings y Spec AdminDashboard | `[ ] Por hacer` | `@KevinMercado1` | Frontend / Angular | [`US-FE-01-correccion-tests-display-bookings-admin-dashboard.md`](US-FE-01-correccion-tests-display-bookings-admin-dashboard.md) |
| **US-FE-02** | Cobertura Tests Componentes LiveKit y Checkout | `[ ] Por hacer` | `@juanvargasviloria2507-gif`| Frontend / Testing | [`US-FE-02-cobertura-componentes-criticos-livekit-checkout.md`](US-FE-02-cobertura-componentes-criticos-livekit-checkout.md) |
| **US-FE-03** | Cobertura Tests Guards y AuthInterceptor | `[ ] Por hacer` | `@andresmbd` | Frontend / Security | [`US-FE-03-cobertura-seguridad-guards-auth-interceptor.md`](US-FE-03-cobertura-seguridad-guards-auth-interceptor.md) |
| **US-BE-01** | Cobertura MockMvc en Controladores Inbound REST | `[ ] Por hacer` | `@JesusLucena-png` | Backend / Testing | [`US-BE-01-cobertura-mockmvc-controllers-inbound.md`](US-BE-01-cobertura-mockmvc-controllers-inbound.md) |
| **US-BE-02** | Cobertura Servicios Aplicación y Adaptadores JPA | `[ ] Por hacer` | `@Va-cod` | Backend / Testing | [`US-BE-02-cobertura-casos-de-uso-adapters-outbound.md`](US-BE-02-cobertura-casos-de-uso-adapters-outbound.md) |
| **US-BE-03** | Saneamiento Configuración Security UserDetailsService | `[ ] Por hacer` | `@YamitGC` | Backend / Security | [`US-BE-03-saneamiento-security-userdetailsservice.md`](US-BE-03-saneamiento-security-userdetailsservice.md) |
| **US-DOCS-01** | Actualización Contrato API.md Cookies HttpOnly / CSRF | `[ ] Por hacer` | `@tsuhikuriislife-lab` | Documentación / API | [`US-DOCS-01-actualizacion-contrato-api-rest-httponly.md`](US-DOCS-01-actualizacion-contrato-api-rest-httponly.md) |
| **US-DOCS-02** | Unificación Modelo Oficial Gemini 3.8 Flash | `[ ] Por hacer` | `@leonardo-200224` | Configuración / IA | [`US-DOCS-02-unificacion-especificaciones-modelos-ia.md`](US-DOCS-02-unificacion-especificaciones-modelos-ia.md) |
| **US-DOCS-03** | Creación y Estandarización de Fichas Base HU-01..11 | `[ ] Por hacer` | `@deleonguillermo87` | Trazabilidad / Docs | [`US-DOCS-03-catalogo-user-stories-funcionalidades-base.md`](US-DOCS-03-catalogo-user-stories-funcionalidades-base.md) |
