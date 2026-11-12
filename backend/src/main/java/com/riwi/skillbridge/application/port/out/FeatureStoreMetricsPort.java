package com.riwi.skillbridge.application.port.out;

public interface FeatureStoreMetricsPort {
    void recordRedisRead(String feature, boolean hit);

    void recordRedisError(String feature);

    void recordPostgresFallback(String feature, String reason);

    void recordReadLatency(String feature, String source, long elapsedNanos);

    void recordBatchReceived();

    void recordDuplicateBatch();
}
