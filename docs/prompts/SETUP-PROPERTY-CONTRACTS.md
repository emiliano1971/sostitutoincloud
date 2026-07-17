Leggi il file CLAUDE.md e docs/db/schema-target.sql
prima di procedere.

Implementa le regole di imputazione costi
per immobile (PropertyContracts).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. ALTER TABLE E SEED SUL DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Esegui sul DB:

psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -c "
CREATE TABLE IF NOT EXISTS property_contract_rule (
id SERIAL PRIMARY KEY,
fk_property_id INTEGER NOT NULL
REFERENCES property(id) ON DELETE CASCADE,
fk_tenant_id INTEGER NOT NULL
REFERENCES tenant(id) ON DELETE RESTRICT,
fk_canale_ota_id INTEGER
REFERENCES canale_ota(id) ON DELETE SET NULL,
tipo VARCHAR(30) NOT NULL,
calc_mode VARCHAR(30) NOT NULL,
valore DECIMAL(10,2) NOT NULL DEFAULT 0,
is_remainder BOOLEAN NOT NULL DEFAULT FALSE,
ordine INTEGER NOT NULL DEFAULT 0,
attivo BOOLEAN NOT NULL DEFAULT TRUE,
created_at TIMESTAMP NOT NULL DEFAULT NOW(),
updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS
idx_contract_rule_property
ON property_contract_rule(fk_property_id);

CREATE INDEX IF NOT EXISTS
idx_contract_rule_tenant
ON property_contract_rule(fk_tenant_id);

CREATE TRIGGER trg_contract_rule_updated_at
BEFORE UPDATE ON property_contract_rule
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
"

Valori ammessi per tipo:
pulizie, commissione_ota, cambio_biancheria,
commissione_pm, provvigione_proprietario

Valori ammessi per calc_mode:
fisso, percentuale, fisso_per_notte,
fisso_per_persona, percentuale_lordo,
rimanenza

Aggiorna docs/db/schema-target.sql con
la nuova tabella.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. MODEL E DAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea model/PropertyContractRule.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Integer id
- Integer fkPropertyId
- Integer fkTenantId
- Integer fkCanaleOtaId (nullable)
- String tipo
- String calcMode
- BigDecimal valore
- Boolean isRemainder
- Integer ordine
- Boolean attivo
- LocalDateTime createdAt
- LocalDateTime updatedAt

Crea dao/mapper/PropertyContractRuleRowMapper.java

Crea dao/PropertyContractRuleDAO.java:
- @Repository @Log4j2

  List<PropertyContractRule> findByPropertyId(
  Integer propertyId)
    - SELECT * FROM property_contract_rule
      WHERE fk_property_id = ?
      AND attivo = TRUE
      ORDER BY ordine ASC, id ASC
    - Log DEBUG

  PropertyContractRule findById(Integer id)
    - SELECT * WHERE id = ?
    - lancia RuntimeException se non trovato

  PropertyContractRule insert(
  PropertyContractRule rule)
    - INSERT con KeyHolder
    - Log INFO

  PropertyContractRule update(
  PropertyContractRule rule)
    - UPDATE SET tipo, calc_mode, valore,
      is_remainder, fk_canale_ota_id, ordine,
      updated_at = NOW()
      WHERE id = ?
    - Log INFO

  void delete(Integer id)
    - DELETE WHERE id = ?
    - Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/property/PropertyContractRuleDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Integer id
- Integer fkPropertyId
- Integer fkCanaleOtaId (nullable)
- String canaleName (nullable) ← nome canale OTA
- String tipo
- String tipoLabel ← label italiana del tipo
- String calcMode
- String calcModeLabel ← label italiana
- BigDecimal valore
- Boolean isRemainder
- Integer ordine

Crea dto/property/PropertyContractRuleCreateDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- Integer fkPropertyId (obbligatorio)
- Integer fkCanaleOtaId (nullable)
- String tipo (obbligatorio)
- String calcMode (obbligatorio)
- BigDecimal valore
- Boolean isRemainder (default false)
- Integer ordine (default 0)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/PropertyContractService.java:
- @Service @Log4j2
- Costruttore con PropertyContractRuleDAO,
  CanaleOtaDAO

  List<PropertyContractRuleDTO>
  findByPropertyId(Integer tenantId,
  Integer propertyId)
    - Verifica che property appartenga al tenant
    - carica regole
    - per ogni regola risolve canaleName
      da CanaleOtaDAO se fkCanaleOtaId != null
    - mappa su DTO con tipoLabel e calcModeLabel

  Mappe label (costanti private):
  Map<String,String> TIPO_LABELS = Map.of(
  "pulizie", "Pulizie Abitazione",
  "commissione_ota", "Commissione OTA",
  "cambio_biancheria", "Cambio Biancheria",
  "commissione_pm", "Commissione PM",
  "provvigione_proprietario",
  "Provvigione Proprietario"
  )
  Map<String,String> CALC_MODE_LABELS = Map.of(
  "fisso", "Importo Fisso (€)",
  "percentuale", "Percentuale (%)",
  "fisso_per_notte", "Fisso per Notte (€)",
  "fisso_per_persona", "Fisso per Persona (€)",
  "percentuale_lordo", "Percentuale sul Lordo (%)",
  "rimanenza", "Rimanenza automatica"
  )

  PropertyContractRuleDTO create(
  Integer tenantId,
  PropertyContractRuleCreateDTO dto)
    - Valida tipo e calcMode
    - Valida che non esista già una regola
      is_remainder per questa property
      se dto.isRemainder = true →
      lancia IllegalArgumentException
      "Esiste già una voce rimanenza"
    - Valida coerenza tipo/calcMode:
        * commissione_ota richiede fkCanaleOtaId
        * rimanenza solo per commissione_pm
          o provvigione_proprietario
    - Salva e restituisce DTO

  PropertyContractRuleDTO update(
  Integer tenantId, Integer ruleId,
  PropertyContractRuleCreateDTO dto)
    - Verifica esistenza e appartenenza tenant
    - Stesse validazioni di create
    - Salva e restituisce DTO

  void delete(Integer tenantId, Integer ruleId)
    - Verifica esistenza e appartenenza tenant
    - Chiama DAO.delete()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea controller/PropertyContractController.java:
- @RestController @Log4j2
- @RequestMapping("/api/properties/
  {propertyId}/contracts")
- tenantId = SecurityUtils.getCurrentTenantId()

  GET /api/properties/{propertyId}/contracts
  → List<PropertyContractRuleDTO>

  POST /api/properties/{propertyId}/contracts
  → @RequestBody PropertyContractRuleCreateDTO
  → ResponseEntity.status(201).body(result)

  PUT /api/properties/{propertyId}/contracts/{id}
  → @RequestBody PropertyContractRuleCreateDTO
  → ResponseEntity.ok(result)

  DELETE /api/properties/{propertyId}/contracts/{id}
  → ResponseEntity.noContent()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. FRONTEND — API
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/api/contractApi.ts:

```ts
export interface ContractRule {
  id: number;
  fkPropertyId: number;
  fkCanaleOtaId?: number;
  canaleName?: string;
  tipo: string;
  tipoLabel: string;
  calcMode: string;
  calcModeLabel: string;
  valore: number;
  isRemainder: boolean;
  ordine: number;
}

export interface ContractRuleCreate {
  fkPropertyId: number;
  fkCanaleOtaId?: number;
  tipo: string;
  calcMode: string;
  valore: number;
  isRemainder: boolean;
  ordine: number;
}

export async function getContractRules(
  propertyId: number): Promise<ContractRule[]>
// GET /api/properties/{propertyId}/contracts

export async function createContractRule(
  propertyId: number,
  data: ContractRuleCreate): Promise<ContractRule>
// POST /api/properties/{propertyId}/contracts

export async function updateContractRule(
  propertyId: number,
  ruleId: number,
  data: ContractRuleCreate): Promise<ContractRule>
// PUT /api/properties/{propertyId}/contracts/{id}

export async function deleteContractRule(
  propertyId: number,
  ruleId: number): Promise<void>
// DELETE /api/properties/{propertyId}/contracts/{id}
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. AGGIORNA PropertyContracts.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Leggi frontend/src/pages/tenant/
PropertyContracts.tsx

Sostituisci i mock con dati reali:

1. Rimuovi import mockProperties e
   createMockRules
2. Aggiungi useEffect → getContractRules(id)
   con loading/error state
3. Il dato immobile (display_name, internal_code)
   caricalo da getPropertyById(id) già esistente
4. Canali OTA per il select nel dialog:
   carica da getOtas() già esistente
5. handleAddRule → createContractRule()
6. handleEditRule → updateContractRule()
7. handleDeleteRule → deleteContractRule()
8. Dopo ogni operazione ricarica le regole

Il simulatore prenotazione rimane
client-side come ora — calcola in memoria
usando le regole caricate dal backend.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. AGGIORNA AuditService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In PropertyContractService aggiungi
AuditService e logga:
- contract.create → "Aggiunta regola
  {tipo} a immobile {propertyId}"
- contract.update → "Modificata regola
  {tipo} immobile {propertyId}"
- contract.delete → "Eliminata regola
  id={ruleId} immobile {propertyId}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
9. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

Verifica che /properties/{id}/contracts
mostri la pagina con:
- Tabella regole vuota (nessun mock)
- Bottone "Aggiungi Regola" funzionante
- Dialog con tipi e modalità di calcolo

Riporta output di entrambi.