# Progetto: `sostitutoincloud`

## Descrizione
Applicazione PMS fiscale (Sostituto in Cloud).
Riferimento funzionale: `docs/Architettura_logica_PMS_fiscale_Sostituto_in_Cloud_v3.md`

## Contesto
Migrazione da stack Supabase/Node.js+React verso stack PostgreSQL / Java Spring Boot / React.

## Stack ATTUALE (da cui veniamo)
- Frontend: React + TypeScript + Vite
- Backend: Node.js (Express o simile)
- Database/Auth/Storage: Supabase

## Stack TARGET (verso cui andiamo)
1. Frontend: 
    - React + TypeScript (invariato) 
    - Vite (invariato)
2. Backend: 
    - Java Spring Boot '3.5.x' con JAVA 17
    - Spring Security + Spring Authorization Server (gestito da Boot `1.5.7`)
    - Auth Login: [da decidere — es. Spring Security + JWT] per ora usa solo Spring Security con UserDetailsService + BDAO provider, niente JWT per ora" 
3. Build con pom.xml Maven con: 
   1. definiti 3 tipi di profili:
       1. `local` (default) che legge da application-local.yml
       2.  `test`
       3.  `prod` include il build del frontend React
   2. Il pom dovrà inoltre fare il build del frontend di React
   3. packaging `war` per Tomcat classico standalone
4. Database: PostgreSQL 18

## Regole generali
- NON tocchiamo il frontend React salvo istruzioni esplicite
- NON usare Node.js nel nuovo backend
- NON generare codice Supabase client
- NON modificare file esistenti senza chiedere conferma
- Il backend Java usa Spring Boot 3.5.x, Java 17
- I file di configurazione Spring sono SEMPRE in formato .yml (non .properties)
- Le API REST usano SEMPRE body JSON e risposta JSON (@RestController, @RequestBody, @ResponseBody, ResponseEntity<>)
- L'accesso al database avviene ESCLUSIVAMENTE tramite classi DAO  con naming convention: [NomeTabella]DAO.java (es. UtenteDAO.java)
- Usa sempre Lombok per ridurre boilerplate
- Per le API segui convenzioni REST standard
- La creazione delle tabelle con relativi campi verrà specificato in una seconda fase con altre istruzioni, 
in base alla analisi delle pagine e ai campi presenti nel frontend (vedi docs/db/)

## Pattern di accesso al database
- Ogni tabella ha una classe dedicata: [NomeTabella]DAO.java
- I DAO usano JdbcTemplate 
- Le query SQL complesse vanno esternalizzate in file dedicati: src/main/resources/sql/[nometabella]/[nomequery].sql
- Struttura chiamate obbligatoria: Controller → Service → [NomeTabella]DAO → JdbcTemplate → PostgreSQL
- I Service chiamano solo i DAO, mai JdbcTemplate direttamente
- I Controller chiamano solo i Service, mai i DAO direttamente
  Esempio struttura DAO:
```
UtenteController → UtenteService → UtenteDAO → PostgreSQL
OrdineController → OrdineService → OrdineDAO → PostgreSQL
```

## Strategia di deploy Frontend/Backend

### Situazione attuale
- Frontend React buildata dentro il WAR e servita da Tomcat
- Frontend e backend sulla stessa origine — nessun problema CORS in test e prod

### Evoluzione futura prevista
L'architettura è predisposta per una eventuale separazione futura:
- Frontend deployato separatamente (Nginx, CDN, app mobile, Capacitor)
- Backend Spring Boot raggiunto tramite URL assoluto
- Questa separazione NON richiederà modifiche al codice React grazie a `VITE_API_BASE_URL`

### Configurazione URL API nel frontend
Il base URL delle API è gestito tramite variabili d'ambiente Vite — mai hardcoded nel codice.

`frontend/.env.local`:
```
VITE_API_BASE_URL=http://localhost:8081/sostitutoincloud/api
```

`frontend/.env.test`:
```
VITE_API_BASE_URL=https://testpms.siv.cloud.it:8443/sostitutoincloud/api
```

