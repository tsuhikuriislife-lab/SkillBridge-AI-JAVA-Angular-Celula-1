package com.riwi.skillbridge.infrastructure.adapter.out.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicrometerFeatureStoreMetricsAdapterTest {
    @Test
    void recordsRedisFallbackLatencyAndBatchReceiptMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerFeatureStoreMetricsAdapter metrics = new MicrometerFeatureStoreMetricsAdapter(registry);

        metrics.recordRedisRead("mentor", true);
        metrics.recordRedisRead("embedding", false);
        metrics.recordRedisError("embedding");
        metrics.recordPostgresFallback("embedding", "redis_error");
        metrics.recordReadLatency("embedding", "postgres", 1_000_000);
        metrics.recordBatchReceived();
        metrics.recordDuplicateBatch();

        assertEquals(1, registry.counter("feature_store.redis.reads",
                "feature", "mentor", "result", "hit").count());
        assertEquals(1, registry.counter("feature_store.redis.reads",
                "feature", "embedding", "result", "miss").count());
        assertEquals(1, registry.counter("feature_store.redis.errors", "feature", "embedding").count());
        assertEquals(1, registry.counter("feature_store.postgres.fallback",
                "feature", "embedding", "reason", "redis_error").count());
        assertEquals(1, registry.timer("feature_store.read.latency",
                "feature", "embedding", "source", "postgres").count());
        assertEquals(1, registry.counter("feature_store.batch.received").count());
        assertEquals(1, registry.counter("feature_store.batch.duplicates").count());
        assertTrue(registry.get("feature_store.batch.last_received_timestamp").gauge().value() > 0);
    }
}
