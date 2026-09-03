import { get, post, patch, getToken } from '@/lib/apiClient';
import { getConfig } from '@/config/AppConfig';

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
  sdiProgressivo?: string;
  sdiFilePath?: string;
  sdiSentAt?: string;
  sdiErrorMsg?: string;
  // Versamento F24 della ritenuta e CU dell'anno (solo ricevuta owner)
  f24RecordId?: number;
  f24Periodo?: string;
  f24Stato?: string;
  f24Pagato?: boolean;
  cuRecordId?: number;
  cuTaxYear?: number;
  cuStato?: string;
  cuConsegnata?: boolean;
  // Liquidazione che include la prenotazione del documento (assente se non liquidata).
  // A differenza di F24 e CU è valorizzata anche in lista e per qualsiasi tipo documento.
  settlementId?: number;
  settlementStato?: string;
  propertyName?: string;
  channelName?: string;
  fkBookingId?: number;
  externalBookingId?: string;
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
  // Auto-invio SDI (tenant_settings.sdi_auto_send): presenti solo per la fattura PM
  // e solo se l'auto-invio è attivo, assenti in ogni altro caso.
  sdiAutoGenerato?: boolean;
  sdiFilePath?: string;
  sdiProgressivo?: string;
  sdiDatiIncompleti?: boolean;
  sdiAutoSendError?: string;
}

export async function generateDocument(
  data: DocumentGenerateRequest
): Promise<DocumentGenerateResponse> {
  return post<DocumentGenerateResponse>('/documents/generate', data);
}

export async function aggiornaStatoDocumento(id: number, stato: string): Promise<void> {
  await patch<void>(`/documents/${id}/stato`, { stato });
}

/**
 * Scarica il file XML archiviato di un invio SDI (GET /api/sdi/download/{progressivo}).
 * Come downloadDocumentPdf non usa apiClient: la risposta è un blob, non JSON.
 */
export async function downloadSdiXml(progressivo: string): Promise<void> {
  const base = getConfig().apiBaseUrl;
  const token = getToken();
  const res = await fetch(`${base}/sdi/download/${encodeURIComponent(progressivo)}`, {
    headers: {
      'Accept': 'application/xml',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    },
  });

  if (!res.ok) {
    let message = `Errore ${res.status} durante il download del file XML`;
    try {
      const json = await res.json();
      if (json.message) message = json.message;
    } catch { /* body non JSON */ }
    throw new Error(message);
  }

  // Il nome file arriva dal Content-Disposition; fallback sul progressivo.
  const disposition = res.headers.get('Content-Disposition') ?? '';
  const match = disposition.match(/filename="?([^";]+)"?/);
  const fileName = match ? match[1] : `${progressivo}.xml`;

  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  try {
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
  } finally {
    URL.revokeObjectURL(url);
  }
}

export interface SdiElaborazioneResult {
  elaborati: number;
  accettati: number;
  scartati: number;
  metadati: number;
  errori: number;
  dettagli: string[];
}

/** Elabora le risposte SDI presenti in incoming/ (POST /api/sdi/elabora-risposte). */
export async function elaboraRisposteSdi(): Promise<SdiElaborazioneResult> {
  return post<SdiElaborazioneResult>('/sdi/elabora-risposte', {});
}

export interface SdiInvioResponse {
  message: string;
  filePath: string;
  progressivo: string;
}

/** Genera il file XML SDI della fattura PM (POST /api/documents/{id}/sdi). */
export async function inviaSdi(id: number): Promise<SdiInvioResponse> {
  return post<SdiInvioResponse>(`/documents/${id}/sdi`, {});
}

/**
 * Scarica il PDF del documento generato server-side e avvia il download nel browser.
 * Non usa apiClient perché la risposta è un blob, non JSON.
 *
 * Back-office del tenant: /api/documents/** è precluso a owner_user. Dal portale
 * proprietario si usa downloadOwnerDocumentPdf().
 */
export async function downloadDocumentPdf(id: number, documentNumber: string): Promise<void> {
  return scaricaDocumentoPdf(`/documents/${id}/pdf`, documentNumber);
}

/**
 * Scarica il PDF di una propria ricevuta dal portale proprietario
 * (GET /api/owner/documents/{id}/pdf). Il backend verifica che il documento sia una
 * ricevuta dell'owner del token.
 */
export async function downloadOwnerDocumentPdf(id: number, documentNumber: string): Promise<void> {
  return scaricaDocumentoPdf(`/owner/documents/${id}/pdf`, documentNumber);
}

async function scaricaDocumentoPdf(path: string, documentNumber: string): Promise<void> {
  const base = getConfig().apiBaseUrl;
  const token = getToken();
  const res = await fetch(`${base}${path}`, {
    headers: {
      'Accept': 'application/pdf',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    },
  });

  if (!res.ok) {
    // In caso di errore il backend risponde JSON con il campo message
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
    link.download = `${documentNumber}.pdf`;
    document.body.appendChild(link);
    link.click();
    link.remove();
  } finally {
    URL.revokeObjectURL(url);
  }
}
