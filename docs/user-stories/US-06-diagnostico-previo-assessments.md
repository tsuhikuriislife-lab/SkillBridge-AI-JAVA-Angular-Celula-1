# US-06: Diagnóstico Técnico Previo con IA y Detección de Prerrequisitos

**Estado:** `[✅] Terminada`

---

## 1. Descripción y Objetivo de Ingeniería
Ofrecer evaluaciones técnicas dinámicas de prerrequisitos antes de confirmar una mentoría, evaluando las respuestas del usuario mediante IA con sistema de caché de sesión en dos niveles y protección contra trampas.

---

## 2. Arquitectura de la Solución
1. **Generación Dinámica:** Generación de preguntas técnicas contextualizadas según la oferta seleccionada.
2. **Evaluación y Feedback:** Calificación automática y generación de roadmap de nivelación en caso de insuficiencia.

---

## 3. Componentes Técnicos y Referencias de Implementación
- Caso de uso de evaluaciones:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/application/service/AssessmentService.java` línea 25:90)*.
- Controlador REST de assessments:
  *(Ver `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AssessmentController.java` línea 20:55)*.
