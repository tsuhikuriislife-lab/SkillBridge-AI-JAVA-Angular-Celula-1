package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

public interface BookingListingProjection {
    UUID getId();
    UUID getOfferingId();
    String getOfferingTitle();
    BigDecimal getPrice();
    Instant getScheduledAt();
    String getStatus();
}