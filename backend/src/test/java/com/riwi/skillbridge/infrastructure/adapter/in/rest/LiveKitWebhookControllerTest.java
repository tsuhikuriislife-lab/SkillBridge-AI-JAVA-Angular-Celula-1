package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.application.port.in.meeting.ProcessLiveKitWebhookUseCase;
import com.riwi.skillbridge.infrastructure.config.LiveKitProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiveKitWebhookControllerTest {

    private static final String API_KEY = "devkey";
    private static final String API_SECRET = "mySecretKeyForLiveKitTestingMustBe32BytesLong!";
    private static final String LIVEKIT_URL = "ws://localhost:7880";

    private ProcessLiveKitWebhookUseCase webhookUseCase;
    private LiveKitProperties properties;
    private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        webhookUseCase = mock(ProcessLiveKitWebhookUseCase.class);
        properties = new LiveKitProperties(LIVEKIT_URL, API_KEY, API_SECRET, 3600);
        objectMapper = new ObjectMapper();
        LiveKitWebhookController controller = new LiveKitWebhookController(webhookUseCase, properties, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private String generateToken(String apiKey, String apiSecret, byte[] body) throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(apiSecret.getBytes(StandardCharsets.UTF_8));
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String sha256 = Base64.getEncoder().encodeToString(md.digest(body));

        return Jwts.builder()
            .issuer(apiKey)
            .claim("sha256", sha256)
            .signWith(key)
            .compact();
    }

    // ---------------------------------------------------------------- CA 3.1: Validación Criptográfica

    @Test
    @DisplayName("CA 3.1: Rechazar petición sin cabecera Authorization con 401 Unauthorized")
    void receive_missingAuthorization_returns401() throws Exception {
        String body = "{\"event\":\"room_started\"}";

        mockMvc.perform(post("/api/webhooks/livekit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(webhookUseCase);
    }

    @Test
    @DisplayName("CA 3.1: Rechazar petición con firma inválida o secreto incorrecto con 401 Unauthorized")
    void receive_invalidSignature_returns401() throws Exception {
        byte[] body = "{\"event\":\"room_started\"}".getBytes(StandardCharsets.UTF_8);
        String wrongSecret = "wrongSecretKeyForLiveKitTestingMustBe32Bytes!";
        String token = generateToken(API_KEY, wrongSecret, body);

        mockMvc.perform(post("/api/webhooks/livekit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(webhookUseCase);
    }

    @Test
    @DisplayName("CA 3.1: Rechazar petición con apiKey distinta a la configurada con 401 Unauthorized")
    void receive_mismatchedApiKey_returns401() throws Exception {
        byte[] body = "{\"event\":\"room_started\"}".getBytes(StandardCharsets.UTF_8);
        String token = generateToken("anotherApiKey", API_SECRET, body);

        mockMvc.perform(post("/api/webhooks/livekit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(webhookUseCase);
    }

    @Test
    @DisplayName("CA 3.1: Rechazar petición con cuerpo manipulado (sha256 mismatch) con 401 Unauthorized")
    void receive_tamperedBody_returns401() throws Exception {
        byte[] originalBody = "{\"event\":\"room_started\",\"room\":{\"name\":\"original\"}}".getBytes(StandardCharsets.UTF_8);
        String token = generateToken(API_KEY, API_SECRET, originalBody);

        byte[] tamperedBody = "{\"event\":\"room_started\",\"room\":{\"name\":\"tampered\"}}".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/webhooks/livekit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tamperedBody))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(webhookUseCase);
    }

    @Test
    @DisplayName("CA 3.1: Rechazar cuando LiveKit no está configurado en properties con 401 Unauthorized")
    void receive_unconfiguredProperties_returns401() throws Exception {
        LiveKitProperties unconfigured = new LiveKitProperties("", "", "", 3600);
        LiveKitWebhookController ctrl = new LiveKitWebhookController(webhookUseCase, unconfigured, objectMapper);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(ctrl).build();

        byte[] body = "{\"event\":\"room_started\"}".getBytes(StandardCharsets.UTF_8);

        mvc.perform(post("/api/webhooks/livekit")
                .header("Authorization", "Bearer validToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(webhookUseCase);
    }

    // ---------------------------------------------------------------- CA 3.2: Ciclo de Vida de Salas

    @Test
    @DisplayName("CA 3.2: Despachar evento room_started al caso de uso de dominio y responder 200 OK")
    void receive_roomStarted_callsDomainUseCase() throws Exception {
        UUID meetingId = UUID.randomUUID();
        String json = """
            {
              "event": "room_started",
              "room": {
                "name": "%s",
                "creationTime": 1729450000
              },
              "createdAt": 1729450000
            }
            """.formatted(meetingId);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String token = generateToken(API_KEY, API_SECRET, body);

        mockMvc.perform(post("/api/webhooks/livekit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());

        ArgumentCaptor<OffsetDateTime> timestampCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(webhookUseCase).handleRoomStarted(eq(meetingId), timestampCaptor.capture());
        assertNotNull(timestampCaptor.getValue());
    }

    @Test
    @DisplayName("CA 3.2: Despachar evento room_finished al caso de uso de dominio y responder 200 OK")
    void receive_roomFinished_callsDomainUseCase() throws Exception {
        UUID meetingId = UUID.randomUUID();
        String json = """
            {
              "event": "room_finished",
              "room": {
                "name": "%s"
              },
              "createdAt": 1729453600
            }
            """.formatted(meetingId);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String token = generateToken(API_KEY, API_SECRET, body);

        mockMvc.perform(post("/api/webhooks/livekit")
                .header("Authorization", token) // Probar también sin prefijo 'Bearer '
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());

        verify(webhookUseCase).handleRoomFinished(eq(meetingId), any(OffsetDateTime.class));
    }

    // ---------------------------------------------------------------- CA 3.3: Seguimiento de Asistencia

    @Test
    @DisplayName("CA 3.3: Despachar evento participant_joined capturando identity y room name")
    void receive_participantJoined_callsDomainUseCase() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String json = """
            {
              "event": "participant_joined",
              "room": {
                "name": "%s"
              },
              "participant": {
                "identity": "%s",
                "joinedAt": 1729450120
              },
              "createdAt": 1729450120
            }
            """.formatted(meetingId, userId);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String token = generateToken(API_KEY, API_SECRET, body);

        mockMvc.perform(post("/api/webhooks/livekit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());

        verify(webhookUseCase).handleParticipantJoined(eq(meetingId), eq(userId), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("CA 3.3: Despachar evento participant_left capturando identity y room name")
    void receive_participantLeft_callsDomainUseCase() throws Exception {
        UUID meetingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String json = """
            {
              "event": "participant_left",
              "room": {
                "name": "%s"
              },
              "participant": {
                "identity": "%s"
              },
              "createdAt": 1729453500
            }
            """.formatted(meetingId, userId);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String token = generateToken(API_KEY, API_SECRET, body);

        mockMvc.perform(post("/api/webhooks/livekit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());

        verify(webhookUseCase).handleParticipantLeft(eq(meetingId), eq(userId), any(OffsetDateTime.class));
    }

    // ---------------------------------------------------------------- Tolerancia y Casos Límite

    @Test
    @DisplayName("Tolerar sala con nombre no UUID respondiendo 200 OK sin lanzar error")
    void receive_nonUuidRoomName_returns200OkGracefully() throws Exception {
        String json = "{\"event\":\"room_started\",\"room\":{\"name\":\"custom-non-uuid-room\"}}";
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String token = generateToken(API_KEY, API_SECRET, body);

        mockMvc.perform(post("/api/webhooks/livekit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());

        verifyNoInteractions(webhookUseCase);
    }

    @Test
    @DisplayName("Tolerar evento desconocido respondiendo 200 OK sin fallar")
    void receive_unknownEvent_returns200OkGracefully() throws Exception {
        UUID meetingId = UUID.randomUUID();
        String json = """
            {
              "event": "track_published",
              "room": {
                "name": "%s"
              }
            }
            """.formatted(meetingId);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String token = generateToken(API_KEY, API_SECRET, body);

        mockMvc.perform(post("/api/webhooks/livekit")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());

        verifyNoInteractions(webhookUseCase);
    }
}

