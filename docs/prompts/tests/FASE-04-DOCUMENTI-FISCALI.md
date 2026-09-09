Leggi il file CLAUDE.md prima di procedere.
Leggi i file esistenti:
- frontend/tests/e2e/fase03-import-booking.spec.ts
- frontend/tests/e2e/helpers/auth.ts
- frontend/tests/e2e/helpers/api.ts
- frontend/tests/e2e/helpers/form.ts
  prima di procedere.

Crea il test E2E Fase 04 — Documenti Fiscali:
emissione ricevuta owner e fattura PM,
verifica importi, PDF e invio SDI.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
STRATEGIA
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Usa un booking esistente nel DB
(non creare nuovi booking in questa fase).

Le prenotazioni con prefisso SEED-
(da docs/db/seed-data.sql) sono preparate
per i casi limite — vanno escluse dalla
ricerca del booking di lavoro.

In beforeAll:
- Ottieni token tenantAdmin
- Chiama GET /api/bookings e trova
  il primo booking in stato 'ready'
  senza documenti emessi
  (statoDocumento != 'doc_issued')
- Salva bookingId e importi per
  le verifiche successive

Se non esiste un booking in stato ready:
- Cerca uno in stato 'enriched' o
  'imported' e aggiornalo via API
  oppure usa il booking 199 (BC001CC2026)
  che sappiamo essere in stato ready

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TEST CASES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

test.beforeAll:
- getToken tenantAdmin
- trova booking in stato ready
  via GET /api/bookings
- salva bookingId, grossAmount,
  ownerNetAmount, withholdingAmount

test.afterAll:
- se documenti emessi durante il test:
  DELETE /api/test/cleanup-documenti
  { bookingId: bookingId }
  (elimina fiscal_document e
  withholding_ledger del booking
  e riporta stato a 'ready')

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4.1 — Login come tenant_admin
→ verifica redirect /dashboard

4.2 — Apri dettaglio booking
→ naviga a /bookings/{bookingId}
→ verifica che lo split economico
sia visibile
→ verifica che le card documento
mostrino "Da emettere"

4.3 — Emetti ricevuta owner
→ clicca sulla card "Ricevuta Owner"
o sul pulsante di emissione
→ si apre dialog ReceiptOwnerDialog
→ verifica che il dialog mostri:
* nome proprietario
* canone lordo > 0
* ritenuta > 0
→ clicca "Emetti ricevuta"
→ verifica toast di successo
→ verifica che il dialog si chiuda

4.4 — Verifica ricevuta emessa
→ nella pagina dettaglio booking
→ verifica card "Ricevuta Owner"
con stato diverso da "Da emettere"
→ verifica numero documento
formato RIC-YYYY-NNNN

4.5 — Emetti fattura PM
→ clicca sulla card "Fattura PM"
→ si apre dialog InvoicePMDialog
→ verifica che il dialog mostri:
* ragione sociale PM
* importo totale > 0
* righe dettaglio (OTA, pulizie,
provvigione)
→ clicca "Emetti fattura"
→ verifica toast di successo

4.6 — Verifica fattura emessa
→ verifica card "Fattura PM"
con numero FT-YYYY-NNNN
→ verifica stato booking →
"Doc. emesso"

4.7 — Verifica importi ricevuta
→ naviga a /documents
→ cerca la ricevuta appena emessa
→ apri dettaglio
→ verifica sezione "Importi Fiscali":
* Canone lordo = ownerNetAmount
del booking
* Ritenuta > 0
* Netto a pagare =
canone - ritenuta

4.8 — Verifica importi fattura
→ trova fattura in /documents
→ apri dettaglio
→ verifica sezione "Importi Fiscali":
* Totale documento > 0
* IVA 22% > 0
* Imponibile = totale / 1.22

4.9 — Download PDF ricevuta
→ nel dettaglio ricevuta
→ clicca "Scarica PDF"
→ verifica che il download parta
(verifica evento download
con page.waitForEvent('download'))
→ verifica che il filename contenga
"RIC-"

4.10 — Download PDF fattura
→ nel dettaglio fattura
→ clicca "Scarica PDF"
→ verifica evento download
→ verifica filename contiene "FT-"

4.11 — Invia SDI (se sdi_auto_send=false)
→ nel dettaglio fattura
→ se presente pulsante "Invia SDI":
clicca
verifica toast con progressivo SDI
verifica badge "Inviato SDI"
o "Accettato AdE"
→ se non presente (sdi_auto_send=true):
verifica che badge SDI sia già
presente dopo emissione fattura

4.12 — Verifica coerenza valori
→ tramite API verifica che:
ricevuta.canoneLocazione ==
booking.ownerNetAmount
ricevuta.ritenutaAmount ==
booking.withholdingAmount
fattura.totalAmount ==
booking.otaCommissionAmount +
booking.cleaningAmount +
booking.pmFeeAmount
(i servizi sono già lordi IVA inclusa:
l'IVA va SCORPORATA dal lordo,
non aggiunta sopra)
fattura.imponibile ==
totalAmount / 1.22
fattura.vatAmount ==
totalAmount - imponibile
(approssimato a 2 decimali)
NB: in regime forfettario (RF19)
non c'è scorporo — imponibile = lordo
e IVA = 0

4.13 — Fattura PM bloccata se i servizi
PM sono a zero
→ cerca via API un booking in stato
'ready' con otaCommissionAmount,
cleaningAmount e pmFeeAmount tutti a 0
(la lista non espone i servizi:
serve GET /api/bookings/{id}
su ogni candidato)
→ nel seed esiste
SEED-NO-SERVIZI-001: immobile 2 senza
regole property_contract_rule
(pulizie e provvigione a 0) e canale
diretto (nessuna commissione OTA)
→ se non esiste nessun booking così:
test.skip
→ naviga a /bookings/{id} e apri
il dialog "Emetti Fattura PM"
→ verifica l'avviso preventivo nel
dialog: "servizi PM risultano tutti
a zero" con rimando alle regole
contratto dell'immobile
→ clicca "Emetti Documento"
→ verifica che il server rifiuti
(HTTP 422 da POST /api/documents/generate):
toast "Errore generazione documento"
e messaggio "nessun servizio PM calcolato"
→ verifica via GET /api/documents che
nessun documento sia stato creato
per quel booking

Nessun cleanup necessario: il test
non emette nulla.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
BACKEND — cleanup documenti
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a TestRunnerController.java:

DELETE /api/test/cleanup-documenti
@RequestBody con:
Integer bookingId

Logica in TestCleanupService:
1. Verifica che il booking appartenga
   al tenant del chiamante
2. Elimina in ordine:
    - sdi_progressivo NON toccare
    - withholding_ledger
      WHERE fk_booking_id = ?
    - fiscal_document
      WHERE fk_booking_id = ?
    - Riporta stato booking a 'ready':
      UPDATE booking SET
      fk_stato_prenotazione_id =
      (SELECT id FROM stato_prenotazione
      WHERE codice = 'ready')
      WHERE id = ?

Restituisce Map con righe eliminate.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ESECUZIONE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package

cd frontend
npx playwright test \
tests/e2e/fase04-documenti-fiscali.spec.ts

Riporta output con ✅/❌ per ogni test.
Se fallisce riporta screenshot e errore
senza correggere — aspetta istruzioni.