package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Offering;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AdminManageOfferingsUseCase {
    List<Offering> listAllOfferings();
    Offering createOffering(String title, String description, String category, BigDecimal price);
    Offering updateOffering(UUID id, String title, String description, String category, BigDecimal price);
    Offering toggleStatus(UUID id);
    void deleteOffering(UUID id);
}
