# Installazione Sostituto in Cloud

## Prerequisiti
- Java 17
- Maven 3.8+
- PostgreSQL 15+
- Node.js 18+
- Apache Tomcat 10.1.55 (nella cartella padre del progetto)

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
Il progetto si deploya direttamente nella cartella `webapps` di Tomcat.
Tomcat deve essere nella cartella padre:
```
<parent>/
  apache-tomcat-10.1.55/   ← Tomcat
  sostitutoincloud/        ← questo progetto
```

Imposta il profilo Spring in `apache-tomcat-10.1.55/bin/setenv.sh`:
```bash
export SPRING_PROFILES_ACTIVE=local
export JAVA_OPTS="-Xms512m -Xmx1024m"
```

## 4. Configura il database
Copia `.env.example` in `.env` e adatta le credenziali DB:
```bash
cp .env.example .env
# Modifica DB_URL / DB_USERNAME / DB_PASSWORD se necessario
```

> Il file `.env` non va committato nel repo (è in `.gitignore`).
> Ogni sviluppatore mantiene il proprio `.env` locale.
> Le credenziali sono lette da `src/main/resources/db.properties`
> (placeholder `${DB_*:default}`) risolti a runtime da Spring: prima dal
> `.env` (spring-dotenv) / variabili d'ambiente, poi dai default.

## 4b. Adatta la configurazione al tuo ambiente

### Backend — application-local.yml
Verifica/modifica:
- `server.port` (default 8081)
- `app.cors.allowed-origins`
- `app.base-url`

### Backend — .env (credenziali DB)
Verifica/modifica:
- `DB_URL` — host, porta e nome DB
- `DB_USERNAME` e `DB_PASSWORD`

### Frontend — frontend/public/config.local.json
Verifica/modifica:
- `apiBaseUrl` — deve corrispondere a host:porta del tuo Tomcat,
  es. `http://localhost:8081/sostitutoincloud/api`

Per il profilo test adatta gli stessi file con suffisso `-test`:
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
- Il frontend React viene buildato automaticamente durante `mvn package`
  (profilo local/test/prod)
- I log si trovano in `logs/` nella root del progetto
- Per il profilo test usare `mvn -Ptest clean package`

## Deploy su Coolify

### Template PDF
Copiare i template PDF nella directory storage del container:
```
/opt/sostitutoincloud/storage/templates/
└── f24-semplificato-acroform.pdf
```

Se la directory non esiste o il file non è presente, il sistema usa il
template incluso nel WAR come fallback.
Per aggiornare il template F24 (es. nuova versione AdE) sostituire il file
nella directory senza necessità di rebuild.
