import { get } from '@/lib/apiClient';

export interface SplitEconomico {
  grossAmount: number;
  otaCommissionAmount: number;
  cleaningAmount: number;
  pmFeeAmount: number;
  ownerNetAmount: number;
  withholdingAmount: number;
  liquidazioneOwner: number;
  touristTaxAmount: number;
  touristTaxIncludedInGross: boolean;
}

export interface FiscalDocumentSummary {
  id: number;
  documentNumber: string;
  tipoDocumento: string;
  statoDocumento: string;
  dataEmissione: string;
  importoTotale: number;
  imponibile: number;
  ritenutaAmount: number;
  bolloAmount: number;
  ivaAmount: number;
}

export interface BookingListItem {
  id: number;
  fkPropertyId?: number;
  externalBookingId: string;
  guestName: string;
  propertyName: string;
  ownerName: string;
  channelName: string;
  checkinDate: string;
  checkoutDate: string;
  nights: number;
  guests: number;
  grossAmount: number;
  ownerNetAmount: number;
  statoPrenotazione: string;
  paymentStatus: string;
  documentStatus: string;
  settlementStatus: string;
  createdAt: string;
}

export interface BookingDetail extends BookingListItem {
  fkTenantId: number;
  fkPropertyId: number;
  fkOwnerId: number;
  guestTaxCode?: string;
  fiscalScenarioCode?: string;
  otaCommissionAmount?: number;
  cleaningAmount?: number;
  pmFeeAmount?: number;
  withholdingAmount?: number;
  touristTaxAmount?: number;
  touristTaxIncludedInGross: boolean;
  touristTaxCollection?: string;
  updatedAt: string;
  splitEconomico: SplitEconomico;
  // Dati immobile per dialog
  propertyAddress?: string;
  propertyCity?: string;
  propertyInternalCode?: string;
  // Dati proprietario per dialog
  ownerTaxCode?: string;
  ownerIban?: string;
  ownerEmail?: string;
  // Dati tenant per dialog fattura PM
  tenantLegalName?: string;
  tenantVatNumber?: string;
  tenantTaxCode?: string;
  tenantLegalAddress?: string;
  tenantPec?: string;
  // Documenti fiscali associati alla prenotazione
  documenti: FiscalDocumentSummary[];
}

export async function getBookings(params?: {
  status?: string;
  channel?: string;
  q?: string;
  page?: number;
  size?: number;
}): Promise<BookingListItem[]> {
  if (!params || Object.keys(params).length === 0) {
    return get<BookingListItem[]>('/bookings');
  }
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.channel) qs.set('channel', params.channel);
  if (params.q) qs.set('q', params.q);
  if (params.page !== undefined) qs.set('page', String(params.page));
  if (params.size !== undefined) qs.set('size', String(params.size));
  return get<BookingListItem[]>(`/bookings?${qs.toString()}`);
}

export async function getBookingById(id: number): Promise<BookingDetail> {
  return get<BookingDetail>(`/bookings/${id}`);
}
