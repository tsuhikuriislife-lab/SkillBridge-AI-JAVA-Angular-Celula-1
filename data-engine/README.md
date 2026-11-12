# Entorno PySpark de SkillBridge AI

El servicio `pyspark` usa Python 3.11, Java 17 y PySpark 3.5.6, aislado del backend que continúa con Java 21. Compose lo incluye bajo el perfil `data`; no se inicia con el perfil predeterminado.

## Smoke test JDBC

Con Docker Compose disponible y las variables habituales del proyecto cargadas (o usando los valores de desarrollo del Compose):

```bash
docker compose --profile data build pyspark
docker compose --profile data run --rm pyspark
```

También puede ejecutarse el smoke test desde la raíz del repositorio con `make test-data`.

Compose espera a que PostgreSQL esté saludable. El script ejecuta una consulta JDBC de solo lectura a `current_database()` y comprueba que el nombre coincide con `POSTGRES_DB`. No requiere tablas de negocio ni modifica datos.

Variables opcionales: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `SPARK_MASTER` (por defecto `local[*]`) y `SPARK_DRIVER_MEMORY` (por defecto `2g`). Ese parámetro de Spark limita el heap del driver. No se pasa `-Xmx` en `spark.driver.extraJavaOptions` porque Spark 3.5 rechaza explícitamente esa opción ahí. El contenedor tiene un límite de memoria de 3 GiB para dejar margen al heap de 2 GiB y a la memoria nativa de Spark/JVM.

La imagen incluye el driver JDBC PostgreSQL 42.7.7. `SparkSessionFactory.get_session()` devuelve la sesión singleton del proceso; `SparkSessionFactory.stop()` la cierra.

## Parquet local

El volumen `./data-engine:/workspace` permite leer y escribir rutas bajo `/workspace` desde el contenedor. Por ejemplo, con la sesión de fábrica, un DataFrame puede usar `df.write.mode("overwrite").parquet("/workspace/output/example")` y luego leerse con `spark.read.parquet("/workspace/output/example")`.
