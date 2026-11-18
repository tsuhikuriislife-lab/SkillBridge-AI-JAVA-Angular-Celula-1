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
- `service:emb:{id}`: valor binario con los componentes float32 del embedding. Se prefiere binario sobre JSON porque ocupa aproximadamente cuatro veces menos que representar cada float como texto, lo que satisface la compresión indicada en CA 6.1. La dimensión y el `batch_id` se guardan en la clave auxiliar HASH `service:emb:{id}:meta`, con campos `dimension` y `batch_id`. Los bytes son little-endian: el loader conserva los bytes de PostgreSQL, que el generador Python produce con `struct.pack("<...f")`; los consumidores deben decodificar cada float32 en ese orden.
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
- El loader de analytics declara únicamente el exchange topic durable (`durable=true`, `auto_delete=false`). El backend declara la cola durable `features.updated.queue`, el exchange DLX durable `feature-store.dlx`, la DLQ durable `features.updated.dlq` y el binding de la cola a `feature-store.events` con la routing key `features.updated`. La cola principal enruta mensajes rechazados al DLX con la clave `features.updated.failed`, enlazada a la DLQ. No declarar la cola ni el binding en el loader; ambos lados deben conservar estos nombres y parámetros.
- La publicación usa `RABBITMQ_URL`, mensaje persistente (`delivery_mode=2`), `content_type=application/json`, `message_id=batch_id` y publisher confirms. La publicación es obligatoria: el loader solo considera confirmado el mensaje cuando el broker confirma su recepción y la ruta existe.
- El listener del backend aplica la configuración existente `spring.rabbitmq.listener.simple.retry`: hasta 3 intentos con intervalo inicial de 2 segundos y `default-requeue-rejected=false`. Los mensajes malformados o inválidos se rechazan sin reencolar y llegan directamente a la DLQ; otros errores agotarán los reintentos y se enviarán allí.
- Se realizan hasta 3 intentos de publicación, con espera creciente (2 y 4 segundos entre intentos). Si todos fallan, se registra el error y el proceso termina con código 3; los datos en Redis y el estado PostgreSQL `COMPLETED` se conservan. Es una falla de notificación, no de carga, por lo que no se revierte ni se marca `FAILED`; el batch puede volver a ejecutarse y el consumidor debe tolerar entregas repetidas por `batch_id`.

El payload JSON contiene `batch_id`, `mentores_cargados`, `embeddings_cargados`, `embeddings_rechazados`, `started_at` y `finished_at`. Las fechas son ISO-8601 en UTC. El evento se publica únicamente después de escribir todos los lotes, actualizar `featurestore:last_batch` y cambiar `feature_batches` a `COMPLETED`; nunca se publica para un batch `FAILED`. El consumidor del backend debe ser idempotente por `batch_id` para tolerar entregas repetidas.

## 7. Lectura en el backend

La aplicación define el puerto de salida `FeatureStorePort` en `application/port/out` y su adaptador Redis en `infrastructure/adapter/out/cache`; el puerto `FeatureSourcePort` y su adaptador JPA de solo lectura acceden a las tablas fuente de PostgreSQL. En una lectura, la aplicación consulta Redis primero y, si la clave falta o Redis falla, recupera el registro desde PostgreSQL. El backend no escribe ni renueva las claves del Feature Store; el loader es el único escritor.

Se registran métricas Micrometer de hit/miss, errores Redis, uso del fallback PostgreSQL y latencia de lectura. El listener convierte el evento a un modelo de aplicación y delega en `HandleFeaturesUpdatedUseCase`; el caso de uso ignora `batch_id` repetidos durante la vida del proceso y registra el último batch recibido y su antigüedad. La petición HTTP solo consulta resultados existentes: no ejecuta cómputo pesado ni espera a que se ejecute un batch.

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
- **CA 6.3 — Latencia:** medir el p95 de una ruta pública representativa bajo la misma carga y condiciones antes, durante y después de la sincronización. Comparar las mediciones para verificar que el batch no causa una degradación significativa y que la petición no ejecuta cómputo pesado.

