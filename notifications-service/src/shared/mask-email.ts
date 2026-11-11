/** "ana.gomez@gmail.com" → "a***@gmail.com" (para logs sin datos personales). */
export function maskEmail(email: string): string {
  const [user, domain] = email.split('@');
  if (!user || !domain) return '***';
  return `${user[0]}***@${domain}`;
}
