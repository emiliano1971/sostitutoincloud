# Analisi Frontend — Entità e API

Estratto da `frontend/src/pages/`, `frontend/src/components/`,
`frontend/src/data/mock-data.ts`, `frontend/src/types/index.ts`.

---

## Enum e Tipi Globali (`src/types/index.ts`)

| Tipo | Valori |
|------|--------|
| `UserRole` | `super_admin` · `tenant_admin` · `pm_user` · `owner_user` |
| `TenantStatus` | `draft` · `active` · `suspended` · `closed` |
| `OwnerType` | `persona_fisica` · `piva` · `societa` |
| `FiscalRegime` | `cedolare_secca` · `iva_10` · `ordinario` |
| `BookingStatus` | `imported` · `enriched` · `ready` · `doc_issued` · `settled` · `cancelled` |
| `DocumentStatus` | `draft` · `ready` · `sent_sdi` · `accepted` · `rejected` · `error` |
| `PaymentStatus` | `pending` · `received` · `failed` |
| `SettlementStatus` | `pending` · `calculated` · `approved` · `paid` |
| `F24Status` | `draft` · `ready` · `sent` · `paid` · `error` |
| `CUStatus` | `draft` · `generated` · `sent` · `delivered` |

---

## Tenant

### Pagine coinvolte

| Pagina | File | Operazione |
|--------|------|------------|
| TenantsList | `pages/admin/TenantsList.tsx` | LIST, SEARCH, SUSPEND/ACTIVATE |
| SuperAdminDashboard | `pages/admin/SuperAdminDashboard.tsx` | KPI globale |
| TenantDashboard | `pages/tenant/TenantDashboard.tsx` | display tenant corrente |
| InvoicePMDialog | `components/booking/InvoicePMDialog.tsx` | intestazione fattura |
| F24PreviewDialog | `components/f24/F24PreviewDialog.tsx` | dati contribuente |

### Dati visualizzati

| Campo | Tipo | Dove |
|-------|------|------|
| `tenant_id` | string | TenantsList |
| `legal_name` | string | TenantsList, F24PreviewDialog, InvoicePMDialog |
| `display_name` | string | TenantsList, Dashboard |
| `tax_code` | string (16 car) | TenantsList, F24PreviewDialog (box singole cifre) |
| `vat_number` | string | TenantsList, InvoicePMDialog |
| `tenant_status` | TenantStatus | TenantsList (badge colore) |
| `administrative_email` | string | anagrafe |
| `pec` | string | InvoicePMDialog |
| `phone` | string | anagrafe |
| `legal_address` | string | InvoicePMDialog, F24PreviewDialog |
| `created_at` | YYYY-MM-DD | TenantsList |
| `activated_at` | YYYY-MM-DD | Dashboard (opzionale) |
| `properties_count` | number | TenantsList (KPI) |
| `owners_count` | number | TenantsList (KPI) |
| `bookings_count` | number | TenantsList (KPI) |

### Operazioni

- **LIST**: tabella con tutti i campi sopra
- **SEARCH**: per `display_name` e `legal_name` (case-insensitive)
- **FILTER**: per `tenant_status`
- **SUSPEND/ACTIVATE**: toggle status `active` ↔ `suspended` (icona Pause/Play)

### Campi nei form

Nessun form di creazione visibile nel frontend mock (solo super_admin in produzione).

### Mock usati

```json
{
  "tenant_id": "t1",
  "legal_name": "Casa Vacanze Italia SRL",
  "display_name": "Casa Vacanze Italia",
  "tax_code": "CVITRL80A01H501Z",
  "vat_number": "IT12345678901",
  "tenant_status": "active",
  "administrative_email": "admin@casavacanze.it",
  "pec": "casavacanze@pec.it",
  "phone": "+39 06 1234567",
  "legal_address": "Via Roma 1, 00100 Roma RM",
  "created_at": "2024-01-15",
  "activated_at": "2024-01-20",
  "properties_count": 5,
  "owners_count": 3,
  "bookings_count": 28
}
```
3 tenant mock (t1, t2, t3).

### Note per il Controller

- `GET /api/admin/tenants` — lista tutti i tenant (solo `super_admin`)
- `GET /api/admin/tenants/{id}` — dettaglio
- `PATCH /api/admin/tenants/{id}/status` body `{ "status": "suspended" }`
- Il dashboard tenant corrente legge dati dal tenant in sessione: `GET /api/tenants/me`
- InvoicePMDialog e F24PreviewDialog leggono il tenant via contesto, non chiamata separata

---

## Owner (Proprietario)

### Pagine coinvolte