## 10. Verificación de CA 6.3

### Método y alcance

- Base local de desarrollo `skillbridge`, con conteos iniciales de 4 `app_users`, 1 `PROVIDER` y 3 `services`.
- `seed_feature_data.py --load-test 10000` creó 10.000 usuarios `PROVIDER` y 10.000 servicios, cada uno vinculado por FK. Los emails usan `@users.invalid`, y tanto usuarios como servicios quedaron `INACTIVE`. Cada usuario recibió cuatro scores aleatorios y cada servicio un embedding float32 little-endian de 384 dimensiones. La categoría se tomó de un servicio existente y la contraseña es un hash ficticio no apto para autenticación.
- El batch fue `42228152-53bf-4b51-bba7-fea6fbf5f30c`. El loader se ejecutó tres veces sobre el mismo batch, idempotentemente, con duraciones de 24,88 s, 24,98 s y 24,37 s; así cubrió la ventana de carga, con aproximadamente 1,0 s entre ejecuciones. No se cambiaron `FEATURE_STORE_BATCH_SIZE` ni la pausa porque se cumplió el criterio.
- k6 usó tasa constante durante 60 s: 20 solicitudes/s a cada ruta (40/s total), sin VUs descartados. La ruta real pública del catálogo es `GET /api/services`; el repositorio no expone `GET /api/offerings` (esa ruta respondió 401 por la política de seguridad). También se midió `GET /actuator/health`.
- El código actual del catálogo consulta PostgreSQL directamente: no encontré Cache-Aside ni uso de Redis en esa ruta. Por eso esta prueba mide el impacto de escribir en el Redis compartido sobre la disponibilidad/latencia del backend, no el camino de lectura de un caché del catálogo. Además, todavía no existe un endpoint que consuma las features del Feature Store; no se modificó `AiRecommendationService` en esta HU.
- La fase A se ejecutó antes de la carga, pero no se hizo calentamiento controlado de JVM/conexiones ni repetición de baseline; pudo incluir efectos de cachés frías. La comparación debe leerse como ausencia de degradación medible en la muestra, no como mejora atribuible a la carga.

### Resultados k6

Hora local de Windows (UTC-05:00). Las cifras de latencia son milisegundos; cada celda de error muestra tasa y solicitudes fallidas/total.

| Fase | Ventana local | Ruta | p50 | p95 | p99 | Errores | Solicitudes |
|---|---|---|---:|---:|---:|---:|---:|
| A — base | 01:20:14.287–01:21:15.558 | `/api/services` | 9,38 | 14,53 | 21,59 | 0% (0/1201) | 1201 |
| A — base | 01:20:14.287–01:21:15.558 | `/actuator/health` | 4,17 | 8,01 | 14,12 | 0% (0/1201) | 1201 |
| B — loader | 01:22:08.849–01:23:10.097 | `/api/services` | 6,52 | 12,46 | 20,96 | 0% (0/1201) | 1201 |
| B — loader | 01:22:08.849–01:23:10.097 | `/actuator/health` | 3,50 | 7,52 | 11,66 | 0% (0/1201) | 1201 |
| C — posterior | 01:25:22.715–01:26:24.270 | `/api/services` | 8,18 | 10,19 | 16,27 | 0% (0/1201) | 1201 |
| C — posterior | 01:25:22.715–01:26:24.270 | `/actuator/health` | 4,05 | 5,37 | 8,04 | 0% (0/1201) | 1201 |

Para el catálogo, el límite de aceptación de p95 fue `max(14,53 ms × 1,20, 14,53 ms + 5 ms) = 19,53 ms`; la fase B midió 12,46 ms y cero errores. En estas tres fases no se midió degradación del p95 bajo el umbral; el valor inferior durante B no se interpreta como una mejora causada por la carga. El health también mantuvo cero errores. El backend permaneció healthy. El objetivo funcional de latencia menor de 20 ms para una petición que realmente consuma el Feature Store queda pendiente hasta integrar su lectura con `AiRecommendationService` y medir ese flujo.

