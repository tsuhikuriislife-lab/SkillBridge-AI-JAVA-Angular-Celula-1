# Arquitectura y Diseño del Subsistema de Inteligencia Artificial (IA)

## 1. Visión General y Propósito

El subsistema de Inteligencia Artificial de **SkillBridge AI** integra modelos de lenguaje fundacionales (LLMs) de Google (familia **Google Gemini**) para enriquecer la experiencia de estudiantes y mentores mediante dos capacidades clave:

1. **Recomendador Semántico de Cursos y Mentorías (`/api/ai/recommendations`):** 
   Analiza metas profesionales en lenguaje natural del estudiante y devuelve sugerencias personalizadas, ancladas (*grounded*) estrictamente en el catálogo disponible, con enlaces directos para agilizar la inscripción sin fricción ni búsquedas manuales.
2. **Diagnóstico Técnico Previo Automatizado (*Mini-Assessment*, `/api/assessments/*`):**
   Genera dinámicamente evaluaciones diagnósticas de opción múltiple orientadas a evaluar los **prerrequisitos formativos** de un servicio específico. Evalúa las respuestas del alumno en tiempo real, calcula el puntaje, otorga retroalimentación pedagógica detallada por pregunta y emite recomendaciones de preparación.

---

## 2. Arquitectura Hexagonal y Desacoplamiento

El subsistema sigue estrictamente el patrón **Ports & Adapters (Arquitectura Hexagonal)**. Ninguna librería de Spring AI, cliente HTTP de Google o SDK propietario contamina el dominio o la capa de aplicación.

```text
               CAPA INBOUND (REST)
       [AiController]      [AssessmentController]
              |                      |
              v                      v
       ================================================
                     CAPA DE APLICACIÓN
       [GenerateRecommendationUseCase]  [GenerateAssessmentUseCase]
                                       [EvaluateAssessmentUseCase]
                     |                      |
             [AiRecommendationService]  [AssessmentService]
       ================================================
                     PUERTOS DE SALIDA (Ports)
       [AiRecommendationPort]   [AiAssessmentPort]  [AssessmentSessionCachePort]
              |                         |                      |
       ================================================
                   CAPA DE INFRAESTRUCTURA
              |                         |                      |
              v                         v                      v
      [GeminiAiAdapter]     [GeminiAiAssessmentAdapter] [RedisAssessmentCacheAdapter]
              \                         /                      |
           (Spring AI ChatClient / Gemini API)        (Redis 8 / Local Fallback)
```

### Reglas de Dependencia
- **Dominio (`domain`):** Define modelos inmutables (`TechnicalAssessment`, `AssessmentQuestion`, `AssessmentEvaluation`, `AssessmentQuestionResult`) y excepciones de negocio (`AiConfigurationException`, `AiQuotaExceededException`, `AiNetworkException`). No tiene dependencias de Spring AI ni anotaciones de infraestructura.
- **Aplicación (`application`):** Define los puertos de entrada (`GenerateRecommendationUseCase`, `GenerateAssessmentUseCase`, `EvaluateAssessmentUseCase`) y salida (`AiRecommendationPort`, `AiAssessmentPort`, `AssessmentSessionCachePort`), coordinando la lógica de negocio y las reglas de aprobación (umbral del 60%).
- **Infraestructura (`infrastructure`):** Implementa los adaptadores con `ChatClient` de Spring AI y los adaptadores de caché en Redis. Si se reemplaza Gemini por OpenAI, Anthropic Claude o un modelo local vía Ollama/vLLM, solo se sustituyen los adaptadores sin tocar el núcleo de la aplicación.

---

## 3. Componentes y Flujos de Información

### 3.1. Recomendador de Catálogo con Enlaces Directos (Grounding)

El servicio `AiRecommendationService` interactúa con el catálogo activo a través de `OfferingPort`.

```text
Usuario (Angular) ──> POST /api/ai/recommendations { "goal": "Aprender Spring Boot..." }
                               │
                               ▼
                   [AiRecommendationService]
                               │ Obtiene catálogo activo (OfferingPort)
                               ▼
                      [GeminiAiAdapter]
                               │ Construye Prompt con catálogo anclado
                               ▼
                   Google Gemini (LLM API)
                               │ Genera recomendación en Markdown con links
                               ▼
                     Post-procesamiento de Seguridad
      (Garantiza que ningún curso mencionado quede sin link directo /service/:id)
                               │
                               ▼
                       Respuesta al Cliente
```

#### Estrategia Anti-Alucinación (Grounding)
1. **Inyección en el Contexto:** Solo se inyectan los servicios activos con sus IDs, códigos, nombres y resúmenes.
2. **Instrucción Estricta en el Prompt:**
   ```text
   Eres el asistente virtual experto de SkillBridge AI. Recomienda como máximo 3 cursos o servicios del catálogo
   que ayuden al usuario a lograr su objetivo... No inventes cursos ni servicios que no estén en el catálogo disponible.
   Por cada curso, es OBLIGATORIO incluir: [Ver curso: NOMBRE](/service/ID)
   ```