| Pagina | File | Operazione |
|--------|------|------------|
| OwnersList | `pages/tenant/OwnersList.tsx` | LIST, SEARCH |
| OwnerDetail | `pages/tenant/OwnerDetail.tsx` | DISPLAY, DEACTIVATE |
| PropertyCreate | `pages/tenant/PropertyCreate.tsx` | SELECT in dropdown |
| PropertyDetail | `pages/tenant/PropertyDetail.tsx` | DISPLAY, CHANGE OWNER |
| BookingDetail | `pages/tenant/BookingDetail.tsx` | display nome proprietario |
| OwnerBookings | `pages/owner/OwnerBookings.tsx` | LIST prenotazioni filtrate |
| OwnerSettlements | `pages/owner/OwnerSettlements.tsx` | LIST liquidazioni filtrate |
| OwnerCU | `pages/owner/OwnerCU.tsx` | LIST CU filtrate |

### Dati visualizzati

| Campo | Tipo | Dove |
|-------|------|------|
| `owner_id` | string | tutti i dettagli |
| `tenant_id` | string | (filtro, non visualizzato) |
| `owner_type` | OwnerType | OwnersList, OwnerDetail (badge) |
| `first_name` | string | OwnersList, OwnerDetail, BookingDetail |
| `last_name` | string | OwnersList, OwnerDetail, BookingDetail |
| `legal_name` | string? | OwnerDetail (solo se piva/societa) |
| `tax_code` | string (16) | OwnersList, OwnerDetail (monospace) |
| `vat_number` | string? | OwnerDetail (solo se piva/societa) |
| `fiscal_regime` | FiscalRegime | OwnersList, OwnerDetail (badge) |
| `email` | string | OwnerDetail |
| `phone` | string | OwnerDetail |
| `iban` | string | OwnerDetail (monospace) |
| `status` | `active` \| `inactive` | OwnersList, OwnerDetail (badge) |
| `properties_count` | number | OwnersList, OwnerDetail |
| `created_at` | YYYY-MM-DD | OwnerDetail |

**OwnerDetail — Riepilogo Economico calcolato:**
- Immobili gestiti, Prenotazioni recenti, Lordo recente, Netto proprietario, Liquidazioni count

### Operazioni

- **LIST**: tabella con search per `first_name`+`last_name` e `tax_code`
- **DETAIL** (`/owners/:id`): view anagrafe completa + immobili associati (cliccabili)
- **DEACTIVATE/REACTIVATE**: toggle `status` active ↔ inactive (icona Power/PowerOff)
- **CHANGE OWNER**: dialog select in PropertyDetail per riassegnare

### Campi nei form

Nessun form di creazione visibile nel frontend mock. PropertyCreate ha un **select** che mostra `first_name + last_name` dei proprietari attivi del tenant.

### Mock usati

```json
{
  "owner_id": "o1",
  "tenant_id": "t1",
  "owner_type": "persona_fisica",
  "first_name": "Anna",
  "last_name": "Moretti",
  "tax_code": "MRTANN85A41H501X",
  "fiscal_regime": "cedolare_secca",
  "email": "anna.moretti@email.it",
  "phone": "+39 333 1111111",
  "iban": "IT60X0542811101000000123456",
  "status": "active",
  "properties_count": 2,
  "created_at": "2024-01-20"
}
```
5 owner mock; nel tenant t1 ne appaiono 3 (o1, o2, o3).

### Note per il Controller

- `GET /api/owners` — lista proprietari del tenant in sessione
- `GET /api/owners/{id}` — dettaglio + campi calcolati (properties_count, ecc.)
- `PATCH /api/owners/{id}/status` body `{ "status": "inactive" }`
- Il select in PropertyCreate chiama `GET /api/owners?status=active`
- Il matching booking→owner avviene via `owner_name` (stringa denormalizzata): da allineare con FK `owner_id` nel backend

---

## Property (Immobile)

### Pagine coinvolte

| Pagina | File | Operazione |
|--------|------|------------|
| PropertiesList | `pages/tenant/PropertiesList.tsx` | LIST, SEARCH |
| PropertyDetail | `pages/tenant/PropertyDetail.tsx` | DISPLAY, DEACTIVATE, CHANGE OWNER |
| PropertyCreate | `pages/tenant/PropertyCreate.tsx` | CREATE |
| PropertyContracts | `pages/tenant/PropertyContracts.tsx` | EDIT regole costo |
| BookingDetail | `pages/tenant/BookingDetail.tsx` | display nome immobile |

### Dati visualizzati

