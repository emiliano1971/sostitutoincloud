Leggi il file CLAUDE.md prima di procedere.
Leggi i file esistenti:
- frontend/tests/e2e/fase04-documenti-fiscali.spec.ts
- frontend/tests/e2e/helpers/auth.ts
- frontend/tests/e2e/helpers/api.ts
- frontend/tests/e2e/helpers/form.ts
  prima di procedere.

Crea il test E2E Fase 05 — F24:
generazione F24, verifica totale ritenute,
download PDF, cambio stato.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
STRATEGIA
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Usa le ritenute già presenti nel DB
(withholding_ledger) senza emettere
nuovi documenti.

In beforeAll:
- Ottieni token tenantAdmin
- Chiama GET /api/withholding-ledger
  o GET /api/f24 per trovare un periodo
  con ritenute da versare
- Se esiste già un F24 non pagato
  per quel periodo → usalo
- Se non esiste → generalo via API
  POST /api/f24/genera
  con il periodo trovato
- Salva f24Id e periodoF24

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TEST CASES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

test.beforeAll:
- getToken tenantAdmin
- trova o genera F24 di test
- salva f24Id, periodoF24,
  totalAmount atteso

test.afterAll:
- se F24 generato dal test
  (non preesistente):
  DELETE /api/test/cleanup-f24
  { f24Id: f24Id }
  (elimina solo se stato != 'paid')

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5.1 — Login come tenant_admin
→ verifica redirect /dashboard

5.2 — Naviga a lista F24
→ clicca voce menu "F24" o
naviga a /f24
→ verifica heading F24 visibile
→ verifica che la lista non sia vuota

5.3 — Verifica F24 in lista
→ trova riga con periodoF24
→ verifica colonne:
* Periodo visibile (es. "08/2026")
* Totale > 0
* Stato badge visibile

5.4 — Verifica totale F24 = Σ ritenute
→ via API:
GET /api/f24/{f24Id} →
totalAmount
GET /api/withholding-ledger
filtra per periodo →
Σ ritenutaAmount
→ verifica che i due valori
coincidano (± 0.01 per arrotondamenti)
→ check "✅ F24 totale = Σ ritenute
del periodo"

5.5 — Apri anteprima F24
→ clicca icona occhio sulla riga F24
→ si apre F24PreviewDialog
→ verifica che il dialog mostri:
* CF tenant visibile
* Codice tributo 1919
* Importo = totalAmount
* Periodo corretto

5.6 — Download PDF F24
→ nel dialog o nella lista
→ clicca "Scarica PDF"
→ verifica evento download
(page.waitForEvent('download'))
→ verifica filename contiene "F24"

5.7 — Ricalcolo F24
→ se esistono ritenute non ancora
incluse nell'F24:
clicca "Ricalcola" sul F24
verifica che il totale si aggiorni
→ se non esistono ritenute extra:
verifica che il pulsante ricalcola
sia presente ma il totale resti
invariato dopo il click

5.8 — Cambio stato: ready → paid
→ trova il pulsante per segnare
l'F24 come pagato
→ clicca
→ verifica conferma dialog se presente
→ verifica che il badge stato
diventi "Pagato"
→ verifica che il pulsante ricalcola
sia disabilitato o non presente
(F24 pagato non modificabile)

5.9 — Verifica F24 pagato non modificabile
→ tenta di cliccare "Ricalcola"
su F24 pagato
→ verifica che restituisca errore
o che il pulsante sia disabilitato
→ verifica che il totale non cambi

5.10 — Verifica filtro anno/mese
→ nella lista F24 usa il filtro
anno = anno del periodoF24
→ verifica che l'F24 di test
compaia nella lista filtrata
→ usa filtro mese = mese del periodo
→ verifica stesso risultato

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
BACKEND — cleanup F24
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a TestRunnerController.java:

DELETE /api/test/cleanup-f24
@RequestBody con:
Integer f24Id

Logica in TestCleanupService:
1. Verifica che l'F24 appartenga
   al tenant del chiamante
2. Verifica che stato != 'paid'
   (non eliminare F24 pagati)
3. Elimina in ordine:
    - Aggiorna withholding_ledger:
      SET fk_f24_record_id = NULL
      WHERE fk_f24_record_id = ?
    - Elimina f24_record WHERE id = ?
      AND fk_tenant_id = tenant corrente

Restituisce Map con righe aggiornate.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ESECUZIONE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Assicurati che siano attivi:
- Tomcat su :8081
- Vite su :5173

mvn -Plocal -DskipTests clean package

cd frontend
npx playwright test \
tests/e2e/fase05-f24.spec.ts

Riporta output con ✅/❌ per ogni test.
Se fallisce riporta screenshot e errore
senza correggere — aspetta istruzioni.