import { get, post, patch, getToken } from '@/lib/apiClient';
import { getConfig } from '@/config/AppConfig';

export interface F24Record {
  id: number;
  periodoMese: number;
  periodoAnno: number;
  totalAmount: number;
  withholdingsCount: number;
  stato: string;
  deadlineDate: string;
  paymentDate?: string;
  codiceTributo: string;
}

export interface WithholdingLedgerItem {
  id: number;
  ownerName?: string;
  // Prenotazione collegata: bookingId serve al link verso la scheda,
  // il periodo (mese/anno del ledger) a segnalare gli arretrati.
  bookingId?: number;
  bookingExternalId?: string;
  guestName?: string;
  propertyName?: string;
  checkinDate?: string;
  checkoutDate?: string;
  documentNumber?: string;
  dataEvento: string;
  periodoMese?: number;
  periodoAnno?: number;
  canoneLocazione: number;
  aliquotaRitenuta: number;
  ritenutaAmount: number;
  stato: string;
}

export interface F24GenerazioneResult {
  f24RecordId: number;
  periodoMese: number;
  periodoAnno: number;
  totaleRitenute: number;
  numeroRitenute: number;
  scadenza: string;
  stato: string;
  ritenute: WithholdingLedgerItem[];
}

export async function getF24List(): Promise<F24Record[]> {
  return get<F24Record[]>('/f24');
}

export async function generaF24(anno: number, mese: number): Promise<F24GenerazioneResult> {
  return post<F24GenerazioneResult>('/f24/genera', { anno, mese });
}

export async function getF24Detail(id: number): Promise<F24GenerazioneResult> {
  return get<F24GenerazioneResult>(`/f24/${id}`);
}

export async function marcaF24Pagato(id: number): Promise<F24Record> {
  return patch<F24Record>(`/f24/${id}/pagato`, {});
}

export async function ricalcolaF24(id: number): Promise<F24GenerazioneResult> {
  return patch<F24GenerazioneResult>(`/f24/${id}/ricalcola`, {});
}

/** Scarica il PDF del modello F24 nel browser. */
export async function downloadF24Pdf(id: number): Promise<void> {
  const base = getConfig().apiBaseUrl;
  const token = getToken();
  const res = await fetch(`${base}/f24/${id}/pdf`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try {
      const j = await res.json();
      msg = j.error || j.message || msg;
    } catch { /* body non JSON */ }
    throw new Error(msg);
  }
  // nome file dal Content-Disposition, fallback F24_{id}.pdf
  const cd = res.headers.get('Content-Disposition') ?? '';
  const match = cd.match(/filename="?([^"]+)"?/);
  const filename = match ? match[1] : `F24_${id}.pdf`;

  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
