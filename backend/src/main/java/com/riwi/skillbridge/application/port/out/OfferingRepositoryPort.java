package com.riwi.skillbridge.application.port.out;

public interface OfferingRepositoryPort extends OfferingPort {
    long countByProviderId(java.util.UUID providerId);
}
