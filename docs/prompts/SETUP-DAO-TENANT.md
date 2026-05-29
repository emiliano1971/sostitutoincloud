Leggi il file CLAUDE.md e il file docs/db/schema-target.sql
prima di procedere.

- Per ora implementare SOLO metodi di lettura (find*)
- Insert, update e delete verranno aggiunti in una fase successiva

Crea i seguenti file per la gestione del Tenant:

1. MODELLO: src/main/java/it/gavia/sostitutoincloud/model/Tenant.java
    - @Data @Builder @NoArgsConstructor @AllArgsConstructor @Log4j2
    - Campi esatti dalla tabella tenant in schema-target.sql
    - Tipi Java:
        * SERIAL/INTEGER → Integer
        * VARCHAR/CHAR → String
        * BOOLEAN → Boolean
        * TIMESTAMP → LocalDateTime
        * DATE → LocalDate
        * DECIMAL → BigDecimal
    - Nomi campi in camelCase da snake_case SQL
      (es. legal_name → legalName, created_at → createdAt)

2. MAPPER: src/main/java/it/gavia/sostitutoincloud/dao/mapper/TenantRowMapper.java
    - implements RowMapper<Tenant>
    - @Log4j2
    - mapRow() mappa ogni colonna SQL → campo Java
    - Usa i nomi colonna ESATTI da schema-target.sql
    - Per colonne nullable usa rs.getObject() con cast appropriato
    - Per TIMESTAMP: rs.getObject("created_at", LocalDateTime.class)
    - Per DATE: rs.getObject("activated_at", LocalDate.class)

3. DAO: src/main/java/it/gavia/sostitutoincloud/dao/TenantDAO.java
    - @Repository @Log4j2
    - Costruttore con JdbcTemplate (non @Autowired sul campo)
    - Istanza TenantRowMapper come campo privato final
    - Metodi:
        * findAll() → List<Tenant>
        * findById(Integer id) → Optional<Tenant>
        * findByEmail(String email) → Optional<Tenant>
        * existsById(Integer id) → boolean
    - Query SQL con nomi colonna esatti da schema-target.sql
    - Log DEBUG su ogni metodo:
      "TenantDAO.findAll() - trovati {} record"
      "TenantDAO.findById() - id={}"
    - Per Optional usare query() e controllare se la lista è vuota
      NON usare queryForObject() che lancia eccezione se non trova

4. Dopo aver creato i file lancia:
   mvn -Plocal clean package
   e riporta l'output del build.