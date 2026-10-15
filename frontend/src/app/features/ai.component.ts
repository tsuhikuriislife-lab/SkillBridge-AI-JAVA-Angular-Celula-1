import { Component, ElementRef, ViewChild, AfterViewChecked, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { apiBase } from '../core/api';
import { Offering, OfferingService } from '../core/offering.service';

export interface RecommendedCourse {
  id: string;
  title: string;
  category?: string;
}

export interface ChatMessage {
  text: string;
  sender: 'user' | 'assistant';
  isWelcome?: boolean;
  courses?: RecommendedCourse[];
  formattedHtml?: SafeHtml;
}

@Component({
  selector: 'app-ai',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ai.component.html',
  styleUrls: ['./ai.component.css']
})
export class AiComponent implements OnInit, AfterViewChecked {
  messages: ChatMessage[] = [];
  inputValue: string = '';
  loading: boolean = false;
  showSuggestions: boolean = true;
  offerings: Offering[] = [];
  
  suggestions = [
    { text: '¿Cómo estructurar una arquitectura hexagonal en Spring Boot?', icon: 'bx-layer' },
    { text: 'Explicación del flujo de eventos con caché en esta plataforma', icon: 'bx-bolt-circle' },
    { text: 'Recomiéndame un servicio según mi perfil de desarrollo', icon: 'bx-sparkles-alt' }
  ];

  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;

  constructor(
    private http: HttpClient,
    private offeringService: OfferingService,
    private router: Router,
    private sanitizer: DomSanitizer
  ) {
    this.resetChat();
  }

  ngOnInit(): void {
    this.offeringService.list().subscribe({
      next: (data) => {
        this.offerings = data || [];
        // Re-analizar mensajes previos para vincular nombres y categorías del catálogo
        for (const msg of this.messages) {
          if (msg.sender === 'assistant' && (!msg.courses || msg.courses.length === 0)) {
            msg.courses = this.extractCourses(msg.text);
          }
        }
      },
      error: () => {
        // En caso de error de red secundario, el asistente continúa operando con los datos locales
      }
    });
  }

  ngAfterViewChecked(): void {
    this.scrollToBottom();
  }

  scrollToBottom(): void {
    try {
      this.messagesContainer.nativeElement.scrollTop = this.messagesContainer.nativeElement.scrollHeight;
    } catch(err) { }
  }

  goToCourse(courseId: string): void {
    if (!courseId) return;
    this.router.navigate(['/service', courseId]);
  }

  handleBubbleClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    const link = target.closest('a') as HTMLAnchorElement | null;
    if (link) {
      const serviceId = link.getAttribute('data-service-id');
      const href = link.getAttribute('href');
      if (serviceId) {
        event.preventDefault();
        this.goToCourse(serviceId);
      } else if (href && href.includes('/service/')) {
        event.preventDefault();
        const parts = href.split('/service/');
        if (parts.length > 1) {
          this.goToCourse(parts[1].split('?')[0].split('#')[0]);
        }
      }
    }
  }

  sendMessage(text?: string): void {
    const content = (text ?? this.inputValue).trim();
    if (!content) return;

    this.inputValue = '';
    this.showSuggestions = false;

    // Add user message
    this.messages.push(this.createMessage(content, 'user'));
    this.loading = true;

    // Call API
    this.http.post<{recommendation: string}>(`${apiBase()}/ai/recommendations`, { goal: content }).subscribe({
      next: (response) => {
        this.messages.push(this.createMessage(response.recommendation, 'assistant'));
        this.loading = false;
      },
      error: (e) => {
        let errorMsg = 'Error al comunicarse con la IA.';
        if (e.status === 401 || e.status === 403) {
          errorMsg = 'Tu sesión ha expirado o no tienes permisos. Por favor, inicia sesión.';
        } else if (e?.error?.detail) {
          errorMsg = e.error.detail;
        }
        this.messages.push(this.createMessage(errorMsg, 'assistant'));
        this.loading = false;
      }
    });
  }

  resetChat(): void {
    this.messages = [
      this.createMessage(
        '¡Hola! Soy el asistente inteligente de SkillBridge AI. Puedo recomendarte los cursos y mentorías ideales para tu perfil, resolver dudas sobre arquitectura de software (Spring Boot, Angular, Cloud) y darte enlaces directos para inscribirte de inmediato. ¿En qué trabajaremos hoy?',
        'assistant',
        true
      )
    ];
    this.showSuggestions = true;
    this.inputValue = '';
    this.loading = false;
  }

  private createMessage(text: string, sender: 'user' | 'assistant', isWelcome: boolean = false): ChatMessage {
    const msg: ChatMessage = {
      text,
      sender,
      isWelcome
    };

    if (sender === 'assistant') {
      msg.courses = this.extractCourses(text);
      msg.formattedHtml = this.formatAssistantText(text);
    } else {
      const escaped = this.escapeHtml(text).replace(/\n/g, '<br>');
      msg.formattedHtml = this.sanitizer.bypassSecurityTrustHtml(escaped);
    }

    return msg;
  }

  private extractCourses(text: string): RecommendedCourse[] {
    const courses: RecommendedCourse[] = [];
    const seenIds = new Set<string>();

    // 1. Extraer enlaces explícitos Markdown [Título](/service/uuid)
    const mdLinkRegex = /\[([^\]]+)\]\((?:https?:\/\/[^\/]+)?\/service\/([a-f0-9\-]{36})\)/gi;
    let match: RegExpExecArray | null;
    while ((match = mdLinkRegex.exec(text)) !== null) {
      const rawTitle = match[1].replace(/^(?:Ver curso:?|Ir al curso:?|Curso:?)\s*/i, '').trim();
      const id = match[2];
      if (!seenIds.has(id)) {
        seenIds.add(id);
        const offering = this.offerings.find(o => o.id.toLowerCase() === id.toLowerCase());
        courses.push({
          id,
          title: offering ? offering.title : rawTitle,
          category: offering?.category
        });
      }
    }

    // 2. Extraer rutas directas /service/uuid
    const pathRegex = /\/service\/([a-f0-9\-]{36})/gi;
    while ((match = pathRegex.exec(text)) !== null) {
      const id = match[1];
      if (!seenIds.has(id)) {
        seenIds.add(id);
        const offering = this.offerings.find(o => o.id.toLowerCase() === id.toLowerCase());
        courses.push({
          id,
          title: offering ? offering.title : 'Curso recomendado',
          category: offering?.category
        });
      }
    }

    // 3. Fallback: buscar títulos de cursos del catálogo si no se detectó ID
    if (courses.length === 0 && this.offerings.length > 0) {
      for (const offering of this.offerings) {
        const regex = new RegExp(`\\b${this.escapeRegExp(offering.title)}\\b`, 'i');
        if (regex.test(text) && !seenIds.has(offering.id)) {
          seenIds.add(offering.id);
          courses.push({
            id: offering.id,
            title: offering.title,
            category: offering.category
          });
        }
      }
    }

    return courses;
  }

  private formatAssistantText(text: string): SafeHtml {
    let formatted = this.escapeHtml(text);

    // Negrita **texto** -> <strong>texto</strong>
    formatted = formatted.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');

    // Cursos con enlaces Markdown [Título](/service/ID)
    formatted = formatted.replace(
      /\[([^\]]+)\]\((?:https?:\/\/[^\/]+)?\/service\/([a-f0-9\-]{36})\)/gi,
      '<a href="/service/$2" class="chat-service-link" data-service-id="$2"><i class="bx bx-link-external"></i> $1</a>'
    );

    // Enlaces directos /service/UUID sueltos
    formatted = formatted.replace(
      /(?<!href="|">)\/service\/([a-f0-9\-]{36})/gi,
      '<a href="/service/$1" class="chat-service-link" data-service-id="$1"><i class="bx bx-link-external"></i> Ver curso</a>'
    );

    // Listas viñetas
    formatted = formatted.replace(/^[*-]\s+(.+)$/gm, '• $1');

    // Separadores horizontales
    formatted = formatted.replace(/^---$/gm, '<hr class="chat-divider">');

    // Saltos de línea
    formatted = formatted.replace(/\n/g, '<br>');

    return this.sanitizer.bypassSecurityTrustHtml(formatted);
  }

  private escapeHtml(str: string): string {
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }

  private escapeRegExp(str: string): string {
    return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }
}
