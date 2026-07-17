Leggi il file CLAUDE.md, docs/db/schema-target.sql e
docs/analisi-frontend.md prima di procedere.

Il CuRecordDAO (solo lettura) esiste già da SETUP-DAO-F24-CU-AUDIT.
Il CuService e CuController esistono già come stub da SETUP-SERVICE-CU.
Ora aggiungi la logica reale di generazione CU.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. CuRecordDAO — aggiungi metodi write + aggregazione
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/CuRecordDAO.java:

CuRecord insert(CuRecord cu)
- INSERT INTO cu_record (fk_tenant_id, fk_owner_id, tax_year,
  total_compensi, total_imponibile, total_ritenute, stato)
  VALUES (?, ?, ?, ?, ?, ?, ?)
- usa KeyHolder per recuperare id generato
- rileggi con findById() e restituisci
- Log INFO "CuRecordDAO.insert() - id={}"

CuRecord updateTotaliAndStato(Integer id, BigDecimal totalCompensi,
BigDecimal totalImponibile, BigDecimal totalRitenute, String stato)
- UPDATE cu_record SET total_compensi=?, total_imponibile=?,
  total_ritenute=?, stato=?, updated_at=NOW() WHERE id=?
- rileggi con findById() e restituisci
- Log INFO "CuRecordDAO.updateTotaliAndStato() - id={}"

CuRecord updateStato(Integer id, String stato)
- UPDATE cu_record SET stato=?, updated_at=NOW() WHERE id=?
- rileggi con findById() e restituisci
- Log INFO "CuRecordDAO.updateStato() - id={} stato={}"

Aggiungi query SQL esternalizzata in
src/main/resources/sql/cu_record/aggregate_by_owner_year.sql:
SELECT
SUM(wl.gross_due_to_beneficiary) AS total_compensi,
SUM(wl.taxable_base)             AS total_imponibile,
SUM(wl.withholding_amount)       AS total_ritenute
FROM withholding_ledger wl
WHERE wl.fk_tenant_id = ?
AND wl.fk_owner_id  = ?
AND EXTRACT(YEAR FROM wl.payment_date) = ?

Aggiungi query SQL in
src/main/resources/sql/cu_record/owners_with_withholding.sql:
SELECT DISTINCT wl.fk_owner_id
FROM withholding_ledger wl
WHERE wl.fk_tenant_id = ?
AND EXTRACT(YEAR FROM wl.payment_date) = ?

Aggiungi a CuRecordDAO i metodi che usano quelle query:

Map<String,Object> aggregateByOwnerYear(Integer tenantId,
Integer ownerId, Integer taxYear)
- carica SQL da src/main/resources/sql/cu_record/aggregate_by_owner_year.sql
- jdbcTemplate.queryForMap(sql, tenantId, ownerId, taxYear)
- lancia IllegalArgumentException se nessuna riga trovata
- Log DEBUG "CuRecordDAO.aggregateByOwnerYear() - tenantId={} ownerId={} year={}"

List<Integer> findOwnerIdsWithWithholding(Integer tenantId,
Integer taxYear)
- carica SQL da src/main/resources/sql/cu_record/owners_with_withholding.sql
- jdbcTemplate.queryForList(sql, Integer.class, tenantId, taxYear)
- Log DEBUG "CuRecordDAO.findOwnerIdsWithWithholding() - tenantId={} year={} trovati={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. Aggiungi DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/cu/CuGeneraRequestDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- Integer ownerId    ← nullable: se null genera per tutti
- Integer taxYear    ← obbligatorio

Crea dto/cu/CuGeneraBatchResponseDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Integer generated
- Integer skipped
- List<CuListDTO> records

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CuService — sostituisci lo stub con la logica reale
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica service/CuService.java:

Aggiungi metodo privato:
CuListDTO generaPerOwner(Integer tenantId, Integer ownerId,
Integer taxYear)
- chiama cuRecordDAO.aggregateByOwnerYear()
- cerca CU esistente con findByTenantIdAndOwnerId()
  filtrata in Java per taxYear
