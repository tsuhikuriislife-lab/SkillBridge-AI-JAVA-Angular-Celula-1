package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.domain.exception.AiConfigurationException;
import com.riwi.skillbridge.domain.exception.AiNetworkException;
import com.riwi.skillbridge.domain.exception.AiQuotaExceededException;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeminiAiAdapter implements AiRecommendationPort {
    private final ChatClient chatClient;

    public GeminiAiAdapter(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String recommend(String goal, List<Offering> offerings) {
        String catalog = offerings.stream()
                .map(o -> "- ID: %s | Título: \"%s\" [%s] | Enlace directo: /service/%s | Descripción: %s"
                        .formatted(
                                o.id(),
                                o.name(),
                                o.code(),
                                o.id(),
                                o.shortDescription() != null && !o.shortDescription().isBlank()
                                        ? o.shortDescription()
                                        : (o.detail() != null ? o.detail() : "Sin descripción")
                        ))
                .reduce("", (a, b) -> a + "\n" + b);

        String prompt = """
                Eres el asistente virtual experto de SkillBridge AI. Recomienda como máximo 3 cursos o servicios del catálogo
                que ayuden al usuario a lograr su objetivo. Explica de forma clara, motivadora y concisa por qué cada curso le conviene
                y propone un siguiente paso práctico. No inventes cursos ni servicios que no estén en el catálogo disponible.

                INSTRUCCIONES OBLIGATORIAS DE ENLACES DIRECTOS:
                1. Por cada servicio o curso que recomiendes del catálogo, es OBLIGATORIO que incluyas su enlace directo en formato Markdown exactamente así:
                   [Ver curso: NOMBRE DEL CURSO](/service/ID)
                   (utilizando el ID y el Nombre exactos del catálogo).
                2. Al final de tu recomendación, añade siempre una nota destacada recordando al usuario que puede hacer clic directamente en el enlace o en la notificación de abajo para ir a la vista detallada del curso e iniciar su inscripción sin tener que buscarlo manualmente por nombre.

                Objetivo del usuario:
                %s

                Catálogo disponible:
                %s
                """.formatted(goal, catalog);

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            if (response == null || response.isBlank()) {
                throw new BusinessRuleException("Gemini no devolvió una respuesta válida");
            }

            // Respaldo de seguridad: si Gemini mencionó el nombre de un curso pero omitió el enlace directo /service/ID,
            // garantizamos que se anexe al final para asegurar la navegación directa sin búsqueda manual.
            String finalResponse = response;
            List<Offering> mentionedWithoutLink = offerings.stream()
                    .filter(o -> finalResponse.toLowerCase().contains(o.name().toLowerCase()))
                    .filter(o -> !finalResponse.contains("/service/" + o.id()))
                    .toList();

            if (!mentionedWithoutLink.isEmpty()) {
                StringBuilder appendix = new StringBuilder();
                appendix.append("\n\n---\n**Enlaces directos a los cursos recomendados:**\n");
                for (Offering o : mentionedWithoutLink) {
                    appendix.append("- [Ver curso: %s](/service/%s)\n".formatted(o.name(), o.id()));
                }
                appendix.append("\n*Haz clic en el enlace para ir directamente a la vista del curso sin tener que buscarlo manualmente por su nombre.*");
                response += appendix.toString();
            }

            return response;
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
            throw new BusinessRuleException("Ocurrió un error inesperado al comunicarse con Gemini: " + ex.getClass().getSimpleName());
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
}
