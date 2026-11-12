# HU-PS06 — Contrato técnico del Feature Store

## 1. Objetivo y alcance

Definir el contrato de sincronización de features de mentores y embeddings de servicios desde PostgreSQL hacia Redis, y la notificación de fin de batch hacia el backend. El job de PySpark que calcula embeddings y recomendaciones es una pieza aparte: este documento especifica la interfaz entre sus resultados persistidos en PostgreSQL y Redis/backend, no el algoritmo ni la implementación del job. Para esta HU se probará con datos sintéticos.

## 2. Fuente de verdad en PostgreSQL

La migración **V18** crea estas tablas:

- `feature_batches`: `batch_id`, `estado` (`RUNNING`, `COMPLETED` o `FAILED`), `mentores_cargados`, `embeddings_cargados`, `started_at` y `finished_at`. Registra el ciclo de vida y resultado de cada carga.
- `mentor_features`: `mentor_id`, cuatro scores numéricos normalizados entre 0 y 1 (`compatibility_score`, `expertise_score`, `availability_score`, `popularity_score`), `batch_id` y `updated_at`. Los scores son campos tipados, no JSONB; son valores genéricos de arranque y pueden ampliarse con una migración posterior.
- `service_embeddings`: `service_id`, embedding en `bytea` codificado como float32, `dimension` mayor que cero, `batch_id` y `updated_at`. La dimensión almacenada debe corresponder al número de componentes float32 del embedding.

PostgreSQL es la fuente de verdad y permite recuperar o reconstruir los datos. Redis es únicamente una caché de lectura; no reemplaza estas tablas.

La dimensión se configura mediante `EMBEDDING_DIMENSION`, con valor predeterminado **384**. Es un valor provisional: debe coincidir con la dimensión del modelo que use el job de PySpark; se cambia solo mediante la variable de entorno cuando el modelo esté definido. Antes de iniciar la carga, el loader debe fallar explícitamente si la variable no es un entero mayor que cero y rechazar la carga completa si cualquier fila tiene una dimensión distinta a la configurada.

## 3. Esquema de claves en Redis

Se usará el servicio compartido `redis` del Compose (`REDIS_URL`), **nunca** `redis-livekit`.

- `mentor:feat:{id}`: HASH con `compatibility_score`, `expertise_score`, `availability_score`, `popularity_score` y `batch_id`.
- `service:emb:{id}`: valor binario con los componentes float32 del embedding. Se prefiere binario sobre JSON porque ocupa aproximadamente cuatro veces menos que representar cada float como texto, lo que satisface la compresión indicada en CA 6.1. La dimensión y el `batch_id` se guardan en la clave auxiliar HASH `service:emb:{id}:meta`, con campos `dimension` y `batch_id`. La codificación float32 debe ser consistente entre productor y consumidor.
- `featurestore:last_batch`: HASH con `batch_id` y fecha de finalización del último batch cargado con éxito. Se escribe **solo al final**, después de completar todos los lotes.

## 4. Reglas de carga (CA 6.1 y 6.3)

- La carga es idempotente: usa las mismas claves deterministas y sobrescribe sus valores; repetir un batch no crea duplicados.
- Procesa registros en lotes de 500 mediante pipeline. El tamaño será configurable.
- No se permite `KEYS` ni `FLUSHALL`. Si se requiere recorrer claves, se utiliza `SCAN`.
- Incluye una pausa configurable entre lotes para limitar la presión sobre Redis.
- Si falla un lote, se reintenta según la política configurada. Si no se completa la carga, el batch no se marca como completado, no se actualiza `featurestore:last_batch` y no se publica el evento de éxito.
- La ejecución puede reintentarse completa sin duplicar registros. Solo después de que todos los lotes se escriban correctamente se actualizan el estado/fecha de fin del batch y `featurestore:last_batch`.

## 5. TTL

El TTL predeterminado es 72 horas, tres veces el intervalo predeterminado de 24 horas. Si se cambia el intervalo, se debe configurar `FEATURE_STORE_TTL_SECONDS` para mantener la relación 3×. Se aplica jitter independiente de ±10 % por clave para evitar expiraciones masivas. La carga exitosa renueva el TTL de cada clave escrita, incluidas las claves auxiliares de embeddings; `featurestore:last_batch` también debe tener expiración renovada para no quedar obsoleta indefinidamente.

## 6. Evento de fin de batch (CA 6.2)

Siguiendo el patrón de booking, se define:

