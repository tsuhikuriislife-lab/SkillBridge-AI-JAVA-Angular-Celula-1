package com.riwi.skillbridge.application.port.in.catalog;

import com.riwi.skillbridge.domain.model.CatalogItem;

public interface CreateCatalogItemUseCase {
    CatalogItem createCatalogItem(CatalogItem catalogItem);
}
