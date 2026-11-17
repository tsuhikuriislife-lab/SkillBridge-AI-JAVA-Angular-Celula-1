"""
test_session_feedback_analytics.py
Pruebas unitarias para el pipeline analítico de feedback y LLM (HU-PS05).
Utiliza mocks para aislar la inferencia del modelo y verificar agregaciones de PySpark.
"""

import os
import sys
from unittest.mock import patch
import pytest
from pyspark.sql import Row
from pyspark.sql.types import (
    StructType, StructField, StringType, IntegerType
)

# Configurar path para importar desde jobs/
JOBS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../jobs"))
if JOBS_DIR not in sys.path:
    sys.path.insert(0, JOBS_DIR)

from sentiment_llm_client import clean_and_parse_llm_json, fallback_rule_based_sentiment
from session_feedback_analytics import classify_feedbacks_batch, aggregate_mentor_analytics


# =====================================================================
# 1. PRUEBAS DEL PARSER Y PROMPT ESTRUCTURADO (Tarea Técnica 1)
# =====================================================================

def test_clean_and_parse_llm_json_valid_json():
    """Valida la deserialización correcta cuando el LLM responde JSON puro."""
    raw_json = """
    {
      "sentiment": "Positivo",
      "sentiment_score": 0.92,
      "key_topics": {
        "claridad_pedagogica": true,
        "puntualidad": false,
        "dominio_tecnico": true
      },
      "strengths": ["Gran dominio de Spring"],
      "areas_for_improvement": []
    }
    """
    result = clean_and_parse_llm_json(raw_json)

    assert result["sentiment"] == "Positivo"
    assert result["sentiment_score"] == 0.92
    assert result["key_topics"]["claridad_pedagogica"] is True
    assert result["key_topics"]["puntualidad"] is False
    assert result["key_topics"]["dominio_tecnico"] is True
    assert "Gran dominio de Spring" in result["strengths"]


def test_clean_and_parse_llm_json_strips_markdown_codeblocks():
    """Valida que remueva bloques markdown ```json ... ``` devueltos por el LLM."""
    raw_markdown = """```json
    {
      "sentiment": "Crítico",
      "sentiment_score": 0.15,
      "key_topics": {
        "claridad_pedagogica": false,
        "puntualidad": true,
        "dominio_tecnico": false
      },
      "strengths": [],
      "areas_for_improvement": ["Impuntualidad severa"]
    }
    ```"""
    result = clean_and_parse_llm_json(raw_markdown)

    assert result["sentiment"] == "Crítico"
    assert result["sentiment_score"] == 0.15
    assert result["key_topics"]["puntualidad"] is True
    assert "Impuntualidad severa" in result["areas_for_improvement"]


def test_clean_and_parse_llm_json_fallback_on_corrupt():
    """Valida la degradación elegante si el LLM devuelve texto corrupto o no-JSON."""
    corrupt_text = "Lo siento, como modelo de lenguaje no puedo responder en JSON."
    result = clean_and_parse_llm_json(corrupt_text)

    # Debe retornar esquema por defecto sin lanzar excepción
    assert result["sentiment"] in ("Positivo", "Neutral", "Crítico")
    assert 0.0 <= result["sentiment_score"] <= 1.0


# =====================================================================
# 2. PRUEBAS DE CLASIFICACIÓN BATCH CON MOCK DE LLM (CA 5.2)
# =====================================================================

def test_classify_feedbacks_batch_with_mock(spark):
    """
    CA 5.2: Verifica la UDF de inferencia en batch de Spark utilizando un mock de la API.
    """
    # Datos sintéticos de entrada simulando la tabla session_feedbacks
    input_data = [
        Row(id="fb-1", mentor_id="mentor-A", student_id="student-1", rating=5, comment="Excelente pedagogo."),
        Row(id="fb-2", mentor_id="mentor-B", student_id="student-2", rating=2, comment="Llegó muy tarde.")
    ]
    df_input = spark.createDataFrame(input_data)

    # Definir respuesta simulada para evitar llamadas HTTP externas
    def mock_call_gemini(rating, comment):
        if rating >= 4:
            return {
                "sentiment": "Positivo",
                "sentiment_score": 0.90,
                "key_topics": {"claridad_pedagogica": True, "puntualidad": False, "dominio_tecnico": True},
                "strengths": ["Buena pedagogía"],
                "areas_for_improvement": []
            }
        else:
            return {
                "sentiment": "Crítico",
                "sentiment_score": 0.20,
                "key_topics": {"claridad_pedagogica": False, "puntualidad": True, "dominio_tecnico": False},
                "strengths": [],
                "areas_for_improvement": ["Llegar puntual"]
            }

    with patch("session_feedback_analytics.call_gemini_api", side_effect=mock_call_gemini):
        df_classified = classify_feedbacks_batch(df_input)
        rows = df_classified.collect()

        assert len(rows) == 2
        
        # Validación de Mentor A (Positivo)
        row_a = next(r for r in rows if r["mentor_id"] == "mentor-A")
        assert row_a["sentiment"] == "Positivo"
        assert row_a["sentiment_score"] == 0.90
        assert row_a["topic_pedagogia"] is True
        assert row_a["topic_puntualidad"] is False
        assert "Buena pedagogía" in row_a["strengths"]

        # Validación de Mentor B (Crítico)
        row_b = next(r for r in rows if r["mentor_id"] == "mentor-B")
        assert row_b["sentiment"] == "Crítico"
        assert row_b["sentiment_score"] == 0.20
        assert row_b["topic_puntualidad"] is True
        assert "Llegar puntual" in row_b["improvements"]


