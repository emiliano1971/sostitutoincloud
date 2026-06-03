Leggi il file CLAUDE.md prima di procedere.

Implementa due funzionalità:
1. Blocco login per tenant sospeso
2. Modifica dati tenant dal super_admin

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BLOCCO LOGIN TENANT SOSPESO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica config/DatabaseUserDetailsService.java:

Nel metodo loadUserByUsername(String email):
- Dopo aver caricato l'utente dal DB e verificato
  attivo=true, aggiungi questo controllo:
    * Se utente.fkTenantId != null (non è super_admin):
      carica il tenant con una query diretta:
      SELECT stato FROM tenant WHERE id = ?
    * Se stato = 'suspended':
      lancia DisabledException con messaggio:
      "Tenant sospeso — contatta l'amministratore
      di sistema per riattivare l'account"
    * Se stato = 'draft':
      lancia DisabledException con messaggio:
      "Account non ancora attivato —
      contatta l'amministratore di sistema"
    * super_admin (fkTenantId = NULL)
      non viene bloccato mai

In controller/AuthController.java
nel metodo login():
- Aggiungi catch per DisabledException
  PRIMA del catch generico:
  catch (DisabledException e) {
  log.warn("Login bloccato - tenant sospeso: {}",
  loginRequest.getEmail());
  return ResponseEntity.status(401)
  .body(Map.of("error", e.getMessage()));
  }

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. MODIFICA DATI TENANT — BACKEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/tenant/TenantUpdateDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- Stessi campi di TenantCreateDTO ma tutti opzionali:
    * String legalName
    * String displayName
    * String taxCode
    * String vatNumber
    * String administrativeEmail
    * String pec
    * String phone
    * String legalAddress

Aggiungi a service/TenantService.java:

TenantDetailDTO update(Integer id,
TenantUpdateDTO dto)
- Verifica esistenza tenant
- Se dto.taxCode è cambiato: verifica che non
  esista già per un altro tenant
  lancia IllegalArgumentException se duplicato
- Aggiorna i campi non null del DTO
  sull'oggetto Tenant esistente
- Chiama tenantDAO.update(tenant)
- Ricarica e mappa su TenantDetailDTO
- Log INFO: "TenantService.update() - id={}"

Aggiungi a controller/TenantController.java:

PUT /api/admin/tenants/{id}
- @RequestBody TenantUpdateDTO
- chiama tenantService.update(id, dto)
- ResponseEntity.ok(result)
- gestisce IllegalArgumentException → 400

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. MODIFICA DATI TENANT — FRONTEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/tenantApi.ts:

```ts
export interface TenantUpdateRequest {
  legalName?: string;
  displayName?: string;
  taxCode?: string;
  vatNumber?: string;
  administrativeEmail?: string;
  pec?: string;
  phone?: string;
  legalAddress?: string;
}

export async function updateTenant(
  id: number,
  data: TenantUpdateRequest
): Promise<TenantDetail>
// PUT /api/admin/tenants/{id}
```

Modifica frontend/src/pages/admin/TenantDetail.tsx:
- Aggiungi pulsante "Modifica Dati" nella card
  Anagrafica (in alto a destra del CardHeader)
- Aggiungi dialog di modifica con gli stessi campi
  dell'anagrafica pre-popolati con i valori attuali
- Stati: showEdit, editForm, isSaving, editError
- handleSaveEdit():
    * Valida legalName, displayName,
      administrativeEmail, legalAddress obbligatori
    * taxCode 16 char se presente
    * Chiama updateTenant(id, editForm)
    * Successo: aggiorna stato locale, chiudi dialog,
      toast "Tenant aggiornato"
    * Errore 400: mostra messaggio nel dialog

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND — messaggio login bloccato
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In frontend/src/contexts/AuthContext.tsx
nel metodo login():
- Il backend restituisce 401 con body JSON
  { "error": "Tenant sospeso — ..." }
- Assicurati che apiClient.ts estragga il messaggio
  dal body JSON per gli errori 401
  (attualmente il 401 lancia Error('UNAUTHORIZED')
  senza il messaggio — va migliorato)
- Modifica handleResponse in apiClient.ts:
    * Per status 401: leggi il body JSON
      se ha campo "error" → lancia Error(body.error)
      altrimenti → lancia Error('UNAUTHORIZED')
- Il Login.tsx mostrerà automaticamente il messaggio
  dettagliato nel form di errore

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima lancia il build backend:
mvn -Plocal clean package

Riavvia Tomcat e testa il blocco login:
curl -s -X POST \
http://localhost:8081/sostitutoincloud/api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena"}' \
| python3 -m json.tool

(il tenant 1 è sospeso dal test precedente —
deve restituire 401 con messaggio esplicito)

Poi riattiva il tenant 1 con il super_admin:
curl -s -X PATCH \
-H "Authorization: Bearer $TOKEN_SUPER" \
-H "Content-Type: application/json" \
-d '{"stato":"active"}' \
http://localhost:8081/sostitutoincloud/api/admin/tenants/1/status \
| python3 -m json.tool

Verifica che il login torni a funzionare.

Poi:
cd frontend && npm run build

Riporta output di tutti i test.