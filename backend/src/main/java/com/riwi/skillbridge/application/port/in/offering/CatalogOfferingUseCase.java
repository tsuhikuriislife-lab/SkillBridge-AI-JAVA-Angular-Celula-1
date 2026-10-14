package com.riwi.skillbridge.application.port.in.offering;

import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PageResult;

import java.util.UUID;

public interface CatalogOfferingUseCase {
    // Público: solo ACTIVE con cupo disponible, paginado y ordenable ("name,asc" / "price,desc")
    PageResult<Offering> getActiveCatalog(int page, int size, String sort);

    PageResult<Offering> searchCatalog(String name, UUID categoryId, Boolean freeOnly,
                                       int page, int size, String sort);
}
