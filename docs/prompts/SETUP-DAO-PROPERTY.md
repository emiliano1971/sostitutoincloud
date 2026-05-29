Leggi il file CLAUDE.md e il file docs/db/schema-target.sql
prima di procedere.

Crea Model + RowMapper + DAO per la tabella property
e property_ota_code.
Segui esattamente lo stesso pattern di TenantDAO già esistente.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TABELLA: property
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. MODEL: model/Property.java
    - @Data @Builder @NoArgsConstructor @AllArgsConstructor @Log4j2
    - Campi esatti da schema-target.sql
    - fk_tenant_id → Integer fkTenantId
    - fk_owner_id → Integer fkOwnerId
    - fk_pm_user_id → Integer fkPmUserId
    - fk_tipo_immobile_id → Integer fkTipoImmobileId

2. MAPPER: dao/mapper/PropertyRowMapper.java
    - Nomi colonna esatti da schema-target.sql
    - Campi nullable con rs.getObject()

3. DAO: dao/PropertyDAO.java
    - @Repository @Log4j2
    - Costruttore con JdbcTemplate
    - Metodi solo lettura:
        * findAll() → List<Property>
        * findById(Integer id) → Optional<Property>
        * findByTenantId(Integer tenantId) → List<Property>
        * findByOwnerId(Integer ownerId) → List<Property>
        * findByPmUserId(Integer pmUserId) → List<Property>
        * findByAttivo(Boolean attivo) → List<Property>
        * findByTenantIdAndAttivo(Integer tenantId, Boolean attivo) → List<Property>
        * findByCinCode(String cinCode) → Optional<Property>
    - Log DEBUG su ogni metodo
    - Usare query() per Optional, mai queryForObject()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TABELLA: property_ota_code
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. MODEL: model/PropertyOtaCode.java
    - @Data @Builder @NoArgsConstructor @AllArgsConstructor @Log4j2
    - Campi esatti da schema-target.sql
    - fk_property_id → Integer fkPropertyId
    - fk_canale_ota_id → Integer fkCanaleOtaId

2. MAPPER: dao/mapper/PropertyOtaCodeRowMapper.java
    - Nomi colonna esatti da schema-target.sql

3. DAO: dao/PropertyOtaCodeDAO.java
    - @Repository @Log4j2
    - Costruttore con JdbcTemplate
    - Metodi solo lettura:
        * findByPropertyId(Integer propertyId) → List<PropertyOtaCode>
        * findByCanaleOtaId(Integer canaleOtaId) → List<PropertyOtaCode>
        * findByPropertyIdAndCanaleOtaId(Integer propertyId, Integer canaleOtaId) → Optional<PropertyOtaCode>
    - Log DEBUG su ogni metodo

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TEST CONTROLLER
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Aggiungi al TestController esistente:
GET /api/public/test/properties
GET /api/public/test/properties/{id}
GET /api/public/test/properties/owner/{ownerId}
GET /api/public/test/property-ota-codes/{propertyId}

Dopo aver creato tutti i file lancia:
mvn -Plocal clean package
e riporta l'output del build.