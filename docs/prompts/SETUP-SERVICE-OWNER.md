Leggi il file CLAUDE.md, docs/db/schema-target.sql e
docs/analisi-frontend.md prima di procedere.

Crea DTO + Service + Controller per la gestione Owner.
Segui lo stesso pattern di TenantService/TenantController
già esistente.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/owner/OwnerListDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Campi:
    * Integer id
    * String ownerType
    * String firstName
    * String lastName
    * String legalName         ← nullable (solo piva/societa)
    * String taxCode
    * String vatNumber         ← nullable
    * String fiscalRegime      ← codice dal lookup regime_fiscale
    * String email
    * String phone
    * String iban
    * Boolean attivo
    * Integer propertiesCount  ← conteggio immobili
    * LocalDateTime createdAt

Crea dto/owner/OwnerDetailDTO.java:
- Stessi campi di OwnerListDTO più:
    * Integer fkTenantId
    * LocalDateTime updatedAt
    * Integer bookingsCount    ← conteggio prenotazioni
    * BigDecimal totalGrossAmount   ← SUM gross_amount
    * BigDecimal totalOwnerNet      ← SUM owner_net_amount
    * Integer settlementsCount ← conteggio liquidazioni

Crea dto/owner/OwnerStatusUpdateDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- Boolean attivo

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/OwnerService.java:
- @Service @Log4j2
- Costruttore con OwnerProfileDAO, PropertyDAO,
  BookingDAO, SettlementDAO, RegimeFiscaleDAO
- Metodi:

  List<OwnerListDTO> findByTenantId(Integer tenantId)
    - chiama ownerProfileDAO.findByTenantId(tenantId)
    - per ogni owner:
        * propertiesCount = propertyDAO.findByOwnerId(id).size()
    - mappa su OwnerListDTO
    - Log INFO con count

  List<OwnerListDTO> findByTenantIdAndAttivo(Integer tenantId, Boolean attivo)
    - come sopra ma filtra per attivo
    - usato dal select in PropertyCreate

  Optional<OwnerDetailDTO> findById(Integer tenantId, Integer ownerId)
    - verifica che l'owner appartenga al tenant (sicurezza)
    - arricchisce con:
        * propertiesCount
        * bookingsCount = bookingDAO.findByOwnerId(ownerId).size()
        * totalGrossAmount = SUM gross_amount dai booking
        * totalOwnerNet = SUM owner_net_amount dai booking
        * settlementsCount = settlementDAO.findByOwnerId(ownerId).size()
    - mappa su OwnerDetailDTO
    - Log INFO

  OwnerDetailDTO updateStatus(Integer tenantId, Integer ownerId, Boolean attivo)
    - verifica esistenza e appartenenza al tenant
    - stub come TenantService.updateStatus()
    - lancia UnsupportedOperationException

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea controller/OwnerController.java:
- @RestController @Log4j2
- @RequestMapping("/api/owners")
- Costruttore con OwnerService
- Per ora il tenantId è hardcoded a 1 in tutti i metodi
  con commento: // TODO: ricavare dal SecurityContext
- Endpoints:

  GET /api/owners
    - query param opzionale: ?attivo=true|false
    - se presente chiama findByTenantIdAndAttivo()
    - se assente chiama findByTenantId()
    - ResponseEntity<List<OwnerListDTO>>

  GET /api/owners/{id}
    - chiama findById(tenantId, id)
    - 200 o 404
    - ResponseEntity<OwnerDetailDTO>

  PATCH /api/owners/{id}/status
    - @RequestBody OwnerStatusUpdateDTO
    - chiama updateStatus(tenantId, id, dto.getAttivo())
    - 200 / 404 / 501

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Dopo aver creato i file lancia:
mvn -Plocal clean package

Poi riavvia Tomcat e testa:
curl -u admin:admin http://localhost:8081/sostitutoincloud/api/owners
curl -u admin:admin http://localhost:8081/sostitutoincloud/api/owners?attivo=true
curl -u admin:admin http://localhost:8081/sostitutoincloud/api/owners/1
curl -u admin:admin http://localhost:8081/sostitutoincloud/api/owners/99

Riporta l'output del build e dei curl.