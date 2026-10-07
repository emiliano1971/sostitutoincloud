import { get, post, put, patch, del, getToken } from '@/lib/apiClient';
import { getConfig } from '@/config/AppConfig';
import type { MensileDTO } from './dashboardApi';
import type { BookingListItem } from './bookingApi';
import type { SettlementListItem } from './settlementApi';
import type { CuListItem } from './cuApi';
import type { DocumentListItem } from './documentApi';

export interface OwnerListItem {
  id: number;
  ownerType: string;
  firstName: string;
  lastName: string;
  legalName?: string;
  taxCode: string;
  vatNumber?: string;
  fiscalRegime: string;
  email: string;
  phone: string;
  iban: string;
  attivo: boolean;
  propertiesCount: number;
  createdAt: string;
}

export interface OwnerDetail extends OwnerListItem {
  fkTenantId: number;
  updatedAt: string;
  bookingsCount: number;
  totalGrossAmount: number;
  totalOwnerNet: number;
  settlementsCount: number;
}

export async function getOwners(attivo?: boolean): Promise<OwnerListItem[]> {
  const path = attivo !== undefined ? `/owners?attivo=${attivo}` : '/owners';
  return get<OwnerListItem[]>(path);
}

export async function getOwnerById(id: number): Promise<OwnerDetail> {
  return get<OwnerDetail>(`/owners/${id}`);
}

export interface OwnerDashboardDTO {
  ricaviTotali: number;
  prenotazioniCount: number;
  totalRitenute: number;
  totalLiquidato: number;
  ricaviMensili: MensileDTO[];
  // Portale owner
  totalNet: number;
  settlementsCount: number;
  netDaPagare: number;
}

export async function getOwnerDashboard(ownerId: number): Promise<OwnerDashboardDTO> {
  return get<OwnerDashboardDTO>(`/owners/${ownerId}/dashboard`);
}

// ── Portale proprietario (/api/owner/**) ────────────────────────────────────
// Riservati al ruolo owner_user. Il proprietario è ricavato dal token lato server:
// queste funzioni NON accettano un ownerId, ed è esattamente il punto — passarlo dal
// client permetteva di leggere i dati di un altro proprietario.

/** Prenotazioni del proprietario autenticato. */
export async function getOwnerBookings(): Promise<BookingListItem[]> {
  return get<BookingListItem[]>('/owner/bookings');
}

/** Liquidazioni del proprietario autenticato. */
export async function getOwnerSettlements(): Promise<SettlementListItem[]> {
  return get<SettlementListItem[]>('/owner/settlements');
}

/** Certificazioni Uniche del proprietario autenticato. */
export async function getOwnerCu(): Promise<CuListItem[]> {
  return get<CuListItem[]>('/owner/cu');
}

/** Ricevute owner del proprietario autenticato (le fatture PM non sono incluse). */
export async function getOwnerDocuments(): Promise<DocumentListItem[]> {
  return get<DocumentListItem[]>('/owner/documents');
}

/** KPI e ricavi mensili del proprietario autenticato. */
export async function getOwnerDashboardSelf(): Promise<OwnerDashboardDTO> {
  return get<OwnerDashboardDTO>('/owner/dashboard');
}

export interface OwnerCreateRequest {
  ownerType: string;
  firstName: string;
  lastName: string;
  taxCode: string;
  email: string;
  legalName?: string;
  vatNumber?: string;
  fkRegimeFiscaleId?: number;
  phone?: string;
  iban?: string;
}

export async function createOwner(data: OwnerCreateRequest): Promise<OwnerDetail> {
  return post<OwnerDetail>('/owners', data);
}

/**
 * Elimina proprietario, immobili e regole di contratto (DELETE /api/owners/{id}, 204).
 * 400 se ha prenotazioni, dati fiscali o un utente di accesso collegati.
 */
export async function eliminaProprietario(id: number): Promise<void> {
  await del<void>(`/owners/${id}`);
}

export async function updateOwnerStatus(id: number, attivo: boolean): Promise<OwnerDetail> {
  return patch<OwnerDetail>(`/owners/${id}/status`, { attivo });
}

export interface OwnerUpdateRequest {
  ownerType?: string;
  firstName?: string;
  lastName?: string;
  legalName?: string;
  taxCode?: string;
  vatNumber?: string;
  fkRegimeFiscaleId?: number;
  email?: string;
  phone?: string;
  iban?: string;
}

export async function updateOwner(id: number, data: OwnerUpdateRequest): Promise<OwnerDetail> {
  return put<OwnerDetail>(`/owners/${id}`, data);
}

// ── Import massivo proprietari/immobili da Excel ─────────────────────────────

export interface OwnerBulkImportErrore {
  numeroRiga: number;
  descrizioneRiga: string;
  messaggio: string;
}

export interface OwnerBulkImportResult {
  righeProcessate: number;
  proprietariCreati: number;
  proprietariEsistenti: number;
  immobiliCreati: number;
  immobiliSaltati: number;
  righeInErrore: number;
  errori: OwnerBulkImportErrore[];
  /** Righe importate con segnalazioni non bloccanti (es. CIN fuori formato). */
  avvisi: OwnerBulkImportErrore[];
}

export interface OwnerImportRigaPreview {
  numeroRiga: number;
  cognome: string;
  nome: string;
  codFisc: string;
  nomeImmobile: string;
  citta: string;
  stato: 'ok' | 'duplicato_proprietario' | 'duplicato_immobile' | 'errore';
  messaggioStato?: string;
  selezionabile: boolean;
  selezionato: boolean;
}

export interface OwnerImportPreviewResult {
  righe: OwnerImportRigaPreview[];
  righeOk: number;
  righeDuplicato: number;
  righeErrore: number;
}

/** POST multipart verso /owners/import-bulk*: fetch diretta, apiClient invia solo JSON. */
async function postMultipart<T>(path: string, formData: FormData): Promise<T> {
  const token = getToken();
  const res = await fetch(`${getConfig().apiBaseUrl}${path}`, {
    method: 'POST',
    headers: {
      'Accept': 'application/json',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    },
    body: formData,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    let message = text;
    try { const j = JSON.parse(text); message = j.message ?? j.error ?? text; } catch { /* */ }
    throw new Error(message);
  }
  return res.json();
}

/** Analizza il file senza scrivere nulla: stato di ogni riga (POST /api/owners/import-bulk/preview). */
export async function previewImportProprietari(file: File): Promise<OwnerImportPreviewResult> {
  const formData = new FormData();
  formData.append('file', file);
  return postMultipart<OwnerImportPreviewResult>('/owners/import-bulk/preview', formData);
}

/**
 * Importa le righe scelte nella preview (POST /api/owners/import-bulk): il file va
 * ricaricato insieme ai numeri di riga. Lista vuota = tutte le righe valide.
 */
export async function importaProprietari(file: File, righeSelezionate: number[]): Promise<OwnerBulkImportResult> {
  const formData = new FormData();
  formData.append('file', file);
  righeSelezionate.forEach(r => formData.append('righe', String(r)));
  return postMultipart<OwnerBulkImportResult>('/owners/import-bulk', formData);
}
