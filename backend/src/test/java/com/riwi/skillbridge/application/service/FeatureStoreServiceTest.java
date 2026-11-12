package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.model.MentorFeatures;
import com.riwi.skillbridge.application.model.ServiceEmbedding;
import com.riwi.skillbridge.application.port.out.FeatureStoreMetricsPort;
import com.riwi.skillbridge.application.port.out.FeatureSourcePort;
import com.riwi.skillbridge.application.port.out.FeatureStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureStoreServiceTest {
    @Mock
    private FeatureStorePort featureStore;
    @Mock
    private FeatureSourcePort featureSource;
    @Mock
    private FeatureStoreMetricsPort metrics;

    private FeatureStoreService service;

    @BeforeEach
    void setUp() {
        service = new FeatureStoreService(featureStore, featureSource, metrics);
    }

    @Test
    void returnsMentorFeatureCacheHitAndRecordsMetric() {
        UUID id = UUID.randomUUID();
        MentorFeatures cached = new MentorFeatures(id, 0.1f, 0.2f, 0.3f, 0.4f, UUID.randomUUID());
        when(featureStore.findMentorFeatures(id)).thenReturn(Optional.of(cached));

        assertSame(cached, service.findMentorFeatures(id).orElseThrow());

        verify(featureSource, never()).findMentorFeatures(id);
        verify(metrics).recordRedisRead("mentor", true);
        verify(metrics).recordReadLatency(org.mockito.ArgumentMatchers.eq("mentor"),
                org.mockito.ArgumentMatchers.eq("redis"), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void fallsBackToPostgresWhenMentorCacheMisses() {
        UUID id = UUID.randomUUID();
        MentorFeatures source = new MentorFeatures(id, 0.2f, 0.3f, 0.4f, 0.5f, UUID.randomUUID());
        when(featureStore.findMentorFeatures(id)).thenReturn(Optional.empty());
        when(featureSource.findMentorFeatures(id)).thenReturn(Optional.of(source));

        assertSame(source, service.findMentorFeatures(id).orElseThrow());

        verify(metrics).recordRedisRead("mentor", false);
        verify(metrics).recordPostgresFallback("mentor", "miss");
        verify(metrics).recordReadLatency(org.mockito.ArgumentMatchers.eq("mentor"),
                org.mockito.ArgumentMatchers.eq("postgres"), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void fallsBackToPostgresWhenRedisFails() {
        UUID id = UUID.randomUUID();
        ServiceEmbedding source = new ServiceEmbedding(id, new float[]{1f, -2.5f}, 2, UUID.randomUUID());
        when(featureStore.findServiceEmbedding(id)).thenThrow(new IllegalStateException("Redis unavailable"));
        when(featureSource.findServiceEmbedding(id)).thenReturn(Optional.of(source));

        assertSame(source, service.findServiceEmbedding(id).orElseThrow());

        verify(metrics).recordRedisError("embedding");
        verify(metrics).recordPostgresFallback("embedding", "redis_error");
    }

    @Test
    void returnsEmptyWhenCacheAndPostgresHaveNoMentorFeatures() {
        UUID id = UUID.randomUUID();
        when(featureStore.findMentorFeatures(id)).thenReturn(Optional.empty());
        when(featureSource.findMentorFeatures(id)).thenReturn(Optional.empty());

        assertEquals(Optional.empty(), service.findMentorFeatures(id));
        verify(featureSource).findMentorFeatures(id);
    }

    @Test
    void returnsEmptyWhenCacheAndPostgresHaveNoEmbedding() {
        UUID id = UUID.randomUUID();
        when(featureStore.findServiceEmbedding(id)).thenReturn(Optional.empty());
        when(featureSource.findServiceEmbedding(id)).thenReturn(Optional.empty());

        assertEquals(Optional.empty(), service.findServiceEmbedding(id));
        verify(metrics).recordPostgresFallback("embedding", "miss");
    }
}
