import { get, post, patch, getToken } from '@/lib/apiClient';
import { getConfig } from '@/config/AppConfig';

export interface SettlementListItem {
  id: number;
  ownerName: string;
  period: string;
  totalAmount: number;
  withholdingAmount: number;
  netAmount: number;
  bookingsCount: number;
  stato: string;
  paymentDate?: string;
  createdAt: string;
}

export interface SettlementBookingItem {
  bookingId: number;
  externalBookingId: string;
  propertyName: string;
  checkinDate: string;
  checkoutDate: string;
  grossAmount: number;
  otaCommissionAmount: number;
  cleaningAmount: number;
  pmFeeAmount: number;
  ivaAmount: number;    // IVA scorporata dalla provvigione PM
  ownerNetAmount: number;
  withholdingAmount: number;
  bolloCents: number;   // bollo in centesimi (0 o 200)
  /**
   * Tassa di soggiorno della prenotazione. Se inclusa nel lordo è già stata scorporata
   * dalla base dello split, quindi non incide sul canone: serve solo a spiegare perché
   * il canone non corrisponde al lordo meno le altre voci.
   */
  touristTaxAmount?: number;
  touristTaxIncludedInGross?: boolean;
  /**
   * Competenza della ritenuta (es. "08/2026") quando differisce dal periodo del
   * settlement: la prenotazione è entrata come arretrato. null/assente nel caso normale.
   */
  periodoLedger?: string | null;
}

export interface SettlementDetail extends SettlementListItem {
  fkTenantId: number;
  fkOwnerId: number;
  updatedAt: string;
  bookings: SettlementBookingItem[];
}

export async function getSettlements(params?: {
  ownerId?: number;
  period?: string;
}): Promise<SettlementListItem[]> {
  if (!params || Object.keys(params).length === 0) {
    return get<SettlementListItem[]>('/settlements');
  }
  const qs = new URLSearchParams();
  if (params.ownerId !== undefined) qs.set('ownerId', String(params.ownerId));
  if (params.period) qs.set('period', params.period);
  return get<SettlementListItem[]>(`/settlements?${qs.toString()}`);
}

export async function getSettlementById(id: number): Promise<SettlementDetail> {
  return get<SettlementDetail>(`/settlements/${id}`);
}

export interface SettlementCalcolaRequest {
  mese: number;
  anno: number;
}

export interface SettlementCalcolaResult {
  generated: number;
  updated: number;
  skipped: number;
  settlements: SettlementListItem[];
}

export async function calcolaSettlements(
  req: SettlementCalcolaRequest,
): Promise<SettlementCalcolaResult> {
  return post<SettlementCalcolaResult>('/settlements/calcola', req);
}

export async function updateSettlementStatus(
  id: number,
  stato: string,
): Promise<SettlementListItem> {
  return patch<SettlementListItem>(`/settlements/${id}/status`, { stato });
}

export interface BookingDaLiquidare {
  bookingId: number;
  externalBookingId: string;
  ownerName: string;
  propertyName: string;
  checkinDate: string;
  checkoutDate: string;
  canoneLocazione: number;
  ritenutaAmount: number;
  nettoProprietario: number;
  /** Periodo di competenza della ritenuta, es. "08/2026" */
  periodoLedger: string;
}

/** Prenotazioni con documenti emessi non ancora incluse in una liquidazione. */
export async function getBookingsDaLiquidare(): Promise<BookingDaLiquidare[]> {
  return get<BookingDaLiquidare[]>('/settlements/da-liquidare');
}

/**
 * Scarica il rendiconto PDF della liquidazione e avvia il download nel browser.
 * Non usa apiClient perché la risposta è un blob, non JSON.
 */
export async function downloadSettlementPdf(
  id: number,
  periodo: string,
  ownerName: string,
): Promise<void> {
  const base = getConfig().apiBaseUrl;
  const token = getToken();
  const res = await fetch(`${base}/settlements/${id}/pdf`, {
    headers: {
      'Accept': 'application/pdf',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    },
  });

  if (!res.ok) {
    // In caso di errore il backend risponde JSON con il campo message
    let message = `Errore ${res.status} durante la generazione del PDF`;
    try {
      const json = await res.json();
      if (json.message) message = json.message;
    } catch { /* body non JSON */ }
    throw new Error(message);
  }

  const fileName = `Rendiconto_${periodo}_${ownerName}`.replace(/\s+/g, '_');
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  try {
    const link = document.createElement('a');
    link.href = url;
    link.download = `${fileName}.pdf`;
    document.body.appendChild(link);
    link.click();
    link.remove();
  } finally {
    URL.revokeObjectURL(url);
  }
}
