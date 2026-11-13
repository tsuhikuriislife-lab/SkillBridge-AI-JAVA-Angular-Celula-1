package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.service.FeatureStoreService;
import com.riwi.skillbridge.application.port.out.FeatureSourcePort;
import com.riwi.skillbridge.application.port.out.FeatureStorePort;
import com.riwi.skillbridge.infrastructure.adapter.out.cache.RedisFeatureStoreAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.metrics.MicrometerFeatureStoreMetricsAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaMentorFeaturesRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaServiceEmbeddingRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "FEATURE_STORE_TEST_FALLBACK", matches = "true")
class FeatureStoreFallbackComposeIntegrationTest {
    private static RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private JpaMentorFeaturesRepository mentorFeatures;

    @Autowired
    private JpaServiceEmbeddingRepository serviceEmbeddings;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",
                () -> requiredEnvironment("FEATURE_STORE_TEST_DATABASE_URL"));
        properties.add("spring.datasource.username",
                () -> requiredEnvironment("FEATURE_STORE_TEST_DATABASE_USER"));
        properties.add("spring.datasource.password",
                () -> requiredEnvironment("FEATURE_STORE_TEST_DATABASE_PASSWORD"));
    }

    @AfterEach
    void closeRedisConnection() {
        if (redisConnectionFactory instanceof LettuceConnectionFactory lettuce) {
            lettuce.destroy();
            redisConnectionFactory = null;
        }
    }

    @Test
    void readsFromPostgresAndRecordsRedisFailureWhenRedisIsUnavailable() {
        UUID mentorId = UUID.fromString(requiredEnvironment("FEATURE_STORE_TEST_MENTOR_ID"));
        UUID serviceId = UUID.fromString(requiredEnvironment("FEATURE_STORE_TEST_SERVICE_ID"));
        MeterRegistry meters = new SimpleMeterRegistry();
        FeatureStoreService featureStore = new FeatureStoreService(
                redisAdapter(),
                new FeatureSourcePersistenceAdapter(mentorFeatures, serviceEmbeddings),
                new MicrometerFeatureStoreMetricsAdapter(meters));

        assertTrue(featureStore.findMentorFeatures(mentorId).isPresent());
        var embedding = featureStore.findServiceEmbedding(serviceId).orElseThrow();
        assertEquals(384, embedding.dimension());
        assertTrue(meters.counter("feature_store.redis.errors", "feature", "mentor").count() > 0);
        assertTrue(meters.counter("feature_store.redis.errors", "feature", "embedding").count() > 0);
        assertTrue(meters.counter("feature_store.postgres.fallback",
                "feature", "mentor", "reason", "redis_error").count() > 0);
        assertTrue(meters.counter("feature_store.postgres.fallback",
                "feature", "embedding", "reason", "redis_error").count() > 0);
    }

    private static FeatureStorePort redisAdapter() {
        String redisUrl = requiredEnvironment("FEATURE_STORE_TEST_REDIS_URL");
        var uri = io.lettuce.core.RedisURI.create(redisUrl);
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(uri.getHost(), uri.getPort());
        connectionFactory.afterPropertiesSet();
        redisConnectionFactory = connectionFactory;

        RedisTemplate<byte[], byte[]> template = new RedisTemplate<>();
        RedisSerializer<byte[]> serializer = RedisSerializer.byteArray();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return new RedisFeatureStoreAdapter(template);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for this integration test");
        }
        return value;
    }
}
