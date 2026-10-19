export function apiBase(): string {
  return window.__env?.API_URL || '/api';
}
