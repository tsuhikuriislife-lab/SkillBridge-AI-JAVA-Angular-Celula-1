package com.riwi.skillbridge.application.port.in.offering;

import com.riwi.skillbridge.domain.model.Offering;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetrieveOfferingUseCase {
    Optional<Offering> getOfferingById(UUID id);
    Optional<Offering> getOfferingByCode(String code);
    List<Offering> getOfferingsByCreator(UUID createdBy);
    List<Offering> getAllOfferings();
}
