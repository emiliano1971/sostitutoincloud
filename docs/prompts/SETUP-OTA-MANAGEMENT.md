Leggi il file CLAUDE.md e docs/db/schema-target.sql
prima di procedere.

Implementa la gestione completa dei canali OTA.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. AGGIORNA SCHEMA DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

IMPORTANTE: la tabella canale_ota esiste già nel DB.
Aggiungere SOLO la colonna tourist_tax_collection
con ALTER TABLE — non ricreare la tabella.

Aggiungi in fondo a docs/db/schema-target.sql:

```sql
-- Aggiunta tourist_tax_collection a canale_ota
ALTER TABLE canale_ota
  ADD COLUMN IF NOT EXISTS tourist_tax_collection
  VARCHAR(20) DEFAULT 'contanti';

COMMENT ON COLUMN canale_ota.tourist_tax_collection IS
  'Modalità default riscossione tassa soggiorno per questo canale';
```

Esegui sul DB:
psql -U sostitutoincloud -d sostitutoincloud -h localhost \
-c "ALTER TABLE canale_ota ADD COLUMN IF NOT EXISTS
tourist_tax_collection VARCHAR(20) DEFAULT 'contanti';"

Poi aggiorna i valori di default per i canali esistenti:
psql -U sostitutoincloud -d sostitutoincloud -h localhost \
-c "UPDATE canale_ota SET tourist_tax_collection = 'payment_link'
WHERE codice IN ('booking', 'expedia', 'vrbo');"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. AGGIORNA MODEL E ROWMAPPER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna model/CanaleOta.java:
- Aggiungi campo: String touristTaxCollection

Aggiorna dao/mapper/CanaleOtaRowMapper.java:
- Aggiungi mapping: rs.getString("tourist_tax_collection")

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. AGGIUNGI METODI WRITE A CanaleOtaDAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/CanaleOtaDAO.java:

CanaleOta insert(CanaleOta canale)
- INSERT INTO canale_ota con tutti i campi
  (esclusi id, created_at, updated_at)
- Usa KeyHolder per id generato
- Dopo insert: rileggi con findById()
- Log INFO: "CanaleOtaDAO.insert() - codice={}"

CanaleOta update(CanaleOta canale)
- UPDATE canale_ota SET
  nome=?, commissione_default_pct=?,
  tourist_tax_included=?,
  tourist_tax_collection=?,
  attivo=?, updated_at=NOW()
  WHERE id=?
- Dopo update: rileggi con findById()
- Log INFO: "CanaleOtaDAO.update() - id={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. DTO + SERVICE + CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/ota/CanaleOtaDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Integer id
- String codice
- String nome
- BigDecimal commissioneDefaultPct
- Boolean touristTaxIncluded
- String touristTaxCollection
- Boolean attivo
- LocalDateTime createdAt
- LocalDateTime updatedAt

Crea dto/ota/CanaleOtaCreateDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- String codice (obbligatorio, univoco)
- String nome (obbligatorio)
- BigDecimal commissioneDefaultPct
- Boolean touristTaxIncluded
- String touristTaxCollection
  ← "contanti"|"payment_link"|"altro"

Crea dto/ota/CanaleOtaUpdateDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- Stessi campi di CanaleOtaCreateDTO
  ma tutti opzionali (partial update)

Crea service/CanaleOtaService.java:
- @Service @Log4j2
- Costruttore con CanaleOtaDAO

  List<CanaleOtaDTO> findAll()
    - chiama canaleOtaDAO.findAll()
    - mappa su CanaleOtaDTO
    - Log DEBUG

  CanaleOtaDTO findById(Integer id)
    - chiama canaleOtaDAO.findById(id)
    - lancia RuntimeException se non trovato
    - mappa su CanaleOtaDTO

  CanaleOtaDTO create(CanaleOtaCreateDTO dto)
    - Verifica che codice non esista già
      lancia IllegalArgumentException se duplicato
    - Costruisce CanaleOta da DTO
    - Chiama canaleOtaDAO.insert()
    - Mappa e restituisce CanaleOtaDTO
    - Log INFO

  CanaleOtaDTO update(Integer id,
  CanaleOtaUpdateDTO dto)
    - Verifica esistenza
    - Aggiorna i campi non null (partial update)
    - Chiama canaleOtaDAO.update()
    - Mappa e restituisce CanaleOtaDTO
    - Log INFO

  CanaleOtaDTO updateStatus(Integer id, Boolean attivo)
    - UPDATE canale_ota SET attivo=?, updated_at=NOW()
      WHERE id=?
    - Aggiunge metodo updateStatus a CanaleOtaDAO:
      CanaleOta updateStatus(Integer id, Boolean attivo)
    - Log INFO

Crea controller/CanaleOtaController.java:
- @RestController @Log4j2
- @RequestMapping("/api/canali-ota")
- Costruttore con CanaleOtaService
- Endpoint protetti —