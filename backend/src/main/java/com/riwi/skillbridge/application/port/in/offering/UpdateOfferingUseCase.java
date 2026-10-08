package com.riwi.skillbridge.application.port.in.offering;

import com.riwi.skillbridge.domain.model.Offering;

import java.util.Optional;
import java.util.UUID;

public interface UpdateOfferingUseCase {
    Optional<Offering> updateOffering(UUID id, Offering offering);
}
