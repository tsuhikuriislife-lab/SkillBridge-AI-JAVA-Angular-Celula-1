export function apiBase(): string {
  const url = window.__env?.API_URL;
  if (url && !url.startsWith('http')) {
    return url;
  }
  return '/api';
}
