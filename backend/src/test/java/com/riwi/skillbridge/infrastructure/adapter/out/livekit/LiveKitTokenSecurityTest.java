package com.riwi.skillbridge.infrastructure.adapter.out.livekit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.domain.enums.MeetingRole;
import com.riwi.skillbridge.infrastructure.config.LiveKitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CA 6.1 - Validación de Permisos y Ciclo de Vida de Tokens JWT LiveKit")
class LiveKitTokenSecurityTest {

    private LiveKitRestAdapter liveKitAdapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String API_KEY = "devkey";
    private static final String API_SECRET = "secret_dev_key_skillbridge_livekit_2026";
    private static final int TOKEN_TTL_MINUTES = 120; // 2 horas según especificación

    @BeforeEach
    void setUp() {
        LiveKitProperties properties = new LiveKitProperties(
            "http://localhost:7880",
            API_KEY,
            API_SECRET,
            3600,
            TOKEN_TTL_MINUTES
        );
        liveKitAdapter = new LiveKitRestAdapter(properties);
    }

    @Test
    @DisplayName("CA 6.1: Token de Tutor debe contener canPublish=true, roomAdmin=true y expirar en ~2 horas")
    void shouldGenerateValidTokenForTutorWithAdminAndPublishGrants() throws Exception {
        // Arrange
        String roomName = "sala-qa-tutor-001";
        String tutorId = "tutor-123";
        String tutorName = "Profesor John Doe";
        long nowSeconds = Instant.now().getEpochSecond();

        // Act
        String tokenJwt = liveKitAdapter.createAccessToken(
            roomName,
            tutorId,
            tutorName,
            MeetingRole.PROVIDER,
            null // Usa el TTL por defecto de 120 minutos
        );

        assertNotNull(tokenJwt, "El token JWT no debe ser nulo");

        // Decodificar el payload del JWT
        JsonNode claims = decodeJwtPayload(tokenJwt);

        // Assert - Identidad y emisor
        assertEquals(API_KEY, claims.path("iss").asText());
        assertEquals(tutorId, claims.path("sub").asText());
        assertEquals(tutorName, claims.path("name").asText());

        // Assert - Grants de video
        JsonNode videoGrant = claims.path("video");
        assertTrue(videoGrant.path("roomJoin").asBoolean(), "Debe permitir unirse a la sala");
        assertEquals(roomName, videoGrant.path("room").asText(), "Debe coincidir con la sala solicitada");
        assertTrue(videoGrant.path("canPublish").asBoolean(), "El Tutor DEBE tener canPublish=true");
        assertTrue(videoGrant.path("roomAdmin").asBoolean(), "El Tutor DEBE tener roomAdmin=true");
        assertTrue(videoGrant.path("canSubscribe").asBoolean());

        // Assert - Expiración en 2 horas (tolerancia de +- 10 segundos)
        long expSeconds = claims.path("exp").asLong();
        long expectedExpSeconds = nowSeconds + (TOKEN_TTL_MINUTES * 60L);
        assertTrue(Math.abs(expSeconds - expectedExpSeconds) <= 10,
            "El token debe expirar aproximadamente en 2 horas (120 minutos)");
    }

    @Test
    @DisplayName("CA 6.1 (Contraste): Token de Alumno debe tener canPublish=false y roomAdmin=false")
    void shouldGenerateRestrictedTokenForStudent() throws Exception {
        // Arrange
        String roomName = "sala-qa-alumno-001";
        String studentId = "student-456";
        String studentName = "Alumno Jane Doe";

        // Act
        String tokenJwt = liveKitAdapter.createAccessToken(
            roomName,
            studentId,
            studentName,
            MeetingRole.PARTICIPANT,
            null
        );

        // Decodificar el payload
        JsonNode claims = decodeJwtPayload(tokenJwt);
        JsonNode videoGrant = claims.path("video");

        // Assert - Grants restringidos
        assertFalse(videoGrant.path("canPublish").asBoolean(), "El Alumno NO debe publicar por defecto");
        assertFalse(videoGrant.path("roomAdmin").asBoolean(), "El Alumno NO debe ser roomAdmin");
        assertTrue(videoGrant.path("canSubscribe").asBoolean(), "El Alumno sí debe escuchar/ver");
        assertTrue(videoGrant.path("canPublishData").asBoolean(), "El Alumno sí puede chatear");
    }

    /**
     * Utilidad para decodificar la sección Payload (segundo segmento) del JWT en JSON.
     */
    private JsonNode decodeJwtPayload(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length, "El JWT debe tener 3 partes (header.payload.signature)");
        byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
        String payloadJson = new String(decodedBytes, StandardCharsets.UTF_8);
        return objectMapper.readTree(payloadJson);
    }
}
