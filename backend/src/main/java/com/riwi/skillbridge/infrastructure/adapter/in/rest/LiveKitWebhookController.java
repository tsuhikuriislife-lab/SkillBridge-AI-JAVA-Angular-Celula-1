package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.application.port.in.meeting.ProcessLiveKitWebhookUseCase;
import com.riwi.skillbridge.infrastructure.config.LiveKitProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Webhook de LiveKit: mantiene sincronizado en tiempo real el ciclo de vida
 * de las salas y el seguimiento de asistencia de participantes (HU-29.3).
 *
 * LiveKit firma criptográficamente cada entrega con un JWT (Authorization)
 * usando el secreto compartido y adjunta en el claim 'sha256' el hash del cuerpo.
 * Se rechaza cualquier petición sin firma válida o con payload manipulado (HTTP 401).
 */
@RestController
@RequestMapping("/api/webhooks/livekit")
public class LiveKitWebhookController {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookController.class);

    private final ProcessLiveKitWebhookUseCase webhookUseCase;
    private final LiveKitProperties properties;
    private final SecretKey signingKey;
    private final ObjectMapper objectMapper;

    public LiveKitWebhookController(ProcessLiveKitWebhookUseCase webhookUseCase,
                                    LiveKitProperties properties,
                                    ObjectMapper objectMapper) {
        this.webhookUseCase = webhookUseCase;
        this.properties = properties;
        byte[] secret = secretBytes(properties.getApiSecret());
        this.signingKey = secret.length >= 32 ? Keys.hmacShaKeyFor(secret) : null;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @RequestBody(required = false) byte[] rawBody) {
        byte[] body = rawBody != null ? rawBody : new byte[0];

        if (!properties.isConfigured() || !verify(authorization, body)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || root.isEmpty()) {
                return ResponseEntity.ok().build();
            }

            String event = root.path("event").asText(null);
            JsonNode room = root.path("room");
            JsonNode participant = root.path("participant");

            String roomName = room.path("name").asText(null);
            if (roomName == null || roomName.isBlank()) {
                log.debug("Webhook recibido sin nombre de sala. Evento: {}", event);
                return ResponseEntity.ok().build();
            }

            UUID meetingId = UUID.fromString(roomName);
            OffsetDateTime timestamp = extractTimestamp(root);
            String identity = participant.path("identity").asText(null);

            switch (event == null ? "" : event) {
                case "room_started" ->
                    webhookUseCase.handleRoomStarted(meetingId, timestamp);
                case "room_finished" ->
                    webhookUseCase.handleRoomFinished(meetingId, timestamp);
                case "participant_joined" -> {
                    if (identity != null && !identity.isBlank()) {
                        webhookUseCase.handleParticipantJoined(meetingId, UUID.fromString(identity), timestamp);
                    }
                }
                case "participant_left" -> {
                    if (identity != null && !identity.isBlank()) {
                        webhookUseCase.handleParticipantLeft(meetingId, UUID.fromString(identity), timestamp);
                    }
                }
                default -> log.debug("Evento de LiveKit ignorado: {}", event);
            }

            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.debug("Sala o identidad no corresponden a UUID interno: {}", e.getMessage());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("Error procesando payload de LiveKit: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }
    }

    private boolean verify(String authorization, byte[] rawBody) {
        if (signingKey == null || authorization == null || authorization.isBlank()) {
            return false;
        }

        String token = authorization.startsWith("Bearer ")
            ? authorization.substring(7).trim()
            : authorization.trim();

        if (token.isEmpty()) {
            return false;
        }

        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            if (!properties.getApiKey().equals(claims.getIssuer())) {
                log.warn("Webhook LiveKit: issuer '{}' no coincide con apiKey configurada", claims.getIssuer());
                return false;
            }

            // Validación criptográfica del cuerpo contra el claim sha256 (CA 3.1)
            String claimSha256 = claims.get("sha256", String.class);
            if (claimSha256 != null && !claimSha256.isBlank()) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(rawBody);
                String computedBase64 = Base64.getEncoder().encodeToString(digest);
                String computedHex = HexFormat.of().formatHex(digest);

                if (!claimSha256.equals(computedBase64) && !claimSha256.equalsIgnoreCase(computedHex)) {
                    log.warn("Webhook LiveKit: sha256 mismatch. claim={}, computed={}", claimSha256, computedBase64);
                    return false;
                }
            }

            return true;
        } catch (JwtException | IllegalArgumentException | NoSuchAlgorithmException e) {
            log.warn("Webhook de LiveKit con firma inválida o error criptográfico: {}", e.getMessage());
            return false;
        }
    }

    private OffsetDateTime extractTimestamp(JsonNode root) {
        long createdAt = root.path("createdAt").asLong(0);
        if (createdAt <= 0) {
            createdAt = root.path("room").path("creationTime").asLong(0);
        }
        if (createdAt > 0) {
            Instant instant = createdAt > 100_000_000_000L
                ? Instant.ofEpochMilli(createdAt)
                : Instant.ofEpochSecond(createdAt);
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static byte[] secretBytes(String secret) {
        if (secret == null) return new byte[0];
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
