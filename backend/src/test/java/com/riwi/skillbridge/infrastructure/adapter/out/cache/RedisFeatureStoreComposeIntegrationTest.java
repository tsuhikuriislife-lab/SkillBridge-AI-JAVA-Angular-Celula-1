package com.riwi.skillbridge.infrastructure.adapter.out.cache;

import io.lettuce.core.RedisURI;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
class RedisFeatureStoreComposeIntegrationTest {
    @Test
    void readsFeatureKeysCreatedByPythonLoaderWhenComposeRedisIsAvailable() {
        String mentorId = setting("feature.store.test.mentor-id", "FEATURE_STORE_TEST_MENTOR_ID");
        String serviceId = setting("feature.store.test.service-id", "FEATURE_STORE_TEST_SERVICE_ID");
        String url = setting("feature.store.test.redis-url", "FEATURE_STORE_TEST_REDIS_URL");
        if (url == null || url.isBlank()) {
            url = System.getenv().getOrDefault("REDIS_URL", "redis://localhost:6379");
        }
        assumeTrue(mentorId != null && !mentorId.isBlank(), "Set a mentor ID written by redis_batch_loader.py");
        assumeTrue(serviceId != null && !serviceId.isBlank(), "Set a service ID written by redis_batch_loader.py");

        RedisURI redisUri = RedisURI.create(url);
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(redisUri.getHost(), redisUri.getPort());
        connectionFactory.afterPropertiesSet();
        try {
            try {
                connectionFactory.getConnection().ping();
            } catch (RuntimeException exception) {
                assumeTrue(false, "Compose Redis is not reachable: " + exception.getMessage());
            }

            RedisTemplate<byte[], byte[]> template = new RedisTemplate<>();
            RedisSerializer<byte[]> serializer = RedisSerializer.byteArray();
            template.setConnectionFactory(connectionFactory);
            template.setKeySerializer(serializer);
            template.setValueSerializer(serializer);
            template.setHashKeySerializer(serializer);
            template.setHashValueSerializer(serializer);
            template.afterPropertiesSet();

            RedisFeatureStoreAdapter adapter = new RedisFeatureStoreAdapter(template);
            assertTrue(adapter.findMentorFeatures(UUID.fromString(mentorId)).isPresent(),
                    "Expected the Python loader's mentor hash to exist");
            assertTrue(adapter.findServiceEmbedding(UUID.fromString(serviceId)).isPresent(),
                    "Expected the Python loader's embedding and metadata to exist");
        } finally {
            connectionFactory.destroy();
        }
    }

    private static String setting(String property, String environmentVariable) {
        String value = System.getProperty(property);
        return value != null ? value : System.getenv(environmentVariable);
    }
}
