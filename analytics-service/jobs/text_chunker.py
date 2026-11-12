
import tiktoken
from functools import lru_cache


# ==================================================
# 1. TOKENIZADOR
# ==================================================

@lru_cache(maxsize=4)
def get_tokenizer(encoding_name="cl100k_base"):
    """
    Obtiene y reutiliza el tokenizador.
    Evita cargarlo repetidamente.
    """
    return tiktoken.get_encoding(encoding_name)


def count_tokens(text, encoding_name="cl100k_base"):
    """
    Calcula la cantidad de tokens de un texto.
    """

    if not text:
        return 0

    tokenizer = get_tokenizer(encoding_name)

    tokens = tokenizer.encode(
        text,
        disallowed_special=()
    )

    return len(tokens)


# ==================================================
# 2. SLIDING WINDOW CHUNKING
# ==================================================

def sliding_window_chunk(
    text,
    chunk_size=512,
    overlap=0.10,
    encoding_name="cl100k_base"
):
    """
    Divide un texto en fragmentos utilizando Sliding Window.
    """

    if chunk_size <= 0:
        raise ValueError("El tamaño del chunk debe ser positivo")

    if not 0 <= overlap < 1:
        raise ValueError("El overlap debe estar entre 0 y 1")

    if text is None or not text.strip():
        return []

    tokenizer = get_tokenizer(encoding_name)

    # 1. Convertir texto en tokens
    tokens = tokenizer.encode(
        text,
        disallowed_special=()
    )

    # 2. Calcular solapamiento y desplazamiento
    overlap_size = int(chunk_size * overlap)
    step = chunk_size - overlap_size

    chunks = []
    start = 0

    # 3. Recorrer las ventanas
    while start < len(tokens):

        end = start + chunk_size
        chunk_tokens = tokens[start:end]

        # 4. Reconstruir el texto
        chunk_text = tokenizer.decode(chunk_tokens)

        # 5. Guardar fragmento
        chunks.append({
            "chunk_index": len(chunks),
            "chunk_text": chunk_text,
            "token_count": len(chunk_tokens)
        })

        # Evitar crear una ventana redundante
        if end >= len(tokens):
            break

        start += step

    return chunks


# ==================================================
# 3. PROCESAMIENTO DE REGISTROS DEL CATÁLOGO
# ==================================================

TEXT_FIELDS = [
    "short_description",
    "detail",
    "learning_objectives",
    "prerequisites"
]


def process_service_record(
    service,
    chunk_size=512,
    overlap=0.10
):
    """
    Procesa los campos textuales de un servicio.

    Conserva:
    - Identificador del servicio.
    - Campo de origen.
    - Índice del chunk.
    - Texto del chunk.
    - Cantidad de tokens.
    """

    service_id = service["service_id"]

    service_chunks = []

    for field in TEXT_FIELDS:

        text = service.get(field)

        if not text or not text.strip():
            continue

        chunks = sliding_window_chunk(
            text=text,
            chunk_size=chunk_size,
            overlap=overlap
        )

        for chunk in chunks:

            service_chunks.append({
                "service_id": service_id,
                "source_field": field,
                "chunk_index": chunk["chunk_index"],
                "chunk_text": chunk["chunk_text"],
                "token_count": chunk["token_count"]
            })

    return service_chunks


# ==================================================
# 4. PROCESAMIENTO DEL CATÁLOGO COMPLETO
# ==================================================

def process_catalog(
    catalog,
    chunk_size=512,
    overlap=0.10
):
    """
    Procesa una colección de servicios normalizados.
    """

    catalog_chunks = []

    for service in catalog:

        chunks = process_service_record(
            service=service,
            chunk_size=chunk_size,
            overlap=overlap
        )

        catalog_chunks.extend(chunks)

    return catalog_chunks
