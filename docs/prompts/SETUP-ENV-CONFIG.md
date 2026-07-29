Leggi il file CLAUDE.md, pom.xml e my-build.xml
prima di procedere.

Semplifica la configurazione dell'applicazione:
- Le credenziali DB vengono lette da un file .env
- Un solo db.properties con placeholder
- Il frontend legge l'API URL da variabile
  d'ambiente Vite con fallback path relativo
- Eliminati i config.*.json per profilo

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DIPENDENZA MAVEN — spring-dotenv
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi in pom.xml:

<dependency>
    <groupId>me.paulschwarz</groupId>
    <artifactId>spring-dotenv</artifactId>
    <version>4.0.0</version>
</dependency>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. FILE .env
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea .env nella root del progetto:

# Database
DB_URL=jdbc:postgresql://localhost:5432/sostitutoincloud
DB_USERNAME=sostitutoincloud
DB_PASSWORD=sostitutoincloud

# Frontend (Vite dev server)
VITE_API_BASE_URL=http://localhost:8081/sostitutoincloud/api

Crea .env.example nella root del progetto
(questo va nel repo, .env no):

# Database — copia in .env e adatta
DB_URL=jdbc:postgresql://localhost:5432/sostitutoincloud
DB_USERNAME=sostitutoincloud
DB_PASSWORD=sostitutoincloud

# Frontend (Vite dev server)
# In produzione usa path relativo automaticamente
VITE_API_BASE_URL=http://localhost:8081/sostitutoincloud/api

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. .gitignore
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che .gitignore contenga:
.env

Se non c'è aggiungilo.
Verifica che .env.example NON sia in
.gitignore (deve stare nel repo).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. DB PROPERTIES — file unico
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea src/main/resources/db.properties:

db.url=${DB_URL:jdbc:postgresql://localhost:5432/sostitutoincloud}
db.username=${DB_USERNAME:sostitutoincloud}
db.password=${DB_PASSWORD:sostitutoincloud}
db.driver-class-name=org.postgresql.Driver
db.pool.maximum-pool-size=10
db.pool.minimum-idle=2

Elimina i file:
- src/main/resources/db-local.properties
- src/main/resources/db-test.properties
- src/main/resources/db-prod.properties

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. pom.xml — aggiorna filtering profili
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Nel pom.xml rimuovi i riferimenti ai file
db-local/test/prod.properties nei profili
Maven (local, test, prod).

Ogni profilo ora usa lo stesso db.properties.

Verifica che my-build.xml copi
db.properties in WEB-INF/classes/
(stessa logica dei file precedenti —
adatta il pattern di copia se necessario).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. DataSource bean — verifica lettura .env
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che il DataSource bean
(DataSourceConfig.java o equivalente)
legga le proprietà da db.properties
con @PropertySource("classpath:db.properties").

Con spring-dotenv le variabili del .env
vengono caricate nell'Environment di Spring
prima dei .properties — quindi
${DB_URL:default} viene risolto
correttamente.

Se il DataSource usa @Value, verifica
che i placeholder siano:
@Value("${db.url}")
@Value("${db.username}")
@Value("${db.password}")

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. FRONTEND — elimina config.*.json
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Elimina i file:
- frontend/public/config.local.json
- frontend/public/config.test.json
- frontend/public/config.prod.json

Crea frontend/public/config.json unico:
{
"environment": "production"
}

Modifica frontend/src/config/api.ts
(o dove è definito API_BASE_URL):

Sostituisci la lettura del config.json
con:

export const API_BASE_URL =
import.meta.env.VITE_API_BASE_URL
?? '/sostitutoincloud/api';

In sviluppo Vite legge VITE_API_BASE_URL
dal .env nella root del progetto.
In produzione/test (WAR su Tomcat) usa
il fallback '/sostitutoincloud/api'
(path relativo — funziona perché frontend
e backend sono sullo stesso container).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. my-build.xml — semplifica copia config
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In my-build.xml rimuovi la logica che
copia config.${deploy.env}.json →
config.json nel build frontend.

Non serve più — il config.json è unico
e non dipende dal profilo.

Se my-build.xml ha logica tipo:
copy file="config.${deploy.env}.json"
tofile="config.json"
rimuovila o sostituiscila con una copia
diretta del config.json unico.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
9. README-INSTALL.md — aggiorna istruzioni
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna README-INSTALL.md:

Nella sezione "Configura il database"
sostituisci:
"Copia e adatta db-local.properties"
con:
"Copia .env.example in .env e adatta
le credenziali DB"

Aggiungi nota:
"Il file .env non va committato nel repo
(è in .gitignore). Ogni sviluppatore
mantiene il proprio .env locale."

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
10. TEST
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

Verifica che:
- Il build Maven legga le credenziali DB
  dal .env (log Spring al boot deve
  mostrare la connessione al DB)
- Il frontend in dev (npm run dev) usi
  VITE_API_BASE_URL dal .env
- Il WAR deployato su Tomcat usi il
  path relativo /sostitutoincloud/api

Riporta output build.

Se il build fallisce per la dipendenza
spring-dotenv non trovata, verifica la
versione compatibile con Spring Boot 3.5:
https://github.com/paulschwarz/spring-dotenv
e usa la versione corretta.