`frontend/.env.production`:
```
VITE_API_BASE_URL=https://prodpms.siv.cloud.it:8443/sostitutoincloud/api
```

Unico punto di configurazione in React:
```ts
// src/config/api.ts
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;
```

Tutte le chiamate API usano questa costante:
```ts
fetch(`${API_BASE_URL}/utenti`)
```

### Sviluppo locale
- Frontend React gira separato su Vite dev server (porta 5173)
- Backend Spring Boot gira su porta 8081
- Il proxy Vite non è necessario grazie a `VITE_API_BASE_URL` ma può restare come fallback:
  ```ts
  // vite.config.ts
  server: {
    proxy: {
      '/sostitutoincloud/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
  ```

### Produzione (attuale)
- Maven builda React e lo copia in `src/main/resources/static`
- Il WAR finale deployato su Tomcat contiene tutto (frontend + backend)
- Un solo server, un solo deploy
- Il profilo `prod` attiva il build frontend (skip.frontend=false)

### CORS
- In locale Spring Security permette richieste da `http://localhost:5173`
- In test e prod le origini consentite sono configurate per profilo nel yml
- La configurazione CORS è già predisposta per un eventuale frontend separato o app mobile in futuro

`application-local.yml`:
```yml
app:
  cors:
    allowed-origins: http://localhost:5173
```

`application-test.yml`:
```yml
app:
  cors:
    allowed-origins: https://testpms.siv.cloud.it:8443
```

`application-prod.yml`:
```yml
app:
  cors:
    allowed-origins: https://prodpms.siv.cloud.it:8443
```

Per aggiungere un frontend separato o app mobile in futuro basta aggiungere l'origine alla lista — zero modifiche al codice Java.

### Context-path — fisso per tutti i profili
Il contesto è SEMPRE `/sostitutoincloud` in tutti gli ambienti (local, test, prod).

In tutti i file yml (application-local.yml, application-test.yml, application-prod.yml):
```yml
server:
  servlet:
    context-path: /sostitutoincloud
```

| Profilo | URL base                                              | Note                                      |
|---------|-------------------------------------------------------|-------------------------------------------|
| local   | `http://localhost:8081/sostitutoincloud`               | porta 8081 per non collidere con altre app|
| test    | `https://testpms.siv.cloud.it:8443/sostitutoincloud`  | dietro proxy, HTTPS                       |
| prod    | `https://prodpms.siv.cloud.it:8443/sostitutoincloud`  | dietro proxy, HTTPS                       |

`application-local.yml`:
```yml
server:
  port: 8081
  servlet:
    context-path: /sostitutoincloud
app:
  base-url: http://localhost:8081/sostitutoincloud
```

`application-test.yml`:
```yml
server:
  port: 8443
  servlet:
    context-path: /sostitutoincloud
app:
  base-url: https://testpms.siv.cloud.it:8443/sostitutoincloud
```

`application-prod.yml`:
```yml
server:
  port: 8443
  servlet:
    context-path: /sostitutoincloud
app:
  base-url: https://prodpms.siv.cloud.it:8443/sostitutoincloud
```
La property `app.base-url` è disponibile nel codice Java tramite `@Value("${app.base-url}")`
per qualsiasi caso in cui serva l'URL assoluto (es. link nelle email, redirect OAuth, ecc.).

Il proxy Vite in sviluppo punta alla porta 8081:
```ts
server: {
  proxy: {
    '/sostitutoincloud/api': {
      target: 'http://localhost:8081',
      changeOrigin: true
    }
  }
}
```

## Build e Deploy su Tomcat — maven-antrun-plugin

### Strategia di deploy
Il progetto risiede direttamente nella cartella `webapps/sostitutoincloud/` di Tomcat.
Dopo la build Maven, il plugin `maven-antrun-plugin` esegue il file `my-build.xml`
che copia il compilato dalla cartella `target/` nella struttura `WEB-INF/` del progetto —
senza generare un WAR da deployare separatamente.

### File coinvolti
- `pom.xml` — configura `maven-antrun-plugin` nella fase `package`
- `my-build.xml` — script Ant che esegue la copia effettiva

