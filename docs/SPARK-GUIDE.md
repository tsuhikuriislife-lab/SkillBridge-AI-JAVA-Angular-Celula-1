# Guía de Arquitectura y Uso de Apache Spark & PySpark

Esta documentación detalla la arquitectura, conceptos fundamentales, comandos de operación y buenas prácticas para el módulo analítico distribuido de **SkillBridge AI** (`analytics-service/`).

---

## 1. ¿Qué es Apache Spark y PySpark?

* **Apache Spark:** Motor de computación distribuido en memoria de alto rendimiento, diseñado para procesamiento de datos a gran escala (Big Data), pipelines ETL y Machine Learning.
* **PySpark:** Interfaz oficial de Python para Apache Spark. Permite escribir algoritmos analíticos con la sintaxis concisa de Python mientras los cálculos se ejecutan distribuidos en paralelo sobre la Java Virtual Machine (JVM).

### ¿Por qué lo usamos en SkillBridge AI?
En lugar de procesar métricas complejas o entrenar algoritmos de recomendación dentro del backend de Spring Boot (lo cual saturaría la CPU y degradaría las peticiones HTTP), el procesamiento pesado se delega a un **clúster desacoplado de Spark** que corre en segundo plano.

---

## 2. Arquitectura del Clúster en Docker

El clúster analítico se encuentra orquestado dentro de `docker-compose.yml` en la red interna `skillbridge`:

```
                 [ Navegador / Dev ]
                 │       │
      :8080 (UI) │       │ :4040 (Jobs UI)
                 ▼       ▼
       ┌───────────────────────────┐
       │       spark-master        │ (Coordinador del Clúster)
       └─────────────┬─────────────┘
                     │ :7077 (RPC)
       ┌─────────────▼─────────────┐
       │       spark-worker        │ (Ejecutor: 2 Cores, 1GB RAM)
       └─────────────┬─────────────┘
                     │ JDBC (5432)
       ┌─────────────▼─────────────┐
       │         postgres          │ (Base de datos relacional)
       └───────────────────────────┘
```

* **`spark-master`**: Nodo maestro que coordina los trabajos y expone la Web UI de administración.
* **`spark-worker`**: Nodo ejecutor que procesa los datos en memoria con recursos asignados.
* **Volumen Compartido (`./analytics-service/jobs:/app/jobs`)**: Montaje en caliente (*bind mount*). Cualquier script creado o modificado en `analytics-service/jobs/` en tu máquina se sincroniza de inmediato dentro del contenedor sin reconstruir la imagen Docker.

---

## 3. Puertos e Interfaces Web de Monitoreo

| Puerto | Interfaz | Descripción |
| :--- | :--- | :--- |
| **`http://localhost:8080`** | **Spark Master Web UI** | Estado del clúster: trabajadores activos, memoria, núcleos disponibles y lista de aplicaciones terminadas o en ejecución. |
| **`http://localhost:8081`** | **Spark Worker Web UI** | Detalle técnico del nodo trabajador (uso de recursos por ejecutor). |
| **`http://localhost:4040`** | **Spark Application UI** | Se activa dinámicamente mientras un trabajo está procesando. Muestra las etapas (*Stages*), tareas (*Tasks*) y el grafo acíclico dirigido (DAG). |

---

## 4. Conceptos Clave de Spark

Para desarrollar scripts en `analytics-service/jobs/`, es fundamental entender estos 4 conceptos:

1. **`SparkSession`**: El punto de entrada central para programar con PySpark. Es el objeto que crea y administra los DataFrames y la conexión al clúster.
   ```python
   spark = SparkSession.builder.appName("MiJob").getOrCreate()
   ```
2. **`DataFrame`**: Estructura de datos tabular distribuida en múltiples particiones de memoria. Similar a un DataFrame de Pandas, pero distribuido en clúster.
3. **Transformaciones vs. Acciones (Lazy Evaluation)**:
   * **Transformación (Lazy):** No se ejecuta de inmediato; solo define el plan lógico de ejecución (ej. `.filter()`, `.select()`, `.groupBy()`, `.join()`).
   * **Acción (Eager):** Dispara el cómputo distribuido real y devuelve un resultado o escribe en disco/base de datos (ej. `.show()`, `.count()`, `.collect()`, `.write.save()`).
4. **Conector JDBC**: Mecanismo mediante el cual Spark lee y escribe tablas en PostgreSQL en paralelo usando particiones SQL.

---

