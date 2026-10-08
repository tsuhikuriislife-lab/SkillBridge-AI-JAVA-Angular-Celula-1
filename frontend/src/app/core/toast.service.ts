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

  clear() {
    this.toast.set(null);
  }
}
