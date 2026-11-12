package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.model.FeaturesUpdatedEvent;
import com.riwi.skillbridge.application.port.in.HandleFeaturesUpdatedUseCase;
import com.riwi.skillbridge.application.port.out.FeatureStoreMetricsPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HandleFeaturesUpdatedService implements HandleFeaturesUpdatedUseCase {
    private static final Logger log = LoggerFactory.getLogger(HandleFeaturesUpdatedService.class);

    private final Set<UUID> processedBatches = ConcurrentHashMap.newKeySet();
    private final FeatureStoreMetricsPort metrics;

    public HandleFeaturesUpdatedService(FeatureStoreMetricsPort metrics) {
        this.metrics = metrics;
    }

    @Override
    public void handle(FeaturesUpdatedEvent event) {
        metrics.recordBatchReceived();
        if (!processedBatches.add(event.batchId())) {
            metrics.recordDuplicateBatch();
            log.info("Duplicate features.updated ignored: batch_id={}", event.batchId());
            return;
        }
        log.info("features.updated received: batch_id={} mentors={} embeddings={} rejected={}",
                event.batchId(), event.mentors(), event.embeddings(), event.rejected());
    }
}
