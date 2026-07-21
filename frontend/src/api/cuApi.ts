import { get, post, patch } from '@/lib/apiClient';

export interface CuListItem {
  id: number;
  ownerName: string;
  taxYear: number;
  totalCompensi: number;
  totalImponibile: number;
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

export interface CuGeneraBatchResponse {
  generated: number;
  skipped: number;
  records: CuListItem[];
}

/** Genera tutte le CU dei proprietari con ritenute nell'anno (ownerId omesso → batch). */
export async function generaCuBatch(taxYear: number): Promise<CuGeneraBatchResponse> {
  return post<CuGeneraBatchResponse>('/cu/genera', { taxYear });
}

export async function updateCuStatus(id: number, stato: string): Promise<CuListItem> {
  return patch<CuListItem>(`/cu/${id}/status`, { stato });
}
