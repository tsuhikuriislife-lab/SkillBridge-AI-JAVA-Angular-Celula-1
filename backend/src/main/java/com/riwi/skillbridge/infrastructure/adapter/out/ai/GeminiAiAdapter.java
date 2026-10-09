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
                .map(o -> "- %s [%s]: %s".formatted(o.title(), o.category(), o.description()))
                .reduce("", (a, b) -> a + "\n" + b);

        String prompt = """
                Eres el asistente de SkillBridge AI. Recomienda como máximo 3 servicios del catálogo
                que ayuden al usuario a lograr su objetivo. Explica brevemente por qué y propone un
                siguiente paso. No inventes servicios que no estén en el catálogo.

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
            return response;
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            String msg = (ex.getMessage() != null) ? ex.getMessage().toLowerCase() : "";
            Throwable cause = ex.getCause();
            while (cause != null) {
                if (cause.getMessage() != null) {
                    msg += " " + cause.getMessage().toLowerCase();
                }
                cause = cause.getCause();
            }

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