| Campo | Tipo | Dove |
|-------|------|------|
| `property_id` | string | tutti i contesti |
| `tenant_id` | string | (filtro) |
| `owner_id` | string | PropertyDetail, PropertiesList |
| `pm_id` | string | (gestionale interno) |
| `internal_code` | string | PropertiesList, PropertyDetail (monospace) |
| `display_name` | string | PropertiesList, PropertyDetail |
| `address` | string | PropertyDetail, InvoicePMDialog |
| `city` | string | PropertiesList, PropertyDetail |
| `region` | string | PropertyDetail |
| `property_type` | string | PropertiesList, PropertyDetail (badge) |
| `cin_code` | string | PropertiesList, PropertyDetail (monospace, primary) |
| `ota_codes` | object | PropertyDetail (lista), PropertiesList (count con tooltip) |
| `status` | `active` \| `inactive` | PropertiesList, PropertyDetail (badge) |
| `listings_count` | number | PropertiesList, PropertyDetail |
| `bookings_count` | number | PropertiesList, PropertyDetail |
| `created_at` | YYYY-MM-DD | PropertyDetail |

**ota_codes struttura:**
```
{
  airbnb_id?: string
  booking_id?: string
  vrbo_id?: string
  tripadvisor_id?: string
  expedia_id?: string
}
```

**property_type valori:** `LT` · `CAV` · `B&B` · `Affittacamere`

### Operazioni

- **LIST**: tabella, search per `display_name`, `internal_code`, `cin_code`; tooltip OTA codes
- **DETAIL** (`/properties/:id`): view info + CIN + OTA + ultime 5 prenotazioni (tabella)
- **DEACTIVATE/REACTIVATE**: toggle `status`
- **CHANGE OWNER**: dialog select proprietario
- **CONTRATTI**: naviga a `/properties/:id/contracts`

### Campi nei form

**PropertyCreate** (`/properties/new`):

| Campo | Tipo UI | Note |
|-------|---------|------|
| `display_name` | Input | obbligatorio |
| `internal_code` | Input | obbligatorio |
| `property_type` | Select | LT, CAV, B&B, Affittacamere |
| `address` | Input | |
| `city` | Input | obbligatorio |
| `region` | Input | |
| `cin_code` | Input | monospace |
| `owner_id` | Select | lista proprietari attivi |
| `ota_codes.airbnb_id` | Input | |
| `ota_codes.booking_id` | Input | |
| `ota_codes.vrbo_id` | Input | |
| `ota_codes.tripadvisor_id` | Input | |
| `ota_codes.expedia_id` | Input | |

**PropertyContracts** — cost rules:

| Campo | Tipo | Note |
|-------|------|------|
| `type` | enum | `pulizie` · `commissione_ota` · `cambio_biancheria` · `commissione_pm` · `provvigione_proprietario` |
| `calc_mode` | enum | `fisso` · `percentuale` · `fisso_per_notte` |
| `value` | number | importo o percentuale |
| `ota_channel` | string? | per commissioni OTA specifiche |
| `is_remainder` | boolean | |

### Mock usati

```json
{
  "property_id": "p1",
  "tenant_id": "t1",
  "owner_id": "o1",
  "pm_id": "pm1",
  "internal_code": "ROM-001",
  "display_name": "Appartamento Trastevere",
  "address": "Via della Scala 15",
  "city": "Roma",
  "region": "Lazio",
  "property_type": "LT",
  "cin_code": "IT058091C1A2B3C4D5",
  "ota_codes": { "airbnb_id": "12345678", "booking_id": "9876543", "vrbo_id": "VR-001122" },
  "status": "active",
  "listings_count": 2,
  "bookings_count": 12,
  "created_at": "2024-01-25"
}
```
8 property mock totali; 5 per tenant t1.

### Note per il Controller

- `GET /api/properties` — lista con possibile `?status=active`
- `GET /api/properties/{id}` — dettaglio + ota_codes
- `POST /api/properties` — creazione
- `PATCH /api/properties/{id}` — modifica parziale (owner, status)
- `GET /api/properties/{id}/bookings?limit=5` — ultime prenotazioni per PropertyDetail
- `GET /api/properties/{id}/contracts` — regole costo
- `PUT /api/properties/{id}/contracts` — salva regole costo

---

## Booking (Prenotazione)

### Pagine coinvolte

| Pagina | File | Operazione |
|--------|------|------------|
| BookingsList | `pages/tenant/BookingsList.tsx` | LIST, SEARCH, FILTER |
| BookingDetail | `pages/tenant/BookingDetail.tsx` | DISPLAY, FATTURA, RICEVUTA |
| PropertyDetail | `pages/tenant/PropertyDetail.tsx` | ultime 5 prenotazioni |
| ImportBookings | `pages/tenant/ImportBookings.tsx` | IMPORT con preview |
| Reconciliation | `pages/tenant/Reconciliation.tsx` | MATCH con alloggiati |
| OwnerBookings | `pages/owner/OwnerBookings.tsx` | LIST filtrate per owner |
| TenantDashboard | `pages/tenant/TenantDashboard.tsx` | KPI mensile |

