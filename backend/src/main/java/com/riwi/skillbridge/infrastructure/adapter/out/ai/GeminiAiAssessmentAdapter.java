package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.application.port.out.AiAssessmentPort;
import com.riwi.skillbridge.domain.exception.AiConfigurationException;
import com.riwi.skillbridge.domain.exception.AiNetworkException;
import com.riwi.skillbridge.domain.exception.AiQuotaExceededException;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.AssessmentQuestion;
import com.riwi.skillbridge.domain.model.Offering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeminiAiAssessmentAdapter implements AiAssessmentPort {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiAssessmentAdapter.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public GeminiAiAssessmentAdapter(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AssessmentQuestion> generateQuestions(Offering offering) {
        String prompt = """
                Eres un evaluador técnico y tutor senior de SkillBridge AI.
                Genera un mini-diagnóstico de 3 preguntas de opción múltiple para evaluar si un estudiante cuenta con los conocimientos previos y PRERREQUISITOS fundamentales necesarios para tomar con éxito la siguiente mentoría o servicio profesional:

                Título del servicio: %s
                Categoría: %s
                Descripción: %s

                Reglas estrictas:
                1. Las 3 preguntas deben evaluar PRERREQUISITOS previos (conceptos que el alumno debe saber ANTES de entrar, no el contenido avanzado que va a aprender dentro).
                2. Cada pregunta debe tener exactamente 4 opciones de respuesta claras y concisas.
                3. Solo 1 opción es la correcta (indicada mediante el índice numérico correctOptionIndex de 0 a 3).
                4. Incluye una breve explicación pedagógica (1 o 2 oraciones) indicando por qué esa opción es la correcta y el concepto evaluado.
                5. Responde ÚNICAMENTE con un array JSON válido sin texto adicional previo ni posterior.

                Estructura JSON requerida:
                [
                  {
                    "id": "q1",
                    "question": "Texto de la pregunta...",
                    "options": ["Opción 0", "Opción 1", "Opción 2", "Opción 3"],
                    "correctOptionIndex": 0,
                    "explanation": "Explicación de la respuesta correcta..."
                  },
                  {
                    "id": "q2",
                    "question": "Texto de la pregunta...",
                    "options": ["Opción 0", "Opción 1", "Opción 2", "Opción 3"],
                    "correctOptionIndex": 1,
                    "explanation": "Explicación de la respuesta correcta..."
                  },
                  {
                    "id": "q3",
                    "question": "Texto de la pregunta...",
                    "options": ["Opción 0", "Opción 1", "Opción 2", "Opción 3"],
                    "correctOptionIndex": 2,
                    "explanation": "Explicación de la respuesta correcta..."
                  }
                ]
                """.formatted(offering.name(), offering.categoryId().toString(), offering.detail());

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return parseQuestions(response);
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            String msg = extractErrorDetails(ex);
            if (msg.contains("401") || msg.contains("403") || msg.contains("api_key") || msg.contains("unauthorized") || msg.contains("api key")) {
                throw new AiConfigurationException("Error de configuración: La API Key de Gemini es inválida o no está configurada.");
            } else if (msg.contains("429") || msg.contains("quota") || msg.contains("too many requests") || msg.contains("exhausted") || msg.contains("token") || msg.contains("rate limit") || msg.contains("rate_limit")) {
                throw new AiQuotaExceededException("Se ha excedido el límite de solicitudes o tokens de Google Gemini (429 Too Many Requests). Por favor, espera unos instantes antes de volver a intentar o revisa tu cuota en Google AI Studio.");
            } else if (msg.contains("timeout") || msg.contains("network") || msg.contains("connection") || msg.contains("ioexception") || msg.contains("503") || msg.contains("502") || msg.contains("504")) {
                throw new AiNetworkException("Error de red: El proveedor de IA no está disponible o hubo un problema de conexión.");
            }
            log.error("Error al generar diagnóstico con Gemini: {}", ex.getMessage(), ex);
            throw new BusinessRuleException("Ocurrió un error inesperado al comunicarse con Gemini para el diagnóstico: " + ex.getClass().getSimpleName());
        }
    }

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

    private List<AssessmentQuestion> parseQuestions(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new BusinessRuleException("Gemini no devolvió una respuesta válida para el diagnóstico.");
        }

        String cleaned = rawResponse.trim();
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        
        if (start != -1 && end != -1 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        } else {
            throw new BusinessRuleException("La respuesta de Gemini no contiene un arreglo JSON válido.");
        }

        try {
            List<AssessmentQuestion> questions = objectMapper.readValue(cleaned, new TypeReference<List<AssessmentQuestion>>() {});
            if (questions == null || questions.isEmpty()) {
                throw new BusinessRuleException("El diagnóstico generado no contiene preguntas.");
            }
            return questions;
        } catch (Exception e) {
            log.error("Fallo al parsear JSON devuelto por Gemini: {}. Respuesta: {}", e.getMessage(), cleaned);
            throw new BusinessRuleException("No se pudo interpretar el formato del diagnóstico generado por la IA.");
        }
    }
}

