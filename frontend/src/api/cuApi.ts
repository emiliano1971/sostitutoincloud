import { get, post, patch, getToken } from '@/lib/apiClient';
import { getConfig } from '@/config/AppConfig';

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

/**
 * Scarica il PDF della CU (GET /api/cu/{id}/pdf) e avvia il download nel browser.
 * Non usa apiClient perché la risposta è un blob, non JSON.
 *
 * Back-office del tenant: /api/cu/** è precluso a owner_user. Dal portale
 * proprietario si usa downloadOwnerCuPdf().
 */
export async function downloadCuPdf(id: number, anno: number, ownerName: string): Promise<void> {
  return scaricaCuPdf(`/cu/${id}/pdf`, anno, ownerName);
}

/**
 * Scarica il PDF della propria CU dal portale proprietario
 * (GET /api/owner/cu/{id}/pdf). Il backend verifica che la CU appartenga
 * all'owner del token: 403 se è di un altro proprietario.
 */
export async function downloadOwnerCuPdf(id: number, anno: number, ownerName: string): Promise<void> {
  return scaricaCuPdf(`/owner/cu/${id}/pdf`, anno, ownerName);
}

async function scaricaCuPdf(path: string, anno: number, ownerName: string): Promise<void> {
  const base = getConfig().apiBaseUrl;
  const token = getToken();
  const res = await fetch(`${base}${path}`, {
    headers: {
      'Accept': 'application/pdf',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    },
  });

  if (!res.ok) {
    let message = `Errore ${res.status} durante la generazione del PDF`;
    try {
      const json = await res.json();
      if (json.message) message = json.message;
    } catch { /* body non JSON */ }
    throw new Error(message);
  }

  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  try {
    const link = document.createElement('a');
    link.href = url;
    // Nome leggibile lato utente: il backend usa il CF, qui il nome del proprietario.
    link.download = `CU_${anno}_${(ownerName ?? '').replace(/[^A-Za-z0-9]+/g, '_') || 'proprietario'}.pdf`;
    document.body.appendChild(link);
    link.click();
    link.remove();
  } finally {
    URL.revokeObjectURL(url);
  }
}