### Dati visualizzati

| Campo | Tipo | Dove |
|-------|------|------|
| `booking_id` | string | ovunque |
| `tenant_id` | string | (filtro) |
| `property_id` | string | (FK) |
| `property_name` | string | BookingsList, BookingDetail |
| `owner_name` | string | BookingsList, BookingDetail |
| `guest_name` | string | BookingsList, BookingDetail |
| `external_booking_id` | string | BookingsList, BookingDetail |
| `channel_name` | string | BookingsList (badge colore), BookingDetail |
| `guest_tax_code` | string | (usato per documenti) |
| `checkin_date` | YYYY-MM-DD | BookingsList, BookingDetail |
| `checkout_date` | YYYY-MM-DD | BookingsList, BookingDetail |
| `nights` | number | BookingsList, BookingDetail, split economico |
| `guests` | number | BookingDetail, tassa soggiorno |
| `gross_amount` | number | BookingsList, split economico |
| `ota_commission_amount` | number | split economico |
| `cleaning_amount` | number | split economico |
| `pm_fee_amount` | number | split economico |
| `owner_net_amount` | number | split economico (bold), OwnerBookings |
| `withholding_amount` | number | split economico |
| `tourist_tax_amount` | number | split economico (highlight amber) |
| `tourist_tax_included_in_gross` | boolean | label split |
| `tourist_tax_collection` | `contanti` \| `payment_link` \| `altro` | (gestionale) |
| `booking_status` | BookingStatus | BookingsList, BookingDetail (badge) |
| `payment_status` | PaymentStatus | BookingDetail (card status) |
| `document_status` | DocumentStatus | BookingDetail (card status) |
| `settlement_status` | SettlementStatus | BookingDetail (card status) |
| `fiscal_scenario_code` | string | BookingDetail (es. `scenario_A`) |
| `created_at` | YYYY-MM-DD | (interno) |

**Split Economico (8 righe mostrate in BookingDetail):**
```
Lordo ospite:          gross_amount
- Commissione OTA:     ota_commission_amount
- Pulizie:             cleaning_amount
- Provvigione PM:      pm_fee_amount
= Netto proprietario:  owner_net_amount        (bold)
- Ritenuta 21%:        withholding_amount
= Liquidazione owner:  owner_net - withholding  (bold)
+ Tassa soggiorno:     tourist_tax_amount       (highlight amber)
```

**channel_name colori:**
- `airbnb` → `bg-[#FF5A5F]/10 text-[#FF5A5F]`
- `booking` → `bg-[#003580]/10 text-[#003580]`
- `vrbo` → `bg-[#3B5998]/10 text-[#3B5998]`

**Row highlighting in BookingsList:**
- Penale (>12 gg scaduto): `bg-destructive/8`
- Overdue (scaduto non penale): `bg-warning/6`

### Operazioni

- **LIST con filtri:**
  - `da_completare` (default): `checkout_date ≤ oggi` AND status NOT IN `(doc_issued, settled, cancelled)`
  - Singolo status: `imported`, `enriched`, `ready`, `doc_issued`, `settled`
  - Per canale: `all`, `airbnb`, `booking`, `vrbo`
  - Search: `guest_name`, `property_name`, `external_booking_id`
- **DETAIL** (`/bookings/:id`): visualizza split, 3 card status, pulsanti azione
- **STAMPA FATTURA P.M.**: apre `InvoicePMDialog`
- **STAMPA RICEVUTA OWNER**: apre `ReceiptOwnerDialog`
- **IMPORT** (`/import/bookings`): upload file, preview, conferma
- **RECONCILIATION** (`/reconciliation`): matching con alloggiati (score %), conferma/rifiuta

### Campi nei form

**ImportBookings** — nessun form dati manuali; solo upload file e conferma.

### Mock usati

50 booking generati algoritmicamente (distribuzione temporale: 8 in penale, 10 overdue, 12 futuri, 20 storici). Distribuiti round-robin su 8 property.

Calcoli mock:
```
nights = 2 + (i % 7)
guests = 1 + (i % 4)
gross_amount = 80 + (i*15) + (nights*50)
ota_commission_amount = gross * 0.15
cleaning_amount = 50 + (i % 4)*10
pm_fee_amount = gross * 0.20
owner_net_amount = gross - OTA - cleaning - PM
withholding_amount = owner_net * 0.21
tourist_tax_amount = guests * min(nights,5) * 3.50
tourist_tax_included_in_gross = (channel === 'airbnb')
```

