"""
Pipeline de Generación de Embeddings en Batch con Tolerancia a Fallos (HU-PS04)
Criterios de Aceptación: CA 4.1, CA 4.2, CA 4.3
"""

import os
import sys
from datetime import datetime
from typing import Iterator, List, Tuple

from pyspark.sql import DataFrame, Row, SparkSession
from pyspark.sql import functions as F
from pyspark.sql.types import (
    ArrayType,
    FloatType,
    IntegerType,
    StringType,
    StructField,
    StructType,
    TimestampType
)

from embedding_client import BatchEmbeddingClient

# Esquema canónico de embeddings indexables (CA 4.3)
EMBEDDINGS_SCHEMA = StructType([
    StructField("chunk_id", StringType(), False),
    StructField("service_id", StringType(), False),
    StructField("mentor_id", StringType(), True),
    StructField("category_name", StringType(), True),
    StructField("chunk_index", IntegerType(), False),
    StructField("chunk_text", StringType(), False),
    StructField("text_hash", StringType(), False),
    StructField("model_name", StringType(), False),
    StructField("dimension", IntegerType(), False),
    StructField("vector", ArrayType(FloatType()), False),
    StructField("embedded_at", TimestampType(), False)
])


def get_pipeline_config():
    """Carga y valida la configuración del pipeline desde variables de entorno."""
    return {
        "input_path": os.getenv("INPUT_DATA_PATH", "/data/processed/catalog_clean.parquet"),
        "output_path": os.getenv("OUTPUT_DATA_PATH", "/data/processed/catalog_embeddings.parquet"),
        "checkpoint_dir": os.getenv("CHECKPOINT_DIR", "/data/checkpoints/embeddings"),
        "provider": os.getenv("EMBEDDING_PROVIDER", "gemini"),
        "model_name": os.getenv("EMBEDDING_MODEL", "text-embedding-004"),
        "dimension": int(os.getenv("EMBEDDING_DIMENSION", 768)),
        "batch_size": int(os.getenv("EMBEDDING_BATCH_SIZE", 20)),
        "max_retries": int(os.getenv("EMBEDDING_MAX_RETRIES", 5)),
        "num_partitions": int(os.getenv("NUM_PARTITIONS", 4)),
        "requests_per_minute": int(os.getenv("EMBEDDING_RPM", 60))
    }


def prepare_chunks_dataframe(spark: SparkSession, input_path: str) -> DataFrame:
    """
    Carga el dataset de entrada. Si es un catálogo limpio de HU-PS02, construye
    los chunks de texto representativos para RAG y búsqueda semántica de mentores.
    """
    print(f"-> Leyendo dataset de entrada desde: {input_path}")
    raw_df = spark.read.parquet(input_path)

    # Si ya contiene chunk_text y chunk_id, respetamos el esquema
    cols = raw_df.columns
    if "chunk_text" in cols and "chunk_id" in cols:
        df_prepared = raw_df
        if "text_hash" not in cols:
            df_prepared = df_prepared.withColumn("text_hash", F.sha2(F.col("chunk_text"), 256))
        return df_prepared

    # Caso catálogo limpio de HU-PS02: Construcción de texto semántico enriquecido
    print("-> Construyendo chunks representativos a partir de catálogo normalizado...")
    chunk_expr = F.concat_ws(
        "\n",
        F.concat(F.lit("Servicio: "), F.coalesce(F.col("service_name"), F.lit(""))),
        F.concat(F.lit("Categoría: "), F.coalesce(F.col("category_name"), F.lit(""))),
        F.concat(F.lit("Mentor: "), F.coalesce(F.col("mentor_name"), F.lit("")), F.lit(" ("), F.coalesce(F.col("mentor_role"), F.lit("")), F.lit(")")),
        F.when(F.col("short_description").isNotNull(), F.concat(F.lit("Resumen: "), F.col("short_description"))),
        F.when(F.col("detail").isNotNull(), F.concat(F.lit("Detalle: "), F.col("detail"))),
        F.when(F.col("learning_objectives").isNotNull(), F.concat(F.lit("Objetivos: "), F.col("learning_objectives"))),
        F.when(F.col("prerequisites").isNotNull(), F.concat(F.lit("Requisitos: "), F.col("prerequisites")))
    )

    df_chunks = raw_df.select(
        F.concat_ws("_", F.col("service_id"), F.lit("0")).alias("chunk_id"),
        F.col("service_id"),
        F.col("mentor_id"),
        F.col("category_name"),
        F.lit(0).alias("chunk_index"),
        chunk_expr.alias("chunk_text")
    ).filter(F.col("chunk_text").isNotNull() & (F.length(F.trim(F.col("chunk_text"))) > 0))

    df_chunks = df_chunks.withColumn("text_hash", F.sha2(F.col("chunk_text"), 256))
    return df_chunks


