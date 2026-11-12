"""
Pipeline ETL: Extracción, Limpieza y Normalización del Catálogo
Historia de Usuario: HU-PS02
"""

import os
import sys
from pyspark.sql import SparkSession
from pyspark.sql import functions as F
from pyspark.sql.types import (
    StructType,
    StructField,
    StringType,
    DecimalType,
    IntegerType,
    TimestampType
)

from cleaning_transforms import clean_text_column, validate_schema

# Definición del esquema estricto (CA 2.3)
CATALOG_CLEAN_SCHEMA = StructType([
    StructField("service_id", StringType(), False),
    StructField("service_name", StringType(), False),
    StructField("service_code", StringType(), False),
    StructField("category_id", StringType(), False),
    StructField("category_name", StringType(), False),
    StructField("mentor_id", StringType(), False),
    StructField("mentor_name", StringType(), False),
    StructField("mentor_email", StringType(), False),
    StructField("mentor_role", StringType(), False),
    StructField("price", DecimalType(12, 2), False),
    StructField("capacity", IntegerType(), True),
    StructField("status", StringType(), False),
    StructField("short_description", StringType(), True),
    StructField("detail", StringType(), True),
    StructField("learning_objectives", StringType(), True),
    StructField("prerequisites", StringType(), True),
    StructField("created_at", TimestampType(), True),
    StructField("updated_at", TimestampType(), True)
])


def get_db_credentials():
    db_host = os.getenv("POSTGRES_HOST", "postgres")
    db_port = os.getenv("POSTGRES_PORT", "5432")
    db_name = os.getenv("POSTGRES_DB")
    db_user = os.getenv("POSTGRES_USER")
    db_password = os.getenv("POSTGRES_PASSWORD")

    missing = [k for k, v in [("POSTGRES_DB", db_name), ("POSTGRES_USER", db_user), ("POSTGRES_PASSWORD", db_password)] if not v]
    if missing:
        raise ValueError(f"Variables de entorno requeridas faltantes: {', '.join(missing)}")

    jdbc_url = f"jdbc:postgresql://{db_host}:{db_port}/{db_name}"
    return jdbc_url, db_user, db_password


def read_partitioned_table(spark: SparkSession, jdbc_url: str, user: str, pwd: str, query: str, num_partitions: int = 4):
    """
    Extracción JDBC particionada utilizando hash sobre UUID (CA 2.1).
    """
    return spark.read \
        .format("jdbc") \
        .option("url", jdbc_url) \
        .option("driver", "org.postgresql.Driver") \
        .option("dbtable", query) \
        .option("user", user) \
        .option("password", pwd) \
        .option("partitionColumn", "spark_partition_id") \
        .option("lowerBound", 0) \
        .option("upperBound", num_partitions - 1) \
        .option("numPartitions", num_partitions) \
        .load() \
        .drop("spark_partition_id")


