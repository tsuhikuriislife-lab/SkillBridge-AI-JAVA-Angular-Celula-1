import { Injectable, signal } from '@angular/core';

export interface ToastAction {
  label: string;
  action: () => void;
  primary?: boolean;
}

export interface ToastMessage {
  message: string;
  actions: ToastAction[];
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  toast = signal<ToastMessage | null>(null);

  show(message: string, actions: ToastAction[]) {
    this.toast.set({ message, actions });
  }

  info(message: string) {
    this.show(message, [{ label: 'Cerrar', primary: true, action: () => this.clear() }]);
  }

  error(message: string) {
    this.show(message, [{ label: 'Entendido', primary: true, action: () => this.clear() }]);
  }

  success(message: string) {
    this.show(message, [{ label: 'OK', primary: true, action: () => this.clear() }]);
  }

  clear() {
    this.toast.set(null);
  }
}
