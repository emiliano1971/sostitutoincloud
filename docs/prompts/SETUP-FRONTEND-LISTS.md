Leggi il file CLAUDE.md e docs/analisi-frontend.md
prima di procedere.

Collega le pagine lista rimanenti al backend reale
sostituendo i dati mock.
Segui lo stesso pattern già usato per OwnersList.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. ANALISI PRELIMINARE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima leggi e descrivi brevemente:
- frontend/src/pages/tenant/DocumentsList.tsx
- frontend/src/pages/tenant/SettlementsList.tsx
- frontend/src/pages/tenant/F24List.tsx
- frontend/src/pages/tenant/CUList.tsx
- frontend/src/pages/tenant/AuditLog.tsx

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. CREA frontend/src/api/documentApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

```ts
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

export interface DocumentDetail extends DocumentListItem {
  fkTenantId: number;
  fkTipoDocumentoId: number;
  fkStatoDocumentoId: number;
  richiedeIva: boolean;
  updatedAt: string;
  righe: DocumentRow[];
}

export interface DocumentRow {
  descrizione: string;
  importoNetto: number;
  aliquotaIva: number;
  importoIva: number;
  importoLordo: number;
}
```

Funzioni:
```ts
export async function getDocuments(params?: {
  stato?: string;
  q?: string;
  page?: number;
  size?: number;
}): Promise<DocumentListItem[]>

export async function getDocumentById(
  id: number
): Promise<DocumentDetail>
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CREA frontend/src/api/settlementApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

```ts
export interface SettlementListItem {
  id: number;
  ownerName: string;
  period: string;
  totalAmount: number;
  withholdingAmount: number;
  netAmount: number;
  bookingsCount: number;
  stato: string;
  paymentDate?: string;
  createdAt: string;
}

export interface SettlementDetail extends SettlementListItem {
  fkTenantId: number;
  fkOwnerId: number;
  updatedAt: string;
  bookings: SettlementBookingItem[];
}

export interface SettlementBookingItem {
  bookingId: number;
  externalBookingId: string;
  propertyName: string;
  checkinDate: string;
  checkoutDate: string;
  grossAmount: number;
  ownerNetAmount: number;
  withholdingAmount: number;
}
```

Funzioni:
```ts
export async function getSettlements(params?: {
  ownerId?: number;
  period?: string;
}): Promise<SettlementListItem[]>

export async function getSettlementById(
  id: number
): Promise<SettlementDetail>
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. CREA frontend/src/api/f24Api.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

```ts
export interface F24ListItem {
  id: number;
  period: string;
  codiceTributo: string;
  totalAmount: number;
  withholdingsCount: number;
  stato: string;
  deadlineDate: string;
  paymentDate?: string;
  createdAt: string;
}

export interface F24Detail extends F24ListItem {
  fkTenantId: number;
  tenantLegalName: string;
  tenantTaxCode: string;
  tenantAddress: string;
  updatedAt: string;
}
```

Funzioni:
```ts
export async function getF24List(): Promise<F24ListItem[]>

export async function getF24ById(
  id: number
): Promise<F24Detail>
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. CREA frontend/src/api/cuApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

```ts
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
```

Funzioni:
```ts
export async function getCuList(params?: {
  ownerId?: number;
  taxYear?: number;
}): Promise<CuListItem[]>

export async function getCuById(
  id: number
): Promise<CuDetail>
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. CREA frontend/src/api/auditApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

```ts
export interface AuditLogItem {
  id: number;
  fkTenantId?: number;
  fkUtenteId?: number;
  userEmail: string;
  action: string;
  entityType: string;
  entityId?: number;
  details: string;
  ipAddress?: string;
  createdAt: string;
}
```

Funzioni:
```ts
export async function getAuditLog(params?: {
  q?: string;
  action?: string;
  page?: number;
  size?: number;
}): Promise<AuditLogItem[]>
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. AGGIORNA LE PAGINE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Per ogni pagina:
- Mantieni TUTTO il layout e UI esistente
- Sostituisci mock con useEffect + chiamata API
- Aggiungi loading/error state
- Mostra "Nessun dato" se lista vuota dopo load
- Adatta i campi mock → API (camelCase)

DocumentsList.tsx:
- useEffect → getDocuments()
- filtro per stato passa a getDocuments({stato})
- search locale su documentNumber, recipientName
- campo mock status → statoDocumento
- campo mock document_type → documentType
- campo mock document_number → documentNumber

SettlementsList.tsx:
- useEffect → getSettlements()
- campo mock owner_name → ownerName
- campo mock total_amount → totalAmount
- campo mock net_amount → netAmount
- campo mock bookings_count → bookingsCount
- campo mock status → stato

F24List.tsx:
- useEffect → getF24List()
- campo mock tax_code (1919) → codiceTributo
- campo mock total_amount → totalAmount
- campo mock withholdings_count → withholdingsCount
- campo mock status → stato
- campo mock deadline_date → deadlineDate

CUList.tsx:
- useEffect → getCuList()
- campo mock owner_name → ownerName
- campo mock tax_year → taxYear
- campo mock total_compensi → totalCompensi
- campo mock total_ritenute → totalRitenute
- campo mock status → stato

AuditLog.tsx:
- useEffect → getAuditLog()
- al cambio filtri (q, action) richiama getAuditLog()
- campo mock user_email → userEmail
- campo mock entity_type → entityType
- campo mock entity_id → entityId
- campo mock ip_address → ipAddress

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che il frontend compili:
cd frontend && npm run build

Riporta eventuali errori TypeScript e il risultato.