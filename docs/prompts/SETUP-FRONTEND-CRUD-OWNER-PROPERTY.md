Leggi il file CLAUDE.md e docs/analisi-frontend.md
prima di procedere.

Collega le operazioni di scrittura al frontend per
owner e property. Il backend è già pronto.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. ANALISI PRELIMINARE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima leggi:
- frontend/src/pages/tenant/OwnersList.tsx
- frontend/src/pages/tenant/OwnerDetail.tsx
- frontend/src/pages/tenant/PropertyCreate.tsx
- frontend/src/pages/tenant/PropertyDetail.tsx

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. AGGIORNA ownerApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/ownerApi.ts:

```ts
export interface OwnerCreateRequest {
  ownerType: string;
  firstName: string;
  lastName: string;
  taxCode: string;
  email: string;
  legalName?: string;
  vatNumber?: string;
  fkRegimeFiscaleId?: number;
  phone?: string;
  iban?: string;
}

export async function createOwner(
  data: OwnerCreateRequest
): Promise<OwnerDetail>
// POST /api/owners → 201

export async function updateOwnerStatus(
  id: number,
  attivo: boolean
): Promise<OwnerDetail>
// PATCH /api/owners/{id}/status
// body: { attivo }
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. AGGIORNA propertyApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/propertyApi.ts:

```ts
export interface PropertyCreateRequest {
  displayName: string;
  internalCode: string;
  propertyType?: string;
  address?: string;
  city: string;
  region?: string;
  cinCode?: string;
  fkOwnerId?: number;
  otaCodes?: { canaleCodiceName: string; externalId: string }[];
}

export async function createProperty(
  data: PropertyCreateRequest
): Promise<PropertyDetail>
// POST /api/properties → 201

export async function updatePropertyStatus(
  id: number,
  attivo: boolean
): Promise<PropertyDetail>
// PATCH /api/properties/{id}/status

export async function updatePropertyOwner(
  id: number,
  fkOwnerId: number
): Promise<PropertyDetail>
// PUT /api/properties/{id}/owner
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. CREA OwnerCreate.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/pages/tenant/OwnerCreate.tsx:
- Mantieni stile coerente con PropertyCreate.tsx
- Form con questi campi:

  Sezione "Anagrafica":
    * ownerType: Select (persona_fisica/piva/societa)
    * firstName: Input (obbligatorio)
    * lastName: Input (obbligatorio se persona_fisica)
    * legalName: Input (obbligatorio se piva/societa)
    * taxCode: Input 16 char (obbligatorio)
    * vatNumber: Input 11 char (opzionale)

  Sezione "Contatti":
    * email: Input email (obbligatorio)
    * phone: Input
    * iban: Input

  Sezione "Fiscale":
    * fkRegimeFiscaleId: Select
      (carica da GET /api/lookup/regime-fiscale —
      se non esiste usa valori hardcoded:
      1=cedolare_secca, 2=iva_10, 3=ordinario)

- Validazione:
    * campi obbligatori non vuoti
    * taxCode esattamente 16 caratteri
    * email formato valido

- Al submit: chiama createOwner(data)
    * successo → navigate('/owners') con toast
    * errore 400 → mostra messaggio errore dal server
    * errore generico → toast errore generico

- Pulsante "Annulla" → navigate('/owners')

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. AGGIORNA OwnersList.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In OwnersList.tsx aggiungi onClick al bottone
"Nuovo Proprietario":
- navigate('/owners/new')

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. AGGIORNA OwnerDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In OwnerDetail.tsx sostituisci handleToggleStatus:
- Chiama updateOwnerStatus(id, !owner.attivo)
- Successo: ricarica owner con getOwnerById(id)
  e aggiorna stato locale
- Errore: mostra toast errore

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. AGGIORNA PropertyCreate.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In PropertyCreate.tsx:
- Sostituisci mockOwners con getOwners(true)
  nella select proprietari
- Sostituisci handleSave mock con createProperty():
    * mappa i campi del form su PropertyCreateRequest
    * i codici OTA vanno mappati come array:
      [{ canaleCodiceName: "airbnb", externalId: airbnb_id },...]
      (solo quelli non vuoti)
    * successo → navigate('/properties') con toast
    * errore 400 → mostra messaggio dal server
    * errore generico → toast errore

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. AGGIORNA PropertyDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In PropertyDetail.tsx:

handleDeactivate:
- Chiama updatePropertyStatus(id, !property.attivo)
- Successo: ricarica property con getPropertyById(id)
- Errore: toast errore

handleAssignOwner:
- Chiama updatePropertyOwner(id, selectedOwnerId)
- Successo: ricarica property con getPropertyById(id)
  e chiudi dialog
- Errore: toast errore

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
9. AGGIORNA App.tsx — aggiungi route
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In frontend/src/App.tsx aggiungi la route:
<Route path="/owners/new" element={<OwnerCreate />} />
Posizionala prima di <Route path="/owners/:id" .../>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
10. BUILD E TEST
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che il frontend compili:
cd frontend && npm run build

Riporta eventuali errori TypeScript e il risultato.