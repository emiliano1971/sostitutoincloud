import { getConfig } from '@/config/AppConfig';

export interface ComuneItaliano {
  id: number;
  nome: string;
  siglaProvincia: string;
  regione: string;
  codiceBelfiore: string;
}

/** Ricerca comuni per prefisso del nome. Ritorna [] se q ha meno di 2 caratteri. */
export async function cercaComuni(q: string): Promise<ComuneItaliano[]> {
  if (!q || q.trim().length < 2) return [];
  const base = getConfig().apiBaseUrl;
  const url = `${base}/public/comuni?q=${encodeURIComponent(q.trim())}`;
  const res = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!res.ok) throw new Error(`Ricerca comuni fallita: HTTP ${res.status}`);
  return res.json();
}
