Leggi il file CLAUDE.md e il file docs/db/schema-target.sql
prima di procedere.

Crea Model + RowMapper + DAO per le tabelle f24_record,
cu_record e audit_log.
Segui esattamente lo stesso pattern di TenantDAO già esistente.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TABELLA: f24_record
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. MODEL: model/F24Record.java
    - @Data @Builder @NoArgsConstructor @AllArgsConstructor @Log4j2
    - Campi esatti da schema-target.sql
    - fk_tenant_id → Integer fkTenantId
    - fk_owner_id → Integer fkOwnerId
    - fk_codice_tributo_id → Integer fkCodiceTributoId
    - f24_status → String (enum PostgreSQL → String)
    - period → String (CHAR(7) formato YYYY-MM)
    - payment_date → LocalDate (nullable)
    - created_at / updated_at → LocalDateTime

2. MAPPER: dao/mapper/F24RecordRowMapper.java
    - Nomi colonna esatti da schema-target.sql
    - payment_date nullable: rs.getObject("payment_date", LocalDate.class)
    - file_path nullable: rs.getString("file_path")

3. DAO: dao/F24RecordDAO.java
    - @Repository @Log4j2
    - Costruttore con JdbcTemplate
    - Metodi solo lettura:
        * findAll() → List<F24Record>
        * findById(Integer id) → Optional<F24Record>
        * findByTenantId(Integer tenantId) → List<F24Record>
        * findByOwnerId(Integer ownerId) → List<F24Record>
        * findByTenantIdAndOwnerId(Integer tenantId, Integer ownerId) → List<F24Record>
        * findByTenantIdAndPeriod(Integer tenantId, String period) → List<F24Record>
        * findByTenantIdAndStatus(Integer tenantId, String status) → List<F24Record>
    - Log DEBUG su ogni metodo
    - Usare query() per Optional, mai queryForObject()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TABELLA: cu_record
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. MODEL: model/CuRecord.java
    - @Data @Builder @NoArgsConstructor @AllArgsConstructor @Log4j2
    - Campi esatti da schema-target.sql
    - fk_tenant_id → Integer fkTenantId
    - fk_owner_id → Integer fkOwnerId
    - cu_status → String (enum PostgreSQL → String)
    - tax_year → Integer
    - transmission_date → LocalDate (nullable)
    - created_at / updated_at → LocalDateTime

2. MAPPER: dao/mapper/CuRecordRowMapper.java
    - Nomi colonna esatti da schema-target.sql
    - transmission_date nullable: rs.getObject("transmission_date", LocalDate.class)
    - file_path nullable: rs.getString("file_path")
    - transmission_id nullable: rs.getString("transmission_id")

3. DAO: dao/CuRecordDAO.java
    - @Repository @Log4j2
    - Costruttore con JdbcTemplate
    - Metodi solo lettura:
        * findAll() → List<CuRecord>
        * findById(Integer id) → Optional<CuRecord>
        * findByTenantId(Integer tenantId) → List<CuRecord>
        * findByOwnerId(Integer ownerId) → List<CuRecord>
        * findByTenantIdAndOwnerId(Integer tenantId, Integer ownerId) → List<CuRecord>
        * findByTenantIdAndTaxYear(Integer tenantId, Integer taxYear) → List<CuRecord>
        * findByTenantIdAndStatus(Integer tenantId, String status) → List<CuRecord>
    - Log DEBUG su ogni metodo
    - Usare query() per Optional, mai queryForObject()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TABELLA: audit_log
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. MODEL: model/AuditLog.java
    - @Data @Builder @NoArgsConstructor @AllArgsConstructor @Log4j2
    - Campi esatti da schema-target.sql
    - fk_tenant_id → Integer fkTenantId (nullable)
    - fk_user_id → Integer fkUserId (nullable)
    - Tabella IMMUTABILE — solo created_at, NO updated_at
    - old_values / new_values → String (JSON come stringa)

2. MAPPER: dao/mapper/AuditLogRowMapper.java
    - Nomi colonna esatti da schema-target.sql
    - Tutti i campi nullable con rs.getObject() o rs.getString()

3. DAO: dao/AuditLogDAO.java
    - @Repository @Log4j2
    - Costruttore con JdbcTemplate
    - Metodi solo lettura:
        * findByTenantId(Integer tenantId) → List<AuditLog>
        * findByUserId(Integer userId) → List<AuditLog>
        * findByTenantIdAndEntityName(Integer tenantId, String entityName) → List<AuditLog>
        * findByTenantIdAndEntityId(Integer tenantId, Integer entityId) → List<AuditLog>
        * findByTenantIdOrderByCreatedAtDesc(Integer tenantId, Integer limit) → List<AuditLog>
          (SELECT ... WHERE fk_tenant_id=? ORDER BY created_at DESC LIMIT ?)
    - Log DEBUG su ogni metodo
    - NON esporre nel TestController — audit_log è dati sensibili

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TEST CONTROLLER
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Aggiungi al TestController esistente:
GET /api/public/test/f24-records/tenant/{tenantId}
GET /api/public/test/cu-records/tenant/{tenantId}
NON aggiungere endpoint per audit_log

Dopo aver creato tutti i file lancia:
mvn -Plocal clean package
e riporta l'output del build.