Leggi il file CLAUDE.md e il file docs/db/schema-target.sql
prima di procedere.

Crea Model + RowMapper + DAO per le tabelle fiscal_document,
settlement e settlement_booking.
Segui esattamente lo stesso pattern di TenantDAO già esistente.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TABELLA: fiscal_document
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. MODEL: model/FiscalDocument.java
    - @Data @Builder @NoArgsConstructor @AllArgsConstructor @Log4j2
    - Campi esatti da schema-target.sql
    - fk_tenant_id → Integer fkTenantId
    - fk_booking_id → Integer fkBookingId (nullable)
    - fk_tipo_documento_id → Integer fkTipoDocumentoId
    - fk_sdi_esito_id → Integer fkSdiEsitoId (nullable)
    - fk_stato_documento_id → Integer fkStatoDocumentoId
    - issue_date → LocalDate
    - created_at / updated_at → LocalDateTime

2. MAPPER: dao/mapper/FiscalDocumentRowMapper.java
    - Nomi colonna esatti da schema-target.sql
    - Campi nullable con rs.getObject()

3. DAO: dao/FiscalDocumentDAO.java
    - @Repository @Log4j2
    - Costruttore con JdbcTemplate
    - Metodi solo lettura:
        * findAll() → List<FiscalDocument>
        * findById(Integer id) → Optional<FiscalDocument>
        * findByTenantId(Integer tenantId) → List<FiscalDocument>
        * findByBookingId(Integer bookingId) → List<FiscalDocument>
        * findByDocumentNumber(String documentNumber) → Optional<FiscalDocument>
        * findByTenantIdAndStatoDocumentoId(Integer tenantId, Integer statoId) → List<FiscalDocument>
        * findByTenantIdAndIssueDateBetween(Integer tenantId, LocalDate from, LocalDate to) → List<FiscalDocument>
        * findBySdiIdentifier(String sdiIdentifier) → Optional<FiscalDocument>
    - Log DEBUG su ogni metodo
    - Usare query() per Optional, mai queryForObject()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TABELLA: settlement
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. MODEL: model/Settlement.java
    - @Data @Builder @NoArgsConstructor @AllArgsConstructor @Log4j2
    - Campi esatti da schema-target.sql
    - fk_tenant_id → Integer fkTenantId
    - fk_owner_id → Integer fkOwnerId
    - settlement_status → String (enum PostgreSQL → String)
    - period_from / period_to → LocalDate
    - payment_date → LocalDate (nullable)
    - created_at / updated_at → LocalDateTime

2. MAPPER: dao/mapper/SettlementRowMapper.java
    - Nomi colonna esatti da schema-target.sql
    - payment_date nullable: rs.getObject("payment_date", LocalDate.class)

3. DAO: dao/SettlementDAO.java
    - @Repository @Log4j2
    - Costruttore con JdbcTemplate
    - Metodi solo lettura:
        * findAll() → List<Settlement>
        * findById(Integer id) → Optional<Settlement>
        * findByTenantId(Integer tenantId) → List<Settlement>
        * findByOwnerId(Integer ownerId) → List<Settlement>
        * findByTenantIdAndOwnerId(Integer tenantId, Integer ownerId) → List<Settlement>
        * findByTenantIdAndStatus(Integer tenantId, String status) → List<Settlement>
        * findByPeriodOverlap(Integer tenantId, Integer ownerId, LocalDate from, LocalDate to) → List<Settlement>
          (WHERE fk_tenant_id=? AND fk_owner_id=?
          AND period_from <= ? AND period_to >= ?)
    - Log DEBUG su ogni metodo
    - Usare query() per Optional, mai queryForObject()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TABELLA: settlement_booking
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. MODEL: model/SettlementBooking.java
    - @Data @Builder @NoArgsConstructor @AllArgsConstructor @Log4j2
    - Campi esatti da schema-target.sql
    - fk_settlement_id → Integer fkSettlementId
    - fk_booking_id → Integer fkBookingId
    - Solo created_at (NON updated_at — tabella immutabile)

2. MAPPER: dao/mapper/SettlementBookingRowMapper.java
    - Nomi colonna esatti da schema-target.sql

3. DAO: dao/SettlementBookingDAO.java
    - @Repository @Log4j2
    - Costruttore con JdbcTemplate
    - Metodi solo lettura:
        * findBySettlementId(Integer settlementId) → List<SettlementBooking>
        * findByBookingId(Integer bookingId) → List<SettlementBooking>
        * existsBySettlementIdAndBookingId(Integer settlementId, Integer bookingId) → boolean

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TEST CONTROLLER
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Aggiungi al TestController esistente:
GET /api/public/test/fiscal-documents
GET /api/public/test/fiscal-documents/{id}
GET /api/public/test/fiscal-documents/booking/{bookingId}
GET /api/public/test/settlements
GET /api/public/test/settlements/{id}
GET /api/public/test/settlements/owner/{ownerId}

Dopo aver creato tutti i file lancia:
mvn -Plocal clean package
e riporta l'output del build.