import { get, post, put, patch } from '@/lib/apiClient';

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
  activatedAt?: string;
  createdAt: string;
  propertiesCount: number;
  ownersCount: number;
  bookingsCount: number;
}

export interface TenantDetail extends TenantListItem {
  pec?: string;
  updatedAt: string;
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
}

export async function updateTenant(id: number, data: TenantUpdateRequest): Promise<TenantDetail> {
  return put<TenantDetail>(`/admin/tenants/${id}`, data);
}
