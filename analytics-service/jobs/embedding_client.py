"""
Cliente Batch de Embeddings con Tolerancia a Fallos y Manejo de Cuotas (HU-PS04)
Criterios de Aceptación: CA 4.1 y CA 4.2
"""

import hashlib
import json
import logging
import math
import os
import random
import time
from typing import List, Optional, Tuple

try:
    import requests
    HAS_REQUESTS = True
except ImportError:
    HAS_REQUESTS = False
    import urllib.error
    import urllib.request

logger = logging.getLogger("embedding_client")
if not logger.handlers:
    handler = logging.StreamHandler()
    formatter = logging.Formatter("[%(asctime)s] [%(levelname)s] [EmbeddingClient] %(message)s")
    handler.setFormatter(formatter)
    logger.addHandler(handler)
    logger.setLevel(logging.INFO)


class BatchEmbeddingClient:
    """
    Cliente para la generación de embeddings en micro-lotes con:
    - Control estricto de rate-limit y cuotas (CA 4.1).
    - Reintentos exponenciales con jitter ante errores 429 / 5xx (CA 4.2).
    - Múltiples proveedores: Gemini (text-embedding-004), HuggingFace y Mock (pruebas).
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        provider: str = "gemini",
        model_name: str = "text-embedding-004",
        expected_dimension: int = 768,
        batch_size: int = 20,
        max_retries: int = 5,
        base_delay: float = 1.0,
        max_delay: float = 30.0,
        requests_per_minute: int = 60
    ):
        self.provider = (provider or os.getenv("EMBEDDING_PROVIDER", "gemini")).lower()
        self.model_name = model_name or os.getenv("EMBEDDING_MODEL", "text-embedding-004")
        self.expected_dimension = int(expected_dimension or os.getenv("EMBEDDING_DIMENSION", 768))
        self.batch_size = max(1, min(int(batch_size or os.getenv("EMBEDDING_BATCH_SIZE", 20)), 100))
        self.max_retries = int(max_retries)
        self.base_delay = float(base_delay)
        self.max_delay = float(max_delay)
        self.min_interval = 60.0 / max(1, float(requests_per_minute))
        self._last_request_time = 0.0

        if self.provider == "gemini":
            self.api_key = api_key or os.getenv("GEMINI_API_KEY")
            if not self.api_key:
                logger.warning("GEMINI_API_KEY no detectada. Las llamadas a la API real fallarán a menos que se use provider='mock'.")
        elif self.provider in ("huggingface", "hf"):
            self.api_key = api_key or os.getenv("HF_API_TOKEN") or os.getenv("HUGGINGFACE_API_KEY")
        else:
            self.api_key = api_key

        self._session = None
        if HAS_REQUESTS:
            self._session = requests.Session()

    def close(self):
        """Cierra conexiones activas de red."""
        if self._session is not None:
            try:
                self._session.close()
            except Exception:
                pass

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()

    def _wait_for_rate_limit(self):
        """Asegura el espaciado mínimo entre peticiones para respetar la cuota (CA 4.1)."""
        now = time.time()
        elapsed = now - self._last_request_time
        if elapsed < self.min_interval:
            sleep_time = self.min_interval - elapsed
            time.sleep(sleep_time)
        self._last_request_time = time.time()

    def embed_texts(self, texts: List[str]) -> List[List[float]]:
        """
        Genera embeddings para una lista arbitraria de textos dividiéndolos en micro-lotes
        controlados (CA 4.1).
        """
        if not texts:
            return []

        all_embeddings: List[List[float]] = []
        for i in range(0, len(texts), self.batch_size):
            chunk_batch = texts[i : i + self.batch_size]
            batch_vectors = self._embed_micro_batch_with_retry(chunk_batch)
            all_embeddings.extend(batch_vectors)

        return all_embeddings

    def _embed_micro_batch_with_retry(self, batch: List[str]) -> List[List[float]]:
        """
        Ejecuta la llamada a la API para un micro-lote aplicando reintentos exponenciales
        y jitter ante fallos transitorios o 429 (CA 4.2).
        """
        if self.provider == "mock":
            return self._call_mock_batch(batch)

        attempt = 0
        last_exception = None

        while attempt < self.max_retries:
            self._wait_for_rate_limit()
            try:
                if self.provider == "gemini":
                    vectors = self._call_gemini_batch(batch)
                elif self.provider in ("huggingface", "hf"):
                    vectors = self._call_huggingface_batch(batch)
                else:
                    raise ValueError(f"Proveedor de embeddings no soportado: '{self.provider}'")

                # Validación de salida del lote
                if len(vectors) != len(batch):
                    raise ValueError(f"Cantidad inconsistente de vectores: esperados {len(batch)}, recibidos {len(vectors)}")

                for vec in vectors:
                    if len(vec) != self.expected_dimension:
                        raise ValueError(f"Dimensión incorrecta: esperada {self.expected_dimension}, obtenida {len(vec)}")

                return vectors

            except Exception as e:
                last_exception = e
                attempt += 1

                # Determinar si el error es retentable (rate limit 429, timeout o 5xx)
                status_code, retry_after = self._extract_http_status_and_retry_after(e)
                is_retryable = (status_code in (429, 500, 502, 503, 504)) or ("timeout" in str(e).lower()) or ("connection" in str(e).lower())

                if not is_retryable or attempt >= self.max_retries:
                    logger.error(f"Fallo definitivo al generar embeddings tras {attempt} intentos: {e}")
                    raise RuntimeError(f"Error irrecuperable en llamada a Embeddings API: {e}") from e

                # Cálculo de espera exponencial con jitter
                if retry_after is not None and retry_after > 0:
                    delay = float(retry_after)
                else:
                    exp_delay = self.base_delay * (2 ** (attempt - 1))
                    jitter = random.uniform(0.1, 0.5) * exp_delay
                    delay = min(self.max_delay, exp_delay + jitter)

                logger.warning(f"[Intento {attempt}/{self.max_retries}] Error ({e}). Reintentando en {delay:.2f}s...")
                time.sleep(delay)

        raise RuntimeError(f"Agotados los {self.max_retries} reintentos: {last_exception}") from last_exception

    def _call_gemini_batch(self, batch: List[str]) -> List[List[float]]:
        """
        Consumo de la API de Embeddings de Gemini (text-embedding-004:batchEmbedContents).
        """
        if not self.api_key:
            raise ValueError("GEMINI_API_KEY es requerida para consumir el proveedor 'gemini'.")

        url = f"https://generativelanguage.googleapis.com/v1beta/models/{self.model_name}:batchEmbedContents?key={self.api_key}"
        requests_payload = [
            {
                "model": f"models/{self.model_name}",
                "content": {"parts": [{"text": text if text and text.strip() else " "}]}
            }
            for text in batch
        ]
        body = {"requests": requests_payload}

        raw_response = self._http_post(url, body)
        data = json.loads(raw_response)

        embeddings_data = data.get("embeddings", [])
        return [item["values"] for item in embeddings_data]

    def _call_huggingface_batch(self, batch: List[str]) -> List[List[float]]:
        """
        Consumo de Hugging Face Inference API o compatible.
        """
        url = f"https://api-inference.huggingface.co/models/{self.model_name}"
        headers = {}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"

        body = {"inputs": batch, "options": {"wait_for_model": True}}
        raw_response = self._http_post(url, body, headers=headers)
        data = json.loads(raw_response)
        if isinstance(data, list):
            return data
        raise ValueError(f"Respuesta inesperada de HuggingFace: {data}")

    def _call_mock_batch(self, batch: List[str]) -> List[List[float]]:
        """
        Generador sintético determinista para pruebas unitarias y entornos sin conexión:
        Calcula un vector normalizado L2 de dimensión fija a partir del SHA-256 del texto.
        """
        results: List[List[float]] = []
        for text in batch:
            seed = hashlib.sha256((text or "").encode("utf-8")).digest()
            raw_vals = []
            for i in range(self.expected_dimension):
                byte_val = seed[i % len(seed)]
                val = ((byte_val + i) % 255) / 127.5 - 1.0
                raw_vals.append(val)
            # Normalización L2
            norm = math.sqrt(sum(v * v for v in raw_vals)) or 1.0
            results.append([float(v / norm) for v in raw_vals])
        return results

    def _http_post(self, url: str, json_body: dict, headers: Optional[dict] = None) -> str:
        """Realiza una petición POST con timeout controlado."""
        req_headers = {"Content-Type": "application/json"}
        if headers:
            req_headers.update(headers)

        body_bytes = json.dumps(json_body).encode("utf-8")

        if HAS_REQUESTS and self._session is not None:
            resp = self._session.post(url, data=body_bytes, headers=req_headers, timeout=20)
            if resp.status_code >= 400:
                retry_header = resp.headers.get("Retry-After")
                msg = f"HTTP {resp.status_code}: {resp.text}"
                exc = RuntimeError(msg)
                exc.status_code = resp.status_code
                exc.retry_after = float(retry_header) if retry_header and retry_header.isdigit() else None
                raise exc
            return resp.text
        else:
            req = urllib.request.Request(url, data=body_bytes, headers=req_headers, method="POST")
            try:
                with urllib.request.urlopen(req, timeout=20) as resp:
                    return resp.read().decode("utf-8")
            except urllib.error.HTTPError as e:
                resp_text = e.read().decode("utf-8", errors="replace")
                retry_header = e.headers.get("Retry-After") if e.headers else None
                exc = RuntimeError(f"HTTP {e.code}: {resp_text}")
                exc.status_code = e.code
                exc.retry_after = float(retry_header) if retry_header and retry_header.isdigit() else None
                raise exc

    @staticmethod
    def _extract_http_status_and_retry_after(exception: Exception) -> Tuple[Optional[int], Optional[float]]:
        """Extrae el código de estado HTTP y Retry-After si están presentes."""
        status = getattr(exception, "status_code", None)
        retry_after = getattr(exception, "retry_after", None)
        if status is None and "429" in str(exception):
            status = 429
        return status, retry_after