### Note per il Controller

- `GET /api/bookings` — con query params: `status`, `channel`, `q` (search), `page`, `size`
- `GET /api/bookings/{id}` — dettaglio completo con split economico
- `GET /api/bookings?status=da_completare` — filtro speciale (checkout ≤ oggi, non doc emesso)
- `POST /api/bookings/import` — import con preview: `{ source, file }` → lista booking da confermare
- `POST /api/bookings/import/confirm` — conferma import
- `GET /api/bookings/reconciliation` — lista match con score
- `POST /api/bookings/reconciliation/{id}/confirm` e `/reject`
- Lo split economico è calcolato dal backend — il frontend lo riceve già pronto

---

## FiscalDocument (Documento Fiscale)

### Pagine coinvolte

| Pagina | File | Operazione |
|--------|------|------------|
| DocumentsList | `pages/tenant/DocumentsList.tsx` | LIST, SEARCH, FILTER |
| InvoicePMDialog | `components/booking/InvoicePMDialog.tsx` | PREVIEW fattura PM |
| ReceiptOwnerDialog | `components/booking/ReceiptOwnerDialog.tsx` | PREVIEW ricevuta owner |
| TenantDashboard | `pages/tenant/TenantDashboard.tsx` | KPI documenti pending |

### Dati visualizzati

| Campo | Tipo | Dove |
|-------|------|------|
| `document_id` | string | DocumentsList |
| `tenant_id` | string | (filtro) |
| `booking_id` | string | (FK) |
| `document_type` | `fattura` \| `ricevuta` \| `nota_credito` | DocumentsList (badge) |
| `document_number` | string | DocumentsList (monospace) |
| `issue_date` | YYYY-MM-DD | DocumentsList |
| `recipient_name` | string | DocumentsList |
| `total_amount` | number | DocumentsList (€, text-right) |
| `vat_amount` | number | (dettaglio fattura) |
| `status` | DocumentStatus | DocumentsList (badge colore) |
| `sdi_status` | string? | `RC` (Ricevuta Consegna) o `MC` (Mancata Consegna) |
| `sdi_identifier` | string? | (interno) |
| `property_name` | string | DocumentsList |
| `channel_name` | string | DocumentsList |

**InvoicePMDialog — dettaglio righe visualizzate:**
- Riaddebito commissione OTA (+ IVA 22%)
- Riaddebito pulizie (+ IVA 22%)
- Provvigione PM (+ IVA 22%)
- Totale imponibile e totale IVA

### Operazioni

- **LIST**: search per `document_number`, `recipient_name`; filter per `status`
- **PREVIEW FATTURA**: dialog A4 con dati intestazione, righe, totali IVA; pulsante Stampa e Invia SDI
- **PREVIEW RICEVUTA**: dialog senza IVA; pulsante Stampa

### Campi nei form

Nessun form di inserimento manuale — i documenti sono generati automaticamente da booking.

### Mock usati

~40 documenti: 20 fatture (`doc-ft-N`, `FT-2025-NNNN`) + 20 ricevute (`doc-ric-N`, `RIC-2025-NNNN`).

Generazione per ogni booking eligible:
```
fattura: total = (OTA + cleaning + PM) * 1.22
ricevuta: total = gross_amount, vat = 0
sdi_status: 'RC' se accepted, 'MC' se sent_sdi, undefined altrimenti
```

### Note per il Controller

- `GET /api/documents` — con params `status`, `q`, `page`
- `GET /api/documents/{id}` — dettaglio con righe di dettaglio
- `POST /api/bookings/{bookingId}/documents/fattura` — genera fattura PM
- `POST /api/bookings/{bookingId}/documents/ricevuta` — genera ricevuta owner
- `POST /api/documents/{id}/send-sdi` — invio al Sistema di Interscambio
- Il dialog InvoicePMDialog riceve `booking`, `owner`, `property`, `tenantData` come props — non fa una chiamata GET separata; il Controller che serve BookingDetail deve restituire tutti questi dati aggregati

---

## Settlement (Liquidazione)

### Pagine coinvolte

| Pagina | File | Operazione |
|--------|------|------------|
| SettlementsList | `pages/tenant/SettlementsList.tsx` | LIST |
| OwnerSettlements | `pages/owner/OwnerSettlements.tsx` | LIST filtrate per owner |
| OwnerDetail | `pages/tenant/OwnerDetail.tsx` | count liquidazioni |
| TenantDashboard | `pages/tenant/TenantDashboard.tsx` | KPI |