def run_etl():
    print("==========================================================")
    print(" INICIANDO PIPELINE ETL: HU-PS02 (CATALOG EXTRACTION & CLEAN) ")
    print("==========================================================")

    jdbc_url, db_user, db_password = get_db_credentials()

    spark = SparkSession.builder \
        .appName("SkillBridge-Catalog-ETL") \
        .config("spark.sql.parquet.compression.codec", "snappy") \
        .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    try:
        # 1. Extracción particionada JDBC (CA 2.1)
        num_partitions = 4

        query_services = f"""(
            SELECT 
                id::text AS service_id,
                name AS raw_service_name,
                category_id::text AS category_id,
                price,
                detail AS raw_detail,
                short_description AS raw_short_description,
                learning_objectives AS raw_learning_objectives,
                prerequisites AS raw_prerequisites,
                capacity,
                code AS service_code,
                status,
                created_by::text AS mentor_id,
                created_at,
                updated_at,
                abs(hashtext(id::text)) % {num_partitions} AS spark_partition_id
            FROM services
        ) AS s_subq"""

        query_categories = f"""(
            SELECT 
                id::text AS cat_id,
                name AS raw_cat_name,
                abs(hashtext(id::text)) % {num_partitions} AS spark_partition_id
            FROM catalog_items
            WHERE type = 'CATEGORY'
        ) AS c_subq"""

        query_users = f"""(
            SELECT 
                id::text AS u_id,
                name AS raw_mentor_name,
                email AS mentor_email,
                role AS mentor_role,
                abs(hashtext(id::text)) % {num_partitions} AS spark_partition_id
            FROM app_users
        ) AS u_subq"""

        print("-> Extrayendo tablas particionadas desde PostgreSQL...")
        df_services = read_partitioned_table(spark, jdbc_url, db_user, db_password, query_services, num_partitions)
        df_categories = read_partitioned_table(spark, jdbc_url, db_user, db_password, query_categories, num_partitions)
        df_users = read_partitioned_table(spark, jdbc_url, db_user, db_password, query_users, num_partitions)

        # 2. Consolidación (Join)
        df_joined = df_services \
            .join(df_categories, df_services.category_id == df_categories.cat_id, "left") \
            .join(df_users, df_services.mentor_id == df_users.u_id, "left")

        # 3. Limpieza y Normalización Nativa (CA 2.2)
        print("-> Aplicando transformaciones y normalización de texto...")
        df_cleaned = df_joined \
            .withColumn("service_name", clean_text_column("raw_service_name", "Servicio sin nombre")) \
            .withColumn("category_name", clean_text_column("raw_cat_name", "Sin Categoria")) \
            .withColumn("mentor_name", clean_text_column("raw_mentor_name", "Mentor Desconocido")) \
            .withColumn("mentor_email", F.lower(clean_text_column("mentor_email", "sin_correo@skillbridge.local"))) \
            .withColumn("mentor_role", clean_text_column("mentor_role", "UNKNOWN")) \
            .withColumn("short_description", clean_text_column("raw_short_description")) \
            .withColumn("detail", clean_text_column("raw_detail")) \
            .withColumn("learning_objectives", clean_text_column("raw_learning_objectives")) \
            .withColumn("prerequisites", clean_text_column("raw_prerequisites")) \
            .withColumn("price", F.col("price").cast(DecimalType(12, 2))) \
            .withColumn("capacity", F.col("capacity").cast(IntegerType())) \
            .select(
                "service_id",
                "service_name",
                "service_code",
                "category_id",
                "category_name",
                "mentor_id",
                "mentor_name",
                "mentor_email",
                "mentor_role",
                "price",
                "capacity",
                "status",
                "short_description",
                "detail",
                "learning_objectives",
                "prerequisites",
                "created_at",
                "updated_at"
            )

        # 4. Validación de Esquema Estricto (CA 2.3)
        print("-> Validando esquema canónico estricto...")
        validate_schema(df_cleaned, CATALOG_CLEAN_SCHEMA)
        print(" [OK] Esquema validado correctamente.")

        # 5. Exportación en Parquet Optimizado (CA 2.3)
        output_path = "/data/processed/catalog_clean.parquet"
        print(f"-> Escribiendo dataset columnar Parquet en: {output_path}...")
        
        # Particionamiento óptimo por categoría para consultas analíticas eficientes
        df_cleaned.write \
            .mode("overwrite") \
            .partitionBy("category_name") \
            .parquet(output_path)

        print("==========================================================")
        print(" [EXITO] PIPELINE HU-PS02 EJECUTADO SATISFACTORIAMENTE")
        print("==========================================================")

    except Exception as e:
        print("==========================================================")
        print(f" [ERROR CRÍTICO EN PIPELINE ETL]: {e}")
        print("==========================================================")
        sys.exit(1)
    finally:
        spark.stop()


if __name__ == "__main__":
    run_etl()