import { get, post, patch } from '@/lib/apiClient';

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
  fkOwnerId?: number;
  ownerName?: string;
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
  aliquotaIva?: number;
  imponibile?: number;
  ritenutaAmount?: number;
  bolloAmount?: number;
  canoneLocazione?: number;
  fkDocumentoCollegatoId?: number;
  externalBookingId?: string;
  checkinDate?: string;
  checkoutDate?: string;
  updatedAt: string;
  righe: DocumentRow[];
  // Emittente (tenant)
  tenantLegalName?: string;
  tenantVatNumber?: string;
  tenantTaxCode?: string;
  tenantLegalAddress?: string;
  tenantPec?: string;
}

export async function getDocuments(params?: {
  stato?: string;
  q?: string;
  ownerId?: number;
  page?: number;
  size?: number;
}): Promise<DocumentListItem[]> {
  if (!params || Object.keys(params).length === 0) {
    return get<DocumentListItem[]>('/documents');
  }
  const qs = new URLSearchParams();
  if (params.stato) qs.set('stato', params.stato);
  if (params.q) qs.set('q', params.q);
  if (params.ownerId !== undefined) qs.set('ownerId', String(params.ownerId));
  if (params.page !== undefined) qs.set('page', String(params.page));
  if (params.size !== undefined) qs.set('size', String(params.size));
  return get<DocumentListItem[]>(`/documents?${qs.toString()}`);
}

export async function getDocumentById(id: number): Promise<DocumentDetail> {
  return get<DocumentDetail>(`/documents/${id}`);
}

export interface DocumentGenerateRequest {
  bookingId: number;
  tipoDocumento: 'ricevuta_owner' | 'fattura_pm';
  dataEmissione?: string;
}

export interface DocumentGenerateResponse {
  documentId: number;
  documentNumber: string;
  tipoDocumento: string;
  dataEmissione: string;
  importoTotale: number;
  importoBollo: number;
  imponibile: number;
  iva: number;
  ritenuta: number;
  statoDocumento: string;
  bookingExternalId: string;
  guestName: string;
  ownerName: string;
  propertyName: string;
}

export async function generateDocument(
  data: DocumentGenerateRequest
): Promise<DocumentGenerateResponse> {
  return post<DocumentGenerateResponse>('/documents/generate', data);
}

export async function aggiornaStatoDocumento(id: number, stato: string): Promise<void> {
  await patch<void>(`/documents/${id}/stato`, { stato });
}