3. **Escaneo Determinista de Respaldo:** El adaptador `GeminiAiAdapter` analiza la respuesta generada por la IA; si el modelo mencionó un curso existente pero omitió el enlace directo Markdown, el backend añade automáticamente una sección de enlaces verificados al final de la respuesta.

---

### 3.2. Diagnóstico Técnico Previo (*Mini-Assessment*)

El diagnóstico previo asegura que los estudiantes posean los fundamentos necesarios antes de inscribirse o pagar una mentoría avanzada.

#### Ciclo de Vida del Diagnóstico:
1. **Generación (`POST /api/assessments/offerings/{offeringId}`):**
   - El servicio recupera los metadatos del servicio ofertado (nombre, categoría, temario).
   - `GeminiAiAssessmentAdapter` solicita a Gemini 3 preguntas de opción múltiple focalizadas en **prerrequisitos previos** (conceptos que debe dominar *antes* de ingresar).
   - El resultado se valida y parsea hacia un `TechnicalAssessment` con un `UUID` de sesión único.
   - La sesión completa (incluyendo respuestas correctas y explicaciones) se almacena en caché temporal con TTL de 30 minutos.
   - **Proyección de Seguridad Anti-Trampas:** El endpoint responde al frontend con `ClientAssessmentResponse`, el cual **elimina** el índice de respuesta correcta (`correctOptionIndex`) y las explicaciones. El alumno jamás recibe las respuestas en el payload de red.

2. **Ejecución en el Cliente:**
   - La interfaz de Angular presenta un modal interactivo paso a paso con barra de progreso y opciones de selección.

3. **Envío y Calificación (`POST /api/assessments/{assessmentId}/submit`):**
   - El cliente envía el mapa de respuestas seleccionadas: `{"answers": {"q1": 0, "q2": 2, "q3": 1}}`.
   - `AssessmentService` recupera la sesión desde el caché (`AssessmentSessionCachePort`). Si ha expirado, responde `404 Not Found`.
   - El motor de calificación compara cada respuesta, calcula el puntaje, evalúa el umbral de aprobación ($\ge 60\%$) y genera la retroalimentación cualitativa y pedagógica.

```text
Cliente                        Backend                          Redis / Fallback         Gemini API
   │                              │                                     │                    │
   │── POST /assessments/... ────>│                                     │                    │
   │                              │── generateQuestions(offering) ──────────────────────────>│
   │                              │<── Array JSON estructurado ──────────────────────────────│
   │                              │── save(TechnicalAssessment) ───────>│                    │
   │<── ClientAssessmentResponse ─│  (Sin respuestas correctas)         │                    │
   │                              │                                     │                    │
   │  (Usuario responde preguntas)│                                     │                    │
   │                              │                                     │                    │
   │── POST /.../submit ─────────>│                                     │                    │
   │                              │── findById(assessmentId) ──────────>│                    │
   │                              │<── TechnicalAssessment completo ────│                    │
   │                              │    (Calificación & Feedback)        │                    │
   │<── AssessmentEvaluation ─────│                                     │                    │
```

---

## 4. Estrategia de Caché de Sesiones y Resiliencia en Dos Niveles

El almacenamiento de las sesiones de evaluación utiliza una arquitectura de caché en dos niveles (**Dual-Tier Caching**) en `RedisAssessmentCacheAdapter`:

```text
                 [Solicitud de Guardado / Lectura]
                                 │
                 ┌───────────────┴───────────────┐
                 ▼                               ▼
       Nivel 1: Redis 8             Nivel 2: Local Memory Fallback
   (Distribuido, TTL: 30 min)          (ConcurrentHashMap thread-safe)
   Clave: assessment:session:{id}       Expira por timestamp en memoria
```

### Justificación de Ingeniería:
- **Disponibilidad Continua:** Si la instancia de Redis se reinicia, satura o pierde conexión temporalmente, el sistema conmuta de forma transparente al mapa concurrente en memoria local (`localFallback`).
- **TTL Acotado:** Las sesiones expiran por defecto a los 30 minutos (`app.cache.assessment-ttl-minutes=30`), evitando fugas de memoria o acumulación de sesiones obsoletas.

---

## 5. Ingeniería de Prompts y Extracción de JSON Estructurado

### Prompt del Diagnóstico Técnico
El adaptador `GeminiAiAssessmentAdapter` utiliza el siguiente contrato de prompt:
```text
Eres un evaluador técnico y tutor senior de SkillBridge AI.
Genera un mini-diagnóstico de 3 preguntas de opción múltiple para evaluar si un estudiante cuenta con los conocimientos previos y PRERREQUISITOS fundamentales necesarios para tomar con éxito la siguiente mentoría o servicio profesional:

Título del servicio: {name}
Categoría: {categoryId}
Descripción: {detail}

Reglas estrictas:
1. Las 3 preguntas deben evaluar PRERREQUISITOS previos (conceptos que el alumno debe saber ANTES de entrar, no el contenido avanzado que va a aprender dentro).
2. Cada pregunta debe tener exactamente 4 opciones de respuesta claras y concisas.
3. Solo 1 opción es la correcta (indicada mediante el índice numérico correctOptionIndex de 0 a 3).
4. Incluye una breve explicación pedagógica (1 o 2 oraciones) indicando por qué esa opción es la correcta y el concepto evaluado.
5. Responde ÚNICAMENTE con un array JSON válido sin texto adicional previo ni posterior.
```

