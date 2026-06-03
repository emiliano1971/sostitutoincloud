Leggi il file CLAUDE.md e docs/analisi-frontend.md
prima di procedere.

Implementa la gestione completa tenant per il super_admin.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. ANALISI PRELIMINARE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima leggi e descrivi brevemente:
- frontend/src/pages/admin/TenantsList.tsx
- frontend/src/pages/admin/SuperAdminDashboard.tsx

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — implementa sospensione tenant
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/TenantDAO.java:

Tenant updateStatus(Integer id, String nuovoStato)
- UPDATE tenant SET stato=?, updated_at=NOW()
  WHERE id=?
- Usa Types.OTHER per la colonna enum stato
- Dopo update: rileggi con findById() e restituisci
- Log INFO: "TenantDAO.updateStatus() - id={} stato={}"

Aggiungi a service/TenantService.java:

TenantDetailDTO updateStatus(Integer id,
String nuovoStato)
- Sostituisce lo stub esistente
- Verifica esistenza tenant
- Verifica che nuovoStato sia "active" o "suspended"
  lancia IllegalArgumentException se non valido
- Chiama tenantDAO.updateStatus(id, nuovoStato)
- Ricarica e mappa su TenantDetailDTO
- Log INFO

In controller/TenantController.java il PATCH
/{id}/status già esiste — rimuove il 501 e ora
funzionerà con il service aggiornato.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — crea nuovo tenant
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/tenant/TenantCreateDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- String legalName (obbligatorio)
- String displayName (obbligatorio)
- String taxCode (16 char, obbligatorio)
- String vatNumber (11 char, opzionale)
- String administrativeEmail (obbligatorio)
- String pec (opzionale)
- String phone (opzionale)
- String legalAddress (obbligatorio)

Aggiungi a dao/TenantDAO.java:

Tenant insert(Tenant tenant)
- INSERT INTO tenant con tutti i campi
  (stato default 'draft')
- Usa KeyHolder per id generato
- Usa Types.OTHER per la colonna enum stato
- Dopo insert: rileggi con findById()
- Log INFO: "TenantDAO.insert() - id={}"

Aggiungi a service/TenantService.java:

TenantDetailDTO create(TenantCreateDTO dto)
- Valida che taxCode non esista già
  lancia IllegalArgumentException se duplicato
- Costruisce Tenant da DTO con stato="draft"
- Chiama tenantDAO.insert(tenant)
- Crea anche tenant_settings di default
  con tenantSettingsDAO.save(defaultSettings)
- Ricarica e mappa su TenantDetailDTO
- Log INFO

Aggiungi a controller/TenantController.java:

POST /api/admin/tenants
- @RequestBody TenantCreateDTO
- chiama tenantService.create(dto)
- ResponseEntity.status(201).body(result)
- gestisce IllegalArgumentException → 400

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND — tenantApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/api/tenantApi.ts:

```ts
export interface TenantListItem {
  id: number;
  legalName: string;
  displayName: string;
  taxCode: string;
  vatNumber?: string;
  stato: string;
  administrativeEmail: string;
  phone?: string;
  legalAddress: string;
  activatedAt?: string;
  createdAt: string;
  propertiesCount: number;
  ownersCount: number;
  bookingsCount: number;
}

export interface TenantDetail extends TenantListItem {
  pec?: string;
  updatedAt: string;
}

export interface TenantCreateRequest {
  legalName: string;
  displayName: string;
  taxCode: string;
  vatNumber?: string;
  administrativeEmail: string;
  pec?: string;
  phone?: string;
  legalAddress: string;
}

export async function getTenants(): Promise<TenantListItem[]>
// GET /api/admin/tenants

export async function getTenantById(
  id: number
): Promise<TenantDetail>
// GET /api/admin/tenants/{id}

export async function createTenant(
  data: TenantCreateRequest
): Promise<TenantDetail>
// POST /api/admin/tenants → 201

export async function updateTenantStatus(
  id: number,
  stato: string
): Promise<TenantDetail>
// PATCH /api/admin/tenants/{id}/status
// body: { stato }
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. AGGIORNA TenantsList.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/admin/TenantsList.tsx:
- Mantieni TUTTO il layout e UI esistente
- Sostituisci mock con useEffect → getTenants()
- Aggiungi loading/error state
- Adatta campi mock → API
- Il bottone "Nuovo Tenant" → navigate('/admin/tenants/new')
- Il pulsante sospendi/riattiva (icona Pause/Play)
  chiama updateTenantStatus(id, 'suspended'/'active')
  e ricarica la lista
- Il pulsante dettaglio (icona occhio) →
  navigate('/admin/tenants/{id}')

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. CREA TenantCreate.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/pages/admin/TenantCreate.tsx:
- Stile coerente con OwnerCreate.tsx
- Form con i campi di TenantCreateRequest
- Validazione:
    * legalName, displayName, administrativeEmail,
      legalAddress obbligatori
    * taxCode esattamente 16 caratteri
    * vatNumber esattamente 11 caratteri se presente
    * email formato valido
- Al submit: chiama createTenant(data)
    * successo → navigate('/admin/tenants') con toast
    * errore 400 → mostra messaggio dal server
- Pulsante "Annulla" → navigate('/admin/tenants')

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. CREA TenantDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/pages/admin/TenantDetail.tsx:
- Stile coerente con OwnerDetail.tsx
- useEffect → getTenantById(Number(id))
- Mostra:
    * Card anagrafica (legalName, taxCode, vatNumber,
      email, pec, phone, legalAddress)
    * Card statistiche (propertiesCount, ownersCount,
      bookingsCount)
    * Card stato con badge e pulsante
      Sospendi/Riattiva che chiama updateTenantStatus()
- Pulsante back → navigate('/admin/tenants')

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. AGGIORNA App.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi le route:
- /admin/tenants/new → TenantCreate
  (PRIMA di /admin/tenants/:id)
- /admin/tenants/:id → TenantDetail

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
9. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima lancia il build backend:
mvn -Plocal clean package

Verifica:
TOKEN_SUPER=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"superadmin@sostitutoincloud.it",
"password":"atena"}' \
| python3 -c \
"import sys,json; print(json.load(sys.stdin)['token'])")

curl -s -H "Authorization: Bearer $TOKEN_SUPER" \
http://localhost:8081/sostitutoincloud/api/admin/tenants \
| python3 -m json.tool

Poi:
cd frontend && npm run build

Riporta output di entrambi.