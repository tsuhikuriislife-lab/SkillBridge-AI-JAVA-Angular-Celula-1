package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PageResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferingPort {

    Offering save(Offering offering);

    Optional<Offering> findById(UUID id);

    Optional<Offering> findByCode(String code);

    List<Offering> findByCreatedBy(UUID createdBy);

    List<Offering> findAll();

    List<Offering> findAllActive();

    // Catálogo paginado: nombre parcial, categoría, gratis (price=0) o pagos
    PageResult<Offering> findCatalog(String name, UUID categoryId, Boolean freeOnly,
                                     int page, int size, String sort);

    void deleteById(UUID id);
}
