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