# =====================================================================
# 3. PRUEBAS DE AGREGACIÓN ANALÍTICA Y PUNTUACIÓN PONDERADA (CA 5.3)
# =====================================================================

def test_aggregate_mentor_analytics_calculations(spark):
    """
    CA 5.3: Verifica el cálculo matemático exacto de weighted_score,
    promedios, conteos y consolidación de fortalezas/mejoras desduplicadas.
    """
    # Simulamos el DataFrame ya clasificado para dos mentores
    classified_data = [
        # Mentor 1: Dos feedbacks (ambos 5 estrellas, sentiment_score 1.0)
        Row(
            id="1", mentor_id="mentor-1", student_id="s1", rating=5, comment="c1",
            sentiment="Positivo", sentiment_score=1.0,
            topic_pedagogia=True, topic_puntualidad=False, topic_tecnico=True,
            strengths=["Dominio Java", "Paciencia"], improvements=[]
        ),
        Row(
            id="2", mentor_id="mentor-1", student_id="s2", rating=5, comment="c2",
            sentiment="Positivo", sentiment_score=1.0,
            topic_pedagogia=False, topic_puntualidad=True, topic_tecnico=True,
            strengths=["Dominio Java"], improvements=[] # "Dominio Java" repetido
        ),
        # Mentor 2: Un feedback crítico (rating 2, score 0.2) y uno neutral (rating 3, score 0.5)
        Row(
            id="3", mentor_id="mentor-2", student_id="s3", rating=2, comment="c3",
            sentiment="Crítico", sentiment_score=0.2,
            topic_pedagogia=False, topic_puntualidad=True, topic_tecnico=False,
            strengths=[], improvements=["Impuntual"]
        ),
        Row(
            id="4", mentor_id="mentor-2", student_id="s4", rating=3, comment="c4",
            sentiment="Neutral", sentiment_score=0.5,
            topic_pedagogia=True, topic_puntualidad=False, topic_tecnico=False,
            strengths=["Temario al día"], improvements=["Ritmo muy rápido"]
        )
    ]

    df_classified = spark.createDataFrame(classified_data)
    df_analytics = aggregate_mentor_analytics(df_classified)
    results = {r["mentor_id"]: r for r in df_analytics.collect()}

    # --- Comprobación Mentor 1 ---
    m1 = results["mentor-1"]
    assert m1["total_feedbacks"] == 2
    assert m1["avg_rating"] == 5.0
    assert m1["positive_count"] == 2
    assert m1["neutral_count"] == 0
    assert m1["critical_count"] == 0
    # Fórmula: (5.0 * 0.6) + ((1.0 * 5.0) * 0.4) = 3.0 + 2.0 = 5.0
    assert m1["weighted_score"] == 5.0
    # Desduplicación: "Dominio Java" no debe repetirse dos veces
    assert "Dominio Java" in m1["top_strengths"]
    assert "Paciencia" in m1["top_strengths"]
    assert len(m1["top_strengths"].split(" | ")) == 2

    # --- Comprobación Mentor 2 ---
    m2 = results["mentor-2"]
    assert m2["total_feedbacks"] == 2
    assert m2["avg_rating"] == 2.5 # (2 + 3) / 2
    assert m2["positive_count"] == 0
    assert m2["neutral_count"] == 1
    assert m2["critical_count"] == 1
    # avg_rating = 2.5 -> 2.5 * 0.6 = 1.50
    # avg_sentiment_score = 0.35 -> (0.35 * 5.0) * 0.4 = 1.75 * 0.4 = 0.70
    # weighted_score = 1.50 + 0.70 = 2.20
    assert m2["weighted_score"] == 2.20
    assert "Impuntual" in m2["areas_of_improvement"]
    assert "Ritmo muy rápido" in m2["areas_of_improvement"]