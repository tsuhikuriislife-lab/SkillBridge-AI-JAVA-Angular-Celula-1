"""
Pruebas unitarias del chunker semántico del catálogo (HU-PS03 / US-ANL-01).

Valida la segmentación Sliding Window de jobs/text_chunker.py sobre
registros simulados provenientes del ETL de catálogo (HU-PS02).
"""

import math

import pytest

from text_chunker import (
    TEXT_FIELDS,
    count_tokens,
    process_catalog,
    process_service_record,
    sliding_window_chunk,
)

CHUNK_SIZE = 512
OVERLAP = 0.10


@pytest.fixture
def catalog_clean():
    """Registros normalizados del catálogo, como los entrega HU-PS02."""
    return [
        {
            "service_id": "SRV-001",
            "service_name": "Java Backend",
            "short_description": "Curso introductorio de desarrollo backend con Java.",
            "detail": (
                "En este servicio aprenderás programación orientada "
                "a objetos, estructuras de datos, manejo de excepciones, "
                "desarrollo de APIs REST y persistencia con Spring Boot. "
            ) * 40,
            "learning_objectives": (
                "Comprender Java, implementar clases, interfaces, "
                "inyección de dependencias y desarrollar APIs REST."
            ),
            "prerequisites": "Conocimientos básicos de programación.",
        },
        {
            "service_id": "SRV-002",
            "service_name": "Angular Frontend",
            "short_description": "Desarrollo de interfaces con Angular.",
            "detail": (
                "Aprenderás componentes, servicios, routing, "
                "inyección de dependencias y consumo de APIs HTTP."
            ),
            "learning_objectives": (
                "Construir aplicaciones frontend utilizando Angular."
            ),
            "prerequisites": None,
        },
    ]


def expected_windows(total_tokens, chunk_size=CHUNK_SIZE, overlap=OVERLAP):
    """Número de ventanas que debe producir el Sliding Window."""
    if total_tokens <= chunk_size:
        return 1
    step = chunk_size - int(chunk_size * overlap)
    return math.ceil((total_tokens - chunk_size) / step) + 1


# ==================================================
# count_tokens
# ==================================================

def test_count_tokens_returns_zero_for_empty_text():
    assert count_tokens("") == 0
    assert count_tokens(None) == 0


def test_count_tokens_returns_positive_for_text():
    assert count_tokens("Desarrollo backend con Java") > 0


# ==================================================
# sliding_window_chunk
# ==================================================

@pytest.mark.parametrize("text", [None, "", "   \n\t "])
def test_sliding_window_returns_empty_list_for_blank_text(text):
    assert sliding_window_chunk(text) == []


def test_short_text_produces_single_chunk():
    text = "Curso introductorio de desarrollo backend con Java."

    chunks = sliding_window_chunk(text, chunk_size=CHUNK_SIZE, overlap=OVERLAP)

    assert len(chunks) == 1
    assert chunks[0]["chunk_index"] == 0
    assert chunks[0]["chunk_text"] == text
    assert chunks[0]["token_count"] == count_tokens(text)


def test_long_text_is_split_into_overlapping_windows(catalog_clean):
    text = catalog_clean[0]["detail"]
    total_tokens = count_tokens(text)

    chunks = sliding_window_chunk(text, chunk_size=CHUNK_SIZE, overlap=OVERLAP)

    assert total_tokens > CHUNK_SIZE
    assert len(chunks) == expected_windows(total_tokens)
    assert [c["chunk_index"] for c in chunks] == list(range(len(chunks)))
    assert all(c["token_count"] <= CHUNK_SIZE for c in chunks)
    # Todas las ventanas salvo la última van llenas
    assert all(c["token_count"] == CHUNK_SIZE for c in chunks[:-1])
    # Con solapamiento, la suma de tokens supera el total del texto
    assert sum(c["token_count"] for c in chunks) > total_tokens


def test_zero_overlap_covers_every_token_exactly_once(catalog_clean):
    text = catalog_clean[0]["detail"]

    chunks = sliding_window_chunk(text, chunk_size=CHUNK_SIZE, overlap=0)

    assert sum(c["token_count"] for c in chunks) == count_tokens(text)


@pytest.mark.parametrize("chunk_size", [0, -1])
def test_invalid_chunk_size_raises_value_error(chunk_size):
    with pytest.raises(ValueError, match="chunk"):
        sliding_window_chunk("texto", chunk_size=chunk_size)


@pytest.mark.parametrize("overlap", [-0.1, 1, 1.5])
def test_invalid_overlap_raises_value_error(overlap):
    with pytest.raises(ValueError, match="overlap"):
        sliding_window_chunk("texto", overlap=overlap)


# ==================================================
# process_service_record / process_catalog
# ==================================================

def test_process_service_record_skips_empty_fields(catalog_clean):
    service = catalog_clean[1]  # prerequisites = None

    chunks = process_service_record(service, chunk_size=CHUNK_SIZE, overlap=OVERLAP)

    source_fields = {c["source_field"] for c in chunks}
    assert "prerequisites" not in source_fields
    assert source_fields == {"short_description", "detail", "learning_objectives"}


def test_process_service_record_keeps_traceability_metadata(catalog_clean):
    service = catalog_clean[0]

    chunks = process_service_record(service, chunk_size=CHUNK_SIZE, overlap=OVERLAP)

    for chunk in chunks:
        assert set(chunk) == {
            "service_id",
            "source_field",
            "chunk_index",
            "chunk_text",
            "token_count",
        }
        assert chunk["service_id"] == "SRV-001"
        assert chunk["source_field"] in TEXT_FIELDS
        assert chunk["chunk_text"].strip()
        assert 0 < chunk["token_count"] <= CHUNK_SIZE


def test_chunk_index_restarts_for_each_field(catalog_clean):
    chunks = process_service_record(catalog_clean[0], chunk_size=CHUNK_SIZE, overlap=OVERLAP)

    for field in TEXT_FIELDS:
        indexes = [c["chunk_index"] for c in chunks if c["source_field"] == field]
        assert indexes == list(range(len(indexes)))


def test_process_catalog_returns_expected_chunks(catalog_clean):
    chunks = process_catalog(catalog=catalog_clean, chunk_size=CHUNK_SIZE, overlap=OVERLAP)

    detail_windows = expected_windows(count_tokens(catalog_clean[0]["detail"]))
    # SRV-001: 3 campos cortos + detail largo. SRV-002: 3 campos cortos (prerequisites vacío)
    assert len(chunks) == 3 + detail_windows + 3
    assert [c["service_id"] for c in chunks].count("SRV-001") == 3 + detail_windows
    assert [c["service_id"] for c in chunks].count("SRV-002") == 3


def test_process_catalog_with_empty_catalog_returns_empty_list():
    assert process_catalog(catalog=[]) == []
