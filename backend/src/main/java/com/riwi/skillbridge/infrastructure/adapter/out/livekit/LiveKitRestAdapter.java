package com.riwi.skillbridge.infrastructure.adapter.out.livekit;

import com.riwi.skillbridge.application.port.out.LiveKitPort;
import com.riwi.skillbridge.domain.enums.MeetingRole;
import com.riwi.skillbridge.domain.exception.LiveKitIntegrationException;
import com.riwi.skillbridge.infrastructure.config.LiveKitProperties;
import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adaptador de salida de LiveKit basado en:
 *  - API REST Twirp de administración (RoomService) con autenticación Basic apiKey:apiSecret.
 *  - Tokens de acceso JWT firmados mediante el SDK oficial de LiveKit (AccessToken).
 *
 * Regla 6 (modalidad unilateral): los permisos de publicación se fijan en el token
 * y no dependen de ocultar botones en el frontend.
 */
@Component
public class LiveKitRestAdapter implements LiveKitPort {

    private static final Logger log = LoggerFactory.getLogger(LiveKitRestAdapter.class);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final LiveKitProperties properties;
    private final HttpClient httpClient;

    public LiveKitRestAdapter(LiveKitProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
    }

    @Override
    public String serverUrl() {
        return properties.getUrl();
    }

    @Override
    public void createRoom(String roomName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", roomName);
        body.put("empty_timeout", properties.getRoomEmptyTimeoutSeconds());
        try {
            // CreateRoom es idempotente: si la sala ya existe, LiveKit devuelve la existente
            post("/twirp/livekit.RoomService/CreateRoom", body, false);
        } catch (LiveKitIntegrationException e) {
            log.error("No se pudo crear la sala {}: {}", roomName, e.getMessage());
            throw e;
        }
    }

    @Override
    public void deleteRoom(String roomName) {
        Map<String, Object> body = Map.of("room", roomName);
        try {
            // tolerante a salas inexistentes: el cierre debe ser idempotente
            post("/twirp/livekit.RoomService/DeleteRoom", body, true);
        } catch (LiveKitIntegrationException e) {
            log.warn("DeleteRoom falló para {}: {}", roomName, e.getMessage());
            throw e;
        }
    }

    @Override
    public String createAccessToken(String roomName, String participantIdentity, String participantName,
                                    MeetingRole role, OffsetDateTime expiresAt) {
        if (!properties.isConfigured())
            throw new LiveKitIntegrationException("LiveKit no está configurado");

        AccessToken token = new AccessToken(properties.getApiKey(), properties.getApiSecret());
        token.setIdentity(participantIdentity);
        token.setName(participantName);
        token.setMetadata("{\"role\":\"" + role + "\"}");

        // Expiración calculada según el fin de la sesión o TTL configurado
        Date expirationDate = expiresAt != null 
            ? Date.from(expiresAt.toInstant())
            : new Date(System.currentTimeMillis() + properties.getTokenTtlMinutes() * 60 * 1000L);
        token.setExpiration(expirationDate);

        boolean isProvider = (role == MeetingRole.PROVIDER);

        // Grants definidos según el rol del usuario (Tutor / Proveedor vs Alumno / Participante)
        token.addGrants(
            new RoomJoin(true),
            new RoomName(roomName),
            new io.livekit.server.CanPublish(isProvider),
            new io.livekit.server.CanSubscribe(true),
            new io.livekit.server.CanPublishData(true),
            new io.livekit.server.RoomAdmin(isProvider)
        );

        return token.toJwt();
    }

    // ------------------------------------------------------------------ REST

    private void post(String path, Map<String, Object> body, boolean tolerateNotFound) {
        if (!properties.isConfigured())
            throw new LiveKitIntegrationException("LiveKit no está configurado");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getApiUrl() + path))
                .timeout(HTTP_TIMEOUT)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                if (tolerateNotFound && response.statusCode() == 404) return;
                throw new LiveKitIntegrationException(
                    "LiveKit respondió " + response.statusCode() + ": " + response.body());
            }
        } catch (LiveKitIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new LiveKitIntegrationException("Error de comunicación con LiveKit: " + e.getMessage());
        }
    }

    private String basicAuth() {
        String credentials = properties.getApiKey() + ":" + properties.getApiSecret();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static String toJson(Map<String, Object> body) {
        StringBuilder sb = new StringBuilder("{");
        body.forEach((k, v) -> sb.append('"').append(k).append("\":")
            .append(v instanceof String s ? "\"" + s + "\"" : v.toString()).append(','));
        if (sb.length() > 1) sb.setLength(sb.length() - 1);
        return sb.append('}').toString();
    }
}
