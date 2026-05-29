import { get } from '@/lib/apiClient';

export interface CuListItem {
  id: number;
  ownerName: string;
  taxYear: number;
  totalCompensi: number;
  totalRitenute: number;
  stato: string;
  generatedAt?: string;
  createdAt: string;
}

export interface CuDetail extends CuListItem {
  fkTenantId: number;
  fkOwnerId: number;
  ownerTaxCode: string;
  ownerIban: string;
  updatedAt: string;
}

export async function getCuList(params?: {
  ownerId?: number;
  taxYear?: number;
}): Promise<CuListItem[]> {
  if (!params || Object.keys(params).length === 0) {
    return get<CuListItem[]>('/cu');
  }
  const qs = new URLSearchParams();
  if (params.ownerId !== undefined) qs.set('ownerId', String(params.ownerId));
  if (params.taxYear !== undefined) qs.set('taxYear', String(params.taxYear));
  return get<CuListItem[]>(`/cu?${qs.toString()}`);
}

export async function getCuById(id: number): Promise<CuDetail> {
  return get<CuDetail>(`/cu/${id}`);
}
