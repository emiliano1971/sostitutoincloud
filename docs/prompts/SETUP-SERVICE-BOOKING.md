Leggi il file CLAUDE.md, docs/db/schema-target.sql e
docs/analisi-frontend.md prima di procedere.

Crea DTO + Service + Controller per la gestione Booking.
Segui lo stesso pattern già esistente.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/booking/BookingListDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Campi:
    * Integer id
    * String externalBookingId
    * String guestName
    * String propertyName      ← denormalizzato da property.display_name
    * String ownerName         ← denormalizzato da owner first+last name
    * String channelName       ← denormalizzato da canale_ota.nome
    * LocalDate checkinDate
    * LocalDate checkoutDate
    * Integer nights
    * Integer guests
    * BigDecimal grossAmount
    * BigDecimal ownerNetAmount
    * String statoPrenotazione ← codice da stato_prenotazione
    * String paymentStatus
    * String documentStatus    ← codice da stato_documento
    * String settlementStatus
    * LocalDateTime createdAt

Crea dto/booking/BookingDetailDTO.java:
- Tutti i campi di BookingListDTO più:
    * Integer fkTenantId
    * Integer fkPropertyId
    * Integer fkOwnerId        ← da property.fk_owner_id
    * String guestTaxCode
    * String fiscalScenarioCode ← da scenario_fiscale.codice
    * BigDecimal otaCommissionAmount
    * BigDecimal cleaningAmount
    * BigDecimal pmFeeAmount
    * BigDecimal withholdingAmount
    * BigDecimal touristTaxAmount
    * Boolean touristTaxIncludedInGross
    * String touristTaxCollection
    * LocalDateTime updatedAt
    * SplitEconomicoDTO splitEconomico ← vedi sotto

Crea dto/booking/SplitEconomicoDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Campi (tutti BigDecimal):
    * grossAmount
    * otaCommissionAmount
    * cleaningAmount
    * pmFeeAmount
    * ownerNetAmount
    * withholdingAmount
    * liquidazioneOwner     ← ownerNetAmount - withholdingAmount
    * touristTaxAmount
- Boolean touristTaxIncludedInGross

Crea dto/booking/BookingFilterDTO.java:
- Per gestire i filtri della lista
- String status            ← "imported","enriched","ready",
  "doc_issued","settled","cancelled",
  "da_completare" (filtro speciale)
- String channel           ← codice canale OTA
- String q                 ← search su guest_name, external_booking_id
- Integer page             ← default 0
- Integer size             ← default 20

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/BookingService.java:
- @Service @Log4j2
- Costruttore con BookingDAO, PropertyDAO, OwnerProfileDAO,
  CanaleOtaDAO, StatoPrenotazioneDAO, StatoDocumentoDAO,
  ScenarioFiscaleDAO

- Metodi privati helper:
  buildLookupMaps(Integer tenantId) → record interno con:
    * Map<Integer, Property> propertiesByid
    * Map<Integer, OwnerProfile> ownersByid
    * Map<Integer, CanaleOta> canaliById
    * Map<Integer, StatoPrenotazione> statiPrenotazioneById
    * Map<Integer, StatoDocumento> statiDocumentoById
      Carica tutto una volta sola — zero N+1

  toListDTO(Booking b, LookupMaps maps) → BookingListDTO
    - risolve tutti i campi denormalizzati dalle maps

  toDetailDTO(Booking b, LookupMaps maps) → BookingDetailDTO
    - come toListDTO più tutti i campi extra
    - calcola SplitEconomicoDTO:
      liquidazioneOwner = ownerNetAmount - withholdingAmount

- Metodi pubblici:

  List<BookingListDTO> findByTenantId(Integer tenantId, BookingFilterDTO filter)
    - carica LookupMaps una volta sola
    - carica tutti i booking del tenant
    - applica filtri IN MEMORIA:
        * se filter.status == "da_completare":
          checkout_date <= oggi AND statoPrenotazione NOT IN
          (doc_issued, settled, cancelled)
        * se filter.status != null e != "da_completare":
          filtra per codice stato
        * se filter.channel != null:
          filtra per codice canale
        * se filter.q != null:
          filtra su guestName CONTAINS q (case-insensitive)
          OR externalBookingId CONTAINS q
    - applica paginazione: skip(page*size).limit(size)
    - mappa su BookingListDTO
    - Log INFO: