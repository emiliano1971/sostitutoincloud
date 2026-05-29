Leggi il file CLAUDE.md e il file docs/db/schema-target.sql
prima di procedere.

Crea Model + RowMapper + DAO per le tabelle utente e owner_profile.
Segui esattamente lo stesso pattern di TenantDAO già esistente.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TABELLA: utente
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. MODEL: model/Utente.java
    - @Data @Builder @NoArgsConstructor @AllArgsConstructor @Log4j2
    - Campi esatti da schema-target.sql
    - NON includere il campo password_hash nel Model —
      non deve mai circolare nell'applicazione
    - fk_tenant_id → Integer fkTenantId
    - fk_owner_id → Integer fkOwnerId (nullable)
    - ruolo è un enum PostgreSQL — mapparlo come String in Java

2. MAPPER: dao/mapper/UtenteRowMapper.java
    - Nomi colonna esatti da schema-target.sql
    - password_hash NON mappato
    - fk_owner_id nullable: rs.getObject("fk_owner_id", Integer.class)

3. DAO: dao/UtenteDAO.java
    - @Repository @Log4j2
    - Costruttore con JdbcTemplate
    - Metodi solo lettura:
        * findAll() → List<Utente>
        * findById(Integer id) → Optional<Utente>
        * findByEmail(String email) → Optional<Utente>
        * findByTenantId(Integer tenantId) → List<Utente>
        * findByRuolo(String ruolo) → List<Utente>
        * findByTenantIdAndRuolo(Integer tenantId, String ruolo) → List<Utente>
        * existsByEmail(String email) → boolean
    - Log DEBUG su ogni metodo
    - Usare query() per Optional, mai queryForObject()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TABELLA: owner_profile
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. MODEL: model/OwnerProfile.java
    - @Data @Builder @NoArgsConstructor @AllArgsConstructor @Log4j2
    - Campi esatti da schema-target.sql
    - fk_tenant_id → Integer fkTenantId
    - fk_regime_fiscale_id → Integer fkRegimeFiscaleId
    - owner_type è un enum PostgreSQL — mapparlo come String

2. MAPPER: dao/mapper/OwnerProfileRowMapper.java
    - Nomi colonna esatti da schema-target.sql
    - Tutti i campi nullable con rs.getObject()

3. DAO: dao/OwnerProfileDAO.java
    - @Repository @Log4j2
    - Costruttore con JdbcTemplate
    - Metodi solo lettura:
        * findAll() → List<OwnerProfile>
        * findById(Integer id) → Optional<OwnerProfile>
        * findByTenantId(Integer tenantId) → List<OwnerProfile>
        * findByTaxCode(String taxCode) → Optional<OwnerProfile>
        * findByAttivo(Boolean attivo) → List<OwnerProfile>
        * findByTenantIdAndAttivo(Integer tenantId, Boolean attivo) → List<OwnerProfile>
    - Log DEBUG su ogni metodo
    - Usare query() per Optional, mai queryForObject()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TEST CONTROLLER
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Aggiungi al TestController esistente:
GET /api/public/test/utenti
GET /api/public/test/utenti/{id}
GET /api/public/test/owner-profiles
GET /api/public/test/owner-profiles/{id}

Dopo aver creato tutti i file lancia:
mvn -Plocal clean package
e riporta l'output del build.