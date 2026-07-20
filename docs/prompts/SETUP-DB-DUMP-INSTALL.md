Leggi il file CLAUDE.md prima di procedere.

Genera i file di installazione DB per il repo
GitHub in modo che un nuovo collega possa
installare il progetto partendo da zero.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DUMP SCHEMA REALE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Esegui:
pg_dump -U sostitutoincloud \
-d sostitutoincloud \
-h localhost \
--schema-only \
--no-owner \
--no-acl \
--no-comments \
-f docs/db/schema-dump.sql

Verifica che il file sia stato creato
e riporta il numero di righe.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. DUMP DATI LOOKUP E TENANT DEMO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Esegui:
pg_dump -U sostitutoincloud \
-d sostitutoincloud \
-h localhost \
--data-only \
--no-owner \
--no-acl \
--disable-triggers \
--table=tenant \
--table=utente \
--table=canale_ota \
--table=tipo_documento \
--table=tipo_immobile \
--table=stato_documento \
--table=codice_tributo \
--table=regime_fiscale \
--table=scenario_fiscale \
--table=owner_profile \
--table=property \
--table=property_ota_code \
--table=tenant_settings \
-f docs/db/seed-dump.sql

Verifica che il file sia stato creato.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. SCRIPT INSTALL COMPLETO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea docs/db/install.sql che esegue
in ordine i due file:

\echo '=== Sostituto in Cloud — DB Init ==='
\echo 'Step 1: schema...'
\i schema-dump.sql
\echo 'Step 2: dati iniziali...'
\i seed-dump.sql
\echo '=== Installazione completata ==='

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. README-INSTALL.md
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea README-INSTALL.md nella root del
progetto con le istruzioni complete
per un nuovo sviluppatore:

# Installazione Sostituto in Cloud

## Prerequisiti
- Java 17
- Maven 3.8+
- PostgreSQL 15+
- Node.js 18+
- Apache Tomcat 10.1.55
  (nella cartella padre del progetto)

## 1. Clona il repository
```bash
git clone <url-repo>
cd sostitutoincloud
```

## 2. Crea il database PostgreSQL
```bash
# Crea utente e database
psql -U postgres -c "CREATE USER sostitutoincloud WITH PASSWORD 'sostitutoincloud';"
psql -U postgres -c "CREATE DATABASE sostitutoincloud OWNER sostitutoincloud;"

# Installa lo schema e i dati iniziali
cd docs/db
psql -U sostitutoincloud -d sostitutoincloud -h localhost -f install.sql
```

## 3. Configura Tomcat
Il progetto si deploya direttamente nella
cartella webapps di Tomcat.
Tomcat deve essere nella cartella padre:
`
<parent>/
apache-tomcat-10.1.55/ ← Tomcat
sostitutoincloud/ ← questo progetto
`

Imposta il profilo Spring in
`apache-tomcat-10.1.55/bin/setenv.sh`:
```bash
export SPRING_PROFILES_ACTIVE=local
export JAVA_OPTS="-Xms512m -Xmx1024m"
```

## 4. Configura il database
Copia e adatta il file properties:
```bash
cp src/main/resources/db-local.properties.example \
   src/main/resources/db-local.properties
# Modifica le credenziali se necessario
```

## 4b. Adatta la configurazione al tuo ambiente

### Backend — application-local.yml
Verifica/modifica:
- `server.port` (default 8081)
- `app.cors.allowed-origins`
- `app.base-url`

### Backend — db-local.properties
Verifica/modifica:
- `db.url` — host, porta e nome DB
- `db.username` e `db.password`

### Frontend — frontend/public/config.local.json
Verifica/modifica:
- `apiBaseUrl` — deve corrispondere
  a host:porta del tuo Tomcat
  es. `http://localhost:8081/sostitutoincloud/api`

Per il profilo test adatta gli stessi
file con suffisso `-test`:
- `db-test.properties`
- `application-test.yml`
- `frontend/public/config.test.json`

## 5. Build e deploy
```bash
mvn -Plocal clean package
```

## 6. Avvia Tomcat
```bash
../apache-tomcat-10.1.55/bin/catalina.sh run
```

## 7. Accedi all'applicazione
- URL: http://localhost:8081/sostitutoincloud
- Super Admin: superadmin@sostitutoincloud.it / atena
- Tenant Admin: admin@casavacanze.it / atena

## Note
- Il frontend React viene buildato
  automaticamente durante mvn package
  (profilo local/test/prod)
- I log si trovano in logs/ nella root
  del progetto
- Per il profilo test usare
  `mvn -Ptest clean package`

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. .gitignore
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che nel .gitignore esistano
già queste voci, aggiungile se mancano:

# DB properties con credenziali reali
src/main/resources/db-local.properties
src/main/resources/db-test.properties
src/main/resources/db-prod.properties

# Config frontend runtime
frontend/public/config.json

# Log
logs/

# Build artifacts
target/
WEB-INF/classes/
WEB-INF/lib/

Crea src/main/resources/
db-local.properties.example con
valori placeholder:
```properties
db.url=jdbc:postgresql://localhost:5432/sostitutoincloud
db.username=sostitutoincloud
db.password=sostitutoincloud
db.pool.min=2
db.pool.max=10
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. VERIFICA
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che i file creati siano:
- docs/db/schema-dump.sql
- docs/db/seed-dump.sql
- docs/db/install.sql
- README-INSTALL.md
- src/main/resources/db-local.properties.example

Riporta dimensione di schema-dump.sql
e seed-dump.sql e conferma che
.gitignore sia aggiornato.