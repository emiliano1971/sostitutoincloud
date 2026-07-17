Leggi il file CLAUDE.md e docs/db/schema-target.sql
prima di procedere.

Implementa la generazione mensile dei settlement.
Il SettlementDAO (solo lettura), SettlementBookingDAO,
model Settlement, SettlementBooking esistono già.
Il SettlementService esiste come stub.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. SettlementDAO — aggiungi metodi write
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/SettlementDAO.java:

Optional<Settlement> findByOwnerAndPeriod(
Integer tenantId, Integer ownerId, String period)
- SELECT * FROM settlement
  WHERE fk_tenant_id = ?
  AND fk_owner_id = ?
  AND period = ?
- Log DEBUG

Settlement insert(Settlement s)
- INSERT INTO settlement (fk_tenant_id,
  fk_owner_id, period, periodo_mese,
  periodo_anno, total_amount,
  withholding_amount, net_amount, stato)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
- usa KeyHolder per id generato
- rileggi con findById() e restituisci
- Log INFO "SettlementDAO.insert() - id={}"

Settlement updateTotali(Integer id,
BigDecimal totalAmount,
BigDecimal withholdingAmount,
BigDecimal netAmount)
- UPDATE settlement
  SET total_amount = ?,
  withholding_amount = ?,
  net_amount = ?,
  stato = 'calculated',
  updated_at = NOW()
  WHERE id = ?
- rileggi con findById() e restituisci
- Log INFO "SettlementDAO.updateTotali() - id={}"

Settlement updateStato(Integer id, String stato)
- UPDATE settlement
  SET stato = ?,
  updated_at = NOW()
  WHERE id = ?
- se stato = 'paid':
  SET payment_date = NOW()
