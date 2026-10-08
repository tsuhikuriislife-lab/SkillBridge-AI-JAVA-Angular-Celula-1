import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { CurrencyPipe, CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { apiBase } from '../core/api';
import { Offering, OfferingService } from '../core/offering.service';
import { AuthService } from '../core/auth.service';
import { AssessmentService, ClientAssessmentResponse, AssessmentEvaluation } from '../core/assessment.service';

@Component({
  selector: 'app-service-details',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, RouterModule],
  templateUrl: './service-details.component.html',
  styleUrls: ['./service-details.component.css']
})
export class ServiceDetailsComponent implements OnInit {
  offering: Offering | null = null;
  loading = true;
  error = '';
  isEnrolled = false;

  // Mini-Assessment state
  showAssessmentModal = false;
  assessmentLoading = false;
  assessmentError = '';
  assessment: ClientAssessmentResponse | null = null;
  currentQuestionIndex = 0;
  selectedAnswers: Record<string, number> = {};
  evaluationLoading = false;
  evaluationResult: AssessmentEvaluation | null = null;

  viewOnly = false;

  constructor(
    private route: ActivatedRoute,
    private service: OfferingService,
    private auth: AuthService,
    private assessmentService: AssessmentService,
    private router: Router,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.viewOnly = this.route.snapshot.queryParamMap.get('viewOnly') === 'true';

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.service.getById(id).subscribe({
        next: data => {
          this.offering = data;
          this.loading = false;
          this.checkEnrollmentStatus(id);
        },
        error: () => {
          this.error = 'No se pudo cargar el servicio.';
          this.loading = false;
        }
      });
    } else {
      this.error = 'ID de servicio no proporcionado.';
      this.loading = false;
    }
  }

  checkEnrollmentStatus(serviceId: string): void {
    if (!this.auth.isLoggedIn()) return;
    this.http.get<any>(`${apiBase()}/enrollments/me?size=100`).subscribe({
      next: (res) => {
        const list = res.content || [];
        this.isEnrolled = list.some((e: any) => e.serviceId === serviceId && e.status === 'ACTIVE');
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/']);
  }

  onInscribirse(): void {
    if (!this.auth.isLoggedIn()) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: `/service/${this.offering?.id}` } });
      return;
    }
    if (this.isEnrolled) {
      return;
    }
    this.router.navigate(['/checkout', this.offering?.id]);
  }

  startAssessment(): void {
    if (!this.offering?.id) return;

    this.showAssessmentModal = true;
    this.assessmentLoading = true;
    this.assessmentError = '';
    this.assessment = null;
    this.evaluationResult = null;
    this.selectedAnswers = {};
    this.currentQuestionIndex = 0;

    this.assessmentService.generateAssessment(this.offering.id).subscribe({
      next: res => {
        this.assessment = res;
        this.assessmentLoading = false;
      },
      error: err => {
        this.assessmentLoading = false;
        if (err?.error?.detail) {
          this.assessmentError = err.error.detail;
        } else {
          this.assessmentError = 'No fue posible generar el diagnóstico con IA. Intenta nuevamente en unos instantes.';
        }
      }
    });
  }

  closeAssessment(): void {
    this.showAssessmentModal = false;
  }

  selectOption(questionId: string, optionIndex: number): void {
    if (this.evaluationResult) return; // Ya evaluado
    this.selectedAnswers[questionId] = optionIndex;
  }

  isOptionSelected(questionId: string, optionIndex: number): boolean {
    return this.selectedAnswers[questionId] === optionIndex;
  }

  allQuestionsAnswered(): boolean {
    if (!this.assessment?.questions) return false;
    return this.assessment.questions.every(q => this.selectedAnswers[q.id] !== undefined);
  }

  nextQuestion(): void {
    if (this.assessment && this.currentQuestionIndex < this.assessment.questions.length - 1) {
      this.currentQuestionIndex++;
    }
  }

  prevQuestion(): void {
    if (this.currentQuestionIndex > 0) {
      this.currentQuestionIndex--;
    }
  }

  submitAssessment(): void {
    if (!this.assessment || !this.allQuestionsAnswered()) return;

    this.evaluationLoading = true;
    this.assessmentError = '';

    this.assessmentService.submitAssessment(this.assessment.assessmentId, this.selectedAnswers).subscribe({
      next: result => {
        this.evaluationResult = result;
        this.evaluationLoading = false;
      },
      error: err => {
        this.evaluationLoading = false;
        if (err?.error?.detail) {
          this.assessmentError = err.error.detail;
        } else {
          this.assessmentError = 'Ocurrió un error al evaluar las respuestas. Por favor, intenta de nuevo.';
        }
      }
    });
  }

  retryAssessment(): void {
    this.startAssessment();
  }
}
