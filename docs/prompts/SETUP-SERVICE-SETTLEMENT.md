Leggi il file CLAUDE.md, docs/db/schema-target.sql e
docs/analisi-frontend.md prima di procedere.

Crea DTO + Service + Controller per la gestione Settlement.
Segui lo stesso pattern già esistente.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/settlement/SettlementListDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Campi:
    * Integer id
    * String ownerName         ← first_name + ' ' + last_name
    * String period            ← YYYY-MM
    * BigDecimal totalAmount
    * BigDecimal withholdingAmount
    * BigDecimal netAmount     ← totalAmount - withholdingAmount
    * Integer bookingsCount    ← count da settlement_booking
    * String stato             ← codice settlement_status
    * LocalDate paymentDate    ← nullable
    * LocalDateTime createdAt

Crea dto/settlement/SettlementDetailDTO.java:
- Stessi campi di SettlementListDTO più:
    * Integer fkTenantId
    * Integer fkOwnerId
    * LocalDateTime updatedAt
    * List<SettlementBookingDTO> bookings ← prenotazioni incluse

Crea dto/settlement/SettlementBookingDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Campi:
    * Integer bookingId
    * String externalBookingId
    * String propertyName
    * LocalDate checkinDate
    * LocalDate checkoutDate
    * BigDecimal grossAmount
    * BigDecimal ownerNetAmount
    * BigDecimal withholdingAmount

Crea dto/settlement/SettlementStatusUpdateDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- String stato   ← "pending"|"calculated"|"approved"|"paid"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/SettlementService.java:
- @Service @Log4j2
- Costruttore con SettlementDAO, SettlementBookingDAO,
  OwnerProfileDAO, BookingDAO, PropertyDAO

- Metodi pubblici:

  List<SettlementListDTO> findByTenantId(Integer tenantId,
  Integer ownerId, String period)
    - carica settlements del tenant
    - se ownerId != null: filtra per owner
    - se period != null: filtra per period
    - per ogni settlement:
        * ownerName da ownerProfileDAO
          (carica tutti gli owner del tenant una volta — no N+1)
        * bookingsCount = settlementBookingDAO
          .findBySettlementId(id).size()
        * netAmount = totalAmount - withholdingAmount
    - mappa su SettlementListDTO
    - Log INFO

  Optional<SettlementDetailDTO> findById(Integer tenantId,
  Integer settlementId)
    - verifica appartenenza al tenant
    - carica owner per ownerName
    - carica settlement_booking per il dettaglio
    - per ogni settlement_booking:
        * carica il booking associato
        * risolve propertyName da property
    - calcola netAmount
    - mappa su SettlementDetailDTO con lista bookings
    - Log INFO

  SettlementListDTO updateStatus(Integer tenantId,
  Integer settlementId, String nuovoStato)
    - verifica esistenza e appartenenza al tenant
    - verifica che nuovoStato sia valido:
      "pending","calculated","approved","paid"
    - stub — lancia UnsupportedOperationException
    - Log WARN

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea controller/SettlementController.java:
- @RestController @Log4j2
- @RequestMapping("/api/settlements")
- Costruttore con SettlementService
- tenantId hardcoded a 1 // TODO: SecurityContext
- Endpoints:

  GET /api/settlements
    - query params opzionali: ownerId, period
    - ResponseEntity<List<SettlementListDTO>>

  GET /api/settlements/{id}
    - 200 o 404
    - ResponseEntity<SettlementDetailDTO>

  PATCH /api/settlements/{id}/status
    - @RequestBody SettlementStatusUpdateDTO
    - 200 / 404 / 400 / 501

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Dopo aver creato i file lancia:
mvn -Plocal clean package

Poi riavvia Tomcat e testa:
curl -u admin:admin \
"http://localhost:8081/sostitutoincloud/api/settlements"
curl -u admin:admin \
"http://localhost:8081/sostitutoincloud/api/settlements/1"
curl -u admin:admin \
"http://localhost:8081/sostitutoincloud/api/settlements/99"

Riporta l'output del build e dei curl.