- se esiste in stato "draft" o "generated":
  aggiorna con updateTotaliAndStato(stato="generated")
- se esiste in stato "delivered" o "sent":
  lancia IllegalStateException("CU già " + stato)
- se non esiste: insert con stato="generated"
- recupera ownerName da ownerProfileDAO.findById(ownerId)
  concatenando first_name + ' ' + last_name
- mappa su CuListDTO
- Log INFO "CuService.generaPerOwner() - tenantId={} ownerId={} year={}"

Aggiungi metodo pubblico:
CuListDTO genera(Integer tenantId, CuGeneraRequestDTO req)
- verifica taxYear != null, altrimenti IllegalArgumentException
- delega a generaPerOwner()
- Log INFO "CuService.genera() - tenantId={} ownerId={} year={}"

Aggiungi metodo pubblico:
CuGeneraBatchResponseDTO generaBatch(Integer tenantId,
Integer taxYear)
- chiama cuRecordDAO.findOwnerIdsWithWithholding()
- per ogni ownerId chiama generaPerOwner() in try/catch:
    * catch IllegalStateException → incrementa skipped, log WARN
- ritorna CuGeneraBatchResponseDTO con generated, skipped, records
- Log INFO "CuService.generaBatch() - tenantId={} year={} generated={} skipped={}"

Sostituisci lo stub updateStatus con logica reale:
CuListDTO updateStatus(Integer tenantId, Integer cuId,
String nuovoStato)
- stati validi: "draft","generated","delivered","sent"
- se stato non valido: lancia IllegalArgumentException
- verifica appartenenza al tenant, lancia NoSuchElementException se non trovata
- chiama cuRecordDAO.updateStato()
- mappa e ritorna CuListDTO
- Log INFO "CuService.updateStatus() - id={} stato={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. CuController — aggiungi endpoint generazione
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/CuController.java:

POST /api/cu/genera
- @RequestBody CuGeneraRequestDTO
- se req.ownerId != null: chiama cuService.genera()
  → ResponseEntity<CuListDTO> 200
- se req.ownerId == null: chiama cuService.generaBatch()
  → ResponseEntity<CuGeneraBatchResponseDTO> 200
- catch IllegalStateException → 422 body {"error": msg}
- catch IllegalArgumentException → 400 body {"error": msg}
- Log INFO "CuController.genera() - ownerId={} year={}"

Modifica PATCH /api/cu/{id}/status (già presente come stub):
- sostituisci il 501 con la chiamata reale a cuService.updateStatus()
- catch IllegalArgumentException → 400
- catch NoSuchElementException → 404

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. Frontend — pagina CUList
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/tenant/CUList.tsx
(già esistente con dati mock).
Segui esattamente lo stesso pattern delle altre
pagine già esistenti (BookingList, F24List, ecc.).

Sostituisci i mock con chiamate reali alle API:
- Lista CU: GET /api/cu con query param taxYear
  (select anno fiscale, default anno corrente - 1)
- Pulsante "Genera tutte": POST /api/cu/genera
  con body { taxYear: annoSelezionato } (ownerId omesso)
- Cambio stato inline: PATCH /api/cu/{id}/status
  con body { stato: nuovoStato }
- Badge stato: draft=grigio, generated=blu,
  delivered=arancione, sent=verde
- Colonne: ownerName, taxYear, totalCompensi €,
  totalRitenute €, stato, generatedAt
- Gestione errori con messaggio visibile all'utente

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Dopo aver modificato tutti i file lancia:
mvn -Plocal clean package

Poi riavvia Tomcat e testa:
curl -s "http://localhost:8081/sostitutoincloud/api/cu?tenantId=1"
curl -s -X POST "http://localhost:8081/sostitutoincloud/api/cu/genera" \
-H "Content-Type: application/json" \
-d '{"taxYear":2025}'
curl -s "http://localhost:8081/sostitutoincloud/api/cu?tenantId=1"

Riporta l'output del build e dei curl.