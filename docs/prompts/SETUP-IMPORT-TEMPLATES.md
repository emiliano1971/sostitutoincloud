Leggi il file CLAUDE.md, docs/db/schema-target.sql
e i file esistenti:
- service/BookingImportService.java
- frontend/src/pages/tenant/ImportBookings.tsx
- frontend/src/api/importApi.ts
  prima di procedere.

Aggiungi la gestione dei template di mapping
per l'import prenotazioni. Un template salva
nome colonne → campi sistema in modo che
l'utente non debba rimappare ogni volta.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MIGRATION DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea docs/db/migrations/
005_import_template.sql:

CREATE TABLE IF NOT EXISTS import_template (
id              SERIAL PRIMARY KEY,
fk_tenant_id    INTEGER NOT NULL
REFERENCES tenants(id)
ON DELETE CASCADE,
nome            VARCHAR(100) NOT NULL,
descrizione     VARCHAR(255),
header_row      INTEGER NOT NULL DEFAULT 0,
booking_mapping JSONB NOT NULL DEFAULT '{}',
guest_mapping   JSONB NOT NULL DEFAULT '{}',
created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
CONSTRAINT uq_import_template_nome
UNIQUE (fk_tenant_id, nome)
);

CREATE TRIGGER import_template_updated_at
BEFORE UPDATE ON import_template
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX IF NOT EXISTS
idx_import_template_tenant
ON import_template(fk_tenant_id);

Esegui sul DB:
psql -U sostitutoincloud -d sostitutoincloud \
-h localhost \
-f docs/db/migrations/005_import_template.sql

Verifica:
psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -c "\d import_template"

Aggiorna docs/db/schema-target.sql
aggiungendo la tabella.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. MODEL + MAPPER + DAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea model/ImportTemplate.java:
- @Data @Builder @NoArgsConstructor
  @AllArgsConstructor
- Integer id
- Integer fkTenantId
- String nome
- String descrizione
- Integer headerRow
- String bookingMapping  ← JSONB come String
- String guestMapping    ← JSONB come String
- LocalDateTime createdAt
- LocalDateTime updatedAt

Crea dao/mapper/ImportTemplateRowMapper.java:
- implements RowMapper<ImportTemplate>
- mappa tutti i campi
- bookingMapping e guestMapping:
  rs.getString("booking_mapping")

Crea dao/ImportTemplateDAO.java:
- @Repository @Log4j2
- Costruttore con JdbcTemplate +
  ImportTemplateRowMapper

List<ImportTemplate> findByTenantId(
Integer tenantId)
- SELECT * FROM import_template
  WHERE fk_tenant_id = ?
  ORDER BY nome
- Log DEBUG

Optional<ImportTemplate> findById(Integer id)
- SELECT * FROM import_template WHERE id = ?
- Log DEBUG

ImportTemplate insert(ImportTemplate t)
- INSERT INTO import_template
  (fk_tenant_id, nome, descrizione,
  header_row, booking_mapping, guest_mapping)
  VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)
- usa KeyHolder
- rileggi con findById() e restituisci
- Log INFO "ImportTemplateDAO.insert() - id={}"

ImportTemplate update(ImportTemplate t)
- UPDATE import_template
  SET nome = ?, descrizione = ?,
  header_row = ?,
  booking_mapping = ?::jsonb,
  guest_mapping = ?::jsonb,
  updated_at = NOW()
  WHERE id = ? AND fk_tenant_id = ?
- rileggi con findById() e restituisci
- Log INFO "ImportTemplateDAO.update() - id={}"

void delete(Integer id, Integer tenantId)
- DELETE FROM import_template
  WHERE id = ? AND fk_tenant_id = ?
- Log INFO "ImportTemplateDAO.delete() - id={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/importing/ImportTemplateDTO.java:
- @Data @Builder @NoArgsConstructor
  @AllArgsConstructor
- Integer id
- String nome
- String descrizione
- Integer headerRow
- Map<String,String> bookingMapping
- Map<String,String> guestMapping
- LocalDateTime createdAt
- LocalDateTime updatedAt

Crea dto/importing/ImportTemplateSaveDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- String nome           ← obbligatorio
- String descrizione    ← opzionale
- Integer headerRow     ← default 0
- Map<String,String> bookingMapping
- Map<String,String> guestMapping

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/ImportTemplateService.java:
- @Service @Log4j2
- Costruttore con ImportTemplateDAO,
  ObjectMapper (Jackson — già nel classpath)

List<ImportTemplateDTO> findByTenant(
Integer tenantId)
- chiama dao.findByTenantId()
- mappa su DTO deserializzando JSONB:
  objectMapper.readValue(
  t.getBookingMapping(),
  new TypeReference<Map<String,String>>(){})
- Log DEBUG

ImportTemplateDTO save(Integer tenantId,
ImportTemplateSaveDTO dto)
- se dto.id != null → update
- altrimenti → insert
- verifica unicità nome per tenant
  (lancia IllegalArgumentException se
  nome già esistente in insert)
- serializza mapping con
  objectMapper.writeValueAsString()
- Log INFO "ImportTemplateService.save()
    - tenantId={} nome={}"

void delete(Integer tenantId, Integer id)
- verifica appartenenza al tenant
- chiama dao.delete()
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea controller/ImportTemplateController.java:
- @RestController @Log4j2
- @RequestMapping("/api/import-templates")
- Costruttore con ImportTemplateService

