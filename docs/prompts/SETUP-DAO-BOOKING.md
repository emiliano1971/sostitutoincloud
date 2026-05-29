Leggi il file CLAUDE.md e il file docs/db/schema-target.sql
prima di procedere.

Crea Model + RowMapper + DAO per la tabella booking.
Segui esattamente lo stesso pattern di TenantDAO già esistente.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TABELLA: booking
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. MODEL: model/Booking.java
    - @Data @Builder @NoArgsConstructor @AllArgsConstructor @Log4j2
    - Campi esatti da schema-target.sql
    - Tipi Java:
        * fk_tenant_id → Integer fkTenantId
        * fk_property_id → Integer fkPropertyId
        * fk_canale_ota_id → Integer fkCanaleOtaId (nullable)
        * fk_scenario_fiscale_id → Integer fkScenarioFiscaleId (nullable)
        * fk_stato_prenotazione_id → Integer fkStatoPrenotazioneId
        * fk_stato_documento_id → Integer fkStatoDocumentoId (nullable)
        * checkin_date → LocalDate
        * checkout_date → LocalDate
        * nights → Integer
        * guests → Integer
        * gross_amount → BigDecimal
        * ota_commission_amount → BigDecimal (nullable)
        * cleaning_amount → BigDecimal (nullable)
        * pm_fee_amount → BigDecimal (nullable)
        * owner_net_amount → BigDecimal (nullable)
        * withholding_amount → BigDecimal (nullable)
        * tourist_tax_amount → BigDecimal (nullable)
        * tourist_tax_included_in_gross → Boolean
        * tourist_tax_collection → String (enum PostgreSQL → String)
        * payment_status → String (enum PostgreSQL → String)
        * settlement_status → String (enum PostgreSQL → String)
        * created_at → LocalDateTime
        * updated_at → LocalDateTime

2. MAPPER: dao/mapper/BookingRowMapper.java
    - Nomi colonna ESATTI da schema-target.sql
    - Per tutti i campi nullable usare rs.getObject()
    - Per BigDecimal nullable: rs.getObject("campo", BigDecimal.class)
    - Per Integer nullable: rs.getObject("campo", Integer.class)
    - Per Boolean: rs.getBoolean("campo")
    - Per DATE: rs.getObject("checkin_date", LocalDate.class)
    - Per TIMESTAMP: rs.getObject("created_at", LocalDateTime.class)

3. DAO: dao/BookingDAO.java
    - @Repository @Log4j2
    - Costruttore con JdbcTemplate
    - Metodi solo lettura:
        * findAll() → List<Booking>
        * findById(Integer id) → Optional<Booking>
        * findByTenantId(Integer tenantId) → List<Booking>
        * findByPropertyId(Integer propertyId) → List<Booking>
        * findByOwnerId(Integer ownerId) → List<Booking>
          (JOIN con property su fk_owner_id)
        * findByStatoPrenotazioneId(Integer statoId) → List<Booking>
        * findByTenantIdAndStatoPrenotazioneId(Integer tenantId, Integer statoId) → List<Booking>
        * findByGuestTaxCode(String taxCode) → List<Booking>
        * findByExternalBookingId(String externalId) → Optional<Booking>
        * findByCheckinDateBetween(LocalDate from, LocalDate to) → List<Booking>
        * findByTenantIdAndCheckinDateBetween(Integer tenantId, LocalDate from, LocalDate to) → List<Booking>
        * countByTenantIdAndStatoPrenotazioneId(Integer tenantId, Integer statoId) → Integer
    - Log DEBUG su ogni metodo con parametri significativi
    - Usare query() per Optional, mai queryForObject()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TEST CONTROLLER
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Aggiungi al TestController esistente:
GET /api/public/test/bookings
GET /api/public/test/bookings/{id}
GET /api/public/test/bookings/tenant/{tenantId}
GET /api/public/test/bookings/property/{propertyId}

Dopo aver creato tutti i file lancia:
mvn -Plocal clean package
e riporta l'output del build.