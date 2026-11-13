"""
session_feedback_analytics.py
Job analítico distribuido de Apache Spark para SkillBridge AI (HU-PS05).
Procesa valoraciones y comentarios de alumnos post-sesión, clasifica con LLM
y genera métricas consolidadas por mentor en PostgreSQL.
"""

import os
import sys
from typing import Dict, Any

from pyspark.sql import SparkSession, DataFrame
from pyspark.sql import functions as F
from pyspark.sql.types import (
    StructType, StructField, StringType,
    DoubleType, BooleanType, ArrayType
)

# Asegurar importación del módulo local de LLM
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
if CURRENT_DIR not in sys.path:
    sys.path.append(CURRENT_DIR)

from sentiment_llm_client import call_gemini_api


# Esquema tipado para la salida de la UDF de inferencia
ANALYSIS_OUTPUT_SCHEMA = StructType([
    StructField("sentiment", StringType(), False),
    StructField("sentiment_score", DoubleType(), False),
    StructField("claridad_pedagogica", BooleanType(), False),
    StructField("puntualidad", BooleanType(), False),
    StructField("dominio_tecnico", BooleanType(), False),
    StructField("strengths", ArrayType(StringType()), False),
    StructField("areas_for_improvement", ArrayType(StringType()), False)
])


def get_postgres_config() -> Dict[str, str]:
    """Obtiene las credenciales de PostgreSQL desde las variables de entorno."""
    host = os.getenv("POSTGRES_HOST", "postgres")
    port = os.getenv("POSTGRES_PORT", "5432")
    db = os.getenv("POSTGRES_DB", "skillbridge")
    user = os.getenv("POSTGRES_USER", "skillbridge")
    password = os.getenv("POSTGRES_PASSWORD", "skillbridge_dev")

    return {
        "url": f"jdbc:postgresql://{host}:{port}/{db}",
        "user": user,
        "password": password,
        "driver": "org.postgresql.Driver"
    }


def create_spark_session() -> SparkSession:
    """Inicializa la sesión distribuida de PySpark."""
    spark = SparkSession.builder \
        .appName("SkillBridge-SessionFeedback-Analytics") \
        .getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    return spark


def ingest_feedbacks(spark: SparkSession, pg_cfg: Dict[str, str]) -> DataFrame:
    """
    CA 5.1: Ingesta de valoraciones y comentarios de sesiones desde PostgreSQL.
    """
    print("-> [CA 5.1] Ingeryendo valoraciones desde la tabla 'session_feedbacks'...")
    df = spark.read \
        .format("jdbc") \
        .option("url", pg_cfg["url"]) \
        .option("driver", pg_cfg["driver"]) \
        .option("dbtable", "session_feedbacks") \
        .option("user", pg_cfg["user"]) \
        .option("password", pg_cfg["password"]) \
        .load()
    return df


def classify_feedbacks_batch(df_feedbacks: DataFrame) -> DataFrame:
    """
    CA 5.2: Clasificación batch de sentimiento y extracción de temas clave.
    Aplica la UDF sobre cada fila invocando el cliente LLM estructurado.
    """
    print("-> [CA 5.2] Ejecutando clasificación batch de sentimiento y tópicos con LLM...")

    def _analyze_row(rating: int, comment: str):
        res = call_gemini_api(rating, comment)
        topics = res.get("key_topics", {})
        return (
            res.get("sentiment", "Neutral"),
            float(res.get("sentiment_score", 0.5)),
            bool(topics.get("claridad_pedagogica", False)),
            bool(topics.get("puntualidad", False)),
            bool(topics.get("dominio_tecnico", False)),
            res.get("strengths", []),
            res.get("areas_for_improvement", [])
        )

    analyze_udf = F.udf(_analyze_row, ANALYSIS_OUTPUT_SCHEMA)

    df_analyzed = df_feedbacks.withColumn("analysis", analyze_udf(F.col("rating"), F.col("comment")))

    # Desanidar campos analíticos para facilitar agregaciones
    return df_analyzed.select(
        F.col("id"),
        F.col("mentor_id"),
        F.col("student_id"),
        F.col("rating"),
        F.col("comment"),
        F.col("analysis.sentiment").alias("sentiment"),
        F.col("analysis.sentiment_score").alias("sentiment_score"),
        F.col("analysis.claridad_pedagogica").alias("topic_pedagogia"),
        F.col("analysis.puntualidad").alias("topic_puntualidad"),
        F.col("analysis.dominio_tecnico").alias("topic_tecnico"),
        F.col("analysis.strengths").alias("strengths"),
        F.col("analysis.areas_for_improvement").alias("improvements")
    )


