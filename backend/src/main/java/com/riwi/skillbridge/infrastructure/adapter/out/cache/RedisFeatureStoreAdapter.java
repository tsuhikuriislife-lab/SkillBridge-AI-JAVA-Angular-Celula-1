package com.riwi.skillbridge.infrastructure.adapter.out.cache;

import com.riwi.skillbridge.application.model.MentorFeatures;
import com.riwi.skillbridge.application.model.ServiceEmbedding;
import com.riwi.skillbridge.application.port.out.FeatureStorePort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class RedisFeatureStoreAdapter implements FeatureStorePort {
    private final RedisTemplate<byte[], byte[]> redis;
    private final HashOperations<byte[], Object, Object> hashes;

    public RedisFeatureStoreAdapter(
            @Qualifier("featureStoreRedisTemplate") RedisTemplate<byte[], byte[]> redis) {
        this.redis = redis;
        this.hashes = redis.opsForHash();
    }

    @Override
    public Optional<MentorFeatures> findMentorFeatures(UUID mentorId) {
        byte[] key = key("mentor:feat:" + mentorId);
        Map<String, String> fields = readHash(key);
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new MentorFeatures(
                mentorId,
                parseFloat(fields, "compatibility_score"),
                parseFloat(fields, "expertise_score"),
                parseFloat(fields, "availability_score"),
                parseFloat(fields, "popularity_score"),
                parseUuid(fields, "batch_id")));
    }

    @Override
    public Optional<ServiceEmbedding> findServiceEmbedding(UUID serviceId) {
        byte[] key = key("service:emb:" + serviceId);
        byte[] bytes = redis.opsForValue().get(key);
        if (bytes == null) {
            return Optional.empty();
        }

        Map<String, String> metadata = readHash(key("service:emb:" + serviceId + ":meta"));
        int dimension = parseInt(metadata, "dimension");
        UUID batchId = parseUuid(metadata, "batch_id");
        int expectedBytes = Math.multiplyExact(dimension, Float.BYTES);
        if (dimension <= 0 || bytes.length != expectedBytes) {
            throw new IllegalStateException("Embedding byte length does not match Redis metadata");
        }

        float[] values = new float[dimension];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(values);
        return Optional.of(new ServiceEmbedding(serviceId, values, dimension, batchId));
    }

    private Map<String, String> readHash(byte[] key) {
        Map<Object, Object> raw = hashes.entries(key);
        Map<String, String> fields = new HashMap<>(raw.size());
        raw.forEach((field, value) -> {
            if (!(field instanceof byte[] fieldBytes) || !(value instanceof byte[] valueBytes)) {
                throw new IllegalStateException("Redis hash values must be binary");
            }
            fields.put(decode(fieldBytes), decode(valueBytes));
        });
        return fields;
    }

    private static float parseFloat(Map<String, String> fields, String name) {
        float value = Float.parseFloat(required(fields, name));
        if (!Float.isFinite(value)) {
            throw new IllegalStateException("Redis field " + name + " is not finite");
        }
        return value;
    }

    private static int parseInt(Map<String, String> fields, String name) {
        return Integer.parseInt(required(fields, name));
    }

    private static UUID parseUuid(Map<String, String> fields, String name) {
        return UUID.fromString(required(fields, name));
    }

    private static String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Redis value is missing field " + name);
        }
        return value;
    }

    private static byte[] key(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String decode(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }
}