### Dati visualizzati

| Campo | Tipo | Dove |
|-------|------|------|
| `settlement_id` | string | SettlementsList |
| `tenant_id` | string | (filtro) |
| `owner_id` | string | (FK) |
| `owner_name` | string | SettlementsList |
| `period` | YYYY-MM | SettlementsList, OwnerSettlements |
| `total_amount` | number | SettlementsList (€) |
| `withholding_amount` | number | SettlementsList (`text-destructive`) |
| `net_amount` | number | SettlementsList, OwnerSettlements (bold) |
| `bookings_count` | number | SettlementsList |
| `status` | SettlementStatus | SettlementsList, OwnerSettlements (badge) |
| `payment_date` | YYYY-MM-DD? | OwnerSettlements (se pagato) |
| `created_at` | YYYY-MM-DD | (interno) |

### Operazioni

- **LIST (Tenant)**: tabella semplice, nessun search/filter nel mock
- **LIST (Owner)**: card layout con periodo, count booking, importi, payment_date
- **STATUS UPDATE**: pending → calculated → approved → paid (pulsanti di approvazione non visibili nel mock ma implicitamente necessari)

### Campi nei form

Nessun form — il settlement è calcolato automaticamente dal sistema.

### Mock usati

9 settlement: 3 owner × 3 periodi (2025-01, 2025-02, 2025-03).

```
status: i==0 → 'paid', i==1 → 'approved', i==2 → 'pending'
total_amount = SUM(owner_net_amount) dei primi (3+ownerIndex) booking
withholding_amount = total * 0.21
net_amount = total - withholding
```

### Note per il Controller

- `GET /api/settlements` — lista del tenant corrente (con filtro opzionale `?owner_id=`, `?period=`)
- `GET /api/owners/{id}/settlements` — vista owner
- `PATCH /api/settlements/{id}/status` body `{ "status": "approved" }`
- `POST /api/settlements/calculate?period=2025-03` — ricalcolo per periodo
- Il campo `net_amount` è derivato (`total - withholding`); può essere calcolato lato backend o frontend

---

## F24

### Pagine coinvolte

| Pagina | File | Operazione |
|--------|------|------------|
| F24List | `pages/tenant/F24List.tsx` | LIST, PREVIEW |
| F24PreviewDialog | `components/f24/F24PreviewDialog.tsx` | DISPLAY modulo compilato |
| AuditLog | `pages/tenant/AuditLog.tsx` | action `f24.generate` |
| TenantDashboard | `pages/tenant/TenantDashboard.tsx` | KPI F24 da generare |

### Dati visualizzati

| Campo | Tipo | Dove |
|-------|------|------|
| `f24_id` | string | F24List |
| `tenant_id` | string | (filtro) |
| `period` | YYYY-MM | F24List, F24PreviewDialog |
| `tax_code` | string | F24List (sempre `1919`) |
| `total_amount` | number | F24List, F24PreviewDialog |
| `withholdings_count` | number | F24List |
| `status` | F24Status | F24List (badge) |
| `deadline_date` | YYYY-MM-DD | F24List |
| `payment_date` | YYYY-MM-DD? | (se pagato) |
| `created_at` | YYYY-MM-DD | (interno) |

**F24PreviewDialog — sezioni visualizzate:**
- **CONTRIBUENTE**: 16 box singola cifra del `tax_code` tenant + denominazione + nome
- **VERSAMENTI**: codice tributo `1919`, importo `total_amount`, causale periodo

### Operazioni

- **LIST**: tabella; click occhio → F24PreviewDialog; download button (mock)
- **PREVIEW**: form F24 compilato; pulsante Stampa (`window.print()`); pulsante Scarica (mock)

### Campi nei form

Nessun form — l'F24 è generato automaticamente dal sistema.

### Mock usati

```json
[
  { "f24_id": "f24-1", "period": "2025-01", "tax_code": "1919", "total_amount": 2345.67, "withholdings_count": 8, "status": "paid", "deadline_date": "2025-02-16", "payment_date": "2025-02-14" },
  { "f24_id": "f24-2", "period": "2025-02", "tax_code": "1919", "total_amount": 1876.34, "withholdings_count": 6, "status": "sent", "deadline_date": "2025-03-17" },
  { "f24_id": "f24-3", "period": "2025-03", "tax_code": "1919", "total_amount": 2100.00, "withholdings_count": 7, "status": "ready", "deadline_date": "2025-04-16" }
]
```

### Note per il Controller

