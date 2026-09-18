import { get, del, patch, post } from '@/lib/apiClient';

export interface SplitEconomico {
  grossAmount: number;
  otaCommissionAmount: number;
  cleaningAmount: number;
  pmFeeAmount: number;
  ownerNetAmount: number;
  withholdingAmount: number;
  aliquotaRitenuta?: number;
  liquidazioneOwner: number;
  touristTaxAmount: number;
  touristTaxIncludedInGross: boolean;
  imponibileFatturaPm?: number;
  ivaScorporataPm?: number;
  fatturaPmTotale?: number;
  warnings?: string[];
  calcoloCompleto?: boolean;
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
  aliquotaIva?: number;
  canoneLocazione?: number;
  fkDocumentoCollegatoId?: number;
}

export interface BookingListItem {
  id: number;
  fkPropertyId?: number;
  externalBookingId: string;
  guestName: string;
  propertyName: string;
  fkOwnerId?: number;
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
  guestBirthDate?: string;
  guestSesso?: string;
  guestBirthPlace?: string;
  guestBirthBelfiore?: string;
  guestDocType?: string;
  guestDocNumber?: string;
  guestCountry?: string;
  guestAddress?: string;
  guestPhone?: string;
  fiscalScenarioCode?: string;
  otaCommissionAmount?: number;
  cleaningAmount?: number;
  pmFeeAmount?: number;
  withholdingAmount?: number;
  touristTaxAmount?: number;
  touristTaxIncludedInGross: boolean;
  touristTaxCollection?: string;
  updatedAt: string;
  // Stato del settlement reale associato (null se nessuno)
  settlementStato?: string;
  settlementId?: number;
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

// Disponibile solo nei profili local e test (endpoint backend @Profile).
export async function deleteBooking(id: number): Promise<void> {
  await del<unknown>(`/bookings/${id}`);
}

export interface BookingCreateRequest {
  fkPropertyId: number;
  fkCanaleOtaId?: number;
  externalBookingId?: string;
  checkinDate: string;
  checkoutDate: string;
  guests: number;
  grossAmount: number;
  guestName: string;
  guestTaxCode?: string;
  guestBirthDate?: string;
  guestSesso?: string;
  guestBirthPlace?: string;
  guestDocType?: string;
  guestDocNumber?: string;
  guestCountry?: string;
  guestAddress?: string;
  guestPhone?: string;
}

/** Inserimento manuale. Split economico, ritenuta e stato sono calcolati dal backend. */
export async function createBooking(data: BookingCreateRequest): Promise<BookingDetail> {
  return post<BookingDetail>('/bookings', data);
}

export interface GuestUpdateRequest {
  guestName: string;
  guestTaxCode?: string;
  guestBirthDate?: string;
  guestSesso?: string;
  guestBirthPlace?: string;
  guestBirthBelfiore?: string;
  guestDocType?: string;
  guestDocNumber?: string;
  guestCountry?: string;
  guestAddress?: string;
  guestPhone?: string;
}

export async function updateBookingGuest(id: number, data: GuestUpdateRequest): Promise<BookingDetail> {
  return patch<BookingDetail>(`/bookings/${id}/guest`, data);
}

/** Calcola il codice fiscale via backend; ritorna solo la stringa CF. */
export async function calcolaCodiceFiscale(
  cognome: string,
  nome: string,
  dataNascita: string,
  sesso: string,
  comuneNascita: string,
): Promise<string> {
  const res = await post<{ codiceFiscale: string }>('/cf/calcola', {
    cognome, nome, dataNascita, sesso, comuneNascita,
  });
  return res.codiceFiscale;
}

/** Genera un CF fittizio per ospite straniero (progressivo per tenant/anno). */
export async function generaCfEstero(): Promise<string> {
  const res = await get<{ cf: string }>('/cf/estero');
  return res.cf;
}
