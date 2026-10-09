package com.riwi.skillbridge.application.port.in.catalog;

import java.util.UUID;

public interface DeleteCatalogItemUseCase {
    boolean deleteCatalogItem(UUID id);
}
