"""
Pruebas Unitarias y de Integración para el Pipeline de Embeddings (HU-PS04)
Criterios de Aceptación: CA 4.1, CA 4.2, CA 4.3
"""

import math
import os
import sys
from datetime import datetime
from unittest.mock import MagicMock, patch

import pytest
from pyspark.sql import Row
from pyspark.sql.types import (
    ArrayType,
    FloatType,
    IntegerType,
    StringType,
    StructField,
    StructType,
    TimestampType
)

# Asegurar path de imports hacia jobs/ tanto en driver como en workers
jobs_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "../jobs"))
if jobs_dir not in sys.path:
    sys.path.insert(0, jobs_dir)
current_pp = os.environ.get("PYTHONPATH", "")
os.environ["PYTHONPATH"] = f"{jobs_dir}:{current_pp}" if current_pp else jobs_dir

from embedding_client import BatchEmbeddingClient
from generate_embeddings_batch import (
    EMBEDDINGS_SCHEMA,
    create_partition_processor,
    filter_pending_chunks,
    prepare_chunks_dataframe,
    validate_embeddings
)


# --------------------------------------------------------------------------
# Pruebas de BatchEmbeddingClient (CA 4.1 y CA 4.2)
# --------------------------------------------------------------------------

def test_client_mock_deterministic_generation():
    """Valida generación de embeddings mock consistente, normalizada L2 y con dimensión fija."""
    client = BatchEmbeddingClient(provider="mock", expected_dimension=384, batch_size=5)
    texts = ["Mentor en Java y Spring Boot", "Especialista en Angular y Cloud", "Arquitecto de Software"]

    vectors = client.embed_texts(texts)

    assert len(vectors) == 3
    for vec in vectors:
        assert len(vec) == 384
        # Verificar normalización L2 (magnitud ~ 1.0)
        norm = math.sqrt(sum(v * v for v in vec))
        assert abs(norm - 1.0) < 1e-4

    # Mismo texto produce exactamente el mismo vector (determinista)
    repeated = client.embed_texts(["Mentor en Java y Spring Boot"])
    assert vectors[0] == repeated[0]


def test_client_micro_batch_chunking():
    """Valida que textos extensos se dividan en micro-lotes respetando batch_size (CA 4.1)."""
    client = BatchEmbeddingClient(provider="mock", expected_dimension=128, batch_size=4)
    texts = [f"Texto número {i}" for i in range(14)]

    vectors = client.embed_texts(texts)
    assert len(vectors) == 14
    for vec in vectors:
        assert len(vec) == 128


def test_client_exponential_backoff_retry_success():
    """Valida mecanismo de reintentos ante error transitorio HTTP 429 con recuperación (CA 4.2)."""
    client = BatchEmbeddingClient(
        provider="gemini",
        api_key="dummy_key",
        expected_dimension=4,
        max_retries=3,
        base_delay=0.01,
        max_delay=0.1
    )

    call_count = 0

    def mock_http_post(url, body, headers=None):
        nonlocal call_count
        call_count += 1
        if call_count < 2:
            exc = RuntimeError("HTTP 429: Resource has been exhausted (rate limit)")
            exc.status_code = 429
            exc.retry_after = 0.01
            raise exc
        return '{"embeddings": [{"values": [0.1, 0.2, 0.3, 0.4]}]}'

    with patch.object(client, "_http_post", side_effect=mock_http_post):
        vectors = client.embed_texts(["Prueba de resiliencia"])
        assert len(vectors) == 1
        assert vectors[0] == [0.1, 0.2, 0.3, 0.4]
        assert call_count == 2


