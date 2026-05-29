Leggi il file CLAUDE.md, docs/db/schema-target.sql e
docs/analisi-frontend.md prima di procedere.

Crea DTO + Service + Controller per la gestione F24.
Segui lo stesso pattern già esistente.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/f24/F24ListDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Campi:
    * Integer id
    * String period            ← YYYY-MM
    * String codiceTributo     ← codice da codice_tributo (sempre "1919")
    * BigDecimal totalAmount
    * Integer withholdingsCount
    * String stato             ← codice f24_status
    * LocalDate deadlineDate
    * LocalDate paymentDate    ← nullable
    * LocalDateTime createdAt

Crea dto/f24/F24DetailDTO.java:
- Stessi campi di F24ListDTO più:
    * Integer fkTenantId
    * String tenantLegalName   ← da tenant.legal_name
    * String tenantTaxCode     ← da tenant.tax_code
    * String tenantAddress     ← da tenant.legal_address
    * LocalDateTime updatedAt

Crea dto/f24/F24StatusUpdateDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- String stato   ← "draft"|"ready"|"sent"|"paid"|"error"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/F24Service.java:
- @Service @Log4j2
- Costruttore con F24RecordDAO, CodiceTributoDAO, TenantDAO

- Metodi pubblici:

  List<F24ListDTO> findByTenantId(Integer tenantId)
    - carica tutti gli f24 del tenant
    - risolve codiceTributo da CodiceTributoDAO
      (carica tutti una volta — no N+1)
    - ordina per period DESC
    - mappa su F24ListDTO
    - Log INFO con count

  Optional<F24DetailDTO> findById(Integer tenantId,
  Integer f24Id)
    - verifica appartenenza al tenant
    - risolve codiceTributo
    - arricchisce con dati tenant:
        * tenantLegalName, tenantTaxCode, tenantAddress
          da tenantDAO.findById(tenantId)
    - mappa su F24DetailDTO
    - Log INFO

  F24ListDTO updateStatus(Integer tenantId,
  Integer f24Id, String nuovoStato)
    - verifica esistenza e appartenenza al tenant
    - verifica che nuovoStato sia valido:
      "draft","ready","sent","paid","error"
    - stub — lancia UnsupportedOperationException
    - Log WARN

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea controller/F24Controller.java:
- @RestController @Log4j2
- @RequestMapping("/api/f24")
- Costruttore con F24Service
- tenantId hardcoded a 1 // TODO: SecurityContext
- Endpoints:

  GET /api/f24
    - ResponseEntity<List<F24ListDTO>>

  GET /api/f24/{id}
    - 200 o 404
    - ResponseEntity<F24DetailDTO>

  PATCH /api/f24/{id}/status
    - @RequestBody F24StatusUpdateDTO
    - 200 / 404 / 400 / 501

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Dopo aver creato i file lancia:
mvn -Plocal clean package

Poi riavvia Tomcat e testa:
curl -u admin:admin \
"http://localhost:8081/sostitutoincloud/api/f24"
curl -u admin:admin \
"http://localhost:8081/sostitutoincloud/api/f24/1"
curl -u admin:admin \
"http://localhost:8081/sostitutoincloud/api/f24/99"

Riporta l'output del build e dei curl.