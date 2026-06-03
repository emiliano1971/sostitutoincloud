Leggi il file CLAUDE.md prima di procedere.
Leggi frontend/src/pages/tenant/OwnerDetail.tsx
per capire lo stile esistente prima di modificarlo.

Aggiungi il dialog di modifica anagrafica in OwnerDetail.tsx.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. AGGIORNA ownerApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/ownerApi.ts:

```ts
export interface OwnerUpdateRequest {
  ownerType?: string;
  firstName?: string;
  lastName?: string;
  legalName?: string;
  taxCode?: string;
  vatNumber?: string;
  fkRegimeFiscaleId?: number;
  email?: string;
  phone?: string;
  iban?: string;
}

export async function updateOwner(
  id: number,
  data: OwnerUpdateRequest
): Promise<OwnerDetail>
// PUT /api/owners/{id}
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. AGGIORNA OwnerDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi un dialog di modifica anagrafica con
questi campi (pre-popolati con i valori attuali):

Sezione "Anagrafica":
- firstName (Input, obbligatorio)
- lastName (Input)
- legalName (Input, visibile solo se piva/societa)
- taxCode (Input, 16 char, obbligatorio)
- vatNumber (Input, visibile solo se piva/societa)

Sezione "Contatti & IBAN":
- email (Input email, obbligatorio)
- phone (Input)
- iban (Input)

Sezione "Fiscale":
- fiscalRegime (Select con opzioni hardcoded):
    * cedolare_secca → "Cedolare Secca"
    * iva_10 → "IVA 10%"
    * ordinario → "Ordinario"
      (mappa il codice a fkRegimeFiscaleId:
      cedolare_secca=1, iva_10=2, ordinario=3)

Aggiungi alla card "Azioni" esistente un pulsante
"Modifica Dati" con icona Edit — accanto al pulsante
Disattiva/Riattiva già presente.

Aggiungi stato:
- showEdit: boolean
- editForm: OwnerUpdateRequest (inizializzato con
  i valori attuali dell'owner al click su "Modifica")
- isSaving: boolean

handleSave nel dialog:
- Valida che firstName e taxCode (16 char) e email
  siano compilati
- Chiama updateOwner(owner.id, editForm)
- Successo: setOwner(updated), chiudi dialog, toast
  "Proprietario aggiornato"
- Errore 400: mostra messaggio dal server nel dialog
- Errore generico: toast errore

Mantieni TUTTO il layout e UI esistente —
aggiungi solo il pulsante e il dialog.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

cd frontend && npm run build

Riporta eventuali errori TypeScript e il risultato.