def test_client_retry_exhaustion_raises_runtime_error():
    """Valida que si se agotan todos los reintentos se lance excepción controlada (CA 4.2)."""
    client = BatchEmbeddingClient(
        provider="gemini",
        api_key="dummy_key",
        expected_dimension=4,
        max_retries=2,
        base_delay=0.01,
        max_delay=0.05
    )

    def failing_http_post(url, body, headers=None):
        exc = RuntimeError("HTTP 503: Service Unavailable")
        exc.status_code = 503
        raise exc

    with patch.object(client, "_http_post", side_effect=failing_http_post):
        with pytest.raises(RuntimeError, match="Error irrecuperable|Agotados los"):
            client.embed_texts(["Texto que fallará"])


# --------------------------------------------------------------------------
# Pruebas de Validación y Esquema (CA 4.3)
# --------------------------------------------------------------------------

def test_validate_embeddings_success(spark):
    """Valida que un DataFrame conforme pase la validación de consistencia."""
    data = [
        Row(
            chunk_id="srv-1_0",
            service_id="srv-1",
            mentor_id="m-1",
            category_name="Backend",
            chunk_index=0,
            chunk_text="Servicio de Spring",
            text_hash="hash123",
            model_name="text-embedding-004",
            dimension=4,
            vector=[0.1, 0.2, 0.3, 0.4],
            embedded_at=datetime.utcnow()
        )
    ]
    df = spark.createDataFrame(data, schema=EMBEDDINGS_SCHEMA)
    assert validate_embeddings(df, expected_dimension=4) is True


def test_validate_embeddings_dimension_mismatch(spark):
    """Valida que vectores con dimensión inconsistente provoquen fallo explícito (CA 4.3)."""
    data = [
        Row(
            chunk_id="srv-1_0",
            service_id="srv-1",
            mentor_id="m-1",
            category_name="Backend",
            chunk_index=0,
            chunk_text="Servicio de Spring",
            text_hash="hash123",
            model_name="text-embedding-004",
            dimension=4,
            vector=[0.1, 0.2],  # Solo 2 elementos, se esperan 4
            embedded_at=datetime.utcnow()
        )
    ]
    df = spark.createDataFrame(data, schema=EMBEDDINGS_SCHEMA)
    with pytest.raises(ValueError, match="Validación fallida"):
        validate_embeddings(df, expected_dimension=4)


# --------------------------------------------------------------------------
# Pruebas de Preparación de Chunks y Anti-Reprocesamiento (CA 4.2)
# --------------------------------------------------------------------------

def test_prepare_chunks_dataframe(spark, tmp_path):
    """Valida construcción de chunks y text_hash a partir del catálogo normalizado."""
    catalog_schema = StructType([
        StructField("service_id", StringType(), False),
        StructField("service_name", StringType(), False),
        StructField("category_name", StringType(), True),
        StructField("mentor_name", StringType(), True),
        StructField("mentor_role", StringType(), True),
        StructField("mentor_id", StringType(), True),
        StructField("short_description", StringType(), True),
        StructField("detail", StringType(), True),
        StructField("learning_objectives", StringType(), True),
        StructField("prerequisites", StringType(), True)
    ])
    catalog_data = [
        Row(
            service_id="srv-001",
            service_name="Mentoría Spring Boot",
            category_name="Desarrollo Backend",
            mentor_name="Carlos Ruiz",
            mentor_role="Senior Architect",
            mentor_id="usr-10",
            short_description="Aprende microservicios",
            detail="Profundización en Hexagonal",
            learning_objectives="Diseño de puertos y adaptadores",
            prerequisites="Java 21 básico"
        )
    ]
    catalog_path = str(tmp_path / "catalog_test.parquet")
    spark.createDataFrame(catalog_data, schema=catalog_schema).write.parquet(catalog_path)

    df_chunks = prepare_chunks_dataframe(spark, catalog_path)
    rows = df_chunks.collect()

    assert len(rows) == 1
    assert rows[0]["chunk_id"] == "srv-001_0"
    assert "Servicio: Mentoría Spring Boot" in rows[0]["chunk_text"]
    assert "Mentor: Carlos Ruiz" in rows[0]["chunk_text"]
    assert len(rows[0]["text_hash"]) == 64  # SHA-256 hex string


