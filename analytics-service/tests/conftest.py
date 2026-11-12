import pytest
from pyspark.sql import SparkSession

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
    yield spark_session
    spark_session.stop()