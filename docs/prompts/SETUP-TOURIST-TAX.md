Leggi il file CLAUDE.md, docs/db/schema-target.sql e
frontend/src/data/tourist-tax.ts prima di procedere.

Implementa la gestione completa della tassa di soggiorno.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MODEL E ROWMAPPER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna model/RegolaTassaSoggiorno.java:
- Aggiungi i nuovi campi:
    * String region
    * BigDecimal baseRate
    * BigDecimal maxAmountPerPerson
    * String exemptions
    * String notes
    * Integer fkTenantId
- Mantieni tutti i campi esistenti

Aggiorna dao/mapper/RegolaTassaSoggiornoRowMapper.java:
- Aggiungi mapping per i nuovi campi

Crea model/TassaFasciaEta.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Integer id
- Integer fkRegolaId
- String label
- Integer minAge
- Integer maxAge
- Integer reductionPct
- LocalDateTime createdAt

Crea model/TassaStagione.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Integer id
- Integer fkRegolaId
- String label
- Integer startMonth
- Integer startDay
- Integer endMonth
- Integer endDay
- Integer reductionPct
- LocalDateTime createdAt

Crea model/TassaZona.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Integer id
- Integer fkRegolaId
- String label
- Integer reductionPct
- LocalDateTime createdAt

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. DAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna dao/RegolaTassaSoggiornoDAO.java:
- Aggiungi metodi write:
    * RegolaTassaSoggiorno insert(RegolaTassaSoggiorno r)
    * RegolaTassaSoggiorno update(RegolaTassaSoggiorno r)
    * RegolaTassaSoggiorno updateStatus(Integer id, Boolean attivo)
- Aggiorna findAll() e findById() per includere
  i nuovi campi nello SELECT

Crea dao/mapper/TassaFasciaEtaRowMapper.java
Crea dao/mapper/TassaStagioneRowMapper.java
Crea dao/mapper/TassaZonaRowMapper.java

Crea dao/TassaFasciaEtaDAO.java:
- findByRegolaId(Integer regolaId) → List<TassaFasciaEta>
- insert(TassaFasciaEta f) → TassaFasciaEta
- deleteByRegolaId(Integer regolaId)

Crea dao/TassaStagioneDAO.java:
- findByRegolaId(Integer regolaId) → List<TassaStagione>
- insert(TassaStagione s) → TassaStagione
- deleteByRegolaId(Integer regolaId)

Crea dao/TassaZonaDAO.java:
- findByRegolaId(Integer regolaId) → List<TassaZona>
- insert(TassaZona z) → TassaZona
- deleteByRegolaId(Integer regolaId)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/touristtax/TassaFasciaEtaDTO.java:
- Stessi campi di TassaFasciaEta (senza id e fkRegolaId)
- Integer minAge, Integer maxAge, Integer reductionPct
- String label

Crea dto/touristtax/TassaStagioneDTO.java:
- label, startMonth, startDay, endMonth, endDay,
  reductionPct

Crea dto/touristtax/TassaZonaDTO.java:
- label, reductionPct

Crea dto/touristtax/RegolaTassaSoggiornoListDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Integer id
- String comune
- String provincia
- String region
- BigDecimal importoPerNotte
- Integer maxNotti
- BigDecimal maxAmountPerPerson
- Boolean attivo
- LocalDate validaDal
- LocalDate validaAl
- Integer fascieEtaCount
- Integer stagioniCount
- Integer zoneCount

Crea dto/touristtax/RegolaTassaSoggiornoDetailDTO.java:
- Tutti i campi di ListDTO più:
    * List<TassaFasciaEtaDTO> fascieEta
    * List<TassaStagioneDTO> stagioni
    * List<TassaZonaDTO> zone
    * String exemptions
    * String notes
    * LocalDateTime createdAt
    * LocalDateTime updatedAt

