package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.GenerateRecommendationUseCase;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.AiRecommendationRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final GenerateRecommendationUseCase useCase;
    public AiController(GenerateRecommendationUseCase useCase) { this.useCase = useCase; }

    @PostMapping("/recommendations")
    public Map<String, String> recommend(@Valid @RequestBody AiRecommendationRequest request) {
        return Map.of("recommendation", useCase.recommend(request.goal()));
    }
}
