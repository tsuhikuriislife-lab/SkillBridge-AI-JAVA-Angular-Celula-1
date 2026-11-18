package com.riwi.skillbridge.infrastructure.adapter.out.metrics;

import com.riwi.skillbridge.application.port.out.FeatureStoreMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MicrometerFeatureStoreMetricsAdapter implements FeatureStoreMetricsPort {
    private final MeterRegistry meters;
    private final AtomicLong lastReceivedAtMillis = new AtomicLong();
    private final Counter receivedBatches;
    private final Counter duplicateBatches;

    public MicrometerFeatureStoreMetricsAdapter(MeterRegistry meters) {
        this.meters = meters;
        this.receivedBatches = meters.counter("feature_store.batch.received");
        this.duplicateBatches = meters.counter("feature_store.batch.duplicates");
        Gauge.builder("feature_store.batch.last_received_timestamp", lastReceivedAtMillis,
                        value -> value.get() / 1000.0)
                .description("Unix timestamp of the most recently received feature batch event")
                .register(meters);
        Gauge.builder("feature_store.batch.last_received_age_seconds", lastReceivedAtMillis,
                        value -> value.get() == 0 ? 0
                                : Math.max(0, Instant.now().toEpochMilli() - value.get()) / 1000.0)
                .description("Age of the most recently received feature batch event")
                .register(meters);
    }

    @Override
    public void recordRedisRead(String feature, boolean hit) {
        meters.counter("feature_store.redis.reads", "feature", feature,
                "result", hit ? "hit" : "miss").increment();
    }

    @Override
    public void recordRedisError(String feature) {
        meters.counter("feature_store.redis.errors", "feature", feature).increment();
    }

    @Override
    public void recordPostgresFallback(String feature, String reason) {
        meters.counter("feature_store.postgres.fallback", "feature", feature, "reason", reason).increment();
    }

    @Override
    public void recordReadLatency(String feature, String source, long elapsedNanos) {
        Timer.builder("feature_store.read.latency")
                .tag("feature", feature)
                .tag("source", source)
                .register(meters)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordBatchReceived() {
        receivedBatches.increment();
        lastReceivedAtMillis.set(System.currentTimeMillis());
    }

    @Override
    public void recordDuplicateBatch() {
        duplicateBatches.increment();
    }
}
