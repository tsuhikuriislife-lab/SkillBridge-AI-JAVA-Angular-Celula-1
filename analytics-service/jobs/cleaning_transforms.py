"""
Transformaciones y funciones de normalización de datos utilizando funciones nativas
de pyspark.sql.functions para asegurar el máximo rendimiento bajo Catalyst Optimizer.
"""

from pyspark.sql import Column, DataFrame
from pyspark.sql import functions as F
from pyspark.sql.types import StructType


def remove_html_tags(col_name: str) -> Column:
    """
    Remueve etiquetas HTML (<...>) y entidades comunes (&nbsp;, &amp;, etc.).
    """
    clean_col = F.regexp_replace(F.col(col_name), r"<[^>]+>", " ")
    clean_col = F.regexp_replace(clean_col, r"&(nbsp|amp|quot|lt|gt);", " ")
    return clean_col


def remove_markdown(col_name: str) -> Column:
    """
    Remueve sintaxis Markdown habitual:
    - Encabezados (# Título)
    - Negritas y cursivas (**texto**, *texto*, __texto__)
    - Enlaces e imágenes ([texto](url), ![alt](url))
    - Bloques de código (`codigo`)
    - Viñetas de listas (*, -, +)
    """
    # Enlaces: [texto](url) -> texto
    step1 = F.regexp_replace(F.col(col_name), r"\[(.*?)\]\(.*?\)", "$1")
    # Imágenes: ![alt](url) -> ""
    step2 = F.regexp_replace(step1, r"!\[.*?\]\(.*?\)", "")
    # Negrita y cursiva (** o __)
    step3 = F.regexp_replace(step2, r"(\*\*|__)(.*?)\1", "$2")
    step4 = F.regexp_replace(step3, r"(\*|_)(.*?)\1", "$2")
    # Código en línea `...`
    step5 = F.regexp_replace(step4, r"`(.*?)`", "$1")
    # Encabezados al inicio de línea (# Encabezado)
    step6 = F.regexp_replace(step5, r"(?m)^#{1,6}\s*", "")
    # Viñetas de listas
    step7 = F.regexp_replace(step6, r"(?m)^[\*\-\+]\s+", "")
    return step7


def remove_control_characters(col_name: str) -> Column:
    """
    Elimina caracteres de control ASCII (0x00 a 0x1F y 0x7F a 0x9F).
    Conserva saltos de línea estandarizados si es necesario o los unifica a espacio.
    """
    return F.regexp_replace(F.col(col_name), r"[\x00-\x08\x0B\x0C\x0E-\x1F\x7F-\x9F]", "")


def normalize_unicode_and_spaces(col_name: str) -> Column:
    """
    Normaliza espacios múltiples continuos a un solo espacio y aplica trim.
    Normaliza caracteres Unicode especiales de espacios en blanco (ej. non-breaking space).
    """
    # Sustituye espacios no divisibles por espacios estándar
    unified_spaces = F.regexp_replace(F.col(col_name), r"[\u00A0\u2007\u202F]", " ")
    # Colapsa múltiples espacios/tabs a uno solo
    single_spaces = F.regexp_replace(unified_spaces, r"\s+", " ")
    return F.trim(single_spaces)


def clean_text_column(col_name: str, default_value: str = None) -> Column:
    """
    Aplica el pipeline completo de limpieza textual de forma nativa:
    1. Remoción de HTML y entidades.
    2. Remoción de sintaxis Markdown.
    3. Eliminación de caracteres de control.
    4. Normalización de espacios y trim.
    5. Manejo de nulos y cadenas vacías resultantes.
    """
    raw_col = F.col(col_name)

    cleaned = remove_html_tags(col_name)
    cleaned = F.regexp_replace(cleaned, r"\[(.*?)\]\(.*?\)", "$1")
    cleaned = F.regexp_replace(cleaned, r"!\[.*?\]\(.*?\)", "")
    cleaned = F.regexp_replace(cleaned, r"(\*\*|__)(.*?)\1", "$2")
    cleaned = F.regexp_replace(cleaned, r"(\*|_)(.*?)\1", "$2")
    cleaned = F.regexp_replace(cleaned, r"`(.*?)`", "$1")
    cleaned = F.regexp_replace(cleaned, r"(?m)^#{1,6}\s*", "")
    cleaned = F.regexp_replace(cleaned, r"(?m)^[\*\-\+]\s+", "")
    cleaned = F.regexp_replace(cleaned, r"[\x00-\x08\x0B\x0C\x0E-\x1F\x7F-\x9F]", "")
    cleaned = F.regexp_replace(cleaned, r"[\u00A0\u2007\u202F]", " ")
    cleaned = F.regexp_replace(cleaned, r"\s+", " ")
    cleaned = F.trim(cleaned)

    # Manejo de nulos y strings vacíos convertidos a default_value o None
    replacement = F.lit(default_value) if default_value is not None else F.lit(None).cast("string")
    return F.when(raw_col.isNull() | (cleaned == ""), replacement).otherwise(cleaned)


def validate_schema(df: DataFrame, expected_schema: StructType) -> bool:
    """
    Valida que los nombres y tipos de columnas del DataFrame coincidan con el esquema esperado.
    """
    current_fields = {f.name: f.dataType.simpleString() for f in df.schema.fields}
    expected_fields = {f.name: f.dataType.simpleString() for f in expected_schema.fields}

    missing_cols = set(expected_fields.keys()) - set(current_fields.keys())
    if missing_cols:
        raise ValueError(f"Faltan columnas en el DataFrame: {missing_cols}")

    type_mismatches = []
    for col_name, expected_type in expected_fields.items():
        actual_type = current_fields.get(col_name)
        if actual_type != expected_type:
            type_mismatches.append(f"Columna '{col_name}': esperada '{expected_type}', obtenida '{actual_type}'")

    if type_mismatches:
        raise TypeError(f"Discrepancia en tipos de datos:\n" + "\n".join(type_mismatches))

    return True