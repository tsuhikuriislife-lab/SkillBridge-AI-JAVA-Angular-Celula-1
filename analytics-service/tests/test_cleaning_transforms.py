import pytest
from pyspark.sql import Row
from pyspark.sql.types import StructType, StructField, StringType, IntegerType
import sys
import os

# Asegurar path de imports
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../jobs")))
from cleaning_transforms import clean_text_column, validate_schema


def test_remove_html_and_markdown(spark):
    data = [
        Row(id=1, raw_text="<h1>Título Principal</h1>\nTexto en **negrita** y *cursiva* con [Enlace](https://example.com)"),
        Row(id=2, raw_text="Lista:\n* Item 1\n* Item 2\n`codigo inline`"),
        Row(id=3, raw_text="&quot;Texto entre comillas con &amp; y &nbsp; entidad&quot;")
    ]
    df = spark.createDataFrame(data)
    result = df.withColumn("cleaned", clean_text_column("raw_text")).collect()

    # Verificaciones
    assert "<h1>" not in result[0]["cleaned"]
    assert "**" not in result[0]["cleaned"]
    assert "https://example.com" not in result[0]["cleaned"]
    assert "Enlace" in result[0]["cleaned"]
    assert "Item 1" in result[1]["cleaned"]
    assert "`" not in result[1]["cleaned"]
    assert "&amp;" not in result[2]["cleaned"]


def test_remove_control_characters_and_normalize_spaces(spark):
    data = [
        Row(id=1, raw_text="Texto con salto\x00 nulo y\x1F campana\a"),
        Row(id=2, raw_text="   Demasiados     espacios     intermedios   \t\n")
    ]
    df = spark.createDataFrame(data)
    result = df.withColumn("cleaned", clean_text_column("raw_text")).collect()

    assert "\x00" not in result[0]["cleaned"]
    assert "\x1F" not in result[0]["cleaned"]
    assert result[1]["cleaned"] == "Demasiados espacios intermedios"


def test_handle_nulls_and_empty_strings(spark):
    data = [
        Row(id=1, raw_text=None),
        Row(id=2, raw_text="    "),
        Row(id=3, raw_text="<p></p>")  # Al limpiar queda vacío
    ]
    df = spark.createDataFrame(data)
    result = df.withColumn("cleaned", clean_text_column("raw_text", default_value="N/A")).collect()

    assert result[0]["cleaned"] == "N/A"
    assert result[1]["cleaned"] == "N/A"
    assert result[2]["cleaned"] == "N/A"


def test_validate_schema_success(spark):
    schema = StructType([
        StructField("id", StringType(), False),
        StructField("value", IntegerType(), True)
    ])
    df = spark.createDataFrame([Row(id="abc", value=123)], schema=schema)
    assert validate_schema(df, schema) is True


def test_validate_schema_failure(spark):
    expected_schema = StructType([
        StructField("id", StringType(), False),
        StructField("price", IntegerType(), True)
    ])
    wrong_df = spark.createDataFrame([Row(id="abc", other_col="test")])

    with pytest.raises(ValueError, match="Faltan columnas"):
        validate_schema(wrong_df, expected_schema)