def filter_pending_chunks(spark: SparkSession, df_incoming: DataFrame, output_path: str) -> Tuple[DataFrame, DataFrame]:
    """
    Mecanismo anti-reprocesamiento: Compara con los embeddings existentes para
    procesar únicamente chunks no vectorizados o cuyo contenido ha cambiado (CA 4.2).
    """
    try:
        df_existing = spark.read.parquet(output_path)
        print("-> Dataset de embeddings previo detectado. Verificando chunks ya vectorizados...")

        # Chunks que ya están procesados y su texto no cambió
        df_unchanged = df_existing.join(
            df_incoming.select("chunk_id", "text_hash"),
            on=["chunk_id", "text_hash"],
            how="inner"
        )

        # Chunks que son nuevos o cambiaron
        df_pending = df_incoming.join(
            df_existing.select("chunk_id", "text_hash"),
            on=["chunk_id", "text_hash"],
            how="left_anti"
        )

        return df_pending, df_unchanged
    except Exception:
        print("-> No se detectó dataset previo. Se procesarán todos los chunks entrantes.")
        empty_existing = spark.createDataFrame([], schema=EMBEDDINGS_SCHEMA)
        return df_incoming, empty_existing


def create_partition_processor(config: dict):
    """
    Fábrica del procesador para mapPartitions (Tarea Técnica 2).
    Asegura una única instancia de cliente y sesión HTTP por partición de Spark.
    """
    provider = config["provider"]
    model_name = config["model_name"]
    dimension = config["dimension"]
    batch_size = config["batch_size"]
    max_retries = config["max_retries"]
    rpm = config["requests_per_minute"]

    def process_partition(iterator: Iterator[Row]) -> Iterator[Row]:
        rows: List[Row] = list(iterator)
        if not rows:
            return iter([])

        client = BatchEmbeddingClient(
            provider=provider,
            model_name=model_name,
            expected_dimension=dimension,
            batch_size=batch_size,
            max_retries=max_retries,
            requests_per_minute=rpm
        )

        try:
            texts = [r["chunk_text"] for r in rows]
            vectors = client.embed_texts(texts)
            now_ts = datetime.utcnow()

            emitted_rows = []
            for row, vec in zip(rows, vectors):
                emitted_rows.append(Row(
                    chunk_id=str(row["chunk_id"]),
                    service_id=str(row["service_id"]),
                    mentor_id=str(row["mentor_id"]) if row["mentor_id"] is not None else None,
                    category_name=str(row["category_name"]) if row["category_name"] is not None else None,
                    chunk_index=int(row["chunk_index"]),
                    chunk_text=str(row["chunk_text"]),
                    text_hash=str(row["text_hash"]),
                    model_name=str(model_name),
                    dimension=int(dimension),
                    vector=[float(v) for v in vec],
                    embedded_at=now_ts
                ))
            return iter(emitted_rows)
        finally:
            client.close()

    return process_partition


def validate_embeddings(df: DataFrame, expected_dimension: int) -> bool:
    """
    Validación de dimensionalidad y consistencia de vectores (Tarea Técnica 3 & CA 4.3).
    """
    # 1. Validación de esquema
    field_map = {f.name: f.dataType for f in df.schema.fields}
    vector_field = field_map.get("vector")

    if not isinstance(vector_field, ArrayType) or not isinstance(vector_field.elementType, FloatType):
        raise TypeError(f"La columna 'vector' debe ser de tipo ArrayType(FloatType()), obtenido: {vector_field}")

    # 2. Validación de consistencia de dimensiones y ausencia de nulos
    invalid_rows = df.filter(
        F.col("vector").isNull() |
        (F.size(F.col("vector")) != expected_dimension) |
        (F.col("dimension") != expected_dimension)
    ).count()

    if invalid_rows > 0:
        raise ValueError(
            f"Validación fallida: {invalid_rows} filas tienen vectores nulos o con dimensión diferente de {expected_dimension}."
        )

    return True