- `GET /api/f24` — lista F24 del tenant corrente
- `GET /api/f24/{id}` — dettaglio (per F24PreviewDialog)
- `POST /api/f24/generate?period=2025-03` — genera F24 dal periodo
- `PATCH /api/f24/{id}/status` body `{ "status": "sent" }`
- Il codice tributo `1919` è fisso — non viene scelto dall'utente
- La `deadline_date` è sempre il 16 del mese successivo al `period`

---

## CU (Certificazione Unica)

### Pagine coinvolte

| Pagina | File | Operazione |
|--------|------|------------|
| CUList | `pages/tenant/CUList.tsx` | LIST (vista tenant) |
| OwnerCU | `pages/owner/OwnerCU.tsx` | LIST filtrate per owner |
| TenantDashboard | `pages/tenant/TenantDashboard.tsx` | KPI |

### Dati visualizzati

| Campo | Tipo | Dove |
|-------|------|------|
| `cu_id` | string | CUList, OwnerCU |
| `tenant_id` | string | (filtro) |
| `owner_id` | string | (FK) |
| `owner_name` | string | CUList, OwnerCU |
| `tax_year` | number | CUList, OwnerCU |
| `total_compensi` | number | CUList, OwnerCU (€) |
| `total_ritenute` | number | CUList, OwnerCU (€) |
| `status` | CUStatus | CUList, OwnerCU (badge) |
| `generated_at` | YYYY-MM-DD? | (se generated/sent) |
| `created_at` | YYYY-MM-DD | (interno) |

### Operazioni

- **LIST (Tenant)**: tabella; occhio (mock); download (mock); pulsante "Genera CU"
- **LIST (Owner)**: card; pulsante "Scarica" (disabilitato se draft)
- **GENERA**: `POST /api/cu/generate?year=2024` (pulsante presente, non implementato nel mock)

### Campi nei form

Nessun form — la CU è generata automaticamente dal sistema su base annuale.

### Mock usati

```json
[
  { "cu_id": "cu1", "owner_id": "o1", "owner_name": "Anna Moretti", "tax_year": 2024, "total_compensi": 15000, "total_ritenute": 3150, "status": "sent", "generated_at": "2025-02-28" },
  { "cu_id": "cu2", "owner_id": "o2", "owner_name": "Marco Bianchi", "tax_year": 2024, "total_compensi": 8500, "total_ritenute": 1785, "status": "generated", "generated_at": "2025-02-28" },
  { "cu_id": "cu3", "owner_id": "o3", "owner_name": "Luigi Ferrari", "tax_year": 2024, "total_compensi": 22000, "total_ritenute": 4620, "status": "draft" }
]
```

### Note per il Controller

- `GET /api/cu` — lista CU del tenant corrente
- `GET /api/owners/{id}/cu` — vista owner
- `POST /api/cu/generate?year=2024` — genera tutte le CU per l'anno
- `PATCH /api/cu/{id}/status` body `{ "status": "sent" }`
- `GET /api/cu/{id}/pdf` — download PDF (da implementare)
- **Calcolo**: `total_compensi = SUM(gross_amount)` per owner nell'anno; `total_ritenute = SUM(withholding_amount)` da settlement nell'anno

---

## Utente (User)

### Pagine coinvolte

| Pagina | File | Operazione |
|--------|------|------------|
| UsersList | `pages/tenant/UsersList.tsx` | LIST, INVITE, DELETE |

### Dati visualizzati

| Campo | Tipo | Dove |
|-------|------|------|
| `user_id` | string | UsersList |
| `tenant_id` | string | (filtro) |
| `first_name` | string | UsersList |
| `last_name` | string | UsersList |
| `email` | string | UsersList |
| `role` | UserRole | UsersList (badge) |
| `scope` | string[] | UsersList (lista immobili assegnati) |
| `status` | `active` \| `inactive` | UsersList (badge) |

### Operazioni

- **LIST**: tabella nome, email, ruolo, scope (immobili), stato
- **INVITE**: pulsante "Invita Utente" (mock)
- **DELETE**: icona cestino per utente

### Campi nei form

**Invite form** (non completamente visibile nel mock):
- Email, Ruolo (select: `tenant_admin`, `pm_user`, `owner_user`), Scope (multi-select immobili)

### Mock usati

Struttura inferita dal codice pagina (nessun mock dedicato visibile).

### Note per il Controller

- `GET /api/users` — lista utenti del tenant
- `POST /api/users/invite` body `{ email, role, scope[] }`
- `DELETE /api/users/{id}`
- I ruoli `super_admin` e `tenant_admin` hanno scope globale; `pm_user` e `owner_user` hanno scope limitato a immobili specifici

