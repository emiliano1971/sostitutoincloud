import { get } from '@/lib/apiClient';

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
