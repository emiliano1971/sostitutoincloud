import { get, post, put, patch } from '@/lib/apiClient';
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
