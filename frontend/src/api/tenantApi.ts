import { get, post, put, patch } from '@/lib/apiClient';
import type { UtenteListItem } from '@/api/userApi';

export interface TenantListItem {
  id: number;
  legalName: string;
  displayName: string;
  taxCode: string;
  vatNumber?: string;
  stato: string;
  administrativeEmail: string;
  phone?: string;
  legalAddress: string;
  cap?: string;
  comune?: string;
  provincia?: string;
  activatedAt?: string;
  createdAt: string;
  propertiesCount: number;
  ownersCount: number;
  bookingsCount: number;
}

export interface TenantDetail extends TenantListItem {
  pec?: string;
  updatedAt: string;
  usersCount: number;
}

export interface TenantCreateRequest {
  legalName: string;
  displayName: string;
  taxCode: string;
  vatNumber?: string;
  administrativeEmail: string;
  pec?: string;
  phone?: string;
  legalAddress: string;
  cap?: string;
  comune?: string;
  provincia?: string;
}

export async function getTenants(): Promise<TenantListItem[]> {
  return get<TenantListItem[]>('/admin/tenants');
}

export async function getTenantById(id: number): Promise<TenantDetail> {
  return get<TenantDetail>(`/admin/tenants/${id}`);
}

export async function createTenant(data: TenantCreateRequest): Promise<TenantDetail> {
  return post<TenantDetail>('/admin/tenants', data);
}

export async function updateTenantStatus(id: number, stato: string): Promise<TenantDetail> {
  return patch<TenantDetail>(`/admin/tenants/${id}/status`, { stato });
}

export interface TenantUpdateRequest {
  legalName?: string;
  displayName?: string;
  taxCode?: string;
  vatNumber?: string;
  administrativeEmail?: string;
  pec?: string;
  phone?: string;
  legalAddress?: string;
  cap?: string;
  comune?: string;
  provincia?: string;
}

export async function updateTenant(id: number, data: TenantUpdateRequest): Promise<TenantDetail> {
  return put<TenantDetail>(`/admin/tenants/${id}`, data);
}

export interface TenantAdminCreateRequest {
  email: string;
  firstName: string;
  lastName: string;
  password: string;
}

/** Crea il primo utente amministratore del tenant — il ruolo tenant_admin è forzato dal backend. */
export async function createTenantAdmin(
  tenantId: number,
  data: TenantAdminCreateRequest,
): Promise<UtenteListItem> {
  return post<UtenteListItem>(`/admin/tenants/${tenantId}/users`, data);
}

export async function getTenantUsers(tenantId: number): Promise<UtenteListItem[]> {
  return get<UtenteListItem[]>(`/admin/tenants/${tenantId}/users`);
}

export interface TenantSummary {
  id: number;
  displayName: string;
  legalName: string;
  stato: string;
  propertiesCount: number;
  ownersCount: number;
  bookingsCount: number;
  createdAt: string;
}

export interface SuperAdminDashboard {
  totalTenant: number;
  tenantAttivi: number;
  tenantSospesi: number;
  tenantDraft: number;
  totalProprietari: number;
  totalImmobili: number;
  totalPrenotazioni: number;
  ultimiTenant: TenantSummary[];
}

export async function getSuperAdminDashboard(): Promise<SuperAdminDashboard> {
  return get<SuperAdminDashboard>('/admin/dashboard');
}
