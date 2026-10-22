package com.riwi.skillbridge.infrastructure.adapter.out.livekit;

import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels.Room;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import retrofit2.Response;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("CA 6.2 & CA 6.3 - Integración Automatizada con LiveKit RoomServiceClient")
class LiveKitRoomIntegrationTest {

    private static final String API_KEY = "devkey";
    private static final String API_SECRET = "secret_dev_key_skillbridge_livekit_2026";
    private static final int LIVEKIT_HTTP_PORT = 7880;

    @Container
    static GenericContainer<?> livekitContainer = new GenericContainer<>(
            DockerImageName.parse("livekit/livekit-server:v1.8")
    )
            .withCommand("--dev", "--keys", API_KEY + ": " + API_SECRET)
            .withExposedPorts(LIVEKIT_HTTP_PORT);

    private static RoomServiceClient roomServiceClient;

    @BeforeAll
    static void setUpAll() {
        String livekitHost = livekitContainer.getHost();
        Integer mappedPort = livekitContainer.getMappedPort(LIVEKIT_HTTP_PORT);
        String serverUrl = "http://" + livekitHost + ":" + mappedPort;

        roomServiceClient = RoomServiceClient.createClient(serverUrl, API_KEY, API_SECRET);
    }

    @Test
    @DisplayName("CA 6.2: Crear sala sintética mediante RoomServiceClient y verificar presencia y metadatos en listRooms()")
    void shouldCreateRoomAndVerifyInListRooms() throws Exception {
        // Arrange
        String roomName = "sala-integracion-qa-" + System.currentTimeMillis();
        String roomMetadata = "{\"modulo\":\"DevOps\",\"maxEstudiantes\":25}";
        int emptyTimeoutSeconds = 300;
        int maxParticipants = 30;

        // Act - 1. Crear sala sintética
        Response<Room> createResponse = roomServiceClient.createRoom(
                roomName,
                emptyTimeoutSeconds,
                maxParticipants,
                null,
                roomMetadata
        ).execute();

        // Assert - Creación exitosa
        assertTrue(createResponse.isSuccessful(), "La creación de la sala en LiveKit debe ser HTTP 2xx");
        Room createdRoom = createResponse.body();
        assertNotNull(createdRoom, "El cuerpo de respuesta no debe ser nulo");
        assertEquals(roomName, createdRoom.getName());
        assertEquals(roomMetadata, createdRoom.getMetadata());

        // Act - 2. Consultar lista de salas activas
        Response<List<Room>> listResponse = roomServiceClient.listRooms().execute();

        // Assert - Verificación en listRooms()
        assertTrue(listResponse.isSuccessful(), "listRooms() debe responder HTTP 2xx");
        List<Room> activeRooms = listResponse.body();
        assertNotNull(activeRooms, "La lista de salas no debe ser nula");

        Room foundRoom = activeRooms.stream()
                .filter(r -> roomName.equals(r.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(foundRoom, "La sala sintética debe existir en listRooms()");
        assertEquals(roomName, foundRoom.getName());
        assertEquals(roomMetadata, foundRoom.getMetadata());
    }

    @Test
    @DisplayName("CA 6.3: Eliminar sala (deleteRoom) y confirmar que deja de figurar inmediatamente en listRooms()")
    void shouldDeleteRoomAndVerifyAbsenceInListRooms() throws Exception {
        // Arrange - Creamos una sala previa para el ciclo de eliminación
        String roomToDelete = "sala-eliminar-qa-" + System.currentTimeMillis();
        Response<Room> createResponse = roomServiceClient.createRoom(roomToDelete).execute();
        assertTrue(createResponse.isSuccessful(), "La sala inicial debe crearse correctamente");

        // Act - Invocar la eliminación de la sala
        Response<Void> deleteResponse = roomServiceClient.deleteRoom(roomToDelete).execute();

        // Assert - 1. La petición deleteRoom fue exitosa
        assertTrue(deleteResponse.isSuccessful(), "deleteRoom() debe responder con código exitoso (HTTP 200)");

        // Act - Consultar inmediatamente el listado activo del servidor
        Response<List<Room>> listAfterDeleteResponse = roomServiceClient.listRooms().execute();
        assertTrue(listAfterDeleteResponse.isSuccessful(), "listRooms() debe responder con éxito");

        List<Room> remainingRooms = listAfterDeleteResponse.body();

        // Assert - 2. Confirmar que la sala ya NO figura en el servidor
        boolean roomStillExists = remainingRooms != null && remainingRooms.stream()
                .anyMatch(r -> roomToDelete.equals(r.getName()));

        assertFalse(roomStillExists, 
                "CA 6.3: La sala eliminada NO debe figurar en el listado de salas activas de LiveKit");
    }
}