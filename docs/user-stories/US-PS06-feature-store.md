# US-PS06: Feature Store con Redis y Fallback Resiliente en PostgreSQL

**Estado:** `[✅] Terminada`

---

## 1. Descripción y Objetivo de Ingeniería

Implementar el subsistema de **Feature Store** de baja latencia para recomendaciones de mentores y similitud de servicios mediante embeddings vectoriales. El sistema debe desacoplar el procesamiento por lotes (batch) en Python/Analytics del servicio transaccional en Spring Boot, garantizando lecturas sub-milisegundo en Redis con fallback transparente hacia PostgreSQL ante fallos de conexión o cold-start de caché.

---

## 2. Arquitectura de la Solución (Hexagonal & EDA)

El flujo implementa una arquitectura dirigida por eventos (EDA) y el patrón Cache-Aside con degradación grácil:

1. **Ingesta Batch (Analytics):**
   - Un worker batch ejecuta la carga masiva de métricas calculadas y vectores hacia Redis (`mentor:feat:<uuid>` como Hash y `service:emb:<uuid>` como Binary Blob Float32 Little-Endian).
   - Al finalizar el lote, publica el evento de integración `features.updated` hacia el exchange `feature-store.events` de RabbitMQ.
2. **Recepción de Eventos (Backend Inbound):**
   - El adaptador `FeaturesUpdatedListener` consume desde la cola `features.updated.queue`.
   - Si el payload es corrupto o inválido, lanza `AmqpRejectAndDontRequeueException` para enrutar el mensaje directamente a la Dead Letter Queue (DLQ) sin causar ciclos infinitos de reintento.
3. **Consulta en Dos Niveles (Application Service):**
   - `FeatureStoreService` coordina la lectura consultando primero el puerto de salida `FeatureStorePort` (Redis).
   - Si Redis arroja una excepción de conectividad o no contiene el registro, conmuta automáticamente al puerto `FeatureSourcePort` (PostgreSQL relacional).
   - Todas las métricas de acierto/fallo y latencia se despachan al puerto `FeatureStoreMetricsPort` (Micrometer/Prometheus).

---

## 3. Componentes Técnicos y Referencias de Implementación

### 3.1. Migración y Esquema Relacional
- Definición de tablas `mentor_features` y `service_embeddings` para persistencia duradera y fallback.
- *(Ver `backend/src/main/resources/db/migration/V18__feature_store.sql` línea 1:65)*.

### 3.2. Casos de Uso y Lógica de Aplicación
- Servicio de consulta orquestador con try/catch de contingencia hacia PostgreSQL y medición de tiempos de ejecución por fuente de datos.
- *(Ver `backend/src/main/java/com/riwi/skillbridge/application/service/FeatureStoreService.java` línea 32:57)* para el flujo de mentores.
- *(Ver `backend/src/main/java/com/riwi/skillbridge/application/service/FeatureStoreService.java` línea 59:84)* para el flujo de embeddings de servicios.
- Definición del evento de dominio deserializado:
- *(Ver `backend/src/main/java/com/riwi/skillbridge/application/model/FeaturesUpdatedEvent.java` línea 1:25)*.

### 3.3. Adaptadores de Entrada (Inbound Messaging)
- Consumidor RabbitMQ con validación estricta de estructura JSON y descarte controlado:
- *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/FeaturesUpdatedListener.java` línea 31:42)*.
- Validación de campos requeridos y tipos numéricos:
- *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/FeaturesUpdatedListener.java` línea 43:71)*.

### 3.4. Adaptadores de Salida (Outbound Cache & Storage)
- Adaptador Redis que decodifica hashes de scores y transforma bytes binarios Little-Endian en arrays `float[]`:
- *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/cache/RedisFeatureStoreAdapter.java` línea 30:45)* para lectura de hashes de mentores.
- *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/cache/RedisFeatureStoreAdapter.java` línea 47:65)* para deserialización de vectores binarios.
- Adaptador de métricas Micrometer con tags dinámicos de entidad y origen:
- *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/metrics/MicrometerFeatureStoreMetricsAdapter.java` línea 20:65)*.

### 3.5. Pipeline Batch de Carga y Publicación (Python / Analytics)
- Loader masivo con pipeline de Redis y cliente Pika para RabbitMQ:
- *(Ver `analytics-service/jobs/redis_batch_loader.py` línea 20:50)*.
- Script de generación sintética de características y embeddings:
- *(Ver `analytics-service/jobs/seed_feature_data.py` línea 1:60)*.

---

## 4. Validación y Criterios de Aceptación

- **CA 6.1 (Consistencia de Lectura):** Verificada con `RedisFeatureStoreAdapterTest` comprobando deserialización de 384 dimensiones flotantes.
- **CA 6.2 (Fallback Transparente):** Verificada con `FeatureStoreFallbackComposeIntegrationTest` deteniendo el servicio Redis y validando respuesta íntegra desde PostgreSQL.
- **CA 6.3 (Tolerancia a Fallos y DLQ):** Verificada en `FeaturesUpdatedListenerTest` garantizando descarte hacia DLQ ante mensajes JSON malformados.
