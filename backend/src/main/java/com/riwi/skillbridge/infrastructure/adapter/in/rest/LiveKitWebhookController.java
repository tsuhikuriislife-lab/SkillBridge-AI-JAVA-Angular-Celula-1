package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.service.MeetingService;
import com.riwi.skillbridge.domain.enums.MeetingEventType;
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
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Webhook de LiveKit: mantiene sincronizado el seguimiento de participación.
 * LiveKit firma cada entrega con un JWT (Authorization: Bearer) usando el secreto
 * compartido; se rechaza cualquier payload sin firma válida.
 */
@RestController
@RequestMapping("/api/webhooks/livekit")
public class LiveKitWebhookController {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookController.class);

    private final MeetingService meetingService;
    private final LiveKitProperties properties;
    private final SecretKey signingKey;

    public LiveKitWebhookController(MeetingService meetingService, LiveKitProperties properties) {
        this.meetingService = meetingService;
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(secretBytes(properties.getApiSecret()));
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @RequestBody Map<String, Object> payload) {
        if (!properties.isConfigured() || !verify(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            String event = (String) payload.get("event");
            @SuppressWarnings("unchecked")
            Map<String, Object> room = (Map<String, Object>) payload.getOrDefault("room", Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> participant = (Map<String, Object>) payload.getOrDefault("participant", Map.of());
            String roomName = (String) room.get("name");
            String identity = (String) participant.get("identity");

            if (roomName == null || identity == null) return ResponseEntity.ok().build();
            UUID meetingId = UUID.fromString(roomName);
            UUID userId = UUID.fromString(identity);

            switch (event == null ? "" : event) {
                case "participant_joined" ->
                    meetingService.registerWebhookEvent(meetingId, userId, MeetingEventType.PARTICIPANTE_INGRESO);
                case "participant_left" ->
                    meetingService.registerWebhookEvent(meetingId, userId, MeetingEventType.PARTICIPANTE_SALIO);
                default -> log.debug("Evento de LiveKit ignorado: {}", event);
            }
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            // sala o identidad que no corresponden al formato interno; se acusa recibo
            return ResponseEntity.ok().build();
        }
    }

    private boolean verify(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return false;
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(authorization.substring(7)).getPayload();
            return properties.getApiKey().equals(claims.getIssuer());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Webhook de LiveKit con firma inválida: {}", e.getMessage());
            return false;
        }
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
