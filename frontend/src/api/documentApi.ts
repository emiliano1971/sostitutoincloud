import { get, post, patch, getToken } from '@/lib/apiClient';
import { getConfig } from '@/config/AppConfig';
import type { BatchRisultato } from '@/api/bookingApi';

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
  /** Ultimo invio email accettato dal server SMTP (non la consegna); assente se mai inviato. */
  emailInviataAt?: string;
  emailInviataA?: string;
  /** Ultimo annullo di un invio manuale allo SDI (resta anche dopo un nuovo invio). */
  sdiManualeAnnullatoAt?: string;
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
  /** Fattura con una nota di credito attiva collegata. */
  stornata?: boolean;
  /** Fattura stornata: NDC attiva collegata. */
  ndcId?: number;
  ndcNumber?: string;
  /** NDC: fattura originale stornata. */
  fatturaCollegataId?: number;
  fatturaCollegataNumber?: string;
  /** Fattura PM attiva: ricevuta attiva della stessa prenotazione. */
  ricevutaCollegataId?: number;
  ricevutaCollegataNumber?: string;
  /** Ricevuta attiva: fattura PM attiva della stessa prenotazione (assente = ricevuta senza fattura). */
  fatturaDellaPrenotazioneId?: number;
  fatturaDellaPrenotazioneNumber?: string;
}

export interface DocumentRow {
  descrizione: string;
  importoNetto: number;
  aliquotaIva: number;
  importoIva: number;
  importoLordo: number;
  /** Voce fuori campo IVA art. 15 (rimborso bollo): niente IVA, fuori imponibile. */
  fuoriCampo?: boolean;
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
  // Note di credito (migration 025)
  /** NDC: numero della fattura stornata. */
  documentoCollegatoNumber?: string;
  /** Fattura: NDC collegate (annullate comprese). */
  noteCredito?: {
    id: number;
    documentNumber: string;
    statoDocumento: string;
    dataEmissione: string;
    importoTotale: number;
  }[];
  /** Fattura: 'parziale' | 'totale' se esiste una NDC attiva. */
  statoStorno?: 'parziale' | 'totale';
}

/** Pagina della lista documenti (GET /api/documents). */
export interface DocumentPage {
  content: DocumentListItem[];
  /** Pagina corrente, 0-based. */
  page: number;
  /** Dimensione pagina effettiva (parametro size o impostazione del tenant). */
  size: number;
  /** Documenti che soddisfano i filtri, su tutte le pagine. */
  totalElements: number;
  totalPages: number;
}

/**
 * Lista documenti paginata: filtri, ordinamento e paginazione sono applicati dal backend
 * su tutti i documenti del tenant. size 0/assente = dimensione pagina del tenant.
 */
export async function getDocuments(params: {
  stato?: string;
  /** Codice tipo_documento: fattura | ricevuta | nota_credito */
  tipo?: string;
  /** Numero, destinatario, proprietario, prenotazione, NDC / fattura collegata */
  search?: string;
  /** Filtro rapido sulle ricevute owner: da_liquidare | f24_non_pagato | senza_cu */
  filtroFiscale?: string;
  /** Data emissione (yyyy-MM-dd), estremi inclusi */
  dataFrom?: string;
  dataTo?: string;
  /** Mese di emissione 1..12 */
  mese?: number;
  /** Anno di emissione */
  anno?: number;
  ownerId?: number;
  /** Stato liquidazione (pending | calculated | approved | paid) o 'none' = non liquidato */
  liquidazione?: string;
  /** Campo di ordinamento (colonne della lista); default data emissione */
  sort?: string;
  dir?: 'asc' | 'desc';
  page?: number;
  size?: number;
} = {}): Promise<DocumentPage> {
  const qs = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') qs.set(k, String(v));
  }
  const query = qs.toString();
  return get<DocumentPage>(query ? `/documents?${query}` : '/documents');
}

/**
 * Stato fiscale di una ricevuta owner (GET /api/documents/{id}/stato-fiscale).
 * Gli stati sono codici del DB: assente = ritenuta non in F24 / da liquidare / CU non generata.
 */
