Leggi il file CLAUDE.md e docs/db/schema-target.sql
prima di procedere.

Aggiungi endpoint PATCH /api/f24/{id}/ricalcola
che aggancia le ritenute da_versare dello stesso
periodo all'F24 esistente e ne aggiorna il totale.
Operazione consentita SOLO se l'F24 NON è pagato.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. WithholdingLedgerDAO — nuovo metodo
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/WithholdingLedgerDAO.java:

List<WithholdingLedger> findDaVersareByPeriodo(
Integer tenantId, Integer mese, Integer anno)
- SELECT * FROM withholding_ledger
  WHERE fk_tenant_id = ?
  AND periodo_mese = ?
  AND periodo_anno = ?
  AND stato = 'da_versare'
  AND fk_f24_record_id IS NULL
- Log DEBUG "WithholdingLedgerDAO
  .findDaVersareByPeriodo() - trovate={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. F24RecordDAO — nuovo metodo
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/F24RecordDAO.java:

void updateTotale(Integer id,
BigDecimal totalAmount, Integer withholdingsCount)
- UPDATE f24_record
  SET total_amount = ?,
  withholdings_count = ?,
  updated_at = NOW()
  WHERE id = ?
- Log INFO "F24RecordDAO.updateTotale()
    - id={} totale={} count={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. F24Service — nuovo metodo ricalcola()
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a service/F24Service.java:

F24GenerazioneResultDTO ricalcola(
Integer tenantId, Integer f24Id)

Logica:
1. Carica F24 esistente con f24RecordDAO.findById()
   Se non trovato: NoSuchElementException

2. Verifica appartenenza al tenant:
   se f24.fkTenantId != tenantId:
   NoSuchElementException

3. Verifica stato NON pagato:
   se f24.stato == "pagato":
   IllegalStateException(
   "F24 già pagato — impossibile modificare")

4. Cerca ritenute da_versare non ancora agganciate:
   withhholdingLedgerDAO.findDaVersareByPeriodo(
   tenantId, f24.periodoMese, f24.periodoAnno)
   Se lista vuota:
   IllegalArgumentException(
   "Nessuna ritenuta nuova da aggiungere
   per il periodo {mese}/{anno}")

5. Per ogni ritenuta trovata:
    - withhholdingLedgerDAO.updateF24Record(
      ritenuta.getId(), f24Id)
    - withhholdingLedgerDAO.updateStato(
      ritenuta.getId(), "versata")

6. Ricalcola totale:
    - carica TUTTE le ritenute agganciate
      all'F24 (stato='versata' AND
      fk_f24_record_id = f24Id)
    - nuovoTotale = sum(ritenuta_amount)
    - nuovoCount = lista.size()
    - f24RecordDAO.updateTotale(
      f24Id, nuovoTotale, nuovoCount)

7. Audit:
   auditService.log("f24.ricalcola",
   "F24", f24Id,
   "Aggiunte {count} ritenute al F24
   periodo {mese}/{anno}
   nuovo totale €{totale}")

8. Ricarica F24 aggiornato e restituisce
   F24GenerazioneResultDTO popolato
   (stesso DTO già usato da genera())

Log INFO "F24Service.ricalcola() -
f24Id={} nuoveRitenute={} nuovoTotale={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. F24Controller — nuovo endpoint
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/F24Controller.java:

PATCH /api/f24/{id}/ricalcola
- tenantId = SecurityUtils.getCurrentTenantId()
- chiama f24Service.ricalcola(tenantId, id)
- ResponseEntity.ok(result)
- catch IllegalStateException → 422
  body {"error": msg}
- catch IllegalArgumentException → 400
  body {"error": msg}
- catch NoSuchElementException → 404
- Log INFO "F24Controller.ricalcola() - id={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. FRONTEND — F24List.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/f24Api.ts:

export async function ricalcolaF24(
id: number
): Promise<F24GenerazioneResult>
// PATCH /api/f24/{id}/ricalcola

Aggiungi a frontend/src/pages/tenant/F24List.tsx
un pulsante "Ricalcola" visibile SOLO sulle righe
con stato diverso da 'pagato':
- icona RefreshCw da lucide-react
- tooltip "Aggiungi ritenute non incluse"
- onClick → ricalcolaF24(id)
    * successo → toast "F24 aggiornato" + ricarica lista
    * errore 400 → toast con msg (nessuna ritenuta nuova)
    * errore 422 → toast con msg (già pagato)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package


TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it","password":"atena"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Stato prima del ricalcolo
curl -s -H "Authorization: Bearer $TOKEN" \
http://localhost:8081/sostitutoincloud/api/f24 \
| python3 -m json.tool

# Ricalcola
curl -s -X PATCH \
-H "Authorization: Bearer $TOKEN" \
http://localhost:8081/sostitutoincloud/api/f24/1/ricalcola \
| python3 -m json.tool

# Stato dopo — deve mostrare 3 ritenute e totale aggiornato
curl -s -H "Authorization: Bearer $TOKEN" \
http://localhost:8081/sostitutoincloud/api/f24 \
| python3 -m json.tool

# Secondo ricalcolo deve dare 400
# (nessuna ritenuta nuova)
curl -s -X PATCH \
-H "Authorization: Bearer $TOKEN" \
http://localhost:8081/sostitutoincloud/api/f24/1/ricalcola \
| python3 -m json.tool

Riporta output build e dei 4 curl.