def test_filter_pending_chunks_incremental(spark, tmp_path):
    """Valida que chunks ya vectorizados con el mismo hash se excluyan de reprocessing (CA 4.2)."""
    incoming_data = [
        Row(chunk_id="c1", text_hash="hash_same", service_id="s1", chunk_text="t1"),
        Row(chunk_id="c2", text_hash="hash_modified_new", service_id="s2", chunk_text="t2_new"),
        Row(chunk_id="c3", text_hash="hash_brand_new", service_id="s3", chunk_text="t3")
    ]
    df_incoming = spark.createDataFrame(incoming_data)

    existing_data = [
        Row(
            chunk_id="c1",
            service_id="s1",
            mentor_id=None,
            category_name=None,
            chunk_index=0,
            chunk_text="t1",
            text_hash="hash_same",
            model_name="mock",
            dimension=4,
            vector=[0.1, 0.2, 0.3, 0.4],
            embedded_at=datetime.utcnow()
        ),
        Row(
            chunk_id="c2",
            service_id="s2",
            mentor_id=None,
            category_name=None,
            chunk_index=0,
            chunk_text="t2_old",
            text_hash="hash_modified_old",
            model_name="mock",
            dimension=4,
            vector=[0.5, 0.6, 0.7, 0.8],
            embedded_at=datetime.utcnow()
        )
    ]
    existing_path = str(tmp_path / "existing_embeddings.parquet")
    spark.createDataFrame(existing_data, schema=EMBEDDINGS_SCHEMA).write.parquet(existing_path)

    df_pending, df_unchanged = filter_pending_chunks(spark, df_incoming, existing_path)

    pending_ids = [r["chunk_id"] for r in df_pending.collect()]
    unchanged_ids = [r["chunk_id"] for r in df_unchanged.collect()]

    # c1 no debe reprocesarse; c2 (cambió hash) y c3 (nuevo) deben reprocesarse
    assert "c1" not in pending_ids
    assert "c1" in unchanged_ids
    assert "c2" in pending_ids
    assert "c3" in pending_ids


# --------------------------------------------------------------------------
# Prueba End-to-End con mapPartitions y Persistencia Parquet (CA 4.3)
# --------------------------------------------------------------------------

def test_map_partitions_and_parquet_persistence(spark, tmp_path):
    """
    Ejecuta el pipeline completo de mapPartitions generando vectores reales (mock)
    y verifica la persistencia en Parquet con tipo ArrayType(FloatType()).
    """
    input_chunks = [
        Row(
            chunk_id=f"chk_{i}",
            service_id=f"srv_{i}",
            mentor_id=f"men_{i}",
            category_name="AI",
            chunk_index=0,
            chunk_text=f"Descripción semántica del curso {i}",
            text_hash=f"hash_{i}"
        )
        for i in range(6)
    ]
    df_input = spark.createDataFrame(input_chunks).repartition(2)

    config = {
        "provider": "mock",
        "model_name": "mock-embedding-model",
        "dimension": 64,
        "batch_size": 2,
        "max_retries": 2,
        "requests_per_minute": 120
    }

    partition_fn = create_partition_processor(config)
    rdd_embedded = df_input.rdd.mapPartitions(partition_fn)

    df_embedded = spark.createDataFrame(rdd_embedded, schema=EMBEDDINGS_SCHEMA)
    assert validate_embeddings(df_embedded, expected_dimension=64) is True

    # Guardar en Parquet y verificar recarga
    output_parquet = str(tmp_path / "test_embeddings.parquet")
    df_embedded.write.mode("overwrite").parquet(output_parquet)

    df_reloaded = spark.read.parquet(output_parquet)
    assert df_reloaded.count() == 6

    # Verificar que el tipo de vector se conserva exactamente como ArrayType(FloatType())
    vector_field = [f for f in df_reloaded.schema.fields if f.name == "vector"][0]
    assert isinstance(vector_field.dataType, ArrayType)
    assert isinstance(vector_field.dataType.elementType, FloatType)