### Parametri passati da pom.xml a my-build.xml
| Proprietà | Valore |
|---|---|
| `project.basedir` | root del progetto |
| `project.build.directory` | cartella `target/` |
| `project.build.outputDirectory` | cartella `target/classes/` |
| `deploy.env` | profilo attivo (local, test, prod) |
| `db.properties.source` | file properties DB per ambiente |
| `log4j.source` | file Log4j2 per ambiente |

### Cosa fa my-build.xml
1. Cancella e ricrea `WEB-INF/classes/`
2. Copia le classi compilate da `target/classes/` → `WEB-INF/classes/`
3. Copia le risorse da `src/main/resources/` → `WEB-INF/classes/`
4. Pulisce e ricopia i JAR da `target/lib/` → `WEB-INF/lib/`
5. Copia `web.xml` da `src/main/webapp/WEB-INF/`
6. Copia il file DB properties per ambiente → `WEB-INF/classes/db.properties`
7. Copia il file Log4j2 per ambiente → `WEB-INF/classes/log4j2.xml`

### Properties in testa al pom.xml
```xml
<properties>
    <spring.profiles.active>local</spring.profiles.active>
    <deploy.env>${spring.profiles.active}</deploy.env>
    <db.properties.source>db-local.properties</db.properties.source>
    <log4j.source>log4j2.xml</log4j.source>
</properties>
```
- `spring.profiles.active` è il valore master — cambiando solo questo si propaga a `deploy.env`
- `db.properties.source` va aggiornato per ogni profilo (db-local, db-test, db-prod)
- `log4j.source` è **fisso** `log4j2.xml` — un solo file Log4j2 che gestisce i livelli internamente

### Struttura file per ambiente in src/main/resources/
```
src/main/resources/
├── db-local.properties
├── db-test.properties
├── db-prod.properties
└── log4j2.xml              ← file unico, gestisce i livelli per profilo internamente
```

### Strategia profili — build e runtime
La configurazione è divisa in due livelli:

**Build time (Maven `-Plocal/-Ptest/-Pprod`)**
- Determina `log.level`, `db.properties.source`, `deploy.env`
- Maven filtering scrive i valori nei file di configurazione al momento della compilazione
- Abilitare filtering delle risorse nel pom.xml:
  ```xml
  <resources>
      <resource>
          <directory>src/main/resources</directory>
          <filtering>true</filtering>
      </resource>
  </resources>
  ```
- Nei profili Maven definire le property:
  ```xml
  <profile>
      <id>local</id>
      <properties>
          <spring.profiles.active>local</spring.profiles.active>
          <deploy.env>local</deploy.env>
          <db.properties.source>db-local.properties</db.properties.source>
          <log4j.source>log4j2.xml</log4j.source>
          <log.level>DEBUG</log.level>
      </properties>
  </profile>
  <profile>
      <id>test</id>
      <properties>
          <spring.profiles.active>test</spring.profiles.active>
          <deploy.env>test</deploy.env>
          <db.properties.source>db-test.properties</db.properties.source>
          <log4j.source>log4j2.xml</log4j.source>
          <log.level>INFO</log.level>
      </properties>
  </profile>
  <profile>
      <id>prod</id>
      <properties>
          <spring.profiles.active>prod</spring.profiles.active>
          <deploy.env>prod</deploy.env>
          <db.properties.source>db-prod.properties</db.properties.source>
          <log4j.source>log4j2.xml</log4j.source>
          <log.level>INFO</log.level>
      </properties>
  </profile>
  ```

**Runtime (Tomcat setenv.sh/bat)**
- Imposta `SPRING_PROFILES_ACTIVE` per il caricamento dei file yml Spring
- Spring carica automaticamente `application-{profilo}.yml` in base a questa variabile
  ```bash
  # setenv.sh — esempio per prod
  export SPRING_PROFILES_ACTIVE=prod
  ```

