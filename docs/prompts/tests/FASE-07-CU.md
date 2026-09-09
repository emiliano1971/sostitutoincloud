Leggi il file CLAUDE.md prima di procedere.
Leggi i file esistenti:
- frontend/tests/e2e/fase06-liquidazione.spec.ts
- frontend/tests/e2e/helpers/auth.ts
- frontend/tests/e2e/helpers/api.ts
- frontend/tests/e2e/helpers/form.ts
  prima di procedere.

Crea il test E2E Fase 07 — CU:
generazione Certificazione Unica,
verifica importi, coerenza con F24
e ritenute, download PDF.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
STRATEGIA
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Usa un owner con ritenute nel
withholding_ledger per l'anno corrente.

In beforeAll:
- Ottieni token tenantAdmin
- Chiama GET /api/cu per trovare
  CU esistenti
- Se esiste CU per owner 4 (Emiliano
  Zerbinati) anno 2026 → usala
- Se non esiste → generala via API
  POST /api/cu/genera
  con anno 2026
- Salva cuId, taxYear, ownerId
- Carica i dati attesi da:
  GET /api/withholding-ledger
  filtra per owner 4 e anno 2026
  → Σ canoneLocazione = totalCompensi atteso
  → Σ ritenutaAmount = totalRitenute atteso

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TEST CASES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

test.beforeAll:
- getToken tenantAdmin
- trova o genera CU di test
- calcola valori attesi da ledger
- salva cuId, taxYear, ownerId,
  totalCompensiAtteso, totalRitenuteAtteso

test.afterAll:
- se CU generata dal test
  (non preesistente):
  DELETE /api/test/cleanup-cu
  { cuId: cuId }

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7.1 — Login come tenant_admin
→ verifica redirect /dashboard

7.2 — Naviga a lista CU
→ clicca voce menu "CU" o
naviga a /cu
→ verifica heading visibile
→ verifica lista non vuota

7.3 — Verifica CU in lista
→ trova riga CU per owner 4 anno 2026
→ verifica colonne:
* Proprietario (nome owner)
* Anno fiscale 2026
* Compensi totali > 0
* Ritenute totali > 0
* Stato badge visibile

7.4 — Verifica coerenza importi CU
→ via API:
GET /api/cu/{cuId}
→ totalCompensi
→ totalRitenute
→ check: totalCompensi ==
totalCompensiAtteso (± 0.01)
"✅ CU totalCompensi = Σ canoni ledger"
→ check: totalRitenute ==
totalRitenuteAtteso (± 0.01)
"✅ CU totalRitenute = Σ ritenute ledger"

7.5 — Verifica coerenza CU vs F24
→ via API:
GET /api/f24 filtra per anno 2026
→ Σ totalAmount degli F24 pagati
dell'anno per questo tenant
→ GET /api/cu/{cuId}
→ totalRitenute
→ check: totalRitenute ≈
Σ F24 pagati anno 2026
(± 1.00 per arrotondamenti
e ritenute non ancora in F24)
→ log risultato anche se non
perfettamente uguale —
è un warning non un errore
(potrebbero esserci ritenute
non ancora versate in F24)

7.6 — Download PDF CU
→ nella lista CU trova la riga
→ clicca pulsante "⬇ PDF"
→ verifica evento download
(page.waitForEvent('download'))
→ verifica filename contiene
"CU_2026"

7.7 — Verifica contenuto PDF CU
→ via API scarica il PDF:
GET /api/cu/{cuId}/pdf
→ verifica che la risposta sia
application/pdf
→ verifica Content-Disposition
contiene "CU_2026"

7.8 — Verifica filtro anno
→ nella lista CU
→ se presente filtro anno:
seleziona 2026
verifica che la CU di test
sia visibile
→ seleziona anno diverso (es. 2025)
→ verifica che la lista sia vuota
o non contenga la CU di test

7.9 — Genera CU per owner senza ritenute
→ tenta POST /api/cu/genera
per un anno senza ritenute
(es. 2020)
→ verifica che l'API risponda
con 0 CU generate o errore
appropriato
→ verifica che nessuna CU 2020
appaia in lista

7.10 — Verifica CU owner_user
→ login come proprietario@email.it
→ naviga a /owner (portale owner)
→ vai alla sezione CU
→ se owner 1 (Anna Moretti) ha CU:
verifica che siano visibili
verifica pulsante PDF
→ se non ha CU:
verifica che la lista sia vuota
senza errori
→ verifica che NON siano visibili
le CU di altri proprietari

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
BACKEND — cleanup CU
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a TestRunnerController.java:

DELETE /api/test/cleanup-cu
@RequestBody con:
Integer cuId

Logica in TestCleanupService:
1. Verifica appartenenza al tenant
2. Verifica che stato != 'sent'
   (non eliminare CU già trasmesse
   all'AdE)
3. Elimina:
   cu_record WHERE id = ?
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
tests/e2e/fase07-cu.spec.ts

Riporta output con ✅/❌ per ogni test.
Se fallisce riporta screenshot e errore
senza correggere — aspetta istruzioni.