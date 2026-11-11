package com.riwi.skillbridge.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuración sensible de LiveKit, proveniente exclusivamente de variables
 * de entorno. Nunca se versionan secretos reales.
 */
@Component
public class LiveKitProperties {

    /** URL pública del servidor LiveKit (por ejemplo wss://livekit.tudominio.com). */
    private final String url;

    /** URL base de la API de administración REST (derivada de la URL pública). */
    private final String apiUrl;

    private final String apiKey;
    private final String apiSecret;

    /** Segundos tras los que LiveKit cierra una sala vacía (margen posterior al límite). */
    private final int roomEmptyTimeoutSeconds;

    /** Tiempo de expiración configurable de los tokens de LiveKit en minutos. */
    private final long tokenTtlMinutes;

    public LiveKitProperties(
        @Value("${app.livekit.url:}") String url,
        @Value("${app.livekit.api-key:}") String apiKey,
        @Value("${app.livekit.api-secret:}") String apiSecret,
        @Value("${app.livekit.room-empty-timeout-seconds:3600}") int roomEmptyTimeoutSeconds,
        @Value("${app.livekit.token-ttl-minutes:120}") long tokenTtlMinutes) {
        this.url = url;
        this.apiUrl = url == null || url.isBlank() ? ""
            : url.replaceFirst("^wss://", "https://").replaceFirst("^ws://", "http://");
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.roomEmptyTimeoutSeconds = roomEmptyTimeoutSeconds;
        this.tokenTtlMinutes = tokenTtlMinutes;
    }

    public LiveKitProperties(String url, String apiKey, String apiSecret, int roomEmptyTimeoutSeconds) {
        this(url, apiKey, apiSecret, roomEmptyTimeoutSeconds, 120L);
    }

    public String getUrl() { return url; }
    public String getApiUrl() { return apiUrl; }
    public String getApiKey() { return apiKey; }
    public String getApiSecret() { return apiSecret; }
    public int getRoomEmptyTimeoutSeconds() { return roomEmptyTimeoutSeconds; }
    public long getTokenTtlMinutes() { return tokenTtlMinutes; }

    public boolean isConfigured() {
        return url != null && !url.isBlank()
            && apiKey != null && !apiKey.isBlank()
            && apiSecret != null && !apiSecret.isBlank();
    }
}
