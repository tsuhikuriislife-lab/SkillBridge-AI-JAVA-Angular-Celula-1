import { Component, ElementRef, ViewChild, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { apiBase } from '../../core/api';

interface ChatMessage {
  text: string;
  sender: 'user' | 'assistant';
  isWelcome?: boolean;
}

@Component({
  selector: 'app-ai',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ai.component.html',
  styleUrls: ['./ai.component.css']
})
export class AiComponent implements AfterViewChecked {
  messages: ChatMessage[] = [];
  inputValue: string = '';
  loading: boolean = false;
  showSuggestions: boolean = true;
  
  suggestions = [
    { text: '¿Cómo estructurar una arquitectura hexagonal en Spring Boot?', icon: 'bx-layer' },
    { text: 'Explicación del flujo de eventos con caché en esta plataforma', icon: 'bx-bolt-circle' },
    { text: 'Recomiéndame un servicio según mi perfil de desarrollo', icon: 'bx-sparkles-alt' }
  ];

  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;

  constructor(private http: HttpClient) {
    this.resetChat();
  }

  ngAfterViewChecked() {
    this.scrollToBottom();
  }

  scrollToBottom(): void {
    try {
      this.messagesContainer.nativeElement.scrollTop = this.messagesContainer.nativeElement.scrollHeight;
    } catch(err) { }
  }

  sendMessage(text?: string) {
    const content = (text ?? this.inputValue).trim();
    if (!content) return;

    this.inputValue = '';
    this.showSuggestions = false;

    // Add user message
    this.messages.push({ text: content, sender: 'user' });
    this.loading = true;

    // Call API
    this.http.post<{recommendation: string}>(`${apiBase()}/ai/recommendations`, { goal: content }).subscribe({
      next: (response) => {
        this.messages.push({ text: response.recommendation, sender: 'assistant' });
        this.loading = false;
      },
      error: (e) => {
        let errorMsg = 'Error al comunicarse con la IA.';
        if (e.status === 401 || e.status === 403) {
          errorMsg = 'Tu sesión ha expirado o no tienes permisos. Por favor, inicia sesión.';
        } else if (e?.error?.detail) {
          errorMsg = e.error.detail;
        }
        this.messages.push({ text: errorMsg, sender: 'assistant' });
        this.loading = false;
      }
    });
  }

  resetChat() {
    this.messages = [{
      text: '¡Hola! Soy el asistente inteligente de SkillBridge AI. Puedo ayudarte a resolver dudas sobre arquitectura de software, configurar tus servicios suscritos o guiarte en el uso de Spring Boot, Angular y soluciones Cloud. ¿En qué trabajaremos hoy?',
      sender: 'assistant',
      isWelcome: true
    }];
    this.showSuggestions = true;
    this.inputValue = '';
    this.loading = false;
  }
}