export interface StatoFiscaleRicevuta {
  documentId: number;
  documentNumber: string;
  /** 'annullata' se la ricevuta è stata stornata con nota di credito. */
  statoDocumento: string;
  proprietarioNome: string;
  canoneLocazione: number;
  ritenutaAmount: number;
  aliquotaRitenuta?: number;
  /** withholding_ledger.stato: da_versare / versata / stornata / credito_imposta */
  ritenutaStato?: string;
  /** f24_record.stato: draft / ready / sent / paid / error */
  f24Stato?: string;
  f24Periodo?: string;
  f24Id?: number;
  f24Mese?: number;
  f24Anno?: number;
  /** settlement.stato: pending / calculated / approved / paid */
  liquidazioneStato?: string;
  liquidazionePeriodo?: string;
  liquidazioneId?: number;
  /** cu_record.stato: draft / generated / delivered / sent */
  cuStato?: string;
  cuAnno?: number;
  cuId?: number;
}

export async function getStatoFiscaleRicevuta(id: number): Promise<StatoFiscaleRicevuta> {
  return get<StatoFiscaleRicevuta>(`/documents/${id}/stato-fiscale`);
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
  /** Avviso non bloccante (es. F24 del periodo già pagato → ritenuta in un F24 integrativo). */
  avviso?: string;
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

/** Avviso non bloccante sulla data di emissione scelta. */
export interface AvvisoEmissione {
  codice: 'F24_PAGATO' | 'F24_NON_PAGATO' | 'CU_EMESSA' | 'ANNO_PRECEDENTE' | 'INVIO_SDI_TARDIVO'
    | 'DATA_PRECEDE_CHECKOUT';
  messaggio: string;
  /** Solo F24_NON_PAGATO: F24 da ricalcolare dopo l'emissione. */
  f24Id?: number;
}

/** Esito di GET /documents/emissione-controllo (i campi null sono omessi dal backend). */
export interface EmissioneControllo {
  dataMinima: string;
  dataMassima: string;
  ultimoDocumentoNumero?: string;
  ultimoDocumentoData?: string;
  data: string;
  /** Assente se la data è ammessa; altrimenti lo stesso messaggio del 422 di emissione. */
  erroreData?: string;
  avvisi?: AvvisoEmissione[];
}

/** Intervallo ammesso e avvisi per la data di emissione: non scrive nulla. data assente = oggi. */
export async function getEmissioneControllo(
  bookingId: number,
  tipoDocumento: DocumentGenerateRequest['tipoDocumento'],
  data?: string,
): Promise<EmissioneControllo> {
  const q = new URLSearchParams({ bookingId: String(bookingId), tipoDocumento });
  if (data) q.set('data', data);
  return get<EmissioneControllo>(`/documents/emissione-controllo?${q}`);
}

/** Avviso non bloccante dell'eliminazione documento (richiede conferma). */
export interface AvvisoEliminazione {
  codice: 'F24_AGGIORNATO' | 'LIQUIDAZIONE_DA_RICALCOLARE' | 'CU_DA_RIGENERARE' | 'CU_VERRA_ELIMINATA' | 'EMAIL_GIA_INVIATA'
    | 'BOLLO_ADDEBITATO' | 'SDI_MANUALE_ANNULLATO' | 'RICEVUTA_RESTA' | 'RICEVUTE_MULTIPLE';
  messaggio: string;
  /** Solo F24_AGGIORNATO: F24 e suo periodo, per il link alla lista F24. */
  f24Id?: number;
  f24Anno?: number;
  f24Mese?: number;
}

export interface DocumentoEliminabile {
  id: number;
  numero: string;
  tipo: 'fattura' | 'ricevuta';
}

/** Uno scenario di eliminazione della fattura: solo fattura, oppure fattura + ricevuta. */
export interface ScenarioEliminazione {
  consentito: boolean;
  blocchi: string[];
  avvisi: AvvisoEliminazione[];
  documenti: DocumentoEliminabile[];
}

/** Esito di GET /documents/{id}/eliminazione-controllo: non scrive nulla. */
export interface EliminazioneControllo {
  consentito: boolean;
  blocchi: string[];
  avvisi: AvvisoEliminazione[];
  /** Documenti che verrebbero eliminati: la fattura con la sua ricevuta, oppure la sola ricevuta. */
  documenti: DocumentoEliminabile[];
  /** Solo per la fattura: eliminazione della sola fattura (blocchi della sola fattura). */
  scenarioSoloFattura?: ScenarioEliminazione;
  /** Solo per la fattura con una ricevuta sulla prenotazione: fattura + ricevuta, tutto o niente. */
  scenarioConRicevuta?: ScenarioEliminazione;
}

export interface EliminazioneEsito {
  bookingId?: number;
  eliminati: DocumentoEliminabile[];
  avvisi: AvvisoEliminazione[];
  /** Solo fattura eliminata: numero della ricevuta rimasta sulla prenotazione. */
  ricevutaLasciata?: string;
}

export async function getEliminazioneControllo(id: number): Promise<EliminazioneControllo> {
  return get<EliminazioneControllo>(`/documents/${id}/eliminazione-controllo`);
}

/**
 * Eliminazione fisica: 400 motivo non valido, 422 non consentita (blocchi nel messaggio).
 * eliminaRicevuta sempre esplicito: dalla fattura true = anche la ricevuta, false = solo la
 * fattura; per la ricevuta il backend lo ignora.
 */
export async function eliminaDocumento(id: number, motivo: string, eliminaRicevuta: boolean): Promise<EliminazioneEsito> {
  return post<EliminazioneEsito>(`/documents/${id}/elimina`, { motivo, eliminaRicevuta });
}

/** Avviso dell'annullo invio manuale: RIAPERTURA sempre, NDC_RIAPERTA per la nota di credito. */
export interface AvvisoAnnulloInvioManuale {
  codice: 'RIAPERTURA' | 'NDC_RIAPERTA';
  messaggio: string;
}

/** Esito di GET /documents/{id}/annulla-invio-manuale-controllo: non scrive nulla. */
export interface AnnulloInvioManualeControllo {
  consentito: boolean;
  blocchi: string[];
  avvisi: AvvisoAnnulloInvioManuale[];
  /** Data dell'invio manuale che verrebbe annullato. */
  inviatoManualmenteAt?: string;
}

export async function getAnnulloInvioManualeControllo(id: number): Promise<AnnulloInvioManualeControllo> {
  return get<AnnulloInvioManualeControllo>(`/documents/${id}/annulla-invio-manuale-controllo`);
}

/** Riporta a "pronto" un documento marcato come inviato manualmente: 400 motivo, 422 non consentito. */
export async function annullaInvioManuale(id: number, motivo: string): Promise<DocumentDetail> {
  return post<DocumentDetail>(`/documents/${id}/annulla-invio-manuale`, { motivo });
}

/** Avviso del cambio data: richiede conferma, non blocca. */
export interface AvvisoCambioData {
  codice: 'DATA_PRECEDE_CHECKOUT' | 'INVIO_SDI_TARDIVO' | 'EMAIL_GIA_INVIATA' | 'NDC_CREDITO_PERIODO'
    | 'RICEVUTA_PERIODO_SPOSTATO';
  messaggio: string;
}

/** Esito di GET /documents/{id}/cambio-data-controllo: non scrive nulla. */
export interface CambioDataControllo {
  consentito: boolean;
  blocchi: string[];
  avvisi: AvvisoCambioData[];
  dataMinima?: string;
  dataMassima?: string;
  dataAttuale: string;
  /** Solo ricevuta: cambio di mese non consentito, dataMinima/dataMassima già ristrette al mese. */
  limitatoAlMese?: boolean;
  /** Motivo concreto del limite al mese (F24, rendiconto, stato della ritenuta). */
  motivoLimiteMese?: string;
}

/** Controllo del cambio data; senza data valuta solo il documento e l'intervallo. */
export async function getCambioDataControllo(id: number, data?: string): Promise<CambioDataControllo> {
  const q = data ? `?data=${encodeURIComponent(data)}` : '';
  return get<CambioDataControllo>(`/documents/${id}/cambio-data-controllo${q}`);
}

/** Cambio data di fattura PM / NDC mai inviata o di ricevuta: 400 data o motivo, 422 non consentito. */
export async function cambiaDataDocumento(id: number, data: string, motivo: string): Promise<DocumentDetail> {
  return post<DocumentDetail>(`/documents/${id}/cambio-data`, { data, motivo });
}

export async function aggiornaStatoDocumento(id: number, stato: string): Promise<void> {
  await patch<void>(`/documents/${id}/stato`, { stato });
}

/**
 * Download di un XML dal backend: nome file dal Content-Disposition, fallback indicato.
 * Come downloadDocumentPdf non usa apiClient: la risposta è un blob, non JSON.
 */
async function scaricaXml(path: string, fallbackName: string): Promise<void> {
  const base = getConfig().apiBaseUrl;
  const token = getToken();
  const res = await fetch(`${base}${path}`, {
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

  const disposition = res.headers.get('Content-Disposition') ?? '';
  const match = disposition.match(/filename="?([^";]+)"?/);
  const fileName = match ? match[1] : fallbackName;

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

/** Scarica il file XML archiviato di un invio SDI (GET /api/sdi/download/{progressivo}). */
export async function downloadSdiXml(progressivo: string): Promise<void> {
  return scaricaXml(`/sdi/download/${encodeURIComponent(progressivo)}`, `${progressivo}.xml`);
}

/**
 * XML SDI di fattura / NDC generato al momento (GET /api/documents/{id}/xml):
 * nessun file scritto, nessun progressivo consumato, qualsiasi stato.
 */
export async function downloadXmlRuntime(id: number): Promise<void> {
  return scaricaXml(`/documents/${id}/xml`, `documento-${id}.xml`);
}

/** ZIP degli XML SDI scaricato da un'azione massiva, con il riepilogo dal backend. */
export interface ZipXmlBatch {
  blob: Blob;
  fileName: string;
  inclusi: number;
  marcati: number;
  scartati: number;
}

/**
 * ZIP degli XML SDI dei documenti indicati + marcatura "inviata manualmente" degli inclusi
 * (POST /api/documents/scarica-e-marca-batch). 422 se nessun documento è idoneo.
 */
export async function scaricaEMarcaBatch(documentIds: number[]): Promise<ZipXmlBatch> {
  return scaricaZipXml('/documents/scarica-e-marca-batch', documentIds);
}

/** Solo ZIP degli XML SDI, nessun cambio di stato (POST /api/documents/scarica-xml-batch). */
export async function scaricaXmlBatch(documentIds: number[]): Promise<ZipXmlBatch> {
  return scaricaZipXml('/documents/scarica-xml-batch', documentIds);
}

async function scaricaZipXml(path: string, documentIds: number[]): Promise<ZipXmlBatch> {
  const base = getConfig().apiBaseUrl;
  const token = getToken();
  const res = await fetch(`${base}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/zip',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ documentIds }),
  });
  if (!res.ok) {
    let message = `Errore ${res.status} durante la creazione dello ZIP`;
    try {
      const json = await res.json();
      if (json.message) message = json.message;
    } catch { /* body non JSON */ }
    throw new Error(message);
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  const now = new Date();
  const ts = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  const match = (res.headers.get('Content-Disposition') ?? '').match(/filename="?([^";]+)"?/);
  return {
    blob: await res.blob(),
    fileName: match ? match[1] : `xml-sdi-${ts}.zip`,
    inclusi: Number(res.headers.get('X-Batch-Inclusi') ?? 0),
    marcati: Number(res.headers.get('X-Batch-Marcati') ?? 0),
    scartati: Number(res.headers.get('X-Batch-Scartati') ?? 0),
  };
}

/** Invio massivo allo SDI tramite tunnel (POST /api/documents/invia-sdi-batch). */
export async function inviaSdiBatch(documentIds: number[]): Promise<BatchRisultato> {
  return post<BatchRisultato>('/documents/invia-sdi-batch', { documentIds });
}

/** Marca fattura / NDC come inviata allo SDI fuori dall'applicazione (POST /api/documents/{id}/sdi-manuale). */
export async function segnaInviataManualmente(id: number): Promise<{ message: string }> {
  return post<{ message: string }>(`/documents/${id}/sdi-manuale`, {});
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

/** PDF della nota di credito (GET /api/ndc/{id}/pdf). */
export async function downloadNdcPdf(id: number, documentNumber: string): Promise<void> {
  return scaricaDocumentoPdf(`/ndc/${id}/pdf`, documentNumber);
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
