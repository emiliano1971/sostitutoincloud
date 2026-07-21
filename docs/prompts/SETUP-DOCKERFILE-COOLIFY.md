Leggi il file CLAUDE.md, pom.xml e my-build.xml
prima di procedere.

Crea Dockerfile multi-stage per deploy su Coolify.
Il build replica esattamente la struttura che
my-build.xml crea su Tomcat locale.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DOCKERFILE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea Dockerfile nella root del progetto:

```dockerfile
# ─── Stage 1: Build ───────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Installa Node.js 18
RUN apt-get update && apt-get install -y \
    curl && \
    curl -fsSL https://deb.nodesource.com/setup_18.x \
    | bash - && \
    apt-get install -y nodejs && \
    apt-get clean

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
    /usr/local/tomcat/webapps/sostitutoincloud/WEB-INF/lib

# Copia struttura compilata dallo stage build
# (my-build.xml ha già popolato WEB-INF/ 
#  nella cartella del progetto durante mvn package)
COPY --from=builder \
    /build/WEB-INF/ \
    /usr/local/tomcat/webapps/sostitutoincloud/WEB-INF/

# Copia static frontend (config.json incluso)
COPY --from=builder \
    /build/WEB-INF/classes/static/ \
    /usr/local/tomcat/webapps/sostitutoincloud/WEB-INF/classes/static/

# Porta Tomcat
EXPOSE 8080

# Variabili ambiente override a runtime
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms512m -Xmx1024m"

CMD ["catalina.sh", "run"]
```

IMPORTANTE: verifica che my-build.xml
nel profilo prod usi percorsi assoluti
compatibili con il build dentro Docker
(non assumere che Tomcat sia nella cartella
padre come in locale).

Se my-build.xml fa riferimento a
`${project.basedir}/../apache-tomcat-*/`
o simili path relativi al Tomcat locale,
adattalo per il profilo prod in modo che
la struttura WEB-INF venga creata dentro
`${project.basedir}/WEB-INF/`
(che è già il comportamento attuale
secondo il CLAUDE.md).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. docker-compose.yml (per test locale)
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea docker-compose.yml nella root:

```yaml
version: '3.8'

services:
  db:
    image: postgres:15
    environment:
      POSTGRES_DB: sostitutoincloud
      POSTGRES_USER: sostitutoincloud
      POSTGRES_PASSWORD: sostitutoincloud
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./docs/db/install.sql:/docker-entrypoint-initdb.d/install.sql
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL",
        "pg_isready -U sostitutoincloud"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: jdbc:postgresql://db:5432/sostitutoincloud
      DB_USERNAME: sostitutoincloud
      DB_PASSWORD: sostitutoincloud
    depends_on:
      db:
        condition: service_healthy

volumes:
  postgres_data:
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. db-prod.properties
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica src/main/resources/db-prod.properties
per leggere le credenziali da variabili
d'ambiente (necessario per Coolify):

db.url=${DB_URL:jdbc:postgresql://localhost:5432/sostitutoincloud}
db.username=${DB_USERNAME:sostitutoincloud}
db.password=${DB_PASSWORD:sostitutoincloud}
db.driver-class-name=org.postgresql.Driver
db.pool.maximum-pool-size=10
db.pool.minimum-idle=2

Il formato ${VAR:default} usa la variabile
d'ambiente se presente, altrimenti il default.
Verifica che Spring/HikariCP supporti questo
formato nel tuo DataSource — se no usa
@Value con ${} in Java.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. .dockerignore
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
5. config.prod.json — URL dinamico
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In frontend/public/config.prod.json
l'apiBaseUrl deve usare un path relativo
oppure il dominio Coolify definitivo.

Per ora usa path relativo che funziona
sempre indipendentemente dal dominio:

{
"apiBaseUrl": "/sostitutoincloud/api",
"environment": "prod"
}

Questo funziona perché frontend e backend
sono serviti dallo stesso Tomcat/container.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. TEST LOCALE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Testa il build Docker localmente:

# Build immagine
docker build -t sostitutoincloud:test .

# Avvia con docker-compose
docker-compose up -d

# Verifica che Tomcat sia avviato
docker-compose logs -f app | grep \
"Server startup in"

# Testa health
curl -s http://localhost:8080/\
sostitutoincloud/api/public/lookup \
| python3 -m json.tool | head -10

Riporta:
- output docker build (successo/errore)
- output docker-compose logs
- output curl health check

Se il build fallisce riporta l'errore
completo per diagnosticare.