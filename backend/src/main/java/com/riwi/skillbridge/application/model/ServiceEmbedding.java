package com.riwi.skillbridge.application.model;

import java.util.UUID;

public record ServiceEmbedding(UUID serviceId, float[] values, int dimension, UUID batchId) {
    public ServiceEmbedding {
        values = values.clone();
        if (dimension <= 0 || values.length != dimension) {
            throw new IllegalArgumentException("Embedding dimension must match its values");
        }
    }

    @Override
    public float[] values() {
        return values.clone();
    }
}
