package com.riwi.skillbridge.infrastructure.adapter.out.livekit;

import com.riwi.skillbridge.application.port.out.LiveKitPort;
import com.riwi.skillbridge.domain.enums.MeetingRole;
import com.riwi.skillbridge.domain.exception.LiveKitIntegrationException;
import com.riwi.skillbridge.infrastructure.config.LiveKitProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adaptador de salida de LiveKit basado en:
 *  - API REST Twirp de administración (RoomService) con autenticación Basic apiKey:apiSecret.
 *  - Tokens de acceso JWT firmados con el secreto de LiveKit (misma biblioteca jjwt
 *    que ya usa el proyecto para su autenticación, pero credenciales independientes).
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
    private final SecretKey signingKey;

    public LiveKitRestAdapter(LiveKitProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
        this.signingKey = Keys.hmacShaKeyFor(secretBytes(properties.getApiSecret()));
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

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Map<String, Object> video = new LinkedHashMap<>();
        video.put("roomJoin", true);
        video.put("room", roomName);
        // Regla 6: el proveedor publica; los participantes solo reciben y envían datos (foro/chat)
        video.put("canPublish", role == MeetingRole.PROVIDER);
        video.put("canSubscribe", true);
        video.put("canPublishData", true);
        video.put("canUpdateOwnMetadata", false);

        return Jwts.builder()
            .issuer(properties.getApiKey())
            .subject(participantIdentity)
            .issuedAt(Date.from(now.toInstant()))
            .notBefore(Date.from(now.minusSeconds(10).toInstant()))
            .expiration(Date.from(expiresAt.toInstant()))
            .claim("name", participantName)
            .claim("metadata", "{\"role\":\"" + role + "\"}")
            .claim("video", video)
            .signWith(signingKey)
            .compact();
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

    /** El secreto de LiveKit se usa como clave HS256; admite formato base64 o texto plano. */
    private static byte[] secretBytes(String secret) {
        if (secret == null) return new byte[0];
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
