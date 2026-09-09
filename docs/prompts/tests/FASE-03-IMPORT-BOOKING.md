Leggi il file CLAUDE.md prima di procedere.
Leggi i file esistenti:
- frontend/tests/e2e/fase01-tenant-onboarding.spec.ts
- frontend/tests/e2e/fase02-anagrafica.spec.ts
- frontend/tests/e2e/helpers/auth.ts
- frontend/tests/e2e/helpers/api.ts
- frontend/tests/e2e/helpers/form.ts
  prima di procedere.

Crea il test E2E Fase 03 — Import Booking:
wizard di importazione prenotazioni
con file Excel, verifica anagrafica ospite,
CF calcolato e CF da file.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. FILE EXCEL DI TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/tests/e2e/fixtures/
bookings-fase03.xlsx e
ospiti-fase03.xlsx
usando gli stessi file già presenti in
src/test/resources/import/
(booking-marco-bianchi.xlsx e
ospiti-marco-bianchi.xlsx)
come riferimento per la struttura.

Crea i file con due prenotazioni:

PRENOTAZIONE 1 — CF calcolato automaticamente:
Id: E2E-CALC-{timestamp}
Stato: CONFIRMADA
Struttura: Ca' Serenella
Importo totale: 380.00
Adulti: 2
Arrivo: primo giorno mese prossimo
Partenza: arrivo + 4 notti
Origine: Booking.com
Cliente: Anna Verdi

OSPITE 1 (CF calcolato):
Id: E2E-CALC-{timestamp}
Nome: Anna
Cognome: Verdi
Data nascita: 15/03/1985
Sesso: Donna
Documento: Carta di identità
Comune emittente: Roma
Comune: Roma
Provincia: RM
Nazione: 100000100

PRENOTAZIONE 2 — CF da file:
Id: E2E-FILE-{timestamp}
Stato: CONFIRMADA
Struttura: Ca' Serenella
Importo totale: 520.00
Adulti: 1
Arrivo: primo giorno mese prossimo + 10
Partenza: arrivo + 7 notti
Origine: Booking.com
Cliente: Marco Bianchi

OSPITE 2 (CF da file):
Id: E2E-FILE-{timestamp}
Nome: Marco
Cognome: Bianchi
Codice Fiscale: BNCMRC80A01H501U
Data nascita: (vuota)
Comune nascita: (vuoto)
Nazione: 100000100

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. TEST FILE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/tests/e2e/
fase03-import-booking.spec.ts

const SUFFIX = 'E2E-' +
Date.now().toString().slice(-6)

Sostituisci {timestamp} nei file Excel
con il SUFFIX prima di usarli nel test
(o usa file statici con ID fissi
se più semplice — il cleanup usa
external_booking_id LIKE 'E2E-%').

test.beforeAll → getToken tenantAdmin
test.afterAll → cleanup booking E2E:
DELETE /api/test/cleanup-bookings
{ externalIdPattern: 'E2E-%' }
(vedi punto 3)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TEST CASES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

3.1 — Login come tenant_admin
→ verifica redirect /dashboard

3.2 — Naviga a import prenotazioni
→ clicca pulsante "Import" in
BookingsList
→ verifica wizard visibile
(step 1 caricamento file)

3.3 — Carica file Excel
→ step 1: carica bookings-fase03.xlsx
come file prenotazioni
→ carica ospiti-fase03.xlsx
come file ospiti
→ clicca "Avanti" / "Continua"
→ verifica step 2 (mapping colonne)

3.4 — Mapping colonne
→ verifica che il mapping automatico
abbia riconosciuto le colonne
principali (Id, Struttura, Importo,
Arrivo, Partenza, Origine, Cliente)
→ clicca "Avanti"
→ verifica step 3 (anteprima)

3.5 — Anteprima prenotazioni
→ verifica che compaiano
2 prenotazioni in anteprima
→ verifica stato "nuova" per entrambe
→ verifica nessun errore
→ verifica che E2E-CALC e E2E-FILE
siano visibili nella lista

3.6 — Conferma import
→ seleziona tutte le prenotazioni
(checkbox "seleziona tutto")
→ clicca "Conferma import"
→ verifica messaggio di successo:
"importate: 2"
→ verifica redirect a BookingsList
o chiusura wizard

3.7 — Verifica booking in lista
→ naviga a /bookings
→ cerca "E2E-CALC" nella search bar
→ verifica che la prenotazione
compaia in lista con:
- ospite "Anna Verdi"
- stato "Importata" o "Arricchita"

3.8 — Verifica CF calcolato
→ apri dettaglio booking E2E-CALC
→ clicca su modifica anagrafica ospite
(icona matita o pulsante)
→ verifica che CF sia valorizzato
(non vuoto)
→ verifica che CF sia lungo 16 caratteri
→ verifica data nascita 15/03/1985
→ verifica comune nascita Roma

3.9 — Verifica CF da file
→ cerca "E2E-FILE" in BookingsList
→ apri dettaglio
→ verifica CF = "BNCMRC80A01H501U"
(quello passato nel file)
→ verifica che data nascita
sia vuota (non calcolata)

3.10 — Verifica duplicato bloccato
→ tenta di importare di nuovo
lo stesso file bookings-fase03.xlsx
→ nella anteprima verifica che
le prenotazioni abbiano stato
"duplicata" e non "nuova"
→ NON confermare — chiudi il wizard

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — cleanup bookings
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a TestRunnerController.java:

DELETE /api/test/cleanup-bookings
@RequestBody con:
String externalIdPattern
← es. "E2E-%"

Logica in TestCleanupService:
Elimina in ordine (rispettando FK):
1. sdi_progressivo non toccare
2. audit_log WHERE entity_id IN
   (SELECT id FROM booking
   WHERE external_booking_id
   LIKE ?)
3. fiscal_document WHERE fk_booking_id IN
   (SELECT id FROM booking
   WHERE external_booking_id LIKE ?)
   Prima: withholding_ledger
   WHERE fk_booking_id IN (...)
   Prima: settlement_booking
   WHERE fk_booking_id IN (...)
4. booking WHERE external_booking_id
   LIKE ?
   AND fk_tenant_id =
   SecurityUtils.getCurrentTenantId()
   (protezione: solo booking del tenant
   chiamante e solo con pattern E2E-)

Restituisce Map con righe eliminate
per tabella.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. ESECUZIONE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Assicurati che siano attivi:
- Tomcat su :8081
- Vite su :5173

mvn -Plocal -DskipTests clean package

cd frontend
npx playwright test \
tests/e2e/fase03-import-booking.spec.ts

Riporta output con ✅/❌ per ogni test.
Se un test fallisce riporta:
- Screenshot del fallimento
- Messaggio di errore Playwright
- Elemento non trovato
  NON correggere senza aspettare istruzioni.