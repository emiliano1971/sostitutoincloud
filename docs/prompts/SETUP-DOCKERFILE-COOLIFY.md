Leggi il file CLAUDE.md, pom.xml e my-build.xml
prima di procedere.

Crea Dockerfile multi-stage per deploy su Coolify.
Il build replica esattamente la struttura che
my-build.xml crea su Tomcat locale.
Il PostgreSQL 18 e l'app girano su container
separati gestiti manualmente su Coolify.
Supporta due ambienti: test e prod.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DOCKERFILE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea Dockerfile nella root del progetto:

# ─── Stage 1: Build ───────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Build argument per profilo Maven
# default prod, override con test su Coolify
ARG MAVEN_PROFILE=prod

# Installa Node.js 18
RUN apt-get update && apt-get install -y \
curl && \
curl -fsSL https://deb.nodesource.com/setup_18.x \
| bash - && \
apt-get install -y nodejs && \
apt-get clean && \
rm -rf /var/lib/apt/lists/*

# Copia pom.xml e scarica dipendenze
# (layer cache — rieseguito solo se pom.xml cambia)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copia tutto il progetto
COPY . .

# Build Maven con profilo da ARG
# (include build frontend React)
RUN mvn -P${MAVEN_PROFILE} clean package \
-DskipTests

# ─── Stage 2: Runtime ─────────────────────
FROM tomcat:10.1-jdk17

# Rimuovi app di default Tomcat
RUN rm -rf /usr/local/tomcat/webapps/*

# Crea struttura app in webapps + storage
RUN mkdir -p \
/usr/local/tomcat/webapps/sostitutoincloud/\
WEB-INF/classes \
/usr/local/tomcat/webapps/sostitutoincloud/\
WEB-INF/lib \
/app/storage/pdf

# Copia struttura WEB-INF compilata dallo
# stage build (my-build.xml ha popolato
# WEB-INF/ nella root del progetto)
COPY --from=builder \
/build/WEB-INF/ \
/usr/local/tomcat/webapps/sostitutoincloud/\
WEB-INF/

# Volume per file persistenti (PDF generati)
VOLUME /app/storage

# Porta Tomcat
EXPOSE 8080

# Variabili ambiente (override a runtime
# da Coolify per ogni ambiente)
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms512m -Xmx1024m"

CMD ["catalina.sh", "run"]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. application.yml (base comune)
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi in src/main/resources/application.yml
la sezione storage — comune a tutti i profili:

app:
storage:
base-path: /app/storage
pdf-path: /app/storage/pdf

In src/main/resources/application-local.yml
aggiungi override per sviluppo locale:

app:
storage:
base-path: /tmp/sostitutoincloud/storage
pdf-path: /tmp/sostitutoincloud/storage/pdf

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. db-test.properties e db-prod.properties
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica entrambi i file per leggere
le credenziali da variabili d'ambiente
impostate su Coolify.

src/main/resources/db-test.properties:
db.url=${DB_URL:jdbc:postgresql://localhost:5432/sostitutoincloud}
db.username=${DB_USERNAME:sostitutoincloud}
db.password=${DB_PASSWORD:sostitutoincloud}
db.driver-class-name=org.postgresql.Driver
db.pool.maximum-pool-size=5
db.pool.minimum-idle=1

src/main/resources/db-prod.properties:
db.url=${DB_URL:jdbc:postgresql://localhost:5432/sostitutoincloud}
db.username=${DB_USERNAME:sostitutoincloud}
db.password=${DB_PASSWORD:sostitutoincloud}
db.driver-class-name=org.postgresql.Driver
db.pool.maximum-pool-size=10
db.pool.minimum-idle=2

Verifica che HikariCP/Spring legga
correttamente il formato ${VAR:default}.
Se non supportato usa environment variables
nel DataSource bean con @Value.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. config.test.json e config.prod.json
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica entrambi i file per usare
path relativo — funziona con qualsiasi
dominio assegnato da Coolify:

frontend/public/config.test.json:
{
"apiBaseUrl": "/sostitutoincloud/api",
"environment": "test"
}

frontend/public/config.prod.json:
{
"apiBaseUrl": "/sostitutoincloud/api",
"environment": "prod"
}

Frontend e backend sono sullo stesso
container quindi il path relativo
funziona sempre indipendentemente
dal dominio Coolify.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. .dockerignore
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea .dockerignore nella root:

target/
node_modules/
frontend/node_modules/
frontend/dist/
.git/
logs/
*.log
WEB-INF/classes/
WEB-INF/lib/

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. README-INSTALL.md — sezione Coolify
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi in README-INSTALL.md una sezione
"## Deploy su Coolify":

### Container PostgreSQL 18
Immagine: postgres:18
Variabili d'ambiente:
POSTGRES_DB=sostitutoincloud
POSTGRES_USER=sostitutoincloud
POSTGRES_PASSWORD={password}
Volume: postgres_data → /var/lib/postgresql/data
Esegui dopo il primo avvio:
psql -U sostitutoincloud -d sostitutoincloud \
-f install.sql

### Container Applicazione
Immagine: build da Dockerfile nel repo GitHub

Variabili d'ambiente ambiente TEST:
MAVEN_PROFILE=test
SPRING_PROFILES_ACTIVE=test
DB_URL=jdbc:postgresql://{host-postgres}:5432/sostitutoincloud
DB_USERNAME=sostitutoincloud
DB_PASSWORD={password}
JAVA_OPTS=-Xms512m -Xmx1024m

Variabili d'ambiente ambiente PROD:
MAVEN_PROFILE=prod
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://{host-postgres}:5432/sostitutoincloud
DB_USERNAME=sostitutoincloud
DB_PASSWORD={password}
JAVA_OPTS=-Xms512m -Xmx1024m

Volume: app_storage → /app/storage
Porta: 8080

Nota: {host-postgres} è il nome del container
PostgreSQL nella rete privata Coolify
(es. sostitutoincloud-db).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. VERIFICA BUILD
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che il build Maven con profilo
prod funzioni ancora normalmente in locale
(senza Docker):

mvn -Pprod clean package -DskipTests

Verifica che i file modificati siano
coerenti:
- WEB-INF/classes/static/config.json
  deve contenere /sostitutoincloud/api
  (copiato da config.prod.json)
- db.properties in WEB-INF/classes/
  deve contenere il formato ${DB_URL:...}

Riporta output build e contenuto
dei due file verificati.