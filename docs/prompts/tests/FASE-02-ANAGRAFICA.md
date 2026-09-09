Leggi il file CLAUDE.md prima di procedere.
Leggi i file esistenti:
- frontend/tests/e2e/fase01-tenant-onboarding.spec.ts
- frontend/tests/e2e/helpers/auth.ts
- frontend/tests/e2e/helpers/api.ts
- frontend/tests/e2e/helpers/form.ts
  prima di procedere.

Crea il test E2E Fase 02 — Anagrafica:
creazione proprietario, immobile,
associazione e configurazione contratto.

I test girano come tenant_admin
(admin@casavacanze.it / atena2026)
sul tenant esistente (Casa Vacanze Italia).
NON creare un nuovo tenant — usa quello
già esistente per semplicità.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. TEST FILE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/tests/e2e/
fase02-anagrafica.spec.ts

Suffisso univoco per i dati di test:
const SUFFIX = 'E2E-' +
Date.now().toString().slice(-6)

Dati proprietario di test:
const OWNER = {
firstName: 'Giovanni',
lastName: `Verdi${SUFFIX}`,
taxCode: 'VRDGNN80A01H501Z',
iban: 'IT60X0542811101000000123456',
email: `giovanni.verdi.${SUFFIX}@test.it`,
phone: '3331234567'
}

Dati immobile di test:
const PROPERTY = {
name: `Appartamento ${SUFFIX}`,
internalCode: `APP-${SUFFIX}`,
city: 'Roma',
address: 'Via Test 123'
}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TEST CASES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

test.beforeAll → login come tenantAdmin
e salva token per cleanup

test.afterAll → cleanup via API:
DELETE /api/owners/{ownerId}
se esiste (verifica prima con GET)
DELETE /api/properties/{propertyId}
se esiste

Oppure se non esistono endpoint DELETE,
usa SQL diretto tramite endpoint
/api/test/cleanup-anagrafica
(vedi punto 2)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2.1 — Login come tenant_admin
→ verifica redirect a /dashboard

2.2 — Naviga a lista proprietari
→ verifica heading "Proprietari"

2.3 — Crea nuovo proprietario
→ /owners/new o pulsante
"+ Nuovo Proprietario"
→ compila:
Tipo: Persona Fisica
Nome: OWNER.firstName
Cognome: OWNER.lastName
CF: OWNER.taxCode
IBAN: OWNER.iban
Email: OWNER.email
Telefono: OWNER.phone
→ salva
→ verifica che appaia in lista
→ salva ownerId dalla URL o dalla lista

2.4 — Verifica dettaglio proprietario
→ naviga a /owners/{ownerId}
→ verifica CF visibile
→ verifica IBAN visibile
→ verifica stato "Attivo"

2.5 — Crea nuovo immobile
→ /properties/new o pulsante
"+ Nuovo Immobile"
→ compila:
Nome display: PROPERTY.name
Codice interno: PROPERTY.internalCode
Città: PROPERTY.city (ComuneAutocomplete)
Indirizzo: PROPERTY.address
Proprietario: OWNER.lastName
(seleziona dal dropdown)
→ salva
→ verifica che appaia in lista
→ salva propertyId

2.6 — Verifica dettaglio immobile
→ naviga a /properties/{propertyId}
→ verifica nome immobile
→ verifica proprietario associato
→ verifica città

2.7 — Configura contratto immobile
→ naviga a
/properties/{propertyId}/contracts
→ verifica pagina contratti visibile
→ aggiungi regola "Pulizie":
Tipo voce: Pulizie Abitazione
Modalità: Importo Fisso
Valore: 50
→ salva
→ verifica che la regola appaia
nella lista

2.8 — Aggiungi codice OTA
→ nella pagina contratti o dettaglio
immobile aggiungi codice OTA
per Booking.com:
external_id = PROPERTY.name
→ verifica che il codice appaia

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — endpoint cleanup anagrafica
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Se non esistono endpoint DELETE per
owner e property, aggiungi a
controller/TestRunnerController.java
(già @Profile("local")):

DELETE /api/test/cleanup-anagrafica
@RequestBody con:
Integer ownerId (nullable)
Integer propertyId (nullable)

Logica:
- Se propertyId presente:
  elimina property_contract_rule
  WHERE fk_property_id = propertyId
  elimina property_ota_code
  WHERE fk_property_id = propertyId
  elimina property
  WHERE id = propertyId
  AND display_name LIKE '%E2E-%'
  (protezione anti-eliminazione reali)

- Se ownerId presente:
  elimina owner_profile
  WHERE id = ownerId
  AND last_name LIKE '%E2E-%'
  (protezione)

Restituisce Map con righe eliminate.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. ESECUZIONE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Assicurati che siano attivi:
- Tomcat su :8081
- Vite su :5173

cd frontend
npx playwright test \
tests/e2e/fase02-anagrafica.spec.ts

Riporta output con ✅/❌ per ogni test.
Se un test fallisce riporta:
- Screenshot del fallimento
- Messaggio di errore Playwright
- Elemento non trovato

NON correggere il codice frontend/backend
se un test fallisce — riporta prima
il fallimento e aspetta istruzioni.