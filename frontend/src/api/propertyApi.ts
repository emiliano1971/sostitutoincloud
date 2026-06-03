import { get, post, put, patch } from '@/lib/apiClient';

export interface OtaCode {
  canaleCodiceName: string;
  externalId: string;
}

export interface PropertyListItem {
  id: number;
  internalCode: string;
  displayName: string;
  address?: string;
  city: string;
  region?: string;
  propertyType: string;
  cinCode?: string;
  attivo: boolean;
  ownerName: string;
  listingsCount: number;
  bookingsCount: number;
  otaCodes: OtaCode[];
  createdAt: string;
}

export interface PropertyDetail extends PropertyListItem {
  fkTenantId: number;
  fkOwnerId: number;
  fkPmUserId?: number;
  updatedAt: string;
}

export async function getProperties(attivo?: boolean): Promise<PropertyListItem[]> {
  const path = attivo !== undefined ? `/properties?attivo=${attivo}` : '/properties';
  return get<PropertyListItem[]>(path);
}

export async function getPropertyById(id: number): Promise<PropertyDetail> {
  return get<PropertyDetail>(`/properties/${id}`);
}

export interface PropertyCreateRequest {
  displayName: string;
  internalCode: string;
  propertyType?: string;
  address?: string;
  city: string;
  region?: string;
  cinCode?: string;
  fkOwnerId?: number;
  otaCodes?: { canaleCodiceName: string; externalId: string }[];
}

export async function createProperty(data: PropertyCreateRequest): Promise<PropertyDetail> {
  return post<PropertyDetail>('/properties', data);
}

export async function updatePropertyStatus(id: number, attivo: boolean): Promise<PropertyDetail> {
  return patch<PropertyDetail>(`/properties/${id}/status`, { attivo });
}

export async function updatePropertyOwner(id: number, fkOwnerId: number): Promise<PropertyDetail> {
  return put<PropertyDetail>(`/properties/${id}/owner`, { fkOwnerId });
}
