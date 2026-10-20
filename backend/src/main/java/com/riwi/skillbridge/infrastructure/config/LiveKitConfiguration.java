package com.riwi.skillbridge.infrastructure.config;

import io.livekit.server.RoomServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LiveKitConfiguration {

    @Value("${app.livekit.url:http://livekit:7880}")
    private String url;

    @Value("${app.livekit.api-key:}")
    private String apiKey;

    @Value("${app.livekit.api-secret:}")
    private String apiSecret;

    public String getUrl() {
        return url;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    /**
     * Bean opcional del cliente de LiveKit para interactuar con la API
     * (crear/cerrar salas, listar participantes, etc. desde el backend).
     */
    @Bean
    public RoomServiceClient roomServiceClient() {
        return RoomServiceClient.create(url, apiKey, apiSecret);
    }
}
