package com.riwi.skillbridge.application.port.in.meeting;

import com.riwi.skillbridge.application.model.MeetingAccess;
import com.riwi.skillbridge.domain.model.Meeting;

import java.util.UUID;

/**
 * Operaciones del proveedor sobre las reuniones de sus servicios.
 * Todas validan que el proveedor sea el propietario del servicio (Regla 1).
 */
public interface ManageMeetingUseCase {

    /**
     * Prepara (si no existe) e inicia la reunión de la sesión programada del servicio.
     * Solo puede ejecutarse dentro de la ventana de inicio de 30 minutos
     * posteriores al horario programado (Regla 3). Idempotente ante reintentos.
     * Devuelve la reunión iniciada junto con el token del proveedor.
     */
    MeetingAccess startMeeting(UUID providerId, UUID serviceId);

    /**
     * Finaliza normalmente una reunión en curso (Regla: transición EN_CURSO → FINALIZADA).
     */
    Meeting finishMeeting(UUID providerId, UUID meetingId);

    /**
     * Cancela una reunión programada o en curso, registrando motivo y responsable (Regla 5).
     */
    Meeting cancelMeeting(UUID providerId, UUID meetingId, String reason);

    /**
     * Concede la extensión máxima de tiempo una única vez (Regla 4).
     */
    Meeting extendMeeting(UUID providerId, UUID meetingId);

    /**
     * Crea e inicia una sala de prueba instantánea para un servicio, permitiendo
     * el acceso al administrador (con permisos completos) y a los usuarios inscritos.
     */
    MeetingAccess createTestMeeting(UUID adminUserId, UUID serviceId);
}
