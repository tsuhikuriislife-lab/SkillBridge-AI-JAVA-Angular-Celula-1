package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.GenerateRecommendationUseCase;
import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.OfferingPort;
import com.riwi.skillbridge.domain.model.Offering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiRecommendationService implements GenerateRecommendationUseCase {

    private static final Logger log = LoggerFactory.getLogger(AiRecommendationService.class);

    private final AiRecommendationPort ai;
    private final OfferingPort offerings;

    public AiRecommendationService(AiRecommendationPort ai, OfferingPort offerings) {
        this.ai = ai;
        this.offerings = offerings;
    }

    @Override
    public String recommend(String goal) {
        List<Offering> active = offerings.findAllActive();
        log.info("Generando recomendación con {} servicios activos", active.size());
        return ai.recommend(goal, active);
    }
}
