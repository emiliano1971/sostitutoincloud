Leggi il file CLAUDE.md e crea la classe principale
Spring Boot e la configurazione base:

1. Crea la classe principale in:
   src/main/java/it/gavia/cloud/sostitutoincloud/SostitutoincloudApplication.java
    - Estende SpringBootServletInitializer
    - Annota con @SpringBootApplication
    - Override di configure() per deploy su Tomcat
    - Metodo main() standard

2. Crea il file application.yml base in src/main/resources/:
    - spring.application.name: sostitutoincloud
    - spring.profiles.active: @spring.profiles.active@
    - server.port e context-path NON qui — vanno nei profili
    - Nessuna configurazione datasource qui

3. Crea application-local.yml con:
    - server.port: 8081
    - server.servlet.context-path: /sostitutoincloud
    - spring.datasource: url, username, password placeholder
      (jdbc:postgresql://localhost:5432/sostitutoincloud)
    - logging.config: classpath:log4j2.xml
    - app.base-url: http://localhost:8081/sostitutoincloud
    - app.cors.allowed-origins: http://localhost:5173

4. Crea application-test.yml con:
    - server.port: 8443
    - server.servlet.context-path: /sostitutoincloud
    - spring.datasource: placeholder
    - app.base-url: https://testpms.siv.cloud.it:8443/sostitutoincloud
    - app.cors.allowed-origins: https://testpms.siv.cloud.it:8443

5. Crea application-prod.yml con:
    - server.port: 8443
    - server.servlet.context-path: /sostitutoincloud
    - spring.datasource: placeholder
    - app.base-url: https://prodpms.siv.cloud.it:8443/sostitutoincloud
    - app.cors.allowed-origins: https://prodpms.siv.cloud.it:8443

6. Crea la classe config/AppConfig.java con:
    - @Configuration
    - @Value per app.base-url e app.cors.allowed-origins
    - Bean CorsConfigurationSource con le origini dal yml
    - Attivo CORS solo se profilo = local

7. Crea la classe config/SecurityConfig.java con:
    - @Configuration @EnableWebSecurity
    - SecurityFilterChain con:
        * /sostitutoincloud/api/public/** → permitAll()
        * /sostitutoincloud/api/** → authenticated()
        * /** → permitAll() (frontend statico)
    - CSRF disabilitato per API REST
    - Per ora autenticazione base in memoria
      (un utente admin con password da yml)
    - CORS configurato tramite AppConfig

Dopo aver creato i file lancia:
mvn -Plocal -Dskip.frontend=true clean package
e riporta l'output del build.