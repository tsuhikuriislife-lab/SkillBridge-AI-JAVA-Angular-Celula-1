package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.model.MentorFeatures;
import com.riwi.skillbridge.application.model.ServiceEmbedding;
import com.riwi.skillbridge.application.port.out.FeatureStoreMetricsPort;
import com.riwi.skillbridge.application.port.out.FeatureSourcePort;
import com.riwi.skillbridge.application.port.out.FeatureStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class FeatureStoreService {
    private static final Logger log = LoggerFactory.getLogger(FeatureStoreService.class);

    private final FeatureStorePort featureStore;
    private final FeatureSourcePort featureSource;
    private final FeatureStoreMetricsPort metrics;

    public FeatureStoreService(
            FeatureStorePort featureStore,
            FeatureSourcePort featureSource,
            FeatureStoreMetricsPort metrics) {
        this.featureStore = featureStore;
        this.featureSource = featureSource;
        this.metrics = metrics;
    }

    public Optional<MentorFeatures> findMentorFeatures(UUID mentorId) {
        long startedAt = System.nanoTime();
        String source = "redis";
        try {
            Optional<MentorFeatures> cached;
            try {
                cached = featureStore.findMentorFeatures(mentorId);
            } catch (RuntimeException exception) {
                metrics.recordRedisError("mentor");
                metrics.recordPostgresFallback("mentor", "redis_error");
                log.warn("Redis read failed for mentor feature {}, falling back to PostgreSQL: {}",
                        mentorId, exception.getMessage());
                source = "postgres";
                return featureSource.findMentorFeatures(mentorId);
            }
            metrics.recordRedisRead("mentor", cached.isPresent());
            if (cached.isPresent()) {
                return cached;
            }
            metrics.recordPostgresFallback("mentor", "miss");
            source = "postgres";
            return featureSource.findMentorFeatures(mentorId);
        } finally {
            metrics.recordReadLatency("mentor", source, System.nanoTime() - startedAt);
        }
    }

    public Optional<ServiceEmbedding> findServiceEmbedding(UUID serviceId) {
        long startedAt = System.nanoTime();
        String source = "redis";
        try {
            Optional<ServiceEmbedding> cached;
            try {
                cached = featureStore.findServiceEmbedding(serviceId);
            } catch (RuntimeException exception) {
                metrics.recordRedisError("embedding");
                metrics.recordPostgresFallback("embedding", "redis_error");
                log.warn("Redis read failed for service embedding {}, falling back to PostgreSQL: {}",
                        serviceId, exception.getMessage());
                source = "postgres";
                return featureSource.findServiceEmbedding(serviceId);
            }
            metrics.recordRedisRead("embedding", cached.isPresent());
            if (cached.isPresent()) {
                return cached;
            }
            metrics.recordPostgresFallback("embedding", "miss");
            source = "postgres";
            return featureSource.findServiceEmbedding(serviceId);
        } finally {
            metrics.recordReadLatency("embedding", source, System.nanoTime() - startedAt);
        }
    }
}