Durante B, `docker stats` recogió 25 muestras: CPU media 27,53%, máximo 71,84%; memoria del contenedor media 697,6 MiB, máximo 713,6 MiB. `redis-cli --latency-history -i 1` registró en su primera ventana de 60 s mínimo 0,158 ms, máximo 0,725 ms y promedio 0,282 ms.

### Uso de memoria y propuesta operativa

| Estado | `used_memory` de Redis |
|---|---:|
| Antes de insertar el Feature Store | 1,59 MiB |
| Con 10.000 mentores y 10.000 embeddings | 25,05 MiB (pico 25,63 MiB) |
| Tras cleanup | 1,79 MiB |

En claves representativas, `MEMORY USAGE` midió 286 bytes por HASH de mentor; 1.869 bytes para la clave binaria del servicio y 151 bytes para su HASH `:meta` (1.536 bytes de embedding). Estimación por servicio con metadatos: 2.020 bytes; por pareja mentor+servicio: unos 2.306 bytes, antes del overhead global del proceso. El incremento observado del Redis completo fue aproximadamente 23,5 MiB para 10.000 parejas.

Se propone al equipo decidir si se configura `maxmemory 512mb` con política `volatile-lru` para el Redis compartido: el Feature Store tiene TTL y la cifra deja margen respecto a la extrapolación lineal de 10.000 parejas. Es una recomendación pendiente de decisión, no un cambio aprobado. No se aplicó ningún cambio a `docker-compose.yml`; con `volatile-lru`, las claves sin TTL no serían elegibles para evicción y deben considerarse al fijar el límite final.

### Evidencia Prometheus/Grafana

Ventanas absolutas para seleccionar en Grafana:

- Base: `2026-11-12 01:20:14.287`–`01:21:15.558 -05:00`
- Carga: `2026-11-12 01:22:08.849`–`01:23:10.097 -05:00`
- Posterior: `2026-11-12 01:25:22.715`–`01:26:24.270 -05:00`

Consulta PromQL para graficar p95 del catálogo en cada ventana:

```promql
histogram_quantile(
  0.95,
  sum by (le) (
    rate(http_server_requests_seconds_bucket{
      application="skillbridge-api",
      uri="/api/services",
      method="GET",
      status="200"
    }[1m])
  )
)
```

No hay captura de Grafana de esta prueba. En esta ejecución, `/actuator/prometheus` expuso `_count` y `_sum`, pero no `http_server_requests_seconds_bucket`; por ello esta consulta aún no devuelve p95 en Prometheus. Tampoco estaban levantados los servicios opcionales Prometheus/Grafana. Los percentiles reportados arriba proceden de k6 y sí son mediciones reales. La primera fase k6 imprimió su resumen estándar válido (incluidos contadores y percentiles), aunque el formateador JSON opcional falló al consultar un contador de iteraciones descartadas que no se emitió; las fases B y C produjeron el resumen JSON. Para capturar la gráfica con la consulta anterior hay que habilitar el histograma de `http.server.requests` y arrancar el perfil `observability`; esos cambios no se hicieron en esta etapa.

### Limpieza y veredicto

`--cleanup --batch-id 42228152-53bf-4b51-bba7-fea6fbf5f30c` eliminó 10.000 usuarios, 10.000 servicios, 10.000 features de mentores, 10.000 embeddings, un batch y 30.001 claves Redis. Después se verificaron de nuevo los conteos originales (4 usuarios, 1 proveedor y 3 servicios), cero filas de features/batches de prueba, cero claves `mentor:feat:*`/`service:emb:*`, ausencia de `featurestore:last_batch` y colas RabbitMQ vacías. En el endpoint medido se observó cero error y sin degradación medible del p95 durante la carga; la conclusión no valida aún el flujo de recomendación que consuma el Feature Store. Quedan pendientes integrar su lectura con `AiRecommendationService`, medir ese endpoint para cerrar el objetivo de <20 ms, y habilitar histogramas/Grafana si se necesita evidencia Prometheus.