- rileggi con findById() e restituisci
- Log INFO "SettlementDAO.updateStato()
    - id={} stato={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. SettlementBookingDAO — aggiungi metodi write
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/SettlementBookingDAO.java:

void insert(Integer settlementId,
Integer bookingId)
- INSERT INTO settlement_booking
  (fk_settlement_id, fk_booking_id)
  VALUES (?, ?)
- Log DEBUG

void deleteBySettlementId(Integer settlementId)
- DELETE FROM settlement_booking
  WHERE fk_settlement_id = ?
- Log DEBUG

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/settlement/SettlementCalcolaRequestDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- Integer mese     ← 1-12
- Integer anno     ← es. 2026

Crea dto/settlement/SettlementCalcolaResultDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Integer generated    ← nuovi settlement creati
- Integer updated      ← settlement ricalcolati
- Integer skipped      ← saltati (già paid)
- List<SettlementListDTO> settlements

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. SettlementService — logica reale
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica service/SettlementService.java:
Aggiungi WithholdingLedgerDAO e
OwnerProfileDAO al costruttore.

Aggiungi metodo privato:
SettlementListDTO calcolaPerOwner(
Integer tenantId, Integer ownerId,
Integer mese, Integer anno)

Logica:
1. String period = String.format(
   "%d-%02d", anno, mese)

2. Aggrega da withholding_ledger:
   SELECT
   SUM(wl.canone_locazione) AS total_amount,
   SUM(wl.ritenuta_amount)  AS withholding_amount,
   COUNT(wl.id)             AS num_righe
   FROM withholding_ledger wl
   WHERE wl.fk_tenant_id = ?
   AND wl.fk_owner_id  = ?
   AND wl.periodo_mese = ?
   AND wl.periodo_anno = ?
   Esegui con jdbcTemplate.queryForMap()
   Se total_amount IS NULL:
   lancia IllegalArgumentException(
   "Nessuna ritenuta per owner=" + ownerId
    + " periodo=" + period)

3. Calcola:
   BigDecimal totalAmount =
   toBigDecimal(row.get("total_amount"))
   BigDecimal withholdingAmount =
   toBigDecimal(row.get("withholding_amount"))
   BigDecimal netAmount =
   totalAmount.subtract(withholdingAmount)

4. Carica withholding_ledger del periodo
   per recuperare i fk_booking_id:
   SELECT DISTINCT fk_booking_id
   FROM withholding_ledger
   WHERE fk_tenant_id = ?
   AND fk_owner_id  = ?
   AND periodo_mese = ?
   AND periodo_anno = ?
   List<Integer> bookingIds

5. Cerca settlement esistente:
   settlementDAO.findByOwnerAndPeriod(
   tenantId, ownerId, period)

   Se esiste in stato 'paid':
   lancia IllegalStateException(
   "Settlement già pagato per " + period)

   Se esiste (non paid):
    - settlementDAO.updateTotali()
    - settlementBookingDAO
      .deleteBySettlementId(existing.getId())
    - reinserisci settlement_booking
      per ogni bookingId
    - tipo = "updated"

   Se non esiste:
    - settlementDAO.insert() con
      stato = 'calculated'
    - inserisci settlement_booking
      per ogni bookingId
    - tipo = "generated"

6. Recupera ownerName da
   ownerProfileDAO.findById(ownerId):
   first_name + ' ' + last_name

7. Mappa su SettlementListDTO e restituisci
   Log INFO "SettlementService
   .calcolaPerOwner() - tenantId={}
   ownerId={} period={} tipo={}"

Aggiungi metodo pubblico:
SettlementCalcolaResultDTO calcola(
Integer tenantId,
SettlementCalcolaRequestDTO req)

Logica:
1. Valida mese (1-12) e anno (> 2020)
   IllegalArgumentException se non validi

2. Trova tutti gli owner con ritenute
   nel periodo:
   SELECT DISTINCT fk_owner_id
   FROM withholding_ledger
   WHERE fk_tenant_id = ?
   AND periodo_mese = ?
   AND periodo_anno = ?
   List<Integer> ownerIds

   Se vuota: lancia IllegalArgumentException(
   "Nessuna ritenuta per il periodo
   {mese}/{anno}")

3. Per ogni ownerId chiama
   calcolaPerOwner() in try/catch:
    - catch IllegalStateException
      → incrementa skipped, log WARN
    - catch IllegalArgumentException
      → log WARN, continua

4. Audit:
   auditService.log("settlement.calcola",
   "Settlement", null,
   "Calcolati settlement periodo
   {mese}/{anno}: generated={} updated={}
   skipped={}")

5. Ritorna SettlementCalcolaResultDTO
   Log INFO "SettlementService.calcola()
    - period={}/{} gen={} upd={} skip={}"

Sostituisci stub updateStatus con logica reale:
SettlementListDTO updateStatus(
Integer tenantId, Integer settlementId,
String nuovoStato)
- stati validi: pending, calculated,
  approved, paid
- IllegalArgumentException se non valido
- verifica appartenenza al tenant
- se stato attuale = 'paid':
  IllegalStateException(
  "Settlement già pagato")
- chiama settlementDAO.updateStato()
- mappa e restituisci SettlementListDTO
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. SettlementController — nuovo endpoint
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/SettlementController.java:

POST /api/settlements/calcola
- @RequestBody SettlementCalcolaRequestDTO
- chiama settlementService.calcola()
- ResponseEntity.ok(result)
- catch IllegalArgumentException → 400
- catch IllegalStateException → 422
- Log INFO "SettlementController.calcola()
    - mese={} anno={}"

Modifica PATCH /api/settlements/{id}/status
(stub esistente):
- sostituisci 501 con chiamata reale
  a settlementService.updateStatus()
- catch IllegalArgumentException → 400
- catch IllegalStateException → 422
- catch NoSuchElementException → 404

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. FRONTEND — SettlementsList.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/settlementApi.ts:

interface SettlementCalcolaRequest {
mese: number
anno: number
}

interface SettlementCalcolaResult {
generated: number
updated: number
skipped: number
settlements: SettlementListItem[]
}

export async function calcolaSettlements(
req: SettlementCalcolaRequest
): Promise<SettlementCalcolaResult>
// POST /api/settlements/calcola

export async function updateSettlementStatus(
id: number, stato: string
): Promise<SettlementListItem>
// PATCH /api/settlements/{id}/status

Modifica frontend/src/pages/tenant/
SettlementsList.tsx:

- Aggiungi pulsante "Calcola liquidazioni"
  che apre un dialog con:
    * Select mese (1-12, default mese corrente)
    * Input anno (default anno corrente)
    * Bottone "Calcola" → chiama
      calcolaSettlements()
    * Mostra risultato: "X nuovi, Y aggiornati,
      Z saltati"
    * Chiudi dialog e ricarica lista

- Aggiungi select filtro stato accanto
  al pulsante (stessa UI di F24List):
  tutti | pending | calculated |
  approved | paid

- Per ogni riga della tabella aggiungi
  azioni inline (solo se stato != paid):
    * "Approva" → updateSettlementStatus
      (stato='approved')
      visibile solo se stato='calculated'
    * "Segna pagato" → updateSettlementStatus
      (stato='paid')
      visibile solo se stato='approved'

- Badge stato colorati:
  pending    → grigio
  calculated → blu
  approved   → arancione
  paid       → verde

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Calcola settlements 06/2026
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"mese":6,"anno":2026}' \
http://localhost:8081/sostitutoincloud/\
api/settlements/calcola \
| python3 -m json.tool

# Lista settlements
curl -s -H "Authorization: Bearer $TOKEN" \
http://localhost:8081/sostitutoincloud/\
api/settlements \
| python3 -m json.tool

# Approva settlement id=1
curl -s -X PATCH \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"stato":"approved"}' \
http://localhost:8081/sostitutoincloud/\
api/settlements/1/status \
| python3 -m json.tool

Verifica che:
- generated=2 (Anna Moretti + Emiliano Zerbinati)
- net_amount = total_amount - withholding_amount
- settlement_booking colleghi i booking corretti
- cambio stato funzioni correttamente

Riporta output build e curl.