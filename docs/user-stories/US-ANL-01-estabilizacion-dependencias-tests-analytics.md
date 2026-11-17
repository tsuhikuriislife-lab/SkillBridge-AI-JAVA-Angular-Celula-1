# US-ANL-01: Estabilización de Dependencias y Suite de Tests en Analytics

**Estado:** `[ ] Por hacer`

---

## 1. Descripción y Objetivo de Ingeniería
Subsanar la ausencia de la biblioteca `tiktoken` en el entorno de Analytics que causa `ModuleNotFoundError` en `text_chunker.py`. Transformar los scripts de prueba informales en suites automatizadas con `pytest` y validar la consistencia de las versiones de Spark y conectores JDBC.

---

## 2. Criterios de Aceptación y Arquitectura
1. **Gestión de Dependencias:** Añadir `tiktoken` con versión anclada a `analytics-service/requirements.txt` y asegurar su instalación en `analytics-service/Dockerfile`.
2. **Suite Unitaria Formal:** Convertir `analytics-service/tests/test_catalog_chunking.py` en una prueba unitaria estructurada con aserciones `assert` nativas de pytest.
3. **Configuración de Pytest:** Crear `analytics-service/pytest.ini` configurando el descubrimiento de pruebas y paths de importación para ejecución headless.

---

## 3. Componentes Técnicos y Referencias de Implementación
- Ingesta de dependencias Python:
  *(Ver `analytics-service/requirements.txt` línea 1:15)*.
- Script de segmentación semántica dependiente de tokenizer:
  *(Ver `analytics-service/jobs/text_chunker.py` línea 1:25)*.
- Pruebas unitarias de transformaciones de texto:
  *(Ver `analytics-service/tests/test_cleaning_transforms.py` línea 1:40)*.
