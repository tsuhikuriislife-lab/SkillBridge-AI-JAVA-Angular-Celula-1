package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.application.model.MentorFeatures;
import com.riwi.skillbridge.application.model.ServiceEmbedding;

import java.util.Optional;
import java.util.UUID;

public interface FeatureStorePort {
    Optional<MentorFeatures> findMentorFeatures(UUID mentorId);

    Optional<ServiceEmbedding> findServiceEmbedding(UUID serviceId);
}
