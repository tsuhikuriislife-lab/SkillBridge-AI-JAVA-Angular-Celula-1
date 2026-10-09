package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.domain.exception.AiConfigurationException;
import com.riwi.skillbridge.domain.exception.AiNetworkException;
import com.riwi.skillbridge.domain.exception.AiQuotaExceededException;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GeminiAiAdapterTest {

    private ChatClient chatClient;
    private ChatClient.Builder chatClientBuilder;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;
    private GeminiAiAdapter adapter;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        chatClientBuilder = mock(ChatClient.Builder.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        adapter = new GeminiAiAdapter(chatClientBuilder);
    }

    @Test
    void shouldReturnRecommendationWithLinksDirectly() {
        UUID id = UUID.randomUUID();
        Offering offering = new Offering(
                id,
                "Microservicios con Spring Boot",
                "Domina Docker, RabbitMQ y Spring Cloud",
                "BACKEND",
                BigDecimal.valueOf(200000),
                true,
                UUID.randomUUID(),
                LocalTime.of(8, 0),
                LocalTime.of(17, 0),
                "MONDAY",
                "https://example.com/img.jpg"
        );

        String aiResponse = "Te sugiero [Ver curso: Microservicios con Spring Boot](/service/" + id + ").";
        when(callResponseSpec.content()).thenReturn(aiResponse);

        String result = adapter.recommend("Quiero aprender microservicios", List.of(offering));

        assertNotNull(result);
        assertTrue(result.contains("/service/" + id));
        verify(requestSpec).user(argThat((String prompt) -> prompt != null && prompt.contains("/service/" + id) && prompt.contains(offering.title())));
    }

    @Test
    void shouldAppendDirectLinkIfAiMentionedTitleWithoutLink() {
        UUID id = UUID.randomUUID();
        Offering offering = new Offering(
                id,
                "Angular Avanzado",
                "Aprende signals y arquitectura moderna",
                "FRONTEND",
                BigDecimal.valueOf(180000),
                true,
                UUID.randomUUID(),
                LocalTime.of(8, 0),
                LocalTime.of(17, 0),
                "MONDAY",
                "https://example.com/img.jpg"
        );

        // La IA menciona el curso pero no colocó el link en markdown
        String aiResponse = "El curso Angular Avanzado es perfecto para tus metas.";
        when(callResponseSpec.content()).thenReturn(aiResponse);

        String result = adapter.recommend("Quiero ser experto en frontend", List.of(offering));

        assertNotNull(result);
        assertTrue(result.contains("El curso Angular Avanzado es perfecto para tus metas."));
        assertTrue(result.contains("[Ver curso: Angular Avanzado](/service/" + id + ")"));
    }

    @Test
    void shouldThrowAiQuotaExceededExceptionOn429() {
        when(callResponseSpec.content()).thenThrow(new RuntimeException("429 Too Many Requests - Quota exceeded"));

        assertThrows(AiQuotaExceededException.class, () -> adapter.recommend("Aprender Java", List.of()));
    }

    @Test
    void shouldThrowAiConfigurationExceptionOn401() {
        when(callResponseSpec.content()).thenThrow(new RuntimeException("401 Unauthorized - Invalid api_key"));

        assertThrows(AiConfigurationException.class, () -> adapter.recommend("Aprender Java", List.of()));
    }

    @Test
    void shouldThrowAiNetworkExceptionOnTimeout() {
        when(callResponseSpec.content()).thenThrow(new RuntimeException("Connection timeout"));

        assertThrows(AiNetworkException.class, () -> adapter.recommend("Aprender Java", List.of()));
    }
}
