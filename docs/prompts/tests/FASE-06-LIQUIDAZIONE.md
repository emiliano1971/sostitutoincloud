Leggi il file CLAUDE.md prima di procedere.
Leggi i file esistenti:
- frontend/tests/e2e/fase05-f24.spec.ts
- frontend/tests/e2e/helpers/auth.ts
- frontend/tests/e2e/helpers/api.ts
- frontend/tests/e2e/helpers/form.ts
  prima di procedere.

Crea il test E2E Fase 06 — Liquidazione:
calcolo settlement, verifica importi,
approvazione, pagamento, rollover arretrati
e download PDF rendiconto.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
STRATEGIA
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Usa un owner che ha booking con ricevute
emesse e ritenute nel withholding_ledger
ma senza settlement per il periodo
di test.

In beforeAll:
- Ottieni token tenantAdmin
- Chiama GET /api/settlements per
  trovare un periodo senza settlement
  per un owner con ritenute
- In alternativa usa owner 4
  (Emiliano Zerbinati) e periodo
  2026-10 (futuro, sicuramente libero)
- Salva ownerId, periodo, settlementId
  (se creato dal test)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TEST CASES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

test.beforeAll:
- getToken tenantAdmin
- verifica che esista almeno un booking
  con withholding_ledger e senza
  settlement per il periodo scelto
- salva dati per le verifiche

test.afterAll:
- se settlement creato dal test:
  DELETE /api/test/cleanup-settlement
  { settlementId: settlementId }
  (solo se stato != 'paid')

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6.1 — Login come tenant_admin
→ verifica redirect /dashboard

6.2 — Naviga a lista liquidazioni
→ clicca voce menu "Liquidazioni"
o naviga a /settlements
→ verifica heading visibile
→ verifica banner "da liquidare"
se esistono prenotazioni pendenti

6.3 — Verifica banner prenotazioni
da liquidare
→ se banner presente:
clicca "Vedi dettaglio"
verifica che si apra la modale
verifica che la tabella contenga
almeno una prenotazione
verifica colonne:
ID, Owner, Immobile,
Check-in, Periodo, Canone €, Netto €
chiudi modale

6.4 — Calcola liquidazioni
→ clicca "+ Calcola liquidazioni"
→ si apre dialog con campo periodo
→ inserisci periodo (es. "2026-09"
o il periodo con ritenute)
→ clicca "Calcola"
→ verifica toast di successo
con numero liquidazioni calcolate
→ verifica che la lista si aggiorni

6.5 — Verifica settlement in lista
→ trova riga del settlement creato
→ verifica colonne:
* Proprietario (nome owner)
* Periodo (es. "2026-09")
* N. Prenotazioni > 0
* Lordo € > 0
* Ritenuta € > 0 (in rosso)
* Netto € > 0
* Stato "Calcolato"

6.6 — Verifica importi settlement
→ via API verifica coerenza:
GET /api/settlements/{settlementId}
→ totalAmount (canone lordo)
→ withholdingAmount (ritenute)
→ netAmount (netto)
→ check: netAmount ==
totalAmount - withholdingAmount
(± 0.01)
→ GET /api/withholding-ledger
filtra per owner e periodo
→ Σ canoneLocazione
→ Σ ritenutaAmount
→ check: settlement.totalAmount ==
Σ canoneLocazione (± 0.01)
→ check: settlement.withholdingAmount ==
Σ ritenutaAmount (± 0.01)

6.7 — Apri dettaglio settlement
→ clicca sulla riga del settlement
→ verifica navigazione a
/settlements/{id}
→ verifica sezione "Prenotazioni":
tabella con almeno una riga
→ verifica sezione importi:
Lordo, Ritenuta, Netto da pagare
→ verifica pulsante "Scarica PDF"

6.8 — Download PDF rendiconto
→ clicca "Scarica PDF"
→ verifica evento download
→ verifica filename contiene
"Rendiconto"

6.9 — Approva settlement
→ torna a /settlements
→ trova riga del settlement di test
→ clicca pulsante "Approva"
(o icona di approvazione)
→ verifica conferma se presente
→ verifica badge stato → "Approvato"

6.10 — Segna come pagato
→ clicca pulsante "Paga"
(o "Segna come pagato")
→ verifica badge stato → "Pagato"
→ verifica che i pulsanti
Approva/Paga siano disabilitati
o non presenti

6.11 — Verifica rollover arretrati
→ se esistono booking con ritenute
di periodi precedenti non liquidate:
calcola liquidazione per il
periodo successivo
verifica che il settlement includa
le prenotazioni in arretrato
verifica colonna "Competenza" nel
dettaglio settlement per i booking
arretrati (es. "» 08/2026")
→ se non esistono arretrati:
verifica che il banner "da liquidare"
sia vuoto dopo il pagamento

6.12 — Verifica idempotenza ricalcolo
→ calcola di nuovo lo stesso periodo
per lo stesso owner
→ se settlement è 'paid':
verifica errore/skip (non modifica)
→ se settlement non è 'paid':
verifica che il totale resti
invariato (idempotente)

6.13 — Verifica filtro stato
→ nella lista settlements
→ seleziona filtro "Pagata"
→ verifica che il settlement
di test (ora paid) sia visibile
→ seleziona filtro "Calcolata"
→ verifica che NON sia visibile

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
BACKEND — cleanup settlement
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a TestRunnerController.java:

DELETE /api/test/cleanup-settlement
@RequestBody con:
Integer settlementId
Boolean forzaSePagato (default false)

Logica in TestCleanupService:
1. Verifica appartenenza al tenant
2. Se stato = 'paid' e
   !forzaSePagato → 400 errore
3. Elimina in ordine:
    - settlement_booking
      WHERE fk_settlement_id = ?
    - settlement
      WHERE id = ?
      AND fk_tenant_id = tenant corrente
4. Restituisce Map con righe eliminate

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ESECUZIONE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Assicurati che siano attivi:
- Tomcat su :8081
- Vite su :5173

mvn -Plocal -DskipTests clean package

cd frontend
npx playwright test \
tests/e2e/fase06-liquidazione.spec.ts

Riporta output con ✅/❌ per ogni test.
Se fallisce riporta screenshot e errore
senza correggere — aspetta istruzioni.