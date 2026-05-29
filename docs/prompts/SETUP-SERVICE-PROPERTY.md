Leggi il file CLAUDE.md, docs/db/schema-target.sql e
docs/analisi-frontend.md prima di procedere.

Crea DTO + Service + Controller per la gestione Property.
Segui lo stesso pattern di OwnerService/OwnerController
già esistente.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/property/PropertyListDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Campi:
    * Integer id
    * String internalCode
    * String displayName
    * String address
    * String city
    * String region
    * String propertyType      ← codice lookup tipo_immobile
    * String cinCode
    * Boolean attivo
    * String ownerName         ← first_name + ' ' + last_name dell'owner
    * Integer listingsCount    ← count da property_ota_code
    * Integer bookingsCount    ← count booking
    * List<OtaCodeDTO> otaCodes ← vedi sotto
    * LocalDateTime createdAt

Crea dto/property/OtaCodeDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Campi:
    * String canaleCodiceName  ← nome canale OTA (es. "Airbnb")
    * String externalId        ← codice esterno su quel canale

Crea dto/property/PropertyDetailDTO.java:
- Stessi campi di PropertyListDTO più:
    * Integer fkTenantId
    * Integer fkOwnerId
    * Integer fkPmUserId
    * LocalDateTime updatedAt

Crea dto/property/PropertyCreateDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- Campi obbligatori:
    * String displayName
    * String internalCode
    * Integer fkTipoImmobileId
    * String city
- Campi opzionali:
    * String address
    * String region
    * String cinCode
    * Integer fkOwnerId
    * List<OtaCodeDTO> otaCodes

Crea dto/property/PropertyStatusUpdateDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- Boolean attivo

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/PropertyService.java:
- @Service @Log4j2
- Costruttore con PropertyDAO, PropertyOtaCodeDAO,
  OwnerProfileDAO, BookingDAO, CanaleOtaDAO, TipoImmobileDAO
- Metodi:

  List<PropertyListDTO> findByTenantId(Integer tenantId)
    - chiama propertyDAO.findByTenantId(tenantId)
    - per ogni property:
        * otaCodes = propertyOtaCodeDAO.findByPropertyId(id)
          mappati su OtaCodeDTO con nome canale risolto da CanaleOtaDAO
          (carica tutti i canali una volta sola — no N+1)
        * ownerName = ownerProfileDAO.findById(fkOwnerId)
          → first_name + ' ' + last_name
          (carica tutti gli owner del tenant una volta sola — no N+1)
        * listingsCount = otaCodes.size()
        * bookingsCount = bookingDAO.findByPropertyId(id).size()
    - mappa su PropertyListDTO
    - Log INFO con count

  List<PropertyListDTO> findByTenantIdAndAttivo(Integer tenantId, Boolean attivo)
    - come sopra ma filtra per attivo

  Optional<PropertyDetailDTO> findById(Integer tenantId, Integer propertyId)
    - verifica appartenenza al tenant
    - arricchisce con tutti i campi come findByTenantId
    - mappa su PropertyDetailDTO
    - Log INFO

  PropertyDetailDTO create(Integer tenantId, PropertyCreateDTO dto)
    - stub — lancia UnsupportedOperationException
    - Log WARN

  PropertyDetailDTO updateStatus(Integer tenantId, Integer propertyId, Boolean attivo)
    - stub — lancia UnsupportedOperationException
    - Log WARN

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea controller/PropertyController.java:
- @RestController @Log4j2
- @RequestMapping("/api/properties")
- Costruttore con PropertyService
- tenantId hardcoded a 1 con commento // TODO: SecurityContext
- Endpoints:

  GET /api/properties
    - query param opzionale: ?attivo=true|false
    - ResponseEntity<List<PropertyListDTO>>

  GET /api/properties/{id}
    - 200 o 404
    - ResponseEntity<PropertyDetailDTO>

  POST /api/properties
    - @RequestBody PropertyCreateDTO
    - chiama propertyService.create()
    - 501 NOT IMPLEMENTED per ora

  PATCH /api/properties/{id}/status
    - @RequestBody PropertyStatusUpdateDTO
    - 200 / 404 / 501

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Dopo aver creato i file lancia:
mvn -Plocal clean package

Poi riavvia Tomcat e testa:
curl -u admin:admin http://localhost:8081/sostitutoincloud/api/properties
curl -u admin:admin http://localhost:8081/sostitutoincloud/api/properties/1
curl -u admin:admin http://localhost:8081/sostitutoincloud/api/properties/99

Riporta l'output del build e dei curl.