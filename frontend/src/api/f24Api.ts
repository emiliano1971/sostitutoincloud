import { get, post, patch } from '@/lib/apiClient';

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
  ownerName: string;
  bookingExternalId: string;
  documentNumber: string;
  dataEvento: string;
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
