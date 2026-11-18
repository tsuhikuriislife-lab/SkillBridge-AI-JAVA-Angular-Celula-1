package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.model.FeaturesUpdatedEvent;
import com.riwi.skillbridge.infrastructure.adapter.out.metrics.MicrometerFeatureStoreMetricsAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandleFeaturesUpdatedServiceTest {
    @Test
    void ignoresDuplicateBatchAndTracksReceiptMetrics() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        HandleFeaturesUpdatedService service =
                new HandleFeaturesUpdatedService(new MicrometerFeatureStoreMetricsAdapter(meters));
        FeaturesUpdatedEvent event = new FeaturesUpdatedEvent(
                UUID.randomUUID(), 3, 5, 1,
                Instant.parse("2026-11-11T12:00:00Z"),
                Instant.parse("2026-11-11T12:01:00Z"));

        service.handle(event);
        service.handle(event);

        assertEquals(2, meters.counter("feature_store.batch.received").count());
        assertEquals(1, meters.counter("feature_store.batch.duplicates").count());
        assertTrue(meters.get("feature_store.batch.last_received_timestamp").gauge().value() > 0);
        assertTrue(meters.get("feature_store.batch.last_received_age_seconds").gauge().value() >= 0);
    }
}