def aggregate_mentor_analytics(df_classified: DataFrame) -> DataFrame:
    """
    CA 5.3: Generación del DataFrame analítico consolidado por mentor (mentor_analytics).
    Calcula puntuación ponderada y consolida fortalezas y áreas de mejora.
    """
    print("-> [CA 5.3] Consolidando métricas ponderadas y áreas de mejora por mentor...")

    # Puntuación Ponderada:
    # 60% peso a las estrellas de los alumnos (escala 1 a 5)
    # 40% peso al sentimiento analizado por el LLM (score 0.0 a 1.0 normalizado a escala 1 a 5)
    weighted_score_expr = F.round(
        (F.avg("rating") * 0.60) + ((F.avg("sentiment_score") * 5.0) * 0.40),
        2
    )

    df_consolidated = df_classified.groupBy("mentor_id").agg(
        F.count("id").alias("total_feedbacks"),
        F.round(F.avg("rating"), 2).alias("avg_rating"),
        weighted_score_expr.alias("weighted_score"),
        F.sum(F.when(F.col("sentiment") == "Positivo", 1).otherwise(0)).alias("positive_count"),
        F.sum(F.when(F.col("sentiment") == "Neutral", 1).otherwise(0)).alias("neutral_count"),
        F.sum(F.when(F.col("sentiment") == "Crítico", 1).otherwise(0)).alias("critical_count"),
        # Consolidar listas de fortalezas y mejoras sin duplicados
        F.array_join(F.array_distinct(F.flatten(F.collect_list("strengths"))), " | ").alias("top_strengths"),
        F.array_join(F.array_distinct(F.flatten(F.collect_list("improvements"))), " | ").alias("areas_of_improvement")
    ).withColumn("last_processed_at", F.current_timestamp())

    return df_consolidated


def write_mentor_analytics(df_analytics: DataFrame, pg_cfg: Dict[str, str]) -> None:
    """Persiste el DataFrame analítico en PostgreSQL."""
    print("-> Escribiendo métricas consolidadas en tabla 'mentor_analytics'...")
    df_analytics.write \
        .format("jdbc") \
        .option("url", pg_cfg["url"]) \
        .option("driver", pg_cfg["driver"]) \
        .option("dbtable", "mentor_analytics") \
        .option("user", pg_cfg["user"]) \
        .option("password", pg_cfg["password"]) \
        .option("truncate", "true") \
        .mode("overwrite") \
        .save()
    print("[EXITO] Datos analíticos persistidos exitosamente en PostgreSQL.")


def main():
    print("==========================================================")
    print("  SKILLBRIDGE AI - PIPELINE ANALÍTICO DE FEEDBACK (HU-PS05) ")
    print("==========================================================")

    pg_cfg = get_postgres_config()
    spark = create_spark_session()

    try:
        # 1. Ingesta (CA 5.1)
        df_feedbacks = ingest_feedbacks(spark, pg_cfg)
        feedbacks_count = df_feedbacks.count()
        print(f"-> Total de feedbacks recuperados: {feedbacks_count}")

        if feedbacks_count == 0:
            print("[AVISO] No hay feedbacks para procesar en esta ejecución.")
            return

        # 2. Inferencia y Clasificación LLM (CA 5.2)
        df_classified = classify_feedbacks_batch(df_feedbacks)
        df_classified.cache()

        print("\n--- Vista preliminar de comentarios analizados (CA 5.2) ---")
        df_classified.select("rating", "sentiment", "sentiment_score", "topic_pedagogia", "topic_puntualidad", "topic_tecnico").show(truncate=False)

        # 3. Agregación y Consolidación por Mentor (CA 5.3)
        df_analytics = aggregate_mentor_analytics(df_classified)

        print("\n--- Consolidado Analítico por Mentor (CA 5.3) ---")
        df_analytics.show(truncate=False)

        # 4. Persistencia en PostgreSQL
        write_mentor_analytics(df_analytics, pg_cfg)

    except Exception as e:
        print(f"[ERROR CRÍTICO] Falló el pipeline analítico: {e}")
        sys.exit(1)
    finally:
        spark.stop()
        print("-> Sesión de Spark liberada.")


if __name__ == "__main__":
    main()