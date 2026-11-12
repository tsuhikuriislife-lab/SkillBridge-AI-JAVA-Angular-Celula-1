"""
sentiment_llm_client.py
Módulo de clasificación de sentimiento y extracción de tópicos de feedback
para SkillBridge AI usando LLM estructurado (JSON Mode + Few-Shot Prompting).
"""

import json
import os
import re
import urllib.request
import urllib.error
from typing import Dict, Any, List

# Esquema canónico esperado de salida
DEFAULT_ANALYSIS_SCHEMA = {
    "sentiment": "Neutral",             # 'Positivo' | 'Neutral' | 'Crítico'
    "sentiment_score": 0.5,             # Escala normalizada de 0.0 (muy crítico) a 1.0 (muy positivo)
    "key_topics": {
        "claridad_pedagogica": False,
        "puntualidad": False,
        "dominio_tecnico": False
    },
    "strengths": [],                    # Lista de fortalezas identificadas
    "areas_for_improvement": []         # Lista de áreas de mejora detectadas
}

FEW_SHOT_SYSTEM_PROMPT = """Eres un asistente de IA especializado en control de calidad educativa para SkillBridge AI.
Tu tarea es analizar el comentario y calificación (1 a 5) de un alumno sobre una mentoría y devolver EXCLUSIVAMENTE un objeto JSON sin formato markdown, explicaciones ni texto adicional.

Reglas de clasificación:
1. "sentiment": Debe ser exactamente uno de: "Positivo", "Neutral", "Crítico".
   - 4-5 estrellas o comentarios de elogio -> "Positivo"
   - 3 estrellas o comentarios neutros/balanceados -> "Neutral"
   - 1-2 estrellas o quejas/insatisfacción severa -> "Crítico"
2. "sentiment_score": Número flotante entre 0.0 (máxima negatividad/crítica) y 1.0 (máxima satisfacción).
3. "key_topics": Objeto booleano indicando si el alumno mencionó explícita o implícitamente:
   - "claridad_pedagogica": Explicaciones, pedagogía, paciencia, didáctica, metodología.
   - "puntualidad": Llegar a tiempo, cumplimiento de horarios, ausencias o cortes de sesión.
   - "dominio_tecnico": Nivel de conocimientos técnicos, dominio del stack tecnológico, resolución de dudas.
4. "strengths": Array de strings breves con puntos destacados del mentor (vacío si no hay).
5. "areas_for_improvement": Array de strings breves con aspectos a corregir o mejorar (vacío si no hay).

--- EJEMPLOS FEW-SHOT ---
Ejemplo 1:
Input: Rating: 5, Comentario: "Excelente sesión. El mentor domina Spring Boot a la perfección y explicó con mucha paciencia la arquitectura hexagonal."
Output:
{
  "sentiment": "Positivo",
  "sentiment_score": 0.95,
  "key_topics": {
    "claridad_pedagogica": true,
    "puntualidad": false,
    "dominio_tecnico": true
  },
  "strengths": ["Dominio avanzado de Spring Boot", "Alta paciencia y didáctica"],
  "areas_for_improvement": []
}

Ejemplo 2:
Input: Rating: 2, Comentario: "El tutor llegó 15 minutos tarde y se notaba distraído. Cuando le pregunté sobre RxJS no supo cómo resolver el memory leak."
Output:
{
  "sentiment": "Crítico",
  "sentiment_score": 0.15,
  "key_topics": {
    "claridad_pedagogica": false,
    "puntualidad": true,
    "dominio_tecnico": true
  },
  "strengths": [],
  "areas_for_improvement": ["Impuntualidad (15 min tarde)", "Falta de preparación en RxJS"]
}

Ejemplo 3:
Input: Rating: 3, Comentario: "La clase estuvo normal, cumplió con el temario pero el ritmo fue un poco apresurado para mi gusto."
Output:
{
  "sentiment": "Neutral",
  "sentiment_score": 0.50,
  "key_topics": {
    "claridad_pedagogica": true,
    "puntualidad": false,
    "dominio_tecnico": false
  },
  "strengths": ["Cumplimiento del temario"],
  "areas_for_improvement": ["Ritmo acelerado de explicación"]
}
"""


def build_user_prompt(rating: int, comment: str) -> str:
    """Construye el prompt de entrada para el modelo."""
    clean_comment = comment.strip() if comment else "Sin comentario."
    return f"Rating: {rating}, Comentario: \"{clean_comment}\"\nOutput:"


