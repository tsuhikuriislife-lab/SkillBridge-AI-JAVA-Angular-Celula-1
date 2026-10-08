package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.application.model.OfferingPage;
import com.riwi.skillbridge.application.model.OfferingSort;
import com.riwi.skillbridge.domain.model.Offering;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferingRepositoryPort {
    List<Offering> findAllActive();
    Optional<Offering> findById(UUID id);
    OfferingPage findPageByProviderId(UUID providerId, int page, int size, OfferingSort sort);
    boolean existsByCode(String code);
    Offering save(Offering offering);
}