**log4j2.xml — livello dinamico tramite filtering Maven**
```xml
<Properties>
    <Property name="logLevel">${log.level}</Property>
    <Property name="logFile">logs/sostitutoincloud-${deploy.env}.log</Property>
</Properties>
<Appenders>
    <RollingFile name="FileAppender" fileName="${logFile}" .../>
    <!-- ConsoleAppender solo in locale -->
</Appenders>
<Loggers>
    <Root level="${logLevel}">
        <AppenderRef ref="FileAppender"/>
    </Root>
</Loggers>
```


### Regole per Claude
- La porta di sviluppo locale è **8081**, mai 8080
- Il context-path `/sostitutoincloud` è fisso — NON cambiarlo per nessun profilo
- Le chiamate API React usano SEMPRE `API_BASE_URL` da `src/config/api.ts` — mai path o URL hardcoded
- Il base URL è definito nei file `.env.*` del frontend, mai nel codice
- Il backend Spring Boot espone tutte le API sotto `/sostitutoincloud/api`
- La configurazione CORS è per profilo nel yml — mai hardcoded nel codice Java
- Per aggiungere origini CORS future modificare solo i file yml, non il codice
- NON generare logica di deploy diversa da questa — è intenzionale e collaudata
- Il file `my-build.xml` deve stare nella root del progetto accanto al `pom.xml`
- I profili Maven devono definire `log.level`, `db.properties.source` e `log4j.source`
- NON usare `maven-war-plugin` per il deploy — il deploy avviene tramite Ant
- La cartella `WEB-INF/` è nella root del progetto (dentro webapps di Tomcat),
  NON dentro `src/main/webapp/`
- Il filtering Maven è abilitato su `src/main/resources/` — le variabili `${...}`
  nei file di configurazione vengono sostituite al momento della compilazione
- `SPRING_PROFILES_ACTIVE` è impostato nel `setenv.sh` di Tomcat per il runtime yml

## Logging

### Libreria
- Usare **Log4j2** — NON Logback (default Spring Boot)
- Escludere `spring-boot-starter-logging` dal pom.xml
- Aggiungere `spring-boot-starter-log4j2`

### Livelli per ambiente
| Profilo | Livello root | Note |
|---|---|---|
| local | `DEBUG` | massimo dettaglio, include query SQL |
| test | `INFO` | standard, come produzione |
| prod | `INFO` | standard |

### Configurazione
- Il file di configurazione è `log4j2-spring.xml` in `src/main/resources/`
- I log vanno scritti su file nella cartella `logs/` nella root del progetto
- Nome file per ambiente:
    - local: `logs/sostitutoincloud-local.log`
    - test: `logs/sostitutoincloud-test.log`
    - prod: `logs/sostitutoincloud-prod.log`
- Usare `RollingFileAppender` con rotazione giornaliera e max 30 giorni di retention
- In locale abilitare anche `ConsoleAppender` per vedere i log in terminale
- In test e prod solo `RollingFileAppender` — niente console

### Regole per Claude
- NON usare Logback — sempre Log4j2
- NON usare `System.out.println()` — sempre logger Log4j2
- Nelle classi Java usare sempre `@Log4j2` di Lombok (non istanziare il logger manualmente)
- Il livello di log è determinato dalla property Maven `${log.level}` — mai hardcodato nel codice
- Il nome del file di log include `${deploy.env}` per distinguere i log per ambiente
- `ConsoleAppender` attivo SOLO in locale, mai in test e prod

---

## Spring Security — configurazione accessi API

### Regola generale
- Tutte le API sotto `/sostitutoincloud/api/**` richiedono autenticazione (`isAuthenticated()`)
- Eccezioni esplicite vanno dichiarate sotto `/sostitutoincloud/api/public/**`

### Pattern di accesso
| Path | Accesso |
|---|---|
| `/sostitutoincloud/api/public/**` | Pubblico — nessuna autenticazione |
| `/sostitutoincloud/api/**` | Autenticato — `isAuthenticated()` |
| `/sostitutoincloud/**` (frontend) | Pubblico — servito da Tomcat |

### Struttura utenti e autenticazione
- La struttura DB degli utenti (tabelle, campi, ruoli) è da definire —
  verrà specificata in una fase successiva in `docs/db/`
