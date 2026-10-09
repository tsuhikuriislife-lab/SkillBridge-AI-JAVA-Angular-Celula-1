package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.AiAssessmentPort;
import com.riwi.skillbridge.application.port.out.AssessmentSessionCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.AssessmentEvaluation;
import com.riwi.skillbridge.domain.model.AssessmentQuestion;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.TechnicalAssessment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssessmentServiceTest {

    private OfferingRepositoryPort offeringRepository;
    private AiAssessmentPort aiAssessmentPort;
    private AssessmentSessionCachePort cachePort;
    private AssessmentService service;

    @BeforeEach
    void setUp() {
        offeringRepository = mock(OfferingRepositoryPort.class);
        aiAssessmentPort = mock(AiAssessmentPort.class);
        cachePort = mock(AssessmentSessionCachePort.class);
        service = new AssessmentService(offeringRepository, aiAssessmentPort, cachePort);
    }

    @Test
    void shouldGenerateAndSaveAssessmentWhenOfferingExists() {
        UUID offeringId = UUID.randomUUID();
        Offering offering = new Offering(
                offeringId,
                "Arquitectura Hexagonal en Spring Boot",
                "Domina puertos y adaptadores",
                "BACKEND",
                BigDecimal.valueOf(150000),
                true,
                UUID.randomUUID(),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                "MONDAY,TUESDAY",
                "UTC"
        );

        List<AssessmentQuestion> mockQuestions = List.of(
                new AssessmentQuestion("q1", "¿Qué es un puerto de entrada?", List.of("A", "B", "C", "D"), 0, "Expl 1"),
                new AssessmentQuestion("q2", "¿Qué es un adaptador?", List.of("A", "B", "C", "D"), 1, "Expl 2"),
                new AssessmentQuestion("q3", "¿Dónde van las entidades JPA?", List.of("A", "B", "C", "D"), 2, "Expl 3")
        );

        when(offeringRepository.findById(offeringId)).thenReturn(Optional.of(offering));
        when(aiAssessmentPort.generateQuestions(offering)).thenReturn(mockQuestions);

        TechnicalAssessment assessment = service.generate(offeringId);

        assertNotNull(assessment);
        assertEquals(offeringId, assessment.offeringId());
        assertEquals("Arquitectura Hexagonal en Spring Boot", assessment.offeringTitle());
        assertEquals(3, assessment.questions().size());
        verify(cachePort, times(1)).save(any(TechnicalAssessment.class));
    }

    @Test
    void shouldThrowDomainNotFoundExceptionWhenOfferingDoesNotExist() {
        UUID offeringId = UUID.randomUUID();
        when(offeringRepository.findById(offeringId)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> service.generate(offeringId));
        verifyNoInteractions(aiAssessmentPort);
        verifyNoInteractions(cachePort);
    }

    @Test
    void shouldEvaluateAnswersCorrectlyAndPassWhenScoreMeetsThreshold() {
        UUID assessmentId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();

        List<AssessmentQuestion> questions = List.of(
                new AssessmentQuestion("q1", "Pregunta 1", List.of("O0", "O1", "O2", "O3"), 0, "Explicación 1"),
                new AssessmentQuestion("q2", "Pregunta 2", List.of("O0", "O1", "O2", "O3"), 1, "Explicación 2"),
                new AssessmentQuestion("q3", "Pregunta 3", List.of("O0", "O1", "O2", "O3"), 2, "Explicación 3")
        );

        TechnicalAssessment assessment = new TechnicalAssessment(
                assessmentId,
                offeringId,
                "Microservicios con Spring Cloud",
                questions,
                Instant.now()
        );

        when(cachePort.findById(assessmentId)).thenReturn(Optional.of(assessment));

        // q1 correct (0), q2 correct (1), q3 incorrect (0 instead of 2) -> Score: 2/3 (66% >= 60%)
        Map<String, Integer> userAnswers = Map.of("q1", 0, "q2", 1, "q3", 0);

        AssessmentEvaluation result = service.evaluate(assessmentId, userAnswers);

        assertNotNull(result);
        assertEquals(2, result.score());
        assertEquals(3, result.totalQuestions());
        assertTrue(result.passed());
        assertEquals(3, result.questionsFeedback().size());
        assertTrue(result.questionsFeedback().get(0).isCorrect());
        assertTrue(result.questionsFeedback().get(1).isCorrect());
        assertFalse(result.questionsFeedback().get(2).isCorrect());
    }

    @Test
    void shouldEvaluateAnswersCorrectlyAndFailWhenScoreBelowThreshold() {
        UUID assessmentId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();

        List<AssessmentQuestion> questions = List.of(
                new AssessmentQuestion("q1", "Pregunta 1", List.of("O0", "O1", "O2", "O3"), 0, "Explicación 1"),
                new AssessmentQuestion("q2", "Pregunta 2", List.of("O0", "O1", "O2", "O3"), 1, "Explicación 2"),
                new AssessmentQuestion("q3", "Pregunta 3", List.of("O0", "O1", "O2", "O3"), 2, "Explicación 3")
        );

        TechnicalAssessment assessment = new TechnicalAssessment(
                assessmentId,
                offeringId,
                "Docker y Kubernetes",
                questions,
                Instant.now()
        );

        when(cachePort.findById(assessmentId)).thenReturn(Optional.of(assessment));

        // Solo 1 correcta de 3 -> Score 1/3 (< 60%)
        Map<String, Integer> userAnswers = Map.of("q1", 0, "q2", 3, "q3", 0);

        AssessmentEvaluation result = service.evaluate(assessmentId, userAnswers);

        assertNotNull(result);
        assertEquals(1, result.score());
        assertEquals(3, result.totalQuestions());
        assertFalse(result.passed());
    }

    @Test
    void shouldThrowDomainNotFoundExceptionWhenSessionNotFoundOrExpired() {
        UUID assessmentId = UUID.randomUUID();
        when(cachePort.findById(assessmentId)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> service.evaluate(assessmentId, Map.of()));
    }
}

