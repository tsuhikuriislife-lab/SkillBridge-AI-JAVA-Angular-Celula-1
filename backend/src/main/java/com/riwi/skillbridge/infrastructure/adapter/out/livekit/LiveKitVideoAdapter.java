package com.riwi.skillbridge.infrastructure.adapter.out.livekit;

import com.riwi.skillbridge.application.port.out.LiveKitVideoPort;
import com.riwi.skillbridge.infrastructure.config.LiveKitConfiguration;
import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import org.springframework.stereotype.Component;

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

        // Si se desea restringir publicación/suscripción explícitamente:
        // token.getGrants().getVideo().setCanPublish(canPublish);
        // token.getGrants().getVideo().setCanSubscribe(true);

        return token.toJwt();
    }
}
