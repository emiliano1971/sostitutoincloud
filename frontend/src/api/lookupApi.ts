import { getConfig } from '@/config/AppConfig';

export interface LookupItem {
  id: number;
  codice: string;
  descrizione: string;
  attivo: boolean;
}

export interface LookupCollection {
  regimiFiscali: LookupItem[];
  tipiImmobile: LookupItem[];
  canaliOta: LookupItem[];
  tipiDocumento: LookupItem[];
  statiPrenotazione: LookupItem[];
  statiDocumento: LookupItem[];
  scenariFiscali: LookupItem[];
}

export async function getLookups(): Promise<LookupCollection> {
  const base = getConfig().apiBaseUrl;
  const url = `${base}/public/lookup`;
  const res = await fetch(url, { headers: { 'Accept': 'application/json' } });
  if (!res.ok) throw new Error(`Lookup fetch failed: HTTP ${res.status}`);
  return res.json();
}