def run_embedding_pipeline():
    print("==========================================================")
    print(" INICIANDO PIPELINE: HU-PS04 (BATCH EMBEDDINGS PIPELINE) ")
    print("==========================================================")

    config = get_pipeline_config()
    print(f"-> Proveedor: {config['provider']} | Modelo: {config['model_name']} | Dimensión: {config['dimension']}")
    print(f"-> Micro-lote (batch_size): {config['batch_size']} | Particiones: {config['num_partitions']}")

    spark = SparkSession.builder \
        .appName("SkillBridge-Batch-Embeddings-Pipeline") \
        .config("spark.sql.parquet.compression.codec", "snappy") \
        .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    # Distribuir dependencias de código Python a todos los workers del clúster
    jobs_dir = os.path.dirname(os.path.abspath(__file__))
    client_script = os.path.join(jobs_dir, "embedding_client.py")
    if os.path.exists(client_script):
        spark.sparkContext.addPyFile(client_script)

    try:
        # Configuración de checkpointing tolerante a fallos (CA 4.2)
        os.makedirs(config["checkpoint_dir"], exist_ok=True)
        spark.sparkContext.setCheckpointDir(config["checkpoint_dir"])

        # 1. Carga y preparación de chunks
        df_incoming = prepare_chunks_dataframe(spark, config["input_path"])

        # 2. Mecanismo anti-reprocesamiento
        df_pending, df_unchanged = filter_pending_chunks(spark, df_incoming, config["output_path"])
        pending_count = df_pending.count()
        unchanged_count = df_unchanged.count()
        print(f"-> Chunks pendientes de vectorización: {pending_count} | Chunks reutilizados: {unchanged_count}")

        if pending_count == 0:
            print("==========================================================")
            print(" [INFO] Todos los chunks ya están vectorizados. Nada que procesar.")
            print("==========================================================")
            return

        # 3. Checkpointing de tolerancia a fallos antes de operaciones de red (CA 4.2)
        print("-> Aplicando checkpointing de PySpark al DataFrame pendiente...")
        df_pending_checkpointed = df_pending.checkpoint()

        # 4. Procesamiento distribuido con mapPartitions (Tarea Técnica 2)
        print(f"-> Reparticionando a {config['num_partitions']} particiones para cómputo distribuido...")
        df_repartitioned = df_pending_checkpointed.repartition(config["num_partitions"])

        partition_fn = create_partition_processor(config)
        rdd_vectors = df_repartitioned.rdd.mapPartitions(partition_fn)

        # 5. Reconstrucción de DataFrame y Validación (CA 4.3)
        print("-> Reconstruyendo DataFrame tipado con esquema indexable...")
        df_newly_embedded = spark.createDataFrame(rdd_vectors, schema=EMBEDDINGS_SCHEMA)

        print(f"-> Validando dimensionalidad estricta ({config['dimension']} floats)...")
        validate_embeddings(df_newly_embedded, config["dimension"])
        print(" [OK] Dimensionalidad y consistencia de vectores validadas exitosamente.")

        # 6. Consolidación y Escritura en Parquet (CA 4.3)
        if unchanged_count > 0:
            print("-> Consolidando nuevos embeddings con registros preexistentes...")
            df_final = df_unchanged.unionByName(df_newly_embedded)
        else:
            df_final = df_newly_embedded

        print(f"-> Guardando dataset vectorial Parquet en: {config['output_path']}...")
        os.makedirs(os.path.dirname(config["output_path"]), exist_ok=True)

        df_final.write \
            .mode("overwrite") \
            .parquet(config["output_path"])

        total_saved = df_final.count()
        print(f" [OK] {total_saved} vectores guardados exitosamente en formato Parquet.")

        print("==========================================================")
        print(" [EXITO] PIPELINE HU-PS04 COMPLETADO CON TOLERANCIA A FALLOS")
        print("==========================================================")

    except Exception as e:
        print("==========================================================")
        print(f" [ERROR CRÍTICO EN PIPELINE DE EMBEDDINGS]: {e}")
        print("==========================================================")
        sys.exit(1)
    finally:
        spark.stop()


if __name__ == "__main__":
    run_embedding_pipeline()
