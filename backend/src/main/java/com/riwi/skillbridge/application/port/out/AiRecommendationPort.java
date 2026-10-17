package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Offering;
import java.util.List;

public interface AiRecommendationPort {
    String recommend(String goal, List<Offering> offerings);
}
