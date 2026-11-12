package com.riwi.skillbridge.application.model;

import java.time.Instant;
import java.util.UUID;

public record FeaturesUpdatedEvent(
        UUID batchId,
        int mentors,
        int embeddings,
        int rejected,
        Instant startedAt,
        Instant finishedAt) {

    public FeaturesUpdatedEvent {
        if (batchId == null || startedAt == null || finishedAt == null) {
            throw new IllegalArgumentException("Feature batch event is missing required values");
        }
        if (mentors < 0 || embeddings < 0 || rejected < 0) {
            throw new IllegalArgumentException("Feature batch event counts cannot be negative");
        }
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Feature batch finishedAt cannot precede startedAt");
        }
    }
}
