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
  documentiPending: number;
  f24DaGenerare: number;
  liquidazioniPending: number;
  ricaviMeseCorrente: number;
  ricaviMesePrecedente: number;
  ricaviUltimi12Mesi: MensileDTO[];
}

export async function getDashboard(): Promise<DashboardDTO> {
  return get<DashboardDTO>('/dashboard');
}
