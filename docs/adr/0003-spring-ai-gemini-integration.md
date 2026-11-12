# ADR-0003: Integración de Google Gemini mediante Spring AI y Arquitectura Hexagonal

## Estado
Aceptado.

## Contexto
SkillBridge AI requiere capacidades de inteligencia artificial generativa para:
1. Recomendar servicios del catálogo basados en metas de los usuarios.
2. Generar diagnósticos técnicos de prerrequisitos (*Mini-Assessments*) a partir de los metadatos de los cursos.

Se necesitaba elegir el proveedor de LLM, el framework de integración y la estrategia para no acoplar el núcleo del negocio a un proveedor específico ni incurrir en costos o bloqueos por límites de cuota (rate limits) en el entorno de evaluación.

## Decisión
1. **Framework de Abstracción:** Utilizar **Spring AI** mediante la interfaz fluida `ChatClient.Builder`. Esto permite tratar a los LLMs como recursos desacoplados configurables vía Spring Boot Starters.
2. **Proveedor y Modelo:** Integrar **Google Gemini**, seleccionando el modelo `gemini-3.1-flash-lite` como modelo predeterminado en entornos de desarrollo y producción. Ofrece latencias sub-segundo, cuota generosa en el tier gratuito y suficiente capacidad de razonamiento para estructuración JSON.
3. **Aislamiento Hexagonal:** Implementar la integración detrás de los puertos de salida de la capa de aplicación (`AiRecommendationPort` y `AiAssessmentPort`). Los adaptadores de infraestructura (`GeminiAiAdapter` y `GeminiAiAssessmentAdapter`) encapsulan los prompts, el formateo de salida y la gestión de fallos.
4. **Manejo Recursivo de Fallos y Cuotas:** Implementar un analizador de causas (`extractErrorDetails`) para capturar códigos anidados de Spring AI (429, 401, 503) y traducirlos a excepciones limpias del dominio (`AiQuotaExceededException`, `AiConfigurationException`, `AiNetworkException`).

## Consecuencias
- **Positivas:**
  - El dominio y los casos de uso no tienen ninguna dependencia de Google Gemini ni de Spring AI. Cambiar a OpenAI, Anthropic o un modelo local (vLLM/Ollama) requiere únicamente crear un nuevo adaptador en `infrastructure`.
  - La clave `GEMINI_API_KEY` permanece 100% aislada en el backend, cumpliendo la política de seguridad estricta de cero secretos en el frontend.
  - La tasa de errores 429 disminuyó drásticamente gracias al modelo Flash-Lite y a la captura transparente de cuotas agotadas.
- **Negativas / Desafíos:**
  - Spring AI abstrae las respuestas como cadenas de texto, por lo que el parsing de esquemas JSON estructurados requiere sanitización adicional y tolerancia a bloques de formato markdown.
