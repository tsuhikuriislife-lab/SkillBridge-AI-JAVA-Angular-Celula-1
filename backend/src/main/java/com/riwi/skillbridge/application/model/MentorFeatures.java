package com.riwi.skillbridge.application.model;

import java.util.UUID;

public record MentorFeatures(
        UUID mentorId,
        float compatibility,
        float expertise,
        float availability,
        float popularity,
        UUID batchId) {
}
