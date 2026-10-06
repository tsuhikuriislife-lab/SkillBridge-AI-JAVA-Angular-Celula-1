package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferingRepositoryPort {
    List<Offering> findAllActive();
    Optional<Offering> findById(UUID id);
    Offering save(Offering offering);
    PageResult<Offering> findByProviderId(UUID providerId, int page, int size);
    List<String> findDistinctCategories();
    List<com.riwi.skillbridge.domain.model.CategoryCount> findTopCategories(int limit);
}
