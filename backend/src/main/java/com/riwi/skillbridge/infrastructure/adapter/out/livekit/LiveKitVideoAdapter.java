package com.riwi.skillbridge.infrastructure.adapter.out.livekit;

import com.riwi.skillbridge.application.port.out.LiveKitVideoPort;
import com.riwi.skillbridge.infrastructure.config.LiveKitConfiguration;
import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class LiveKitVideoAdapter implements LiveKitVideoPort {

    private final LiveKitConfiguration liveKitConfig;

    public LiveKitVideoAdapter(LiveKitConfiguration liveKitConfig) {
        this.liveKitConfig = liveKitConfig;
    }

    @Override
    public String generateAccessToken(String roomName, String participantIdentity, String participantName, boolean canPublish) {
        AccessToken token = new AccessToken(liveKitConfig.getApiKey(), liveKitConfig.getApiSecret());
        
        token.setName(participantName);
        token.setIdentity(participantIdentity);
        
        //Configuracion de tiempo de expiración (por defecto 2 horas / 120 min)
        long ttlMillis = liveKitConfig.getTokenTtlMinutes() * 60 * 1000L;
        token.setExpiration(new Date(System.currentTimeMillis() + ttlMillis));

        // Asignación de permisos dentro de la sala, unirse a la sala y publicar audio/video según el rol del participante
        token.addGrants(
                new RoomJoin(true),
                new RoomName(roomName)
        );

        if (isTutor) {
            // Tutor con todos los permisos multimedia y admin de sala
            token.getGrants().get.Video().setCanPublish(true);
            token.getGrants().get.Video().setCanSubscribe(true);
            token.getGrants().get.Video().setCanPublishData(true);
            token.getGrants().get.Video().setRoomAdmin(true);
        } else {
            // Alumno con suscripción y chat habilitados, pero publicación multimedia denegada
            token.getGrants().get.Video().setCanPublish(false);
            token.getGrants().get.Video().setCanSubscribe(true);
            token.getGrants().get.Video().setCanPublishData(true);
            token.getGrants().get.Video().setRoomAdmin(false);
        }
        return token.toJwt();
    }
}
