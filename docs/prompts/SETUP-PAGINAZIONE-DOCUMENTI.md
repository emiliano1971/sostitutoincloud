Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- service/FiscalDocumentService.java
  metodo findByTenantId()
- controller/DocumentController.java
- frontend/src/pages/tenant/
  DocumentsList.tsx
- frontend/src/api/documentApi.ts
- model/TenantSettings.java
- dao/TenantSettingsDAO.java
- frontend/src/pages/admin/
  TenantSettings.tsx o equivalente
  (pagina impostazioni tenant)
  prima di procedere.

Implementa la paginazione nella lista
documenti fiscali con dimensione pagina
configurabile per tenant, e aggiungi
la scheda "Configurazione" nelle
impostazioni tenant.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MIGRATION DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica l'ultimo numero migration
e crea il file successivo.

ALTER TABLE tenant_settings
ADD COLUMN IF NOT EXISTS
page_size INTEGER
NOT NULL DEFAULT 50;

COMMENT ON COLUMN
tenant_settings.page_size IS
'Dimensione pagina per le liste
paginate (documenti, booking ecc.).
Default 50.';

Aggiorna schema-target.sql.
Esegui sul DB locale.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — TenantSettings
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a model/TenantSettings.java:
Integer pageSize

Aggiorna mapper, DAO e DTO
per leggere e scrivere page_size.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — FiscalDocumentService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna findByTenantId() per
supportare paginazione vera
con conteggio totale.

Crea dto/document/
DocumentPageDTO.java:
List<DocumentListDTO> content
int page        ← pagina corrente (0-based)
int size        ← dimensione pagina
long totalElements ← totale record
int totalPages  ← totale pagine

In FiscalDocumentService:

DocumentPageDTO findByTenantId(
Integer tenantId,
String statoFilter,
String tipoFilter,
String searchQuery,
String filtroFiscale,
LocalDate dataFrom,
LocalDate dataTo,
Integer ownerId,
int page,
int size)

1. Carica il pageSize dal tenant:
   int effectiveSize = size > 0
   ? size
   : tenantSettingsDAO
   .findByTenantId(tenantId)
   .getPageSize()

2. Sposta i filtri lato server:
    - statoFilter → WHERE stato = ?
    - tipoFilter → WHERE tipo = ?
    - searchQuery → WHERE
      document_number ILIKE ?
      OR recipient_name ILIKE ?
      OR owner_name ILIKE ?
      OR booking_external_id ILIKE ?
      OR ndc_number ILIKE ?
      OR fattura_number ILIKE ?
    - filtroFiscale (da_liquidare,
      f24_non_pagato, senza_cu)
      → JOIN appropriati
    - dataFrom/dataTo → WHERE
      data_emissione BETWEEN ? AND ?
    - ownerId → WHERE fk_owner_id = ?

3. Query COUNT per totalElements:
   SELECT COUNT(*) FROM fiscal_document
   WHERE fk_tenant_id = ?
   [+ filtri]

4. Query dati con paginazione:
   SELECT ... FROM fiscal_document
   WHERE fk_tenant_id = ?
   [+ filtri]
   ORDER BY data_emissione DESC, id DESC
   LIMIT ? OFFSET ?
   ← OFFSET = page * effectiveSize

5. Return DocumentPageDTO con
   content, page, size,
   totalElements, totalPages

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BACKEND — Controller
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna DocumentController.java:

GET /api/documents
Aggiungi parametri:
@RequestParam(defaultValue="0")
int page
@RequestParam(defaultValue="0")
int size
← 0 = usa pageSize del tenant

Return: DocumentPageDTO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. FRONTEND — documentApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna getDocuments() per
supportare paginazione:

export interface DocumentPage {
content: DocumentListItem[]
page: number
size: number
totalElements: number
totalPages: number
}

export async function getDocuments(
params: {
stato?: string
tipo?: string
search?: string
filtroFiscale?: string
dataFrom?: string
dataTo?: string
ownerId?: number
page?: number
size?: number
}
): Promise<DocumentPage>
// GET /api/documents

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. FRONTEND — DocumentsList.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna DocumentsList per usare
la paginazione server-side.

