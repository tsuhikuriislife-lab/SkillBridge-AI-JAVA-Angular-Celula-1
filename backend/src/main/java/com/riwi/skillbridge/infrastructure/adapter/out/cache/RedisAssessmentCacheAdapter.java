package com.riwi.skillbridge.infrastructure.adapter.out.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.application.port.out.AssessmentSessionCachePort;
import com.riwi.skillbridge.domain.model.TechnicalAssessment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RedisAssessmentCacheAdapter implements AssessmentSessionCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisAssessmentCacheAdapter.class);
    private static final String KEY_PREFIX = "assessment:session:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    // Fallback local en memoria por si Redis no está disponible
    private final Map<UUID, LocalCacheEntry> localFallback = new ConcurrentHashMap<>();

    private record LocalCacheEntry(TechnicalAssessment assessment, Instant expiresAt) {}

    public RedisAssessmentCacheAdapter(StringRedisTemplate redis,
                                       ObjectMapper objectMapper,
                                       @Value("${app.cache.assessment-ttl-minutes:30}") long ttlMinutes) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    @Override
    public void save(TechnicalAssessment assessment) {
        // Guardar siempre en fallback local
        localFallback.put(assessment.id(), new LocalCacheEntry(assessment, Instant.now().plus(ttl)));

        // Guardar en Redis
        try {
            String json = objectMapper.writeValueAsString(assessment);
            redis.opsForValue().set(KEY_PREFIX + assessment.id(), json, ttl);
        } catch (Exception e) {
            log.warn("No se pudo persistir la sesión de diagnóstico en Redis, usando caché local: {}", e.getMessage());
        }
    }

    @Override
    public Optional<TechnicalAssessment> findById(UUID assessmentId) {
        // Intentar primero desde Redis
        try {
            String json = redis.opsForValue().get(KEY_PREFIX + assessmentId);
            if (json != null && !json.isBlank()) {
                return Optional.of(objectMapper.readValue(json, TechnicalAssessment.class));
            }
        } catch (Exception e) {
            log.warn("Fallo al leer de Redis la sesión {}, consultando fallback local: {}", assessmentId, e.getMessage());
        }

        // Consultar fallback local
        LocalCacheEntry entry = localFallback.get(assessmentId);
        if (entry != null) {
            if (Instant.now().isBefore(entry.expiresAt())) {
                return Optional.of(entry.assessment());
            } else {
                localFallback.remove(assessmentId);
            }
        }

        return Optional.empty();
    }
}

