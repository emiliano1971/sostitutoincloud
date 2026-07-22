# ─── Stage 1: Build ─────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Profilo Maven: prod di default, override con test su Coolify
# (è un BUILD ARG: su Coolify va impostato tra le build variables)
ARG MAVEN_PROFILE=prod

# Node.js 18 — richiesto dai profili test/prod del pom
# (exec-maven-plugin esegue npm install + npm run build)
RUN apt-get update && apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_18.x | bash - && \
    apt-get install -y nodejs && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Layer cache dipendenze Maven: rieseguito solo se pom.xml cambia
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copia il progetto (vedi .dockerignore per le esclusioni)
COPY . .

# Build con profilo da ARG. Il profilo test/prod:
#  - builda il frontend React (npm) con base path /sostitutoincloud/
#  - copia frontend/dist → target/classes/static/
#  - rinomina config.{env}.json → static/config.json
#  - rinomina db-{env}.properties → db.properties
#  - produce il WAR completo in target/
RUN mvn -P${MAVEN_PROFILE} clean package -DskipTests

# ─── Stage 2: Runtime ───────────────────────────────────────
FROM tomcat:10.1-jdk17

# Rimuovi le webapp di default di Tomcat
RUN rm -rf /usr/local/tomcat/webapps/*

# Storage per file persistenti (PDF generati)
RUN mkdir -p /app/storage/pdf

# Deploya il WAR: Tomcat lo espande sul context /sostitutoincloud
# (il WAR è l'artefatto completo: classi, lib, frontend, config.json,
#  db.properties, log4j2.xml filtrato — a differenza della cartella
#  WEB-INF/ del progetto, che è pensata solo per il deploy locale)
COPY --from=builder /build/target/*.war /usr/local/tomcat/webapps/sostitutoincloud.war

# Volume per i file persistenti
VOLUME /app/storage

EXPOSE 8080

# Default: override a runtime dalle env di Coolify
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms512m -Xmx1024m"

CMD ["catalina.sh", "run"]
