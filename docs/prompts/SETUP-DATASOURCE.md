Leggi il file CLAUDE.md prima di procedere.

Esegui le seguenti modifiche:

1. MODIFICA application-local.yml, application-test.yml, application-prod.yml
   Rimuovi completamente il blocco spring.datasource
   Il file deve rimanere solo con:
    - server.port e context-path
    - logging.config
    - app.base-url, app.cors.allowed-origins, app.security.admin-password

   Stesso per application-test.yml e application-prod.yml:
   rimuovi il blocco spring.datasource se presente

2. VERIFICA che in src/main/resources/ esistano i file:
    - db-local.properties
    - db-test.properties
    - db-prod.properties

   Se non esistono creali con questo contenuto:

   db-local.properties:
  DataSource — locale
   db.url=jdbc:postgresql://localhost:5432/sostitutoincloud
   db.username=sostitutoincloud
   db.password=postgres
   db.driver-class-name=org.postgresql.Driver
   db.pool.maximum-pool-size=5
   db.pool.minimum-idle=2

db-test.properties:
DataSource — test
db.url=jdbc:postgresql://testpms.siv.cloud.it:5432/sostitutoincloud
db.username=sostitutoincloud
db.password=CHANGE_ME
db.driver-class-name=org.postgresql.Driver
db.pool.maximum-pool-size=10
db.pool.minimum-idle=2

db-prod.properties:
db.url=jdbc:postgresql://prodpms.siv.cloud.it:5432/sostitutoincloud
db.username=sostitutoincloud
db.password=CHANGE_ME
db.driver-class-name=org.postgresql.Driver
db.pool.maximum-pool-size=20
db.pool.minimum-idle=5

3. CREA src/main/java/it/gavia/sostitutoincloud/config/DataSourceConfig.java:
    - @Configuration
    - @Log4j2
    - @PropertySource("classpath:db.properties")
      (Ant copia il file db-{profilo}.properties come db.properties
      in WEB-INF/classes/ — non usare spring.datasource nell'yml)
    - @Value per tutti i campi: db.url, db.username, db.password,
      db.driver-class-name, db.pool.maximum-pool-size, db.pool.minimum-idle
    - Bean DataSource che usa HikariCP:
      HikariConfig + HikariDataSource
    - Bean JdbcTemplate che riceve il DataSource
    - Log a livello INFO all'avvio:
      "DataSource inizializzato: {url} (pool max={maxPoolSize})"
    - NON loggare mai username o password

4. DISABILITA l'autoconfiguration Spring Boot per il datasource
   in SostitutoincloudApplication.java per evitare conflitti:
   @SpringBootApplication(exclude = {
   DataSourceAutoConfiguration.class
   })

Dopo le modifiche lancia:
mvn -Plocal clean package
e riporta l'output.

Dopo le modifiche lancia:
mvn -Plocal clean package
e riporta l'output.