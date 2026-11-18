# US-05: Asistente de Recomendaciones Semánticas con Google Gemini

**Estado:** `[✅] Terminada`

---

## 1. Descripción y Objetivo de Ingeniería
Integrar capacidades generativas mediante Spring AI para analizar los intereses y objetivos del usuario y generar recomendaciones personalizadas de mentorías a partir del catálogo activo, protegiendo las credenciales en backend.

---

## 2. Arquitectura de la Solución (Hexagonal & AI)
1. **Puerto de Entrada:** `AiController` expone `POST /api/ai/recommendations`.
2. **Adaptador de Salida AI:** `GeminiAiAdapter` utiliza `ChatClient` parametrizado con prompt de sistema estricto para estructurar respuestas en formato JSON.

---

## 3. Componentes Técnicos y Referencias de Implementación
- Adaptador de Spring AI Gemini:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiAiAdapter.java` línea 25:75)*.
- Controlador de recomendaciones:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AiController.java` línea 20:45)*.
