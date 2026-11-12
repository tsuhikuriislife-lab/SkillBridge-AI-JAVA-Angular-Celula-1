"""Singleton factory for the project's local PySpark session."""

import os
from threading import Lock
from typing import Optional

from pyspark.sql import SparkSession


class SparkSessionFactory:
    """Creates one reusable SparkSession per Python process."""

    _session: Optional[SparkSession] = None
    _lock = Lock()

    @classmethod
    def get_session(cls, app_name: str = "skillbridge-data") -> SparkSession:
        with cls._lock:
            if cls._session is None or cls._session.sparkContext._jsc is None:
                master = os.getenv("SPARK_MASTER", "local[*]")
                driver_memory = os.getenv("SPARK_DRIVER_MEMORY", "2g")
                local_dir = os.getenv("SPARK_LOCAL_DIR", "/tmp/spark-temp")
                builder = (
                    SparkSession.builder.appName(app_name)
                    .master(master)
                    .config("spark.driver.memory", driver_memory)
                    .config("spark.local.dir", local_dir)
                    .config("spark.sql.execution.arrow.pyspark.enabled", "true")
                    .config("spark.jars", os.getenv("POSTGRES_JDBC_JAR", "/opt/spark-jars/postgresql-42.7.7.jar"))
                )
                cls._session = builder.getOrCreate()
            return cls._session

    @classmethod
    def stop(cls) -> None:
        with cls._lock:
            if cls._session is not None:
                cls._session.stop()
                cls._session = None
