import os
import sys
from pyspark.sql import SparkSession

def main():
    print("==================================================")
    print(" INICIANDO SMOKE TEST DE PYSPARK + POSTGRESQL ")
    print("==================================================")

    # 1. Variables de entorno leídas obligatoriamente desde Docker / .env
    db_host = os.getenv("POSTGRES_HOST", "postgres")
    db_port = os.getenv("POSTGRES_PORT", "5432")
    db_name = os.getenv("POSTGRES_DB")
    db_user = os.getenv("POSTGRES_USER")
    db_password = os.getenv("POSTGRES_PASSWORD")

    # Validación estricta: si falta alguna credencial crítica, no continuar
    missing_vars = [var for var, val in [("POSTGRES_DB", db_name), ("POSTGRES_USER", db_user), ("POSTGRES_PASSWORD", db_password)] if not val]
    if missing_vars:
        print(f"[ERROR] Variables de entorno faltantes requeridas: {', '.join(missing_vars)}")
        sys.exit(1)

    jdbc_url = f"jdbc:postgresql://{db_host}:{db_port}/{db_name}"

    # 2. Inicializar la sesión distribuida de Spark
    spark = SparkSession.builder \
        .appName("SkillBridge-SmokeTest") \
        .getOrCreate()

    # Reducir el nivel de log para que la salida sea legible
    spark.sparkContext.setLogLevel("WARN")

    print(f"-> Spark Session creada exitosamente. Version: {spark.version}")
    print(f"-> Intentando conectar a PostgreSQL en: {jdbc_url}...")

    try:
        # 3. Prueba de lectura vía JDBC contra PostgreSQL
        df = spark.read \
            .format("jdbc") \
            .option("url", jdbc_url) \
            .option("driver", "org.postgresql.Driver") \
            .option("dbtable", "(SELECT 1 AS connection_test) AS test_table") \
            .option("user", db_user) \
            .option("password", db_password) \
            .load()

        print("-> Consulta ejecutada. Mostrando resultado del DataFrame:")
        df.show()

        print("==================================================")
        print(" [EXITO] SPARK Y POSTGRESQL CONECTADOS CORRECTAMENTE")
        print("==================================================")

    except Exception as e:
        print("==================================================")
        print(f" [ERROR] Falló la conexión JDBC: {e}")
        print("==================================================")
        sys.exit(1)
    finally:
        spark.stop()

if __name__ == "__main__":
    main()