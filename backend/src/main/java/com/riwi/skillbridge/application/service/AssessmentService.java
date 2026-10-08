package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.EvaluateAssessmentUseCase;
import com.riwi.skillbridge.application.port.in.GenerateAssessmentUseCase;
import com.riwi.skillbridge.application.port.out.AiAssessmentPort;
import com.riwi.skillbridge.application.port.out.AssessmentSessionCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AssessmentService implements GenerateAssessmentUseCase, EvaluateAssessmentUseCase {

    private final OfferingRepositoryPort offeringRepository;
    private final AiAssessmentPort aiAssessmentPort;
    private final AssessmentSessionCachePort cachePort;

    public AssessmentService(OfferingRepositoryPort offeringRepository,
                             AiAssessmentPort aiAssessmentPort,
                             AssessmentSessionCachePort cachePort) {
        this.offeringRepository = offeringRepository;
        this.aiAssessmentPort = aiAssessmentPort;
        this.cachePort = cachePort;
    }

    @Override
    public TechnicalAssessment generate(UUID offeringId) {
        Offering offering = offeringRepository.findById(offeringId)
                .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado con id: " + offeringId));

        List<AssessmentQuestion> questions = aiAssessmentPort.generateQuestions(offering);
        TechnicalAssessment assessment = new TechnicalAssessment(
                UUID.randomUUID(),
                offering.id(),
                offering.title(),
                questions,
                Instant.now()
        );

        cachePort.save(assessment);
        return assessment;
    }

    @Override
    public AssessmentEvaluation evaluate(UUID assessmentId, Map<String, Integer> answers) {
        TechnicalAssessment assessment = cachePort.findById(assessmentId)
                .orElseThrow(() -> new DomainNotFoundException("La sesión de evaluación ha expirado o no existe"));

        int score = 0;
        List<AssessmentQuestionResult> questionsFeedback = new ArrayList<>();

        for (AssessmentQuestion question : assessment.questions()) {
            Integer userAnswer = (answers != null) ? answers.get(question.id()) : null;
            boolean isCorrect = (userAnswer != null && userAnswer == question.correctOptionIndex());

            if (isCorrect) {
                score++;
            }

            questionsFeedback.add(new AssessmentQuestionResult(
                    question.id(),
                    question.question(),
                    question.options(),
                    userAnswer,
                    question.correctOptionIndex(),
                    isCorrect,
                    question.explanation()
            ));
        }

        int total = assessment.questions().size();
        int passThreshold = total > 0 ? (int) Math.ceil(total * 0.6) : 0;
        boolean passed = score >= passThreshold;

        String feedback;
        String recommendation;

        if (score == total) {
            feedback = "¡Excelente desempeño! Demuestras un dominio sólido de los conceptos y prerrequisitos clave para este servicio.";
            recommendation = "Estás completamente preparado para aprovechar esta mentoría al 100%. Te recomendamos proceder con la inscripción.";
        } else if (passed) {
            feedback = "¡Buen resultado! Tienes las bases necesarias para seguir el ritmo de la sesión, aunque te sugerimos repasar los puntos indicados.";
            recommendation = "Tu nivel es adecuado para participar con éxito. Durante la mentoría podrás profundizar y aclarar dudas con el tutor.";
        } else {
            feedback = "Te recomendamos reforzar los conceptos fundamentales evaluados antes de inscribirte en esta mentoría para asegurar el mejor aprendizaje.";
            recommendation = "Repasa las explicaciones de las preguntas anteriores y considera afianzar tus bases antes de reservar la sesión.";
        }

        return new AssessmentEvaluation(
                assessment.id(),
                assessment.offeringId(),
                assessment.offeringTitle(),
                score,
                total,
                passed,
                feedback,
                recommendation,
                questionsFeedback
        );
    }
}

