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
  // Classificazione ai fini della ritenuta: true = aliquota primaria, false = secondaria.
  primoImmobile: boolean;
  fkOwnerId?: number;
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

// ownerId: filtro lato backend sugli immobili di un proprietario.
export async function getProperties(attivo?: boolean, ownerId?: number): Promise<PropertyListItem[]> {
  const params = new URLSearchParams();
  if (attivo !== undefined) params.set('attivo', String(attivo));
  if (ownerId !== undefined) params.set('ownerId', String(ownerId));
  const qs = params.toString();
  return get<PropertyListItem[]>(qs ? `/properties?${qs}` : '/properties');
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
  primoImmobile?: boolean;
  otaCodes?: { canaleCodiceName: string; externalId: string }[];
}

export async function createProperty(data: PropertyCreateRequest): Promise<PropertyDetail> {
  return post<PropertyDetail>('/properties', data);
}

export async function updateProperty(id: number, data: PropertyCreateRequest): Promise<PropertyDetail> {
  return put<PropertyDetail>(`/properties/${id}`, data);
}

export async function updatePropertyStatus(id: number, attivo: boolean): Promise<PropertyDetail> {
  return patch<PropertyDetail>(`/properties/${id}/status`, { attivo });
}

export async function updatePropertyOwner(id: number, fkOwnerId: number): Promise<PropertyDetail> {
  return put<PropertyDetail>(`/properties/${id}/owner`, { fkOwnerId });
}

export async function updatePropertyPrimoImmobile(id: number, primoImmobile: boolean): Promise<PropertyDetail> {
  return patch<PropertyDetail>(`/properties/${id}`, { primoImmobile });
}

export interface PrimoImmobileCheck {
  exists: boolean;
  propertyName?: string;
  propertyId?: number;
}

// Verifica se il proprietario ha già un altro immobile attivo come primo immobile.
// excludePropertyId esclude l'immobile in corso di modifica.
export async function checkPrimoImmobile(
  ownerId: number,
  excludePropertyId?: number
): Promise<PrimoImmobileCheck> {
  const params = new URLSearchParams({ ownerId: String(ownerId) });
  if (excludePropertyId !== undefined) params.set('excludePropertyId', String(excludePropertyId));
  return get<PrimoImmobileCheck>(`/properties/check-primo-immobile?${params.toString()}`);
}

// ── Regole contratto immobile ────────────────────────────────────────────────

export interface PropertyContractRule {
  id?: number;
  fkPropertyId?: number;
  tipo: string;
  tipoLabel?: string;
  calcMode: string;
  calcModeLabel?: string;
  valore: number;
  isRemainder: boolean;
  ordine: number;
  attivo?: boolean;
  fkCanaleOtaId?: number;
  canaleName?: string;
}

export async function getPropertyContracts(propertyId: number): Promise<PropertyContractRule[]> {
  return get<PropertyContractRule[]>(`/properties/${propertyId}/contracts`);
}

export async function addPropertyContract(
  propertyId: number,
  rule: PropertyContractRule
): Promise<PropertyContractRule> {
  return post<PropertyContractRule>(`/properties/${propertyId}/contracts`, rule);
}
