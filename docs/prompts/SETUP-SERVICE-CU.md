Leggi il file CLAUDE.md, docs/db/schema-target.sql e
docs/analisi-frontend.md prima di procedere.

Crea DTO + Service + Controller per la gestione CU
(Certificazione Unica).
Segui lo stesso pattern già esistente.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/cu/CuListDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Campi:
    * Integer id
    * String ownerName         ← first_name + ' ' + last_name
    * Integer taxYear
    * BigDecimal totalCompensi
    * BigDecimal totalRitenute
    * String stato             ← codice cu_status
    * LocalDate generatedAt    ← nullable
    * LocalDateTime createdAt

Crea dto/cu/CuDetailDTO.java:
- Stessi campi di CuListDTO più:
    * Integer fkTenantId
    * Integer fkOwnerId
    * String ownerTaxCode      ← da owner_profile.tax_code
    * String ownerIban         ← da owner_profile.iban
    * LocalDateTime updatedAt

Crea dto/cu/CuStatusUpdateDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- String stato   ← "draft"|"generated"|"sent"|"delivered"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/CuService.java:
- @Service @Log4j2
- Costruttore con CuRecordDAO, OwnerProfileDAO

- Metodi pubblici:

  List<CuListDTO> findByTenantId(Integer tenantId,
  Integer ownerId, Integer taxYear)
    - carica tutti i cu del tenant
    - se ownerId != null: filtra per owner
    - se taxYear != null: filtra per tax_year
    - carica tutti gli owner del tenant una volta
      (no N+1 per ownerName)
    - ordina per taxYear DESC
    - mappa su CuListDTO
    - Log INFO con count

  Optional<CuDetailDTO> findById(Integer tenantId,
  Integer cuId)
    - verifica appartenenza al tenant
    - arricchisce con dati owner:
      ownerName, ownerTaxCode, ownerIban
    - mappa su CuDetailDTO
    - Log INFO

  CuListDTO updateStatus(Integer tenantId,
  Integer cuId, String nuovoStato)
    - verifica esistenza e appartenenza al tenant
    - verifica che nuovoStato sia valido:
      "draft","generated","sent","delivered"
    - stub — lancia UnsupportedOperationException
    - Log WARN

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea controller/CuController.java:
- @RestController @Log4j2
- @RequestMapping("/api/cu")
- Costruttore con CuService
- tenantId hardcoded a 1 // TODO: SecurityContext
- Endpoints:

  GET /api/cu
    - query params opzionali: ownerId, taxYear
    - ResponseEntity<List<CuListDTO>>

  GET /api/cu/{id}
    - 200 o 404
    - ResponseEntity<CuDetailDTO>

  PATCH /api/cu/{id}/status
    - @RequestBody CuStatusUpdateDTO
    - 200 / 404 / 400 / 501

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Dopo aver creato i file lancia:
mvn -Plocal clean package

Poi riavvia Tomcat e testa:
curl -u admin:admin \
"http://localhost:8081/sostitutoincloud/api/cu"
curl -u admin:admin \
"http://localhost:8081/sostitutoincloud/api/cu/1"
curl -u admin:admin \
"http://localhost:8081/sostitutoincloud/api/cu/99"

Riporta l'output del build e dei curl.