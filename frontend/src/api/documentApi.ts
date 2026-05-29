import { get } from '@/lib/apiClient';

export interface DocumentListItem {
  id: number;
  documentNumber: string;
  documentType: string;
  issueDate: string;
  recipientName: string;
  recipientTaxCode?: string;
  totalAmount: number;
  vatAmount: number;
  statoDocumento: string;
  sdiIdentifier?: string;
  sdiEsito?: string;
  propertyName?: string;
  channelName?: string;
  fkBookingId?: number;
  createdAt: string;
}

export interface DocumentRow {
  descrizione: string;
  importoNetto: number;
  aliquotaIva: number;
  importoIva: number;
  importoLordo: number;
}

export interface DocumentDetail extends DocumentListItem {
  fkTenantId: number;
  fkTipoDocumentoId: number;
  fkStatoDocumentoId: number;
  richiedeIva: boolean;
  updatedAt: string;
  righe: DocumentRow[];
}

export async function getDocuments(params?: {
  stato?: string;
  q?: string;
  page?: number;
  size?: number;
}): Promise<DocumentListItem[]> {
  if (!params || Object.keys(params).length === 0) {
    return get<DocumentListItem[]>('/documents');
  }
  const qs = new URLSearchParams();
  if (params.stato) qs.set('stato', params.stato);
  if (params.q) qs.set('q', params.q);
  if (params.page !== undefined) qs.set('page', String(params.page));
  if (params.size !== undefined) qs.set('size', String(params.size));
  return get<DocumentListItem[]>(`/documents?${qs.toString()}`);
}

export async function getDocumentById(id: number): Promise<DocumentDetail> {
  return get<DocumentDetail>(`/documents/${id}`);
}