GET /api/import-templates
- tenantId da SecurityUtils
- ResponseEntity<List<ImportTemplateDTO>>
- Log INFO

POST /api/import-templates
- @RequestBody ImportTemplateSaveDTO
- tenantId da SecurityUtils
- ResponseEntity<ImportTemplateDTO>
- catch IllegalArgumentException → 400

PUT /api/import-templates/{id}
- @RequestBody ImportTemplateSaveDTO
- setta dto.id = id dal path
- ResponseEntity<ImportTemplateDTO>
- catch IllegalArgumentException → 400

DELETE /api/import-templates/{id}
- ResponseEntity.ok(Map.of(
  "message","Template eliminato"))
- catch NoSuchElementException → 404

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. FRONTEND — importApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/importApi.ts:

export interface ImportTemplate {
id: number
nome: string
descrizione?: string
headerRow: number
bookingMapping: Record<string, string>
guestMapping: Record<string, string>
createdAt: string
updatedAt: string
}

export interface ImportTemplateSave {
id?: number
nome: string
descrizione?: string
headerRow: number
bookingMapping: Record<string, string>
guestMapping: Record<string, string>
}

export async function getImportTemplates():
Promise<ImportTemplate[]>
// GET /api/import-templates

export async function saveImportTemplate(
data: ImportTemplateSave
): Promise<ImportTemplate>
// POST /api/import-templates (nuovo)
// PUT /api/import-templates/{id} (update)

export async function deleteImportTemplate(
id: number
): Promise<void>
// DELETE /api/import-templates/{id}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. FRONTEND — ImportBookings.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica ImportBookings.tsx per integrare
i template nel wizard esistente.

STEP 0 — Upload Files (già esistente):
Aggiungi sopra le drop zone una sezione
"Template di importazione" con:

Select "Usa template salvato":
- opzione vuota "-- Nessun template --"
- una opzione per ogni template:
  "{nome} ({descrizione})"
- onChange → setSelectedTemplate(template)
  NON applicare ancora il mapping

Se selectedTemplate != null mostra
sotto il select:
"✓ Template selezionato: {nome} —
il mapping verrà applicato al passo
successivo"
+ pulsante X per deselezionare

Pulsante "Gestisci template" (link/button
secondario) → apre TemplateManagerDialog

STEP 1 — Mapping Colonne (già esistente):
All'apertura dello step 1:
- se selectedTemplate != null:
  precompila il mapping con
  selectedTemplate.bookingMapping
  e selectedTemplate.guestMapping
  (solo per le colonne che esistono
  nel file caricato — ignora le altre
  silenziosamente)

In fondo allo step 1 aggiungi sezione
"Salva questo mapping come template":
- Checkbox "Salva per uso futuro"
- Se checked mostra:
  Input "Nome template *"
  (default: nome file senza estensione)
  Input "Descrizione" (opzionale)
- Il salvataggio avviene al click
  "Genera Anteprima" se checkbox attivo:
  chiama saveImportTemplate() prima di
  previewImportV2()
    * successo → toast "Template salvato"
    * errore 400 (nome duplicato) →
      toast "Nome già esistente — scegli
      un altro nome" e NON bloccare
      la preview

TEMPLATE MANAGER DIALOG:
Crea componente TemplateManagerDialog.tsx
in frontend/src/components/:
- Dialog con lista dei template salvati
- Per ogni template:
    * nome + descrizione
    * data creazione
    * pulsante "Elimina" (con confirm)
    * pulsante "Rinomina" (inline edit)
- Pulsante "Chiudi"
- Usa getImportTemplates(),
  deleteImportTemplate(),
  saveImportTemplate() per rename
  (PUT con stesso mapping, nuovo nome)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. TEST
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

# Lista template (vuota inizialmente)
curl -s -H "Authorization: Bearer $TOKEN" \
http://localhost:8081/sostitutoincloud/\
api/import-templates \
| python3 -m json.tool

# Crea template di test
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"nome": "Booking.com Standard",
"descrizione": "Export standard Booking.com",
"headerRow": 0,
"bookingMapping": {
"BOOKING_ID": "Id",
"ORIGINE": "Origine",
"STRUTTURA": "Struttura",
"CHECKIN": "Arrivo",
"CHECKOUT": "Partenza",
"IMPORTO_TOTALE": "Importo totale",
"ADULTI": "Adulti",
"CLIENTE_NOME": "Cliente",
"COMMISSIONE": "Commissione del canale"
},
"guestMapping": {}
}' \
http://localhost:8081/sostitutoincloud/\
api/import-templates \
| python3 -m json.tool

# Lista template (deve mostrare il template creato)
curl -s -H "Authorization: Bearer $TOKEN" \
http://localhost:8081/sostitutoincloud/\
api/import-templates \
| python3 -m json.tool

# Testa nome duplicato → deve dare 400
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"nome": "Booking.com Standard",
"headerRow": 0,
"bookingMapping": {},
"guestMapping": {}
}' \
http://localhost:8081/sostitutoincloud/\
api/import-templates \
| python3 -m json.tool

Verifica:
- GET lista → template creato visibile
- POST duplicato → 400
- mapping JSONB serializzato/deserializzato
  correttamente

Riporta output migration (\d import_template),
build e curl.