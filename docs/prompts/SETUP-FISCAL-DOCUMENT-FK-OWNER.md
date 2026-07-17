Leggi il file CLAUDE.md e docs/db/schema-target.sql
prima di procedere.

Aggiungi fk_owner_id direttamente su fiscal_document.
Il campo viene popolato al momento della generazione
del documento e semplifica tutte le query per owner.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MIGRATION DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Esegui sul DB:

psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -c "
ALTER TABLE fiscal_document
ADD COLUMN IF NOT EXISTS fk_owner_id INTEGER
REFERENCES owner_profile(id) ON DELETE RESTRICT;

-- Popola i dati esistenti risalendo la catena
UPDATE fiscal_document fd
SET fk_owner_id = p.fk_owner_id
FROM booking b
JOIN property p ON p.id = b.fk_property_id
WHERE fd.fk_booking_id = b.id
AND fd.fk_owner_id IS NULL;

CREATE INDEX IF NOT EXISTS
idx_fiscal_doc_fk_owner_id
ON fiscal_document(fk_tenant_id, fk_owner_id);
"

Verifica il risultato:
psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -c "
SELECT fd.document_number, fd.fk_owner_id,
o.first_name, o.last_name
FROM fiscal_document fd
LEFT JOIN owner_profile o ON o.id = fd.fk_owner_id
ORDER BY fd.id;
"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. SCHEMA SQL
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna docs/db/schema-target.sql:
- Aggiungi dopo fk_booking_id:
  fk_owner_id INTEGER REFERENCES
  owner_profile(id) ON DELETE RESTRICT
- Aggiungi l'indice idx_fiscal_doc_fk_owner_id

Crea docs/db/migrations/
002_fiscal_document_fk_owner_id.sql
con l'ALTER TABLE, UPDATE e CREATE INDEX
già eseguiti sopra.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. MODEL + MAPPER + DAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica model/FiscalDocument.java:
- aggiungi Integer fkOwnerId

Modifica dao/mapper/FiscalDocumentRowMapper.java:
- aggiungi mapping:
  rs.getObject("fk_owner_id", Integer.class)

Modifica dao/FiscalDocumentDAO.java:
- aggiungi fk_owner_id in SELECT_ALL
- aggiungi fk_owner_id in INSERT
- aggiungi parametro fkOwnerId nell'insert

Aggiungi metodo:
List<FiscalDocument> findByOwnerAndTenant(
Integer tenantId, Integer ownerId)
- SELECT * FROM fiscal_document
  WHERE fk_tenant_id = ? AND fk_owner_id = ?
  ORDER BY issue_date DESC
- Log DEBUG

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. DocumentGenerationService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica service/DocumentGenerationService.java:

Al momento della creazione del documento
(sia fattura_pm che ricevuta_owner) popola
fkOwnerId risalendo:
booking → property.fkOwnerId

Il valore è già disponibile nel service perché
carica la property per altri campi — non serve
query aggiuntiva.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. FiscalDocumentService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica service/FiscalDocumentService.java:

- Il campo ownerName in DocumentListDTO
  ora viene risolto direttamente da
  fk_owner_id del documento invece della
  catena booking→property→owner:

  Sostituisci resolveOwnerName() con:
  ownersById.get(doc.getFkOwnerId())
  (molto più semplice — nessuna catena)

- Rimuovi la dipendenza da PropertyDAO
  dal costruttore SE non è più usata per
  altro (verifica prima di rimuoverla)

- Il filtro per ownerId in findByTenantId()
  ora filtra su doc.getFkOwnerId() invece
  che sul risultato della catena

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Il campo fkOwnerId è già presente in
DocumentListDTO (aggiunto in SETUP-SERVICE-
DOCUMENT-OWNER-NAME) — nessuna modifica DTO
necessaria. Verifica solo che venga popolato
dal campo diretto e non più dalla catena.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it","password":"atena"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Tutti i documenti con ownerName
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/api/documents" \
| python3 -m json.tool | grep -E \
'"documentNumber"|"ownerName"|"fkOwnerId"'

# Filtro per owner
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/api/documents?ownerId=1" \
| python3 -m json.tool | grep -E \
'"documentNumber"|"ownerName"'

Verifica che:
- tutti i documenti abbiano fkOwnerId != null
- ownerName sia coerente con il documento
- il filtro ownerId=1 restituisca solo
  documenti di Anna Moretti
- nessun documento abbia ownerName null

Riporta output build e curl.