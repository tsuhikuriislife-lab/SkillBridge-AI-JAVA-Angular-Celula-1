export function compactDisplayId(id: string | null | undefined): string {
  if (!id) return '';
  return id.length > 5 ? `...${id.slice(-5)}` : id;
}