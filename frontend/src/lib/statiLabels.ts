// Etichette italiane degli stati che il backend restituisce come codici (enum PostgreSQL o
// codici delle tabelle lookup). Un codice non previsto viene mostrato così com'è, così un
// nuovo stato aggiunto a DB resta comunque visibile invece di sparire.

/** stato_prenotazione.codice */
export const STATI_PRENOTAZIONE: Record<string, string> = {
  imported: 'Importata',
  enriched: 'Arricchita',
  ready: 'Pronta',
  doc_issued: 'Doc. emesso',
  settled: 'Liquidata',
  cancelled: 'Annullata',
};

/** enum cu_status */
export const STATI_CU: Record<string, string> = {
  draft: 'Bozza',
  generated: 'Generata',
  delivered: 'Consegnata',
  sent: 'Inviata AdE',
};

/** enum tenant_status */
export const STATI_TENANT: Record<string, string> = {
  draft: 'Bozza',
  active: 'Attivo',
  suspended: 'Sospeso',
  closed: 'Chiuso',
};

/** stato_documento.codice (stato SDI del documento fiscale) — stesse etichette del filtro in DocumentsList */
export const STATI_DOCUMENTO: Record<string, string> = {
  draft: 'Bozza',
  ready: 'Pronto',
  sent_sdi: 'Inviato SDI',
  accepted: 'Accettato',
  rejected: 'Rifiutato',
  error: 'Errore',
};

/** tipo_documento.codice */
export const TIPI_DOCUMENTO: Record<string, string> = {
  fattura: 'Fattura',
  ricevuta: 'Ricevuta',
  nota_credito: 'Nota di credito',
};

export const labelStatoDocumento = (stato: string): string => STATI_DOCUMENTO[stato] ?? stato;
export const labelTipoDocumento = (tipo: string): string => TIPI_DOCUMENTO[tipo] ?? tipo;

export const labelStatoPrenotazione =(stato: string): string => STATI_PRENOTAZIONE[stato] ?? stato;
export const labelStatoCu = (stato: string): string => STATI_CU[stato] ?? stato;
export const labelStatoTenant = (stato: string): string => STATI_TENANT[stato] ?? stato;
