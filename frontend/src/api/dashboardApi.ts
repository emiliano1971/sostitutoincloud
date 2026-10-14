import { get } from '@/lib/apiClient';

export interface MensileDTO {
  mese: string;
  meseKey: string;
  ricaviPm: number;
  ricaviOw: number;
  commissioni: number;
  ritenute: number;
}

export interface DashboardDTO {
  bookingsDaCompletare: number;
  bookingsInPenale: number;
  /** Prenotazioni 'ready' con check-out passato e senza fattura PM. */
  prenotazioniDaFatturare: number;
  /** Fatture PM e NDC pronte per la trasmissione SDI. */
  fattureDaInviareSDI: number;
  f24DaGenerare: number;
  liquidazioniPending: number;
  ricaviMeseCorrente: number;
  ricaviMesePrecedente: number;
  ricaviUltimi12Mesi: MensileDTO[];
}

export async function getDashboard(): Promise<DashboardDTO> {
  return get<DashboardDTO>('/dashboard');
}
