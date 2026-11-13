import os
import sys
import pytest
from pyspark.sql import SparkSession

# Asegurar que el directorio jobs/ esté en el sys.path y en PYTHONPATH para los workers
jobs_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "../jobs"))
if jobs_path not in sys.path:
    sys.path.insert(0, jobs_path)

current_pp = os.environ.get("PYTHONPATH", "")
os.environ["PYTHONPATH"] = f"{jobs_path}:{current_pp}" if current_pp else jobs_path

@pytest.fixture(scope="session")
def spark():
    """
    Instancia SparkSession local ligera optimizada para pruebas unitarias.
    """
    spark_session = SparkSession.builder \
        .master("local[2]") \
        .appName("SkillBridge-UnitTests") \
        .config("spark.ui.enabled", "false") \
        .config("spark.sql.shuffle.partitions", "2") \
        .getOrCreate()
    
    spark_session.sparkContext.setLogLevel("ERROR")

    # Distribuir archivos de jobs a los workers de Spark
    client_file = os.path.join(jobs_path, "embedding_client.py")
    if os.path.exists(client_file):
        spark_session.sparkContext.addPyFile(client_file)

    yield spark_session
    spark_session.stop()