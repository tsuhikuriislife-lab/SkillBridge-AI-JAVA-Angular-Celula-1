import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiBase } from './api';

export interface ClientAssessmentQuestion {
  id: string;
  question: string;
  options: string[];
}

export interface ClientAssessmentResponse {
  assessmentId: string;
  offeringId: string;
  offeringTitle: string;
  questions: ClientAssessmentQuestion[];
}

export interface AssessmentQuestionResult {
  questionId: string;
  question: string;
  options: string[];
  selectedOptionIndex: number | null;
  correctOptionIndex: number;
  isCorrect: boolean;
  explanation: string;
}

export interface AssessmentEvaluation {
  assessmentId: string;
  offeringId: string;
  offeringTitle: string;
  score: number;
  totalQuestions: number;
  passed: boolean;
  feedback: string;
  recommendation: string;
  questionsFeedback: AssessmentQuestionResult[];
}

@Injectable({
  providedIn: 'root'
})
export class AssessmentService {
  constructor(private http: HttpClient) {}

  generateAssessment(offeringId: string): Observable<ClientAssessmentResponse> {
    return this.http.post<ClientAssessmentResponse>(`${apiBase()}/assessments/offerings/${offeringId}`, {});
  }

  submitAssessment(assessmentId: string, answers: Record<string, number>): Observable<AssessmentEvaluation> {
    return this.http.post<AssessmentEvaluation>(`${apiBase()}/assessments/${assessmentId}/submit`, { answers });
  }
}

