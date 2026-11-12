import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Room, RoomEvent, RemoteTrack, RemoteParticipant, Track } from 'livekit-client';
import { MeetingService, MeetingTokenOut } from '../../core/meeting.service';
import { ToastService } from '../../core/toast.service';

@Component({
  selector: 'app-meeting-room',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './meeting-room.component.html',
  styleUrls: ['./meeting-room.component.css']
})
export class MeetingRoomComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private meetingService = inject(MeetingService);
  private toast = inject(ToastService);

  @ViewChild('localVideo') localVideoRef?: ElementRef<HTMLVideoElement>;
  @ViewChild('remoteMediaContainer') remoteMediaRef?: ElementRef<HTMLDivElement>;

  room?: Room;
  tokenData = signal<MeetingTokenOut | null>(null);
  
  isConnecting = signal<boolean>(true);
  isConnected = signal<boolean>(false);
  isMicMuted = signal<boolean>(false);
  isCameraOff = signal<boolean>(false);
  isScreenSharing = signal<boolean>(false);
  isScreenShareToggling = signal<boolean>(false);
  hasRemoteVideo = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  statusMessage = signal<string>('Preparando sala de videoconferencia...');
  participantsCount = signal<number>(1);

  meetingId: string | null = null;
  serviceId: string | null = null;
  isStartAction = false;

  async ngOnInit(): Promise<void> {
    this.meetingId = this.route.snapshot.paramMap.get('id');
    this.serviceId = this.route.snapshot.queryParamMap.get('serviceId');
    this.isStartAction = this.route.snapshot.queryParamMap.get('start') === 'true';

    await this.initMeetingSession();
  }

  async initMeetingSession(): Promise<void> {
    this.isConnecting.set(true);
    this.errorMessage.set(null);

    if (this.room) {
      try {
        await this.room.disconnect();
      } catch (err) {
        console.warn('Error al desconectar sesión previa:', err);
      }
      this.room = undefined;
    }

    try {
      let tokenOut: MeetingTokenOut | null = this.meetingId ? this.meetingService.consumeCachedToken(this.meetingId) : null;

      if (!tokenOut) {
        if (this.isStartAction && this.serviceId) {
          this.statusMessage.set('Iniciando sala como mentor...');
          tokenOut = await this.meetingService.startMeeting(this.serviceId).toPromise() as MeetingTokenOut;
        } else if (this.meetingId) {
          this.statusMessage.set('Conectando a la reunión...');
          tokenOut = await this.meetingService.joinMeeting(this.meetingId).toPromise() as MeetingTokenOut;
        } else {
          throw new Error('Identificador de reunión o servicio no proporcionado');
        }
      }

      this.tokenData.set(tokenOut);
      this.meetingId = tokenOut.meetingId;

      await this.connectLiveKit(tokenOut);
    } catch (err: any) {
      console.error('Error al inicializar sesión de reunión:', err);
      const msg = err?.error?.message || err?.error?.detail || err?.message || 'No fue posible acceder a la reunión';
      this.errorMessage.set(msg);
      this.isConnecting.set(false);
      this.statusMessage.set('Error de conexión');
    }
  }

  private resolveLiveKitUrl(url: string): string {
    // Si el backend entrega el hostname interno de docker (livekit:7880), lo adaptamos al host del navegador
    if (url.includes('livekit:7880') && typeof window !== 'undefined') {
      const host = window.location.hostname || 'localhost';
      return url.replace('livekit:7880', `${host}:7880`);
    }
    return url;
  }

  @HostListener('click')
  @HostListener('keydown')
  async onUserGesture(): Promise<void> {
    if (this.room && !this.room.canPlaybackAudio) {
      try {
        await this.room.startAudio();
      } catch {
        // Ignorar si aún no hay audio listo
      }
    }
  }

  private async connectLiveKit(tokenOut: MeetingTokenOut): Promise<void> {
    const wsUrl = this.resolveLiveKitUrl(tokenOut.livekitUrl);
    this.statusMessage.set('Estableciendo conexión WebRTC...');

    this.room = new Room({
      adaptiveStream: false,
      dynacast: false,
      webAudioMix: false
    });

    this.room.on(RoomEvent.Connected, () => {
      this.isConnecting.set(false);
      this.isConnected.set(true);
      this.statusMessage.set('En vivo');
      this.updateParticipantsCount();
    });

    this.room.on(RoomEvent.Reconnecting, () => {
      this.statusMessage.set('Reconectando...');
    });

    this.room.on(RoomEvent.Reconnected, () => {
      this.statusMessage.set('En vivo');
      this.checkRemoteVideoAvailability();
    });

    this.room.on(RoomEvent.Disconnected, () => {
      this.isConnected.set(false);
      this.isScreenSharing.set(false);
      this.hasRemoteVideo.set(false);
      this.statusMessage.set('Sesión finalizada');
      this.toast.info('Te has desconectado de la reunión.');
    });

    this.room.on(RoomEvent.LocalTrackUnpublished, (pub) => {
      if (pub.source === Track.Source.ScreenShare) {
        this.isScreenSharing.set(false);
      }
    });

    this.room.on(RoomEvent.ParticipantConnected, () => {
      this.updateParticipantsCount();
      this.checkRemoteVideoAvailability();
    });

    this.room.on(RoomEvent.ParticipantDisconnected, () => {
      this.updateParticipantsCount();
      this.checkRemoteVideoAvailability();
    });

    // Escuchar tracks remotos (video o audio de otros participantes)
    this.room.on(RoomEvent.TrackSubscribed, (track: RemoteTrack) => {
      this.handleTrackSubscribed(track);
    });

    this.room.on(RoomEvent.TrackUnsubscribed, (track: RemoteTrack) => {
      this.handleTrackUnsubscribed(track);
    });

    this.room.on(RoomEvent.TrackMuted, (pub) => {
      if (pub.kind === 'video') {
        this.checkRemoteVideoAvailability();
      }
    });

    this.room.on(RoomEvent.TrackUnmuted, (pub) => {
      if (pub.kind === 'video') {
        if (pub.track) {
          this.handleTrackSubscribed(pub.track as RemoteTrack);
        }
        this.checkRemoteVideoAvailability();
      }
    });

    this.room.on(RoomEvent.LocalTrackPublished, () => {
      this.attachLocalVideo();
    });

    await this.room.connect(wsUrl, tokenOut.token, {
      autoSubscribe: true,
      peerConnectionTimeout: 20000
    });

    try {
      await this.room.startAudio();
    } catch {
      // Audio desbloqueará con el primer gesto del usuario
    }

    // Adjuntar tracks de participantes remotos que ya estuvieran transmitiendo
    this.attachExistingRemoteTracks();

    // Si es el mentor (PROVIDER) o el administrador (ADMIN), activamos sus periféricos por defecto
    if (tokenOut.role === 'PROVIDER' || tokenOut.role === 'ADMIN') {
      try {
        await this.room.localParticipant.enableCameraAndMicrophone();
        this.attachLocalVideo();
      } catch (mediaErr: any) {
        console.warn('No se pudo acceder a la cámara/micrófono:', mediaErr);
        this.toast.error('No se pudo acceder a tu cámara o micrófono. Verifica los permisos del navegador.');
      }
    }
  }

  private handleTrackSubscribed(track: RemoteTrack): void {
    if (!this.remoteMediaRef) return;
    const existingEl = document.getElementById(`track-${track.sid}`) as HTMLMediaElement | null;
    if (existingEl) {
      track.attach(existingEl);
    } else {
      const element = track.attach();
      element.id = `track-${track.sid}`;
      element.classList.add('remote-track-media');
      if (track.kind === 'video') {
        (element as HTMLVideoElement).autoplay = true;
        (element as HTMLVideoElement).playsInline = true;
      }
      this.remoteMediaRef.nativeElement.appendChild(element);
    }

    if (track.kind === 'video') {
      this.hasRemoteVideo.set(true);
    }
  }

  private handleTrackUnsubscribed(track: RemoteTrack): void {
    track.detach().forEach(el => el.remove());
    this.checkRemoteVideoAvailability();
  }

  private checkRemoteVideoAvailability(): void {
    if (!this.room) {
      this.hasRemoteVideo.set(false);
      return;
    }
    let hasVideo = false;
    this.room.remoteParticipants.forEach(participant => {
      participant.videoTrackPublications.forEach(pub => {
        if (pub.isSubscribed && pub.track && !pub.isMuted) {
          hasVideo = true;
        }
      });
    });
    this.hasRemoteVideo.set(hasVideo);
  }

  private attachExistingRemoteTracks(): void {
    if (!this.room || !this.remoteMediaRef) return;
    this.room.remoteParticipants.forEach(participant => {
      participant.trackPublications.forEach(pub => {
        if (pub.track && pub.isSubscribed) {
          this.handleTrackSubscribed(pub.track as RemoteTrack);
        }
      });
    });
    this.checkRemoteVideoAvailability();
  }

  private attachLocalVideo(): void {
    if (!this.room || !this.localVideoRef) return;
    const cameraPub = this.room.localParticipant.getTrackPublication(Track.Source.Camera);
    if (cameraPub && cameraPub.videoTrack) {
      cameraPub.videoTrack.attach(this.localVideoRef.nativeElement);
      return;
    }
    const pub = Array.from(this.room.localParticipant.videoTrackPublications.values())[0];
    if (pub && pub.videoTrack) {
      pub.videoTrack.attach(this.localVideoRef.nativeElement);
    }
  }

  private updateParticipantsCount(): void {
    if (!this.room) return;
    this.participantsCount.set(this.room.remoteParticipants.size + 1);
  }

  async toggleMicrophone(): Promise<void> {
    if (!this.room) return;
    const nextState = !this.isMicMuted();
    this.isMicMuted.set(nextState);
    await this.room.localParticipant.setMicrophoneEnabled(!nextState);
  }

  async toggleCamera(): Promise<void> {
    if (!this.room) return;
    const nextState = !this.isCameraOff();
    this.isCameraOff.set(nextState);
    await this.room.localParticipant.setCameraEnabled(!nextState);
    if (!nextState) {
      setTimeout(() => this.attachLocalVideo(), 300);
    }
  }

  async toggleScreenShare(): Promise<void> {
    if (!this.room || this.room.state !== 'connected' || this.isScreenShareToggling()) {
      return;
    }

    this.isScreenShareToggling.set(true);
    try {
      const nextState = !this.isScreenSharing();
      if (nextState) {
        const pub = await this.room.localParticipant.setScreenShareEnabled(
          true,
          { audio: false },
          { simulcast: false }
        );
        this.isScreenSharing.set(true);

        // Si el usuario cancela o detiene la compartición desde la barra nativa del navegador
        if (pub && pub.track) {
          pub.track.mediaStreamTrack.addEventListener(
            'ended',
            () => {
              this.isScreenSharing.set(false);
              this.room?.localParticipant.setScreenShareEnabled(false).catch(() => {});
            },
            { once: true }
          );
        }
      } else {
        await this.room.localParticipant.setScreenShareEnabled(false);
        this.isScreenSharing.set(false);
      }
    } catch (e: any) {
      console.warn('Compartir pantalla cancelado o error de negociación:', e);
      this.isScreenSharing.set(false);
      try {
        await this.room.localParticipant.setScreenShareEnabled(false);
      } catch {}
    } finally {
      this.isScreenShareToggling.set(false);
    }
  }

  async leaveMeeting(): Promise<void> {
    if (this.room) {
      await this.room.disconnect();
    }
    const role = this.tokenData()?.role;
    if (role === 'ADMIN') {
      this.router.navigate(['/admin']);
    } else if (role === 'PROVIDER') {
      this.router.navigate(['/provider/services']);
    } else {
      this.router.navigate(['/bookings']);
    }
  }

  async finishMeeting(): Promise<void> {
    if (!this.meetingId) return;
    const confirmed = confirm('¿Estás seguro de que deseas finalizar esta reunión para todos los participantes?');
    if (!confirmed) return;

    try {
      await this.meetingService.finishMeeting(this.meetingId).toPromise();
      this.toast.success('Reunión finalizada con éxito.');
      if (this.room) {
        await this.room.disconnect();
      }
      const role = this.tokenData()?.role;
      this.router.navigate([role === 'ADMIN' ? '/admin' : '/provider/services']);
    } catch (err: any) {
      this.toast.error('No fue posible finalizar la reunión en el servidor.');
    }
  }

  ngOnDestroy(): void {
    if (this.room) {
      this.room.disconnect();
    }
  }
}

