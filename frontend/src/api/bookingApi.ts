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
  // Regola di contratto applicata, mostrata sotto la voce. Assenti sugli split storici
  // (prenotazioni con documenti fiscali già emessi).
  pmFeeDescrizione?: string;
  otaDescrizione?: string;
  /** Regime fiscale del PM: 'RF01' ordinario (IVA 22% scorporata) | 'RF19' forfettario (senza IVA). */
  regimeFiscalePm?: string;
}

/** Riga di booking_split_economico (voce di costo dello split). */
export interface BookingSplitRiga {
  id: number;
  fkBookingId: number;
  fkPropertyContractRuleId?: number;
  tipoVoce: string;
  descrizione: string;
  importo: number;
  aliquotaIva: number;
  includeInFatturaPm: boolean;
  ordinamento: number;
  /** 'calcolato' | 'manuale' | 'import' */
  source: string;
  createdAt?: string;
  updatedAt?: string;
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
  // Il backend omette il campo (Jackson NON_NULL) quando la prenotazione
  // non ha un canale OTA associato — es. inserimento manuale senza canale.
  channelName?: string;
  checkinDate: string;
  checkoutDate: string;
  nights: number;
  guests: number;
  grossAmount: number;
  ownerNetAmount: number;
  // Assenti (Jackson NON_NULL) sulle prenotazioni senza tassa di soggiorno valorizzata.
  touristTaxIncludedInGross?: boolean;
  touristTaxAmount?: number;
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
  regimeFiscaleCodice?: string;
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
  // Righe di booking_split_economico ordinate; vuote per le prenotazioni pre-migrazione 018.
  righeSplit?: BookingSplitRiga[];
  /** Somma delle righe split in fattura PM (booking.total_costi_pm). */
  totalCostiPm?: number;
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
  /** true = il lordo comprende già la tassa di soggiorno, che il backend scorpora dallo split. */
  touristTaxIncludedInGross?: boolean;
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

export interface BookingUpdateSplitRequest {
  /** Omesso = flag invariato. */
  touristTaxIncludedInGross?: boolean;
  /** null = nessun override, torna alla commissione delle regole di contratto. */
  otaCommissionOverride?: number | null;
  /** Totale pulizie (pulizie + cambio biancheria) impostato a mano; null = dalle regole. */
  cleaningOverride?: number | null;
  /** Provvigione PM impostata a mano; null = dalle regole. */
  pmFeeOverride?: number | null;
}

/** Modifica gli input dello split e lo fa ricalcolare al backend. 400 se ci sono documenti emessi. */
export async function updateBookingSplit(
  id: number,
  data: BookingUpdateSplitRequest,
): Promise<BookingDetail> {
  return patch<BookingDetail>(`/bookings/${id}/split`, data);
}

/**
 * Ricalcola lo split dalle regole di contratto correnti: body vuoto = flag tassa invariato
 * e nessun override OTA, quindi anche una commissione forzata o da file torna alla regola.
 */
export async function ricalcolaSplit(id: number): Promise<BookingDetail> {
  return patch<BookingDetail>(`/bookings/${id}/split`, {});
}

/** Voce extra dello split (tipo_voce 'extra'), es. "Parcheggio". */
export interface VoceExtraRequest {
  descrizione: string;
  /** Importo lordo, > 0. */
  importo: number;
  /** Omesso = true in creazione, invariato in modifica. */
  includeInFatturaPm?: boolean;
}

/** Aggiunge una voce extra in fondo allo split. 400 se ci sono documenti emessi. */
export async function aggiungiVoceExtra(bookingId: number, data: VoceExtraRequest): Promise<BookingDetail> {
  return post<BookingDetail>(`/bookings/${bookingId}/split/extra`, data);
}

/** Modifica una voce extra. 400 se la riga non è di tipo 'extra', 404 se non esiste. */
export async function aggiornaVoceExtra(
  bookingId: number,
  rigaId: number,
  data: VoceExtraRequest,
): Promise<BookingDetail> {
  return patch<BookingDetail>(`/bookings/${bookingId}/split/${rigaId}`, data);
}

/** Elimina (soft delete) una voce extra. */
export async function eliminaVoceExtra(bookingId: number, rigaId: number): Promise<BookingDetail> {
  return del<BookingDetail>(`/bookings/${bookingId}/split/${rigaId}`);
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