- NON generare una struttura utenti di default — attendere istruzioni esplicite
- Per ora la SecurityChain può usare un utente hardcoded in memoria
  solo per test, mai in prod

### Regole per Claude
- La SecurityChain deve SEMPRE prevedere il path `/api/public/**` come `permitAll()`
  anche se al momento non esistono API pubbliche — è predisposizione per il futuro
- NON mettere logica di autenticazione nel codice dei Controller —
  tutto gestito dalla SecurityChain
- I path pubblici futuri vanno aggiunti SOLO sotto `/api/public/`,
  mai creando eccezioni sparse nella SecurityChain

---

## Comandi principali

### Build
```bash
# Build completo (salta frontend) — usato in sviluppo
mvn -Plocal -Dskip.frontend=true clean package

# Solo compile (check rapido)
mvn -Plocal compile

# Build produzione (include frontend React)
mvn -Pprod clean package
```

### Run
```bash
# Avvia backend locale
mvn -Plocal spring-boot:run

# Avvia frontend (in terminale separato)
cd frontend && npm run dev
```

### Test
```bash
mvn -Plocal test


# Avvio backend locale gestito dal tomcat:
../../bin/./catalina jpda run  

```

## Struttura cartelle target
progetto/
├── CLAUDE.md
├── pom.xml
├── frontend/                        ← React + TypeScript + Vite (esistente)
├── src/
│   └── main/
│       ├── java/com/[package]/
│       │   ├── controller/          ← @RestController
│       │   ├── service/             ← @Service
│       │   ├── dao/                 ← [NomeTabella]DAO.java
│       │   ├── model/               ← POJO / DTO con Lombok
│       │   └── config/              ← Spring Security, DataSource, ecc.
│       └── resources/
│           ├── application.yml
│           ├── application-local.yml
│           ├── application-test.yml
│           ├── application-prod.yml
│           └── sql/                 ← query SQL complesse esternalizzate
│               └── [nometabella]/
│                   └── [nomequery].sql
└── docs/
├── architettura-pms.md          ← descrizione funzionale del progetto
├── migration-plan.md            ← piano di migrazione
└── db/
├── schema-current.md        ← schema attuale da Supabase
└── schema-target.sql        ← tabelle target (fase 2)
--

## Database — file di riferimento

| File | Descrizione |
|---|---|
| `docs/db/analisi-mock.md` | Analisi entità e campi estratti dal frontend mock |
| `docs/db/schema-target.sql` | Schema PostgreSQL 18 definitivo — tabelle, lookup, enum, indici, viste |
| `docs/db/seed-data.sql` | Dati di esempio per sviluppo e test — NON eseguire in prod |

### Convenzioni schema
- Tutte le colonne FK hanno prefisso `fk_`
- Ogni tabella ha `id SERIAL PRIMARY KEY`, `created_at`, `updated_at` con trigger
- Tabelle immutabili (`settlement_booking`, `audit_log`) non hanno `updated_at`
- Enum PostgreSQL solo per stati fissi: `user_role`, `tenant_status`, `owner_type`, `payment_status`, `settlement_status`, `f24_status`, `cu_status`, `tourist_tax_collection`
- Tutto il resto gestito come tabella lookup per consentire modifiche future senza migration complesse

### Regole per Claude
- NON modificare lo schema senza istruzioni esplicite
- Per aggiungere stati o valori usare INSERT nelle tabelle lookup — mai modificare gli enum
- Per modificare un enum esistente aprire una migration dedicata e discuterla prima
- I DAO devono usare i nomi colonna esatti da `schema-target.sql`
- Per le query sui workflow usare sempre il `codice` della tabella lookup, non l'`id` numerico

## Cosa NON fare
- Non generare codice Supabase client
- Non usare Node.js per il nuovo backend
- Non modificare i file esistenti senza chiedere

## Priorità di lavoro
1. Struttura progetto Spring Boot + pom.xml con profili
2. Configurazione Postgres + datasource
3. Porting delle API da Node.js a Spring
4. Schema DB — completato, vedi `docs/db/`