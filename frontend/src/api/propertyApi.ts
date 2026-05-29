import { get } from '@/lib/apiClient';

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
