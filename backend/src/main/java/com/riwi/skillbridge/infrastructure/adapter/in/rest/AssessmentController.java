package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.EvaluateAssessmentUseCase;
import com.riwi.skillbridge.application.port.in.GenerateAssessmentUseCase;
import com.riwi.skillbridge.domain.model.AssessmentEvaluation;
import com.riwi.skillbridge.domain.model.TechnicalAssessment;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.AssessmentSubmissionRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.ClientAssessmentQuestionResponse;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.ClientAssessmentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assessments")
public class AssessmentController {

    private final GenerateAssessmentUseCase generateUseCase;
    private final EvaluateAssessmentUseCase evaluateUseCase;

    public AssessmentController(GenerateAssessmentUseCase generateUseCase,
                                EvaluateAssessmentUseCase evaluateUseCase) {
        this.generateUseCase = generateUseCase;
        this.evaluateUseCase = evaluateUseCase;
    }

    @PostMapping("/offerings/{offeringId}")
    public ResponseEntity<ClientAssessmentResponse> generate(@PathVariable UUID offeringId) {
        TechnicalAssessment assessment = generateUseCase.generate(offeringId);

        List<ClientAssessmentQuestionResponse> clientQuestions = assessment.questions().stream()
                .map(q -> new ClientAssessmentQuestionResponse(q.id(), q.question(), q.options()))
                .toList();

        ClientAssessmentResponse response = new ClientAssessmentResponse(
                assessment.id(),
                assessment.offeringId(),
                assessment.offeringTitle(),
                clientQuestions
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{assessmentId}/submit")
    public ResponseEntity<AssessmentEvaluation> submit(
            @PathVariable UUID assessmentId,
            @Valid @RequestBody AssessmentSubmissionRequest request) {

        AssessmentEvaluation evaluation = evaluateUseCase.evaluate(assessmentId, request.answers());
        return ResponseEntity.ok(evaluation);
    }
}

