# US-PS02: Pipeline ETL de Extracción, Limpieza y Normalización del Catálogo

**Estado:** `[✅] Terminada`

---

## 1. Descripción y Objetivo de Ingeniería

Diseñar e implementar un pipeline de procesamiento por lotes (Batch ETL) utilizando **Apache Spark y PySpark** para extraer la información de servicios, categorías y mentores desde PostgreSQL, sanitizar y normalizar los textos descriptivos eliminando código malicioso/etiquetas HTML/Markdown, y exportar los datos limpios en almacenamiento columnar analítico particionado (**Parquet**).

---

## 2. Arquitectura de la Solución (Data Engineering & Analytics)

El pipeline opera en el contenedor PySpark del clúster analítico (`analytics-service` / `data-engine`):

1. **Extracción Particionada JDBC:**
   - Para evitar bloqueos de tablas y cuellos de botella en PostgreSQL, la consulta JDBC utiliza particionamiento numérico basado en la función hash nativa de PostgreSQL (`abs(hashtext(id::text)) % num_partitions`).
   - Permite lecturas distribuidas y concurrentes entre múltiples ejecutores de Spark.
2. **Transformación y Normalización de Texto Nativa:**
   - La limpieza de texto se ejecuta 100% mediante expresiones nativas de `pyspark.sql.functions` para operar dentro del motor optimizado JVM/Catalyst sin incurrir en penalizaciones de rendimiento por Python UDFs.
   - Proceso de limpieza:
     - Remoción de tags HTML y entidades codificadas (`&nbsp;`, `&amp;`, etc.).
     - Remoción de sintaxis Markdown (encabezados, enlaces, énfasis, bloques de código).
     - Filtrado de caracteres de control ASCII (`[\x00-\x1F\x7F-\x9F]`).
     - Normalización de espacios en blanco y coalescencia de nulos.
3. **Validación de Esquema y Persistencia Columnar:**
   - Validación estricta del DataFrame transformado contra el esquema canónico `CATALOG_CLEAN_SCHEMA`.
   - Escritura particionada por `category_name` en `/data/processed/catalog_clean.parquet` con compresión Snappy.

---

## 3. Componentes Técnicos y Referencias de Implementación

### 3.1. Esquema Canónico y Constantes de Partición
- Definición de tipos de datos estrictos (`StructType`, `StructField`, `StringType`, `DecimalType`):
- *(Ver `analytics-service/jobs/extract_and_clean_catalog.py` línea 21:40)*.

### 3.2. Extracción Paralela vía JDBC
- Función que construye las particiones dinámicas y los límites inferiores/superiores para lectura balanceada en PostgreSQL:
- *(Ver `analytics-service/jobs/extract_and_clean_catalog.py` línea 59:75)*.

### 3.3. Funciones de Transformación Puras
- Módulo de funciones nativas de limpieza de cadenas:
  - Expresión regular para eliminación de HTML: *(Ver `analytics-service/jobs/cleaning_transforms.py` línea 11:18)*.
  - Expresiones para eliminación de sintaxis Markdown: *(Ver `analytics-service/jobs/cleaning_transforms.py` línea 20:43)*.
  - Filtro de caracteres de control y normalización Unicode: *(Ver `analytics-service/jobs/cleaning_transforms.py` línea 45:63)*.
  - Composición de transformaciones y sustitución de nulos: *(Ver `analytics-service/jobs/cleaning_transforms.py` línea 65:92)*.
- Validador estructural del DataFrame resultante contra el contrato esperado:
  - *(Ver `analytics-service/jobs/cleaning_transforms.py` línea 94:108)*.

### 3.4. Orquestación del Job Batch
- Inicialización de `SparkSession`, consolidación de DataFrames vía JOINs relacionales y persistencia particionada:
- *(Ver `analytics-service/jobs/extract_and_clean_catalog.py` línea 78:155)*.

---

## 4. Validación y Criterios de Aceptación

- **CA 2.1 (Extracción JDBC Balanceada):** Validada mediante lectura concurrente sin bloqueos en base de datos `skillbridge`.
- **CA 2.2 (Sanitización Completa):** Validada al 100% en la suite `test_cleaning_transforms.py` evaluando casos borde con HTML anidado y caracteres no imprimibles.
- **CA 2.3 (Almacenamiento Parquet Snappy):** Verificada generación de carpetas de partición física (`category_name=Backend`, `category_name=Frontend`, etc.) con metadata `_SUCCESS`.
