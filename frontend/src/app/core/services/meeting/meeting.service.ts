import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface MeetingTokenOut {
  meetingId: string;
  livekitUrl: string;
  token: string;
  role: 'PROVIDER' | 'PARTICIPANT' | 'ADMIN';
  tokenExpiresAt: string;
}

export interface MeetingOut {
  id: string;
  serviceId: string;
  meetingDate: string;
  startTime: string;
  durationMinutes: number;
  status: 'PROGRAMADA' | 'EN_CURSO' | 'FINALIZADA' | 'CANCELADA' | 'NO_REALIZADA';
  scheduledStart: string;
  actualStart?: string;
  actualEnd?: string;
  maxEnd?: string;
  extensionUsed: boolean;
  cancellationReason?: string;
  cancelledBy?: string;
  createdAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class MeetingService {
  private http = inject(HttpClient);
  private baseUrl = '/api/meetings';
  private cachedToken: MeetingTokenOut | null = null;

  setCachedToken(token: MeetingTokenOut): void {
    this.cachedToken = token;
  }

  consumeCachedToken(meetingId: string): MeetingTokenOut | null {
    if (this.cachedToken && this.cachedToken.meetingId === meetingId) {
      const token = this.cachedToken;
      this.cachedToken = null;
      return token;
    }
    return null;
  }

  /**
   * Inicia una reunión en vivo para el servicio dado (solo para el proveedor propietario).
   */
  startMeeting(serviceId: string): Observable<MeetingTokenOut> {
    return this.http.post<MeetingTokenOut>(`${this.baseUrl}/service/${serviceId}/start`, {});
  }

  /**
   * Se une a una reunión activa obteniendo el token de acceso LiveKit.
   */
  joinMeeting(meetingId: string): Observable<MeetingTokenOut> {
    return this.http.post<MeetingTokenOut>(`${this.baseUrl}/${meetingId}/join`, {});
  }

  /**
   * Finaliza la reunión activa (para el proveedor o el administrador).
   */
  finishMeeting(meetingId: string): Observable<MeetingOut> {
    return this.http.post<MeetingOut>(`${this.baseUrl}/${meetingId}/finish`, {});
  }

  /**
   * Obtiene la información y estado actual de la reunión.
   */
  getMeeting(meetingId: string): Observable<MeetingOut> {
    return this.http.get<MeetingOut>(`${this.baseUrl}/${meetingId}`);
  }

  /**
   * Consulta las reuniones programadas o realizadas para un servicio.
   */
  getByService(serviceId: string): Observable<MeetingOut[]> {
    return this.http.get<MeetingOut[]>(`${this.baseUrl}/service/${serviceId}`);
  }

  /**
   * Crea e inicia una sala de prueba en vivo para un servicio (solo administradores).
   */
  createTestRoom(serviceId: string): Observable<MeetingTokenOut> {
    return this.http.post<MeetingTokenOut>(`${this.baseUrl}/admin/test-room`, { serviceId });
  }
}
