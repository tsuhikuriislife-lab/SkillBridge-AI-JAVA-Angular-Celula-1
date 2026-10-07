package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PageResult;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

public interface ManageProviderOfferingsUseCase {
    Offering create(String providerEmail, String title, String description, String category, BigDecimal price, LocalTime startTime, LocalTime endTime, String endDay, String photoUrl);
    PageResult<Offering> getProviderOfferings(String providerEmail, int page, int size);
    Offering update(String providerEmail, UUID offeringId, Map<String, Object> updates);
    Offering toggleStatus(String providerEmail, UUID offeringId);
}
