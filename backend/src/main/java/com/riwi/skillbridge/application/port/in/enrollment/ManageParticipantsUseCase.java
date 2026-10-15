package com.riwi.skillbridge.application.port.in.enrollment;

import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.ServiceEnrollment;

import java.util.UUID;

public interface ManageParticipantsUseCase {
    PageResult<ServiceEnrollment> listParticipants(UUID providerId, UUID serviceId, int page, int size);

    // Regla especial: solo en servicios GRATUITOS el proveedor puede quitar participantes
    void removeParticipant(UUID providerId, UUID serviceId, UUID participantId);
}
