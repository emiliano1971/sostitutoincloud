Leggi il file CLAUDE.md, pom.xml e my-build.xml
prima di procedere.

Crea Dockerfile multi-stage per deploy su Coolify.
Il build replica esattamente la struttura che
my-build.xml crea su Tomcat locale.
Il PostgreSQL e l'app girano su container separati
gestiti manualmente su Coolify.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DOCKERFILE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea Dockerfile nella root del progetto:

# ─── Stage 1: Build ───────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

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

# Build Maven con profilo prod
# (include build frontend React)
RUN mvn -Pprod clean package -DskipTests

# ─── Stage 2: Runtime ─────────────────────
FROM tomcat:10.1-jdk17

# Rimuovi app di default Tomcat
RUN rm -rf /usr/local/tomcat/webapps/*

# Crea struttura app in webapps
RUN mkdir -p \
/usr/local/tomcat/webapps/sostitutoincloud/WEB-INF/classes \
/usr/local/tomcat/webapps/sostitutoincloud/WEB-INF/lib \
/app/storage/pdf

# Copia struttura WEB-INF compilata
# (my-build.xml ha popolato WEB-INF/ nella
#  root del progetto durante mvn package)
COPY --from=builder \
/build/WEB-INF/ \
/usr/local/tomcat/webapps/sostitutoincloud/WEB-INF/

# Volume per file persistenti (PDF generati ecc.)
VOLUME /app/storage

# Porta Tomcat
EXPOSE 8080

# Variabili ambiente (override a runtime da Coolify)
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms512m -Xmx1024m"

CMD ["catalina.sh", "run"]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. db-prod.properties
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica src/main/resources/db-prod.properties
per leggere le credenziali da variabili
d'ambiente impostate su Coolify:

db.url=${DB_URL:jdbc:postgresql://localhost:5432/sostitutoincloud}
db.username=${DB_USERNAME:sostitutoincloud}
db.password=${DB_PASSWORD:sostitutoincloud}
db.driver-class-name=org.postgresql.Driver
db.pool.maximum-pool-size=10
db.pool.minimum-idle=2

Il formato ${VAR:default} usa la variabile
d'ambiente se presente, altrimenti il default.
Verifica che HikariCP/Spring legga correttamente
questo formato — se non supportato usa
@Value("${DB_URL:...}") nel DataSource bean.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. application-prod.yml
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi la configurazione storage
in src/main/resources/application-prod.yml:

app:
storage:
base-path: /app/storage
pdf-path: /app/storage/pdf

Questa property verrà usata dal PDF service
quando verrà implementato.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. config.prod.json — URL relativo
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/public/config.prod.json
per usare path relativo (funziona con
qualsiasi dominio Coolify):

{
"apiBaseUrl": "/sostitutoincloud/api",
"environment": "prod"
}

Frontend e backend sono sullo stesso container
quindi il path relativo funziona sempre,
indipendentemente dal dominio assegnato
da Coolify.

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
6. VARIABILI AMBIENTE COOLIFY
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Documenta in README-INSTALL.md una sezione
"Deploy su Coolify" con le variabili
d'ambiente da impostare nel container app:

DB_URL=jdbc:postgresql://{host-postgres}:5432/sostitutoincloud
DB_USERNAME=sostitutoincloud
DB_PASSWORD={password}
SPRING_PROFILES_ACTIVE=prod
JAVA_OPTS=-Xms512m -Xmx1024m

E per il container PostgreSQL 18:
POSTGRES_DB=sostitutoincloud
POSTGRES_USER=sostitutoincloud
POSTGRES_PASSWORD={password}

Nota: {host-postgres} è il nome del servizio
PostgreSQL assegnato da Coolify nella
stessa rete privata (es. il nome container).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. TEST BUILD LOCALE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Testa il build Docker localmente
(senza avviare il container — solo verifica
che il Dockerfile compili correttamente):

docker build -t sostitutoincloud:test .

Se il build fallisce riporta l'errore
completo per diagnosticare.
Se il build ha successo riporta:
- dimensione immagine finale
- layer principali