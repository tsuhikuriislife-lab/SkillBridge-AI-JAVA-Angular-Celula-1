# US-ANL-01: Estabilización de Dependencias y Suite de Tests en Analytics

**Estado:** `[👀] En revisión`

---

## 1. Descripción y Objetivo de Ingeniería
Subsanar la ausencia de la biblioteca `tiktoken` en el entorno de Analytics que causa `ModuleNotFoundError` en `text_chunker.py`. Transformar los scripts de prueba informales en suites automatizadas con `pytest` y validar la consistencia de las versiones de Spark y conectores JDBC.

---

## 2. Criterios de Aceptación y Arquitectura
1. **Gestión de Dependencias:** Añadir `tiktoken` con versión anclada a `analytics-service/requirements.txt` y asegurar su instalación en `analytics-service/Dockerfile`.
2. **Suite Unitaria Formal:** Convertir `analytics-service/tests/test_catalog_chunking.py` en una prueba unitaria estructurada con aserciones `assert` nativas de pytest.
3. **Configuración de Pytest:** Crear `analytics-service/pytest.ini` configurando el descubrimiento de pruebas y paths de importación para ejecución headless.

---

## 3. Solución Técnica Implementada

### 3.1. Dependencia `tiktoken` anclada a la imagen de Spark
- La imagen base `apache/spark:3.5.1-python3` trae **Python 3.8.10**. Desde `tiktoken 0.8.0` la librería exige Python ≥ 3.9, por eso se ancla **`tiktoken==0.7.0`**, la última versión con wheel `cp38` para `manylinux x86_64`.
  *(Ver `analytics-service/requirements.txt` línea 6:7)*.
- `tiktoken` descarga la codificación `cl100k_base` en el primer uso. El Dockerfile la descarga durante el build en `TIKTOKEN_CACHE_DIR=/opt/tiktoken-cache` (propiedad del usuario `spark`), así el chunker y los tests funcionan sin acceso a internet en tiempo de ejecución.
  *(Ver `analytics-service/Dockerfile` línea 13:17)*.

### 3.2. Configuración de pytest headless
- `pytest.ini` define `testpaths = tests` y `pythonpath = jobs .`, el mismo orden que `PYTHONPATH=/app/jobs:/app` en `docker-compose.yml`. Desactiva el plugin de caché porque `/app` no es escribible por el usuario `spark` dentro del contenedor.
  *(Ver `analytics-service/pytest.ini` línea 1:12)*.
- El Dockerfile copia `pytest.ini` a `/app`; los tests se montan como volumen en `spark-master` y `spark-worker`.
  *(Ver `analytics-service/Dockerfile` línea 19:20)*.

### 3.3. Suite unitaria del chunker semántico
`test_catalog_chunking.py` deja de ser un script con `print` que se ejecutaba al importarse y pasa a ser una suite de 18 casos con `assert` nativos y una fixture `catalog_clean` con los registros simulados de HU-PS02. Importa `text_chunker` igual que el resto de suites (`from text_chunker import ...`).
*(Ver `analytics-service/tests/test_catalog_chunking.py` línea 1:188)*.

| Bloque | Qué valida | Referencia |
|---|---|---|
| `count_tokens` | Texto vacío/`None` → 0 tokens; texto real → > 0 | líneas 71:77 |
| `sliding_window_chunk` | Texto en blanco → `[]`; texto corto → 1 chunk idéntico; texto largo → número de ventanas esperado, índices consecutivos, ventanas llenas de 512 tokens y solapamiento real; `overlap=0` cubre cada token una sola vez; `ValueError` con `chunk_size`/`overlap` inválidos | líneas 84:133 |
| `process_service_record` | Omite campos vacíos, conserva metadatos de trazabilidad (`service_id`, `source_field`, `chunk_index`, `token_count`) y reinicia `chunk_index` por campo | líneas 140:174 |
| `process_catalog` | Total de chunks por servicio y catálogo vacío | líneas 177:188 |

### 3.4. Consistencia de versiones Spark / JDBC
| Componente | Versión | Fuente |
|---|---|---|
| Apache Spark (imagen) | 3.5.1 | `analytics-service/Dockerfile` línea 1 |
| PySpark | 3.5.1 | `analytics-service/requirements.txt` línea 1 |
| Driver PostgreSQL JDBC | 42.7.3 | `analytics-service/Dockerfile` línea 6 |
| Python (imagen) | 3.8.10 | `apache/spark:3.5.1-python3` |

La versión de PySpark coincide con la del clúster. `STATE.md` decía Python 3.11 / Spark 3.5.6 y se corrigió.

---

## 4. Verificación
Ejecución dentro de la imagen, sin red (valida la caché de `tiktoken`):

```bash
docker build -t skillbridge-analytics analytics-service
docker run --rm --network none --hostname localhost -v "$PWD/analytics-service/tests:/app/tests" -w /app --entrypoint python3 skillbridge-analytics -m pytest
```

Resultado: **37 passed** (18 de chunking, 5 de limpieza, 9 de embeddings y 5 de feedback analytics). Con el stack levantado también funciona `docker compose exec spark-master python3 -m pytest`.