def clean_and_parse_llm_json(raw_response: str) -> Dict[str, Any]:
    """
    Limpia y valida que la respuesta del LLM sea un JSON estricto.
    Remueve bloques ```json si el modelo los incluyó.
    """
    if not raw_response or not raw_response.strip():
        return dict(DEFAULT_ANALYSIS_SCHEMA)

    cleaned = raw_response.strip()
    # Eliminar bloques markdown si vienen presentes
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned)
        cleaned = re.sub(r"\s*```$", "", cleaned)
        cleaned = cleaned.strip()

    try:
        parsed = json.loads(cleaned)
        # Asegurar llaves críticas
        sentiment = parsed.get("sentiment", "Neutral")
        if sentiment not in ("Positivo", "Neutral", "Crítico"):
            sentiment = "Neutral"

        score = float(parsed.get("sentiment_score", 0.5))
        score = max(0.0, min(1.0, score))

        topics = parsed.get("key_topics", {})
        key_topics = {
            "claridad_pedagogica": bool(topics.get("claridad_pedagogica", False)),
            "puntualidad": bool(topics.get("puntualidad", False)),
            "dominio_tecnico": bool(topics.get("dominio_tecnico", False))
        }

        return {
            "sentiment": sentiment,
            "sentiment_score": score,
            "key_topics": key_topics,
            "strengths": list(parsed.get("strengths", [])),
            "areas_for_improvement": list(parsed.get("areas_for_improvement", []))
        }
    except Exception:
        return fallback_rule_based_sentiment(3, cleaned)


def fallback_rule_based_sentiment(rating: int, comment: str) -> Dict[str, Any]:
    """
    Mecanismo de contingencia offline / sin conexión al LLM.
    Garantiza que el job Spark termine exitosamente asignando categorías lógicas.
    """
    comment_lower = (comment or "").lower()
    
    if rating >= 4:
        sentiment = "Positivo"
        score = 0.85
    elif rating <= 2:
        sentiment = "Crítico"
        score = 0.20
    else:
        sentiment = "Neutral"
        score = 0.50

    has_pedagogy = any(w in comment_lower for w in ["explica", "paciencia", "didáctica", "pedagog", "entiende", "claro"])
    has_punctuality = any(w in comment_lower for w in ["tarde", "puntual", "hora", "tiempo", "espera"])
    has_tech = any(w in comment_lower for w in ["código", "técnico", "conocimiento", "spring", "angular", "java", "bug", "arquitectura"])

    strengths = []
    improvements = []
    if sentiment == "Positivo":
        strengths.append("Desempeño general satisfactorio")
    elif sentiment == "Crítico":
        improvements.append("Revisar retroalimentación del alumno")

    return {
        "sentiment": sentiment,
        "sentiment_score": score,
        "key_topics": {
            "claridad_pedagogica": has_pedagogy,
            "puntualidad": has_punctuality,
            "dominio_tecnico": has_tech
        },
        "strengths": strengths,
        "areas_for_improvement": improvements
    }


def call_gemini_api(rating: int, comment: str, api_key: str = None) -> Dict[str, Any]:
    """
    Invoca la API de Gemini (modelo ligero gemini-2.5-flash o gemini-1.5-flash) vía HTTP POST
    usando la librería estándar de Python para evitar dependencias pesadas en los workers de Spark.
    """
    key = api_key or os.getenv("GEMINI_API_KEY")
    if not key:
        # Si no hay API key configurada en el entorno, usa el fallback determinista
        return fallback_rule_based_sentiment(rating, comment)

    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={key}"
    
    payload = {
        "system_instruction": {
            "parts": [{"text": FEW_SHOT_SYSTEM_PROMPT}]
        },
        "contents": [
            {
                "parts": [{"text": build_user_prompt(rating, comment)}]
            }
        ],
        "generationConfig": {
            "temperature": 0.1,
            "response_mime_type": "application/json"
        }
    }

    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST"
    )

    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            res_body = response.read().decode("utf-8")
            data = json.loads(res_body)
            raw_text = data["candidates"][0]["content"]["parts"][0]["text"]
            return clean_and_parse_llm_json(raw_text)
    except Exception:
        # En caso de error de red o timeout, aplicamos degradación elegante
        return fallback_rule_based_sentiment(rating, comment)