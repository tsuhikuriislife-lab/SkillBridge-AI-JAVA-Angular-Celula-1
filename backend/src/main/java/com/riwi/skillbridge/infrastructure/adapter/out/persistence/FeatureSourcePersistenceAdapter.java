package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.model.MentorFeatures;
import com.riwi.skillbridge.application.model.ServiceEmbedding;
import com.riwi.skillbridge.application.port.out.FeatureSourcePort;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaMentorFeaturesRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaServiceEmbeddingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
public class FeatureSourcePersistenceAdapter implements FeatureSourcePort {
    private final JpaMentorFeaturesRepository mentorFeatures;
    private final JpaServiceEmbeddingRepository serviceEmbeddings;

    public FeatureSourcePersistenceAdapter(
            JpaMentorFeaturesRepository mentorFeatures,
            JpaServiceEmbeddingRepository serviceEmbeddings) {
        this.mentorFeatures = mentorFeatures;
        this.serviceEmbeddings = serviceEmbeddings;
    }

    @Override
    public Optional<MentorFeatures> findMentorFeatures(UUID mentorId) {
        return mentorFeatures.findById(mentorId).map(entity -> new MentorFeatures(
                entity.getMentorId(),
                entity.getCompatibility(),
                entity.getExpertise(),
                entity.getAvailability(),
                entity.getPopularity(),
                entity.getBatchId()));
    }

    @Override
    public Optional<ServiceEmbedding> findServiceEmbedding(UUID serviceId) {
        return serviceEmbeddings.findById(serviceId).map(entity -> {
            byte[] bytes = entity.getEmbedding();
            int dimension = entity.getDimension();
            if (dimension <= 0 || bytes.length != Math.multiplyExact(dimension, Float.BYTES)) {
                throw new IllegalStateException("PostgreSQL embedding byte length does not match dimension");
            }
            float[] values = new float[dimension];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(values);
            return new ServiceEmbedding(serviceId, values, dimension, entity.getBatchId());
        });
    }
}
