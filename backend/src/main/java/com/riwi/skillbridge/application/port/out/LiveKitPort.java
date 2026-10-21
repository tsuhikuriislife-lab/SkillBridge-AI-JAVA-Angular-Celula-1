package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.enums.MeetingRole;

import java.time.OffsetDateTime;

/**
 * Puerto de salida para la administración de LiveKit: salas y tokens de acceso.
 * La implementación concreta (HTTP/REST + JWT) vive en la capa de infraestructura.
 */
public interface LiveKitPort {

    /** URL pública del servidor LiveKit (wss://...) que usa el cliente para conectarse. */
    String serverUrl();

    /**
     * Crea una sala. Si la sala ya existe (reintento), la reutiliza sin duplicarla.
     */
    void createRoom(String roomName);

    /**
     * Cierra una sala y desconecta a sus participantes. Debe ser tolerante a
     * salas inexistentes (idempotente).
     */
    void deleteRoom(String roomName);

    /**
     * Genera un token de acceso JWT firmado con el secreto de LiveKit, con los
     * permisos de publicación acordes al rol:
     * - PROVIDER: publica audio, video y pantalla.
     * - PARTICIPANT: solo recibe; puede enviar mensajes de datos (foro/chat).
     */
    String createAccessToken(String roomName, String participantIdentity, String participantName,
                             MeetingRole role, OffsetDateTime expiresAt);
}
