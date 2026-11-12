"""Smoke test for Spark startup and PostgreSQL JDBC reads."""

import os
import sys

from pyspark.sql import SparkSession

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from spark_session_factory import SparkSessionFactory


def main() -> int:
    spark: SparkSession = SparkSessionFactory.get_session("skillbridge-postgres-smoke-test")
    try:
        host = os.getenv("POSTGRES_HOST", "localhost")
        port = os.getenv("POSTGRES_PORT", "5432")
        database = os.getenv("POSTGRES_DB", "skillbridge")
        url = f"jdbc:postgresql://{host}:{port}/{database}"
        query = "(SELECT current_database() AS database_name, 1 AS connection_ok) AS smoke_test"

        result = (
            spark.read.format("jdbc")
            .option("url", url)
            .option("dbtable", query)
            .option("user", os.getenv("POSTGRES_USER", "skillbridge"))
            .option("password", os.getenv("POSTGRES_PASSWORD", "skillbridge_dev"))
            .option("driver", os.getenv("POSTGRES_JDBC_DRIVER", "org.postgresql.Driver"))
            .load()
        )
        row = result.first()
        if row is None or row["connection_ok"] != 1 or row["database_name"] != database:
            raise RuntimeError(f"Unexpected PostgreSQL smoke-test result: {row}")

        print(f"Spark {spark.version} connected to PostgreSQL database '{row['database_name']}' via JDBC.")
        return 0
    except Exception as exc:  # Report the failure to the caller and preserve a nonzero exit code.
        print(f"Spark/PostgreSQL smoke test failed: {exc}", file=sys.stderr)
        return 1
    finally:
        SparkSessionFactory.stop()


if __name__ == "__main__":
    raise SystemExit(main())
