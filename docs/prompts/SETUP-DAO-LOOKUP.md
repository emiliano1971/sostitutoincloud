Leggi il file CLAUDE.md e il file docs/db/schema-target.sql
prima di procedere.

Crea Model + RowMapper + DAO per tutte le tabelle lookup.
Segui esattamente lo stesso pattern di TenantDAO già esistente.

Le tabelle lookup sono:
- stato_prenotazione
- stato_documento
- regime_fiscale
- canale_ota
- tipo_immobile
- tipo_documento
- codice_tributo
- sdi_esito
- scenario_fiscale
- regola_tassa_soggiorno

Per ogni tabella crea:

1. MODEL in src/main/java/it/gavia/sostitutoincloud/model/
    - @Data @Builder @NoArgsConstructor @AllArgsConstructor @Log4j2
    - Campi esatti dalla tabella in schema-target.sql
    - Tipi Java come da convenzione:
        * SERIAL/INTEGER → Integer
        * SMALLINT → Integer
        * VARCHAR/CHAR → String
        * BOOLEAN → Boolean
        * TIMESTAMP → LocalDateTime
        * DATE → LocalDate
        * DECIMAL → BigDecimal

2. MAPPER in src/main/java/it/gavia/sostitutoincloud/dao/mapper/
    - implements RowMapper<NomeModello>
    - @Log4j2
    - Nomi colonna ESATTI da schema-target.sql
    - Per nullable: rs.getObject() con cast
    - Per TIMESTAMP: rs.getObject("created_at", LocalDateTime.class)
    - Per DATE: rs.getObject("campo", LocalDate.class)

3. DAO in src/main/java/it/gavia/sostitutoincloud/dao/
    - @Repository @Log4j2
    - Costruttore con JdbcTemplate
    - Campo private final RowMapper
    - Metodi per ogni DAO:
        * findAll() → List<T>
        * findById(Integer id) → Optional<T>
        * findByCodice(String codice) → Optional<T>
          (solo per tabelle che hanno colonna 'codice')
        * findByAttivo(Boolean attivo) → List<T>
          (solo per tabelle che hanno colonna 'attivo')
    - Log DEBUG su ogni metodo
    - Usare query() per Optional, mai queryForObject()
    - Solo lettura — niente insert/update/delete

Eccezione — regola_tassa_soggiorno NON ha colonna 'codice',
aggiungere invece:
* findByComune(String comune) → List<RegolaTassaSoggiorno>
* findAttive() → List<RegolaTassaSoggiorno>
  (WHERE attivo = true AND valida_al IS NULL
  OR valida_al >= CURRENT_DATE)

Aggiungi anche al TestController esistente
(che è @Profile("local")) un endpoint per ogni DAO:
GET /api/public/test/stato-prenotazione
GET /api/public/test/stato-documento
GET /api/public/test/regime-fiscale
GET /api/public/test/canale-ota
GET /api/public/test/tipo-immobile
GET /api/public/test/tipo-documento
GET /api/public/test/codice-tributo
GET /api/public/test/sdi-esito
GET /api/public/test/scenario-fiscale
GET /api/public/test/regole-tassa-soggiorno

Dopo aver creato tutti i file lancia:
mvn -Plocal clean package
e riporta l'output del build.