---

## Entità Ausiliarie

### OTAChannel (OTA Registry)

**Pagina:** `pages/tenant/OTARegistry.tsx`

| Campo | Tipo | Note |
|-------|------|------|
| `ota_id` | string | |
| `name` | string | es. `Airbnb`, `Booking.com` |
| `commission_default_pct` | number | commissione % default |
| `tourist_tax_included` | boolean | tassa inclusa nel pagamento OTA |
| `tourist_tax_collection` | `contanti` \| `payment_link` \| `altro` | |
| `status` | `active` \| `inactive` | |

**API:** `GET/POST/PUT/DELETE /api/ota-channels`

### TouristTaxRule (Tassa di Soggiorno)

**Pagina:** `pages/tenant/TouristTaxSettings.tsx`

Configurazione per municipio con:
- `city`, `base_rate`, `max_nights_per_stay`
- Riduzioni per fascia età, stagione, zona
- Esempi mock: Venezia (5€/notte max 5 notti), Roma, Firenze, Napoli, Genova

**API:** `GET/PUT /api/tourist-tax-rules`

### AuditLogEntry

**Pagina:** `pages/tenant/AuditLog.tsx`

| Campo | Tipo | Note |
|-------|------|------|
| `log_id` | string | |
| `tenant_id` | string? | opzionale per azioni super_admin |
| `user_email` | string | |
| `action` | string | `booking.import`, `document.issue`, `settlement.approve`, `f24.generate`, `owner.create`, `tenant.create`, `tenant.suspend`, `document.choose_ricevuta` |
| `entity_type` | string | `Booking`, `FiscalDocument`, `Settlement`, `F24`, `Owner`, `Tenant` |
| `entity_id` | string | |
| `details` | string | descrizione libera |
| `ip_address` | string | |
| `created_at` | ISO 8601 | `2025-03-15T10:30:00` |

**Operazioni:** search in `details`+`user_email`; filter per prefisso action.

**API:** `GET /api/audit-log?q=&action=&page=`

---

## Riepilogo Route → Controller

| Route | Controller Java | Metodo HTTP | Note |
|-------|----------------|-------------|------|
| `/bookings` (lista) | `BookingController` | `GET /api/bookings` | params: status, channel, q, page |
| `/bookings/:id` | `BookingController` | `GET /api/bookings/{id}` | include split economico |
| `/bookings/import` | `BookingController` | `POST /api/bookings/import` | multipart/form-data |
| `/bookings/reconciliation` | `BookingController` | `GET /api/bookings/reconciliation` | |
| `/properties` | `PropertyController` | `GET /api/properties` | |
| `/properties/new` | `PropertyController` | `POST /api/properties` | |
| `/properties/:id` | `PropertyController` | `GET /api/properties/{id}` | |
| `/properties/:id/contracts` | `PropertyController` | `GET/PUT /api/properties/{id}/contracts` | |
| `/owners` | `OwnerController` | `GET /api/owners` | |
| `/owners/:id` | `OwnerController` | `GET /api/owners/{id}` | include campi calcolati |
| `/documents` | `DocumentController` | `GET /api/documents` | params: status, q |
| `/settlements` | `SettlementController` | `GET /api/settlements` | |
| `/f24` | `F24Controller` | `GET /api/f24` | |
| `/cu` | `CUController` | `GET /api/cu` | |
| `/audit` | `AuditLogController` | `GET /api/audit-log` | |
| `/admin/tenants` | `TenantController` | `GET /api/admin/tenants` | solo super_admin |

---

## Flussi Dati tra Pagine

### Route Params (useParams)

```
/bookings/:id       → booking_id
/properties/:id     → property_id
/properties/:id/contracts → property_id
/owners/:id         → owner_id
```

### Props dialogs

**InvoicePMDialog:**
```typescript
{
  open: boolean
  onOpenChange: (open: boolean) => void
  booking: Booking
  owner?: OwnerProfile
  property?: Property
  tenantData: { legal_name, vat_number, tax_code, address, pec }
}
```

**F24PreviewDialog:**
```typescript
{
  f24: F24Record | null
  open: boolean
  onOpenChange: (open: boolean) => void
}
```

### Pattern Denormalizzazione (da risolvere nel backend)

Il frontend usa stringhe denormalizzate per evitare JOIN nel mock:
- `booking.owner_name` = `owner.first_name + ' ' + owner.last_name`
- `booking.property_name` = `property.display_name`
- `settlement.owner_name` = stringa
- `document.property_name` = stringa

**Il backend deve restituire questi campi denormalizzati** nelle risposte lista per evitare N+1 query lato frontend.