- Exchange topic: `feature-store.events`
- Routing key: `features.updated`
- El loader de analytics declara únicamente el exchange topic durable. El backend declara la cola durable `features.updated.queue`, su DLX/DLQ y el binding de `features.updated.queue` a `feature-store.events` con la routing key `features.updated`. No declarar la cola ni el binding en el loader; ambos lados deben conservar estos nombres y parámetros.
- La publicación usa `RABBITMQ_URL`, mensaje persistente (`delivery_mode=2`), `content_type=application/json`, `message_id=batch_id` y publisher confirms. La publicación es obligatoria: el loader solo considera confirmado el mensaje cuando el broker confirma su recepción y la ruta existe.
- Se realizan hasta 3 intentos de publicación, con espera creciente (2 y 4 segundos entre intentos). Si todos fallan, se registra el error y el proceso termina con código 3; los datos en Redis y el estado PostgreSQL `COMPLETED` se conservan. Es una falla de notificación, no de carga, por lo que no se revierte ni se marca `FAILED`; el batch puede volver a ejecutarse y el consumidor debe tolerar entregas repetidas por `batch_id`.

El payload JSON contiene `batch_id`, `mentores_cargados`, `embeddings_cargados`, `embeddings_rechazados`, `started_at` y `finished_at`. Las fechas son ISO-8601 en UTC. El evento se publica únicamente después de escribir todos los lotes, actualizar `featurestore:last_batch` y cambiar `feature_batches` a `COMPLETED`; nunca se publica para un batch `FAILED`. El consumidor del backend debe ser idempotente por `batch_id` para tolerar entregas repetidas.

## 7. Lectura en el backend

La aplicación define el puerto de salida `FeatureStorePort` en `application/port/out` y su adaptador Redis en `infrastructure/adapter/out/cache`. En una lectura, el adaptador consulta Redis; si la clave no existe, recupera el registro desde PostgreSQL. Para recomendaciones, si no hay resultado precalculado aplicable, se conserva como fallback el flujo actual con Gemini.

Se registran métricas Micrometer de hit/miss y latencia de lectura. La petición HTTP solo consulta resultados existentes: no ejecuta cómputo pesado ni espera a que se ejecute un batch.

## 8. Configuración y operación

Variables de entorno del contrato:

| Variable | Uso | Valor predeterminado |
|---|---|---|
| `REDIS_URL` | Conexión al Redis compartido `redis` | `redis://redis:6379` en Compose |
| `RABBITMQ_URL` | Conexión a RabbitMQ para publicar/consumir el evento | `amqp://guest:guest@rabbitmq:5672/%2F` |
| `FEATURE_STORE_BATCH_INTERVAL_HOURS` | Intervalo entre batches y base para derivar TTL | `24` |
| `FEATURE_STORE_TTL_SECONDS` | TTL explícito; mantenerlo en 3× el intervalo configurado | `259200` (72 h para el intervalo predeterminado de 24 h) |
| `FEATURE_STORE_BATCH_SIZE` | Registros por lote | `500` |
| `FEATURE_STORE_BATCH_PAUSE_MS` | Pausa entre lotes | `50` ms |
| `EMBEDDING_DIMENSION` | Dimensión esperada del embedding; provisional hasta definir el modelo de PySpark | `384` |

Para el Redis compartido se recomienda configurar posteriormente `maxmemory` con política `volatile-lru`, dado que todas las claves del Feature Store tienen TTL. La configuración actual de `redis` no establece límite de memoria ni política de evicción y habilita AOF: debe vigilarse el crecimiento de memoria y del archivo de persistencia. Estos cambios operativos quedan para un paso posterior; no se modifica aquí el Compose.

## 9. Criterios de verificación

- **CA 6.1 — Idempotencia, TTL y tamaño:** cargar el mismo conjunto sintético dos veces; comprobar que las claves y valores finales no se duplican, que el TTL se aplica y se renueva (incluido el jitter), y comparar el tamaño almacenado de embeddings binarios float32 frente a su representación JSON para confirmar la reducción aproximada de 4×. Verificar además que una dimensión no entera/no positiva en configuración o una fila con dimensión distinta a `EMBEDDING_DIMENSION` falla explícitamente antes de escribir datos.
- **CA 6.2 — Evento:** completar una carga y verificar que el consumidor recibe el evento con los campos y conteos esperados. Reprocesar el mismo `batch_id` y confirmar que el consumidor no duplica efectos.
- **CA 6.3 — Latencia:** medir en Grafana el p95 del endpoint de recomendaciones bajo la misma carga y condiciones antes, durante y después de la sincronización. Comparar las mediciones para verificar que el batch no causa una degradación significativa y que la petición no ejecuta cómputo pesado.