Crea dto/touristtax/RegolaTassaSoggiornoCreateDTO.java:
- Tutti i campi modificabili:
    * String comune (obbligatorio)
    * String provincia (obbligatorio, 2 char)
    * String region
    * BigDecimal importoPerNotte (obbligatorio)
    * Integer maxNotti
    * BigDecimal maxAmountPerPerson
    * LocalDate validaDal (obbligatorio)
    * LocalDate validaAl
    * String exemptions
    * String notes
    * List<TassaFasciaEtaDTO> fascieEta
    * List<TassaStagioneDTO> stagioni
    * List<TassaZonaDTO> zone

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/TouristTaxService.java:
- @Service @Log4j2
- Costruttore con RegolaTassaSoggiornoDAO,
  TassaFasciaEtaDAO, TassaStagioneDAO, TassaZonaDAO

  List<RegolaTassaSoggiornoListDTO> findByTenantId(
  Integer tenantId)
    - carica regole del tenant
    - per ogni regola conta fascieEta, stagioni, zone
      (carica tutto una volta — no N+1)
    - mappa su ListDTO

  RegolaTassaSoggiornoDetailDTO findById(
  Integer tenantId, Integer id)
    - verifica appartenenza al tenant
    - carica regola + fasce + stagioni + zone
    - mappa su DetailDTO

  RegolaTassaSoggiornoDetailDTO create(
  Integer tenantId,
  RegolaTassaSoggiornoCreateDTO dto)
    - inserisce regola con fkTenantId
    - per ogni fasciaEta: insert con fkRegolaId
    - per ogni stagione: insert con fkRegolaId
    - per ogni zona: insert con fkRegolaId
    - restituisce DetailDTO

  RegolaTassaSoggiornoDetailDTO update(
  Integer tenantId, Integer id,
  RegolaTassaSoggiornoCreateDTO dto)
    - verifica esistenza e appartenenza
    - aggiorna regola principale
    - deleteByRegolaId su fasce/stagioni/zone
    - reinserisce fasce/stagioni/zone dal DTO
      (replace completo — più semplice di partial update)
    - restituisce DetailDTO

  RegolaTassaSoggiornoDetailDTO updateStatus(
  Integer tenantId, Integer id, Boolean attivo)
    - aggiorna solo il campo attivo

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. CALCOLO TASSA — BACKEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/TouristTaxCalculatorService.java:
- @Service @Log4j2
- Replica ESATTAMENTE la logica di calculateTouristTax()
  dal file frontend/src/data/tourist-tax.ts
  (già analizzata — pseudocodice disponibile)
- Input:
    * RegolaTassaSoggiornoDetailDTO regola
    * Integer nights
    * LocalDate checkinDate
    * String zona (nullable)
    * List<Integer> guestAges
- Output: TouristTaxCalculationDTO con:
    * BigDecimal total
    * List<GuestTaxDTO> perPerson
      (age, nightsCharged, ratePerNight, total, esente)
- Questo è la fonte di verità per il calcolo
  usato nelle prenotazioni reali

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea controller/TouristTaxController.java:
- @RestController @Log4j2
- @RequestMapping("/api/tourist-tax")
- tenantId = SecurityUtils.getCurrentTenantId()

  GET /api/tourist-tax
    - ResponseEntity<List<RegolaTassaSoggiornoListDTO>>

  GET /api/tourist-tax/{id}
    - 200 o 404

  POST /api/tourist-tax
    - @RequestBody RegolaTassaSoggiornoCreateDTO
    - ResponseEntity.status(201).body(result)

  PUT /api/tourist-tax/{id}
    - @RequestBody RegolaTassaSoggiornoCreateDTO
    - ResponseEntity.ok(result)

  PATCH /api/tourist-tax/{id}/status
    - @RequestBody: { "attivo": true/false }
    - ResponseEntity.ok(result)

  POST /api/tourist-tax/{id}/calculate
    - @RequestBody: {
      nights: int,
      checkinDate: LocalDate,
      zona: String (nullable),
      guestAges: List<Integer>
      }
    - chiama TouristTaxCalculatorService
    - ResponseEntity<TouristTaxCalculationDTO>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. SEED DATA
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a docs/db/seed-data.sql le regole
di Venezia e Roma dal mock con tutti i dati
(fasce età, stagioni, zone) usando
fk_tenant_id = 1.

Esegui il seed sul DB:
psql -U sostitutoincloud -d sostitutoincloud -h localhost

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. FRONTEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/api/touristTaxApi.ts con
tutti i tipi e le funzioni necessarie.

Modifica frontend/src/pages/tenant/TouristTaxSettings.tsx:
- Sostituisci mockTouristTaxRules con
  useEffect → GET /api/tourist-tax
- Sostituisci calculateTouristTax() del mock
  con chiamata POST /api/tourist-tax/{id}/calculate
- Mantieni TUTTO il layout esistente
- Aggiungi loading/error state

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
9. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package

curl -s -H "Authorization: Bearer $TOKEN" \
http://localhost:8081/sostitutoincloud/api/tourist-tax \
| python3 -m json.tool

cd frontend && npm run build

Riporta output di entrambi.