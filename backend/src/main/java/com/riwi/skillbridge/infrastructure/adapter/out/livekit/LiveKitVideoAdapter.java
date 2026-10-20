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
        
        // Asignación de permisos dentro de la sala
        token.addGrants(
                new RoomJoin(true),
                new RoomName(roomName)
        );

        //Configuracion de tiempo de expiración (por defecto 2 horas / 120 min)
        long ttlMillis = liveKitConfig.getTokenTtlMinutes() * 60 * 1000L;
        token.setExpiration(new Date(System.currentTimeMillis() + ttlMillis));

        return token.toJwt();
    }
}