## 5. Comandos de Ejecución y Operación

### 5.1. Levantar o reiniciar los servicios de Spark
```bash
# Levantar solo el clúster de Spark y la BD
docker compose up -d postgres spark-master spark-worker

# Detener los servicios
docker compose stop spark-master spark-worker
```

### 5.2. Ejecutar un script con `spark-submit`
Para enviar un trabajo al clúster distribuido, se utiliza la utilidad oficial `spark-submit`:

```bash
# Ejecutar el Smoke Test de validación
docker exec skillbridge-spark-master /opt/spark/bin/spark-submit \
  --master spark://spark-master:7077 \
  /app/jobs/smoke_test.py
```

### 5.3. Ejecutar un nuevo script desarrollado por el equipo
Crea tu script en la carpeta local:
`analytics-service/jobs/mi_nuevo_script.py`

Y ejecútalo directamente:
```bash
docker exec skillbridge-spark-master /opt/spark/bin/spark-submit \
  --master spark://spark-master:7077 \
  /app/jobs/mi_nuevo_script.py
```

### 5.4. Ver logs en tiempo real del clúster
```bash
# Logs del Master
docker logs -f skillbridge-spark-master

# Logs del Worker
docker logs -f skillbridge-spark-worker
```

---

## 6. Plantilla Base para Leer y Escribir en PostgreSQL

Al crear nuevos scripts analíticos o de Machine Learning, usa esta plantilla recomendada que gestiona la conexión JDBC segura sin credenciales quemadas:

```python
import os
import sys
from pyspark.sql import SparkSession

def main():
    # 1. Variables de entorno inyectadas por Docker
    db_host = os.getenv("POSTGRES_HOST", "postgres")
    db_port = os.getenv("POSTGRES_PORT", "5432")
    db_name = os.getenv("POSTGRES_DB")
    db_user = os.getenv("POSTGRES_USER")
    db_password = os.getenv("POSTGRES_PASSWORD")

    if not all([db_name, db_user, db_password]):
        print("[ERROR] Faltan variables de entorno para PostgreSQL.")
        sys.exit(1)

    jdbc_url = f"jdbc:postgresql://{db_host}:{db_port}/{db_name}"

    # 2. Iniciar sesión de Spark
    spark = SparkSession.builder \
        .appName("SkillBridge-Analytics-Job") \
        .getOrCreate()

    try:
        # 3. LECTURA: Leer tabla desde PostgreSQL a un DataFrame
        df_evaluaciones = spark.read \
            .format("jdbc") \
            .option("url", jdbc_url) \
            .option("driver", "org.postgresql.Driver") \
            .option("dbtable", "assessments") \
            .option("user", db_user) \
            .option("password", db_password) \
            .load()

        df_evaluaciones.printSchema()

        # 4. TRANSFORMACIÓN: Lógica de negocio / MLlib
        # (Filtros, agrupaciones, algoritmos de clustering o similitud)
        df_resultados = df_evaluaciones.filter(df_evaluaciones["score"] >= 80)

        # 5. ESCRITURA: Guardar resultados en tabla analítica
        df_resultados.write \
            .format("jdbc") \
            .option("url", jdbc_url) \
            .option("driver", "org.postgresql.Driver") \
            .option("dbtable", "analytics_top_performers") \
            .option("user", db_user) \
            .option("password", db_password) \
            .mode("append") \
            .save()

        print("[EXITO] Proceso completado y resultados persistidos.")

    finally:
        spark.stop()

if __name__ == "__main__":
    main()
```

---

## 7. Buenas Prácticas para el Equipo

1. **Cero Secretos Quemados:** Nunca escribas contraseñas o URLs de bases de datos hardcodeadas en scripts de Python. Utiliza siempre `os.getenv()`.
2. **Evitar `.collect()` en Datasets Grandes:** El método `.collect()` trae todos los datos a la memoria del driver. Si la tabla tiene miles de filas, puede agotar la memoria. Usa agregaciones, `.show(n)` o escribe el resultado directamente a la base de datos con `.write`.
3. **Liberar Recursos con `spark.stop()`:** Coloca siempre `spark.stop()` dentro de un bloque `finally:` para asegurar que los ejecutores liberen la memoria del clúster al terminar.
4. **Desacoplamiento con Spring Boot:** PySpark solo interactúa mediante tablas o colecciones compartidas. El backend Java lee los resultados consolidados de forma asíncrona a través de sus puertos y adaptadores JPA.