Stato paginazione:
const [page, setPage] = useState(0)
const [totalPages, setTotalPages]
= useState(0)
const [totalElements, setTotalElements]
= useState(0)

Rimuovi il filtro lato client
(ora è tutto lato server).

Aggiorna il caricamento:
const loadDocuments = async () => {
const result = await getDocuments({
stato: statoFilter,
tipo: tipoFilter,
search: searchQuery,
filtroFiscale,
dataFrom,
dataTo,
ownerId: ownerFilter,
page,
size: 0  ← usa pageSize tenant
})
setDocuments(result.content)
setTotalPages(result.totalPages)
setTotalElements(result.totalElements)
}

Reset pagina quando cambiano i filtri:
useEffect(() => {
setPage(0)
}, [statoFilter, tipoFilter,
searchQuery, filtroFiscale,
dataFrom, dataTo, ownerFilter])

Aggiungi componente paginazione
sotto la tabella:

{totalPages > 1 && (
  <div className="flex items-center
    justify-between mt-4">
    <p className="text-sm
      text-muted-foreground">
      {totalElements} documenti —
      Pagina {page + 1} di {totalPages}
    </p>
    <div className="flex items-center
      gap-1">
      <Button
        variant="outline"
        size="sm"
        onClick={() => setPage(0)}
        disabled={page === 0}>
        «
      </Button>
      <Button
        variant="outline"
        size="sm"
        onClick={() =>
          setPage(p => p - 1)}
        disabled={page === 0}>
        ‹
      </Button>

      {← numeri pagina vicini:
         mostra max 5 pagine centrate
         sulla pagina corrente }
      {Array.from(
        { length: Math.min(5,
          totalPages) },
        (_, i) => {
          const start = Math.max(0,
            Math.min(
              page - 2,
              totalPages - 5))
          return start + i
        }
      ).map(p => (
        <Button
          key={p}
          variant={p === page
            ? "default"
            : "outline"}
          size="sm"
          onClick={() => setPage(p)}>
          {p + 1}
        </Button>
      ))}

      <Button
        variant="outline"
        size="sm"
        onClick={() =>
          setPage(p => p + 1)}
        disabled={
          page >= totalPages - 1}>
        ›
      </Button>
      <Button
        variant="outline"
        size="sm"
        onClick={() =>
          setPage(totalPages - 1)}
        disabled={
          page >= totalPages - 1}>
        »
      </Button>
    </div>
  </div>
)}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. FRONTEND — Scheda Configurazione
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Nella pagina impostazioni tenant
aggiungi una nuova scheda
"Configurazione" (o tab) con:

A) Dimensione pagina:
<div className="space-y-2">
  <Label>Dimensione pagina liste</Label>
  <p className="text-xs
    text-muted-foreground">
    Numero di elementi per pagina
    nelle liste (documenti, booking...).
    Default: 50
  </p>
  <Input
    type="number"
    value={settings.pageSize ?? 50}
    onChange={e => setSettings({
      ...settings,
      pageSize: parseInt(e.target.value)
    })}
    min={10}
    max={200}
    step={10}
    className="w-32"
  />
</div>

B) Canale OTA default per import:
← sposta qui dalla sezione
dove si trova attualmente

Aggiorna settingsApi.ts:
pageSize: number
← aggiungi al tipo TenantSettings

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package
cd frontend && npm run build
npm run typecheck

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena2026"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Verifica paginazione
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/documents?page=0" \
| python3 -m json.tool \
| grep -E '"totalElements|\
totalPages|page|size"'

# Verifica filtro server-side
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/documents?search=FT-2026" \
| python3 -m json.tool \
| grep '"documentNumber"'

# Verifica pageSize dal tenant
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/documents?page=0&size=2" \
| python3 -m json.tool \
| grep -E '"totalElements|\
content"'

Verifica che:
- Paginazione funziona con
  totalElements e totalPages corretti
- Filtri funzionano lato server
- pageSize dal tenant viene usato
- Scheda Configurazione mostra
  pageSize e canale OTA default

Riporta output build e curl.