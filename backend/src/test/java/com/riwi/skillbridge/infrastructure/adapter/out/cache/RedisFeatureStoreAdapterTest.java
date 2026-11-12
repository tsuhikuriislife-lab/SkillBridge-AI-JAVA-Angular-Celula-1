package com.riwi.skillbridge.infrastructure.adapter.out.cache;

import com.riwi.skillbridge.application.model.ServiceEmbedding;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisFeatureStoreAdapterTest {
    @Test
    void decodesLoaderFloat32BytesAsLittleEndian() {
        UUID serviceId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID batchId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        byte[] embeddingKey = bytes("service:emb:" + serviceId);
        byte[] metadataKey = bytes("service:emb:" + serviceId + ":meta");
        byte[] loaderBytes = {0, 0, (byte) 0x80, 0x3f, 0, 0, 0x20, (byte) 0xc0};
        Map<Object, Object> metadata = Map.of(
                bytes("dimension"), bytes("2"),
                bytes("batch_id"), bytes(batchId.toString()));

        @SuppressWarnings("unchecked")
        RedisTemplate<byte[], byte[]> template = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<byte[], Object, Object> hashes = mock(HashOperations.class);
        @SuppressWarnings("unchecked")
        ValueOperations<byte[], byte[]> values = mock(ValueOperations.class);
        when(template.opsForHash()).thenReturn(hashes);
        when(template.opsForValue()).thenReturn(values);
        when(values.get(any())).thenReturn(loaderBytes);
        when(hashes.entries(any())).thenAnswer(invocation -> {
            byte[] key = invocation.getArgument(0);
            return Arrays.equals(key, metadataKey) ? metadata : Map.of();
        });

        ServiceEmbedding embedding = new RedisFeatureStoreAdapter(template)
                .findServiceEmbedding(serviceId).orElseThrow();

        assertEquals(2, embedding.dimension());
        assertEquals(batchId, embedding.batchId());
        assertArrayEquals(new float[]{1.0f, -2.5f}, embedding.values());
        assertArrayEquals(loaderBytes, values.get(embeddingKey));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
