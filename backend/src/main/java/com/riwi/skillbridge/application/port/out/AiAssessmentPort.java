package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.AssessmentQuestion;
import com.riwi.skillbridge.domain.model.Offering;

import java.util.List;

public interface AiAssessmentPort {
    List<AssessmentQuestion> generateQuestions(Offering offering);
}

