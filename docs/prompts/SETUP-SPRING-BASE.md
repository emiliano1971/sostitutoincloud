Leggi il file CLAUDE.md e crea la struttura base del
progetto Spring Boot con le seguenti istruzioni:

1. Crea il file pom.xml nella root del progetto con:
    - groupId: it.gavia.cloud
    - artifactId: sostitutoincloud
    - packaging: war
    - Java 21, Spring Boot 3.5.x
    - Profili: local (default), test, prod
    - Dipendenze: spring-boot-starter-web,
      spring-boot-starter-security,
      spring-boot-starter-jdbc,
      postgresql driver,
      lombok,
      spring-boot-starter-log4j2 (con esclusione logback)
    - maven-antrun-plugin con riferimento a my-build.xml
    - Resource filtering abilitato su src/main/resources
    - Properties per profilo: spring.profiles.active,
      deploy.env, db.properties.source, log4j.source, log.level

2. Crea la struttura cartelle Java:
   src/main/java/it/gavia/cloud/sostitutoincloud/
   ├── controller/
   ├── service/
   ├── dao/
   │   └── mapper/
   ├── model/
   └── config/

3. Crea i file yml vuoti:
   src/main/resources/application.yml
   src/main/resources/application-local.yml
   src/main/resources/application-test.yml
   src/main/resources/application-prod.yml

4. Crea il file my-build.xml nella root

Non scrivere ancora nessuna classe Java —
solo struttura e configurazione.