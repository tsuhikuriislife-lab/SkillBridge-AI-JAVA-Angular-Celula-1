package com.riwi.skillbridge.application.port.out;

public interface LiveKitVideoPort {

    /**
     * Genera un token JWT de acceso para unirse a una sala de LiveKit.
     *
     * @param roomName            Nombre o identificador único de la sala
     * @param participantIdentity Identificador único del participante (ej. ID o email del usuario)
     * @param participantName     Nombre visible del participante en la llamada
     * @param canPublish          Indica si el participante puede publicar audio/video
     * @return Token JWT firmado listo para ser enviado al cliente
     */
    String generateAccessToken(String roomName, String participantIdentity, String participantName, boolean canPublish);
}