### Parser Robusto Tolerante a Formato Markdown
Muchos LLMs envuelven sus respuestas en bloques de código ````json ... ```` o agregan texto de cortesía. El método `parseQuestions` procesa la respuesta en crudo:
1. Localiza el primer corchete `[` y el último corchete `]`.
2. Extrae únicamente el substring delimitado.
3. Deserializa mediante Jackson `ObjectMapper` hacia `List<AssessmentQuestion>`.
4. Valida que el array contenga preguntas no nulas con 4 opciones cada una.

---

## 6. Manejo de Errores, Excepciones y Gobernanza de Cuotas

### Análisis Recursivo de la Cadena de Causas (`extractErrorDetails`)
Spring AI encapsula los errores de los SDKs de Google en `NonTransientAiException` o `ClientException`. Para evitar que errores transitorios se expongan al usuario como fallos genéricos 500, los adaptadores recorren recursivamente el grafo de excepciones (`getCause()`):

```java
private String extractErrorDetails(Throwable throwable) {
    StringBuilder sb = new StringBuilder();
    Throwable current = throwable;
    while (current != null) {
        if (current.getMessage() != null) {
            sb.append(" ").append(current.getMessage().toLowerCase());
        }
        current = current.getCause();
    }
    return sb.toString();
}
```

### Matriz de Mapeo de Excepciones

| Causa Raíz Detectada | Excepción de Dominio | Código HTTP | Mensaje al Usuario / Acción |
|---|---|---|---|
| Códigos `401`, `403`, `api_key`, `unauthorized` | `AiConfigurationException` | **503 Service Unavailable** | Informa que la clave API no está configurada o es inválida sin exponer secretos. |
| Códigos `429`, `quota`, `rate_limit`, `too many requests` | `AiQuotaExceededException` | **429 Too Many Requests** | Notifica que se ha alcanzado la cuota de peticiones por minuto/día; solicita reintentar en unos instantes. |
| Códigos `timeout`, `network`, `connection`, `502`, `503`, `504` | `AiNetworkException` | **503 Service Unavailable** | Informa problema de conectividad temporal con el proveedor de IA. |
| Cualquier otra falla inesperada de formato | `BusinessRuleException` | **400 Bad Request / 422** | Retorna el detalle seguro de la regla incumplida. |

---

## 7. Selección del Modelo y Optimización de Costos

### Elección del Modelo: `gemini-3.1-flash-lite`
Inicialmente se evaluaron modelos como `gemini-3.8-flash` y `gemini-1.5-pro`. Sin embargo, en entornos de desarrollo y evaluación:
- Modelos avanzados imponen límites estrictos en el nivel gratuito (e.g. 20 solicitudes/día en ciertos endpoints experimentales).
- `gemini-3.1-flash-lite` proporciona:
  1. **Baja Latencia (< 1.2 segundos):** Fundamental para la generación interactiva del test en el modal de compra.
  2. **Alto Rendimiento y Cuota Generosa:** Permite ejecutar pruebas de carga y múltiples diagnósticos sin alcanzar el error 429.
  3. **Menor Consumo de Tokens:** Suficiente capacidad de razonamiento para la clasificación de prerrequisitos y recomendaciones contextuales.

### Configuración en Variables de Entorno (`.env` / `application.yml`):
```yaml
spring:
  ai:
    gemini:
      api-key: ${GEMINI_API_KEY}
      chat:
        options:
          model: ${GEMINI_MODEL:gemini-3.1-flash-lite}
          temperature: 0.3 # Temperatura baja para respuestas consistentes y precisas
```

---

## 8. Seguridad y Privacidad de Datos (Data Privacy)

1. **Cero Secretos en el Frontend (Regla Mandatoria):** La clave `GEMINI_API_KEY` reside exclusivamente en las variables de entorno del contenedor Docker del backend. Angular jamás se comunica directamente con la API de Google.
2. **Minimización de Datos (Data Minimization):** Ninguna información personal identificable (PII), nombre de usuario, contraseña, dirección de correo o token JWT es enviado en los prompts hacia Google Gemini. Solo se transmiten el objetivo educativo (`goal`) y los identificadores/títulos de servicios del catálogo público.
3. **Validación de Identidad y Roles:** El endpoint de recomendaciones `/api/ai/recommendations` requiere autenticación JWT (`CUSTOMER` o `PROVIDER`). Los diagnósticos técnicos son públicos para incentivar la conversión antes de la compra, pero están protegidos contra abuso mediante Rate Limiting e invalidación de sesión.
