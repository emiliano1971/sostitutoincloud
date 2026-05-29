Leggi il file CLAUDE.md prima di procedere.

Implementa l'autenticazione reale da database sostituendo
l'utente hardcoded in memoria.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. SCRIPT SQL — aggiorna password utenti
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea docs/db/update-passwords.sql:

-- Hash BCrypt di "atena" generato con BCryptPasswordEncoder
-- strength 10 (default Spring Security)
-- Il valore esatto va generato con questo codice Java:
--   new BCryptPasswordEncoder().encode("atena")
-- Eseguire DOPO aver calcolato l'hash reale.

-- Per ora usa questo placeholder che verrà sostituito
-- dallo script Java nel passo 2:
UPDATE utente SET password_hash = '__BCRYPT_HASH__'
WHERE password_hash = '{CHANGE_ME}';

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. UTILITY — genera hash BCrypt
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea src/main/java/it/gavia/sostitutoincloud/util/PasswordHashGenerator.java:
- Classe con metodo main() — utility standalone
- NON è un @Component Spring
- Codice:

  public static void main(String[] args) {
  BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
  String password = args.length > 0 ? args[0] : "atena";
  String hash = encoder.encode(password);
  System.out.println("Password: " + password);
  System.out.println("Hash BCrypt: " + hash);
  System.out.println();
  System.out.println("SQL:");
  System.out.println("UPDATE utente SET password_hash = '"
  + hash + "' WHERE password_hash = '{CHANGE_ME}';");
  }

Poi esegui la classe con:
mvn -Plocal compile exec:java \
-Dexec.mainClass="it.gavia.sostitutoincloud.util.PasswordHashGenerator"

Copia l'hash generato e aggiorna docs/db/update-passwords.sql
con il valore reale, poi esegui:
psql -U sostitutoincloud -d sostitutoincloud -h localhost \
-f docs/db/update-passwords.sql

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. UserDetailsService DA DATABASE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea src/main/java/it/gavia/sostitutoincloud/config/DatabaseUserDetailsService.java:
- implements UserDetailsService
- @Service @Log4j2
- Costruttore con JdbcTemplate
- Metodo loadUserByUsername(String email):
    * query diretta con JdbcTemplate (NON usa UtenteDAO
      perché UtenteDAO non mappa password_hash):
      SELECT id, email, password_hash, ruolo, fk_tenant_id,
      attivo
      FROM utente
      WHERE email = ?
    * se non trovato: lancia UsernameNotFoundException
    * se attivo = false: lancia DisabledException
    * mappa ruolo → GrantedAuthority:
      "super_admin" → ROLE_SUPER_ADMIN
      "tenant_admin" → ROLE_TENANT_ADMIN
      "pm_user"     → ROLE_PM_USER
      "owner_user"  → ROLE_OWNER_USER
    * restituisce org.springframework.security.core.userdetails.User
      con email, password_hash, authorities
    * IMPORTANTE: aggiungi anche un attributo custom per tenantId —
      usa una classe interna CustomUserDetails che estende
      org.springframework.security.core.userdetails.User:

Crea classe interna o separata CustomUserDetails:
- estende org.springframework.security.core.userdetails.User
- campo aggiuntivo: Integer tenantId
- campo aggiuntivo: Integer utenteId
- costruttore che passa tutto alla superclasse + salva tenantId e utenteId
- getter getTenantId() e getUtenteId()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. AGGIORNA SecurityConfig
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica config/SecurityConfig.java:
- Rimuovi il bean UserDetailsService hardcoded in memoria
- Rimuovi il campo adminPassword e @Value
- Aggiungi costruttore con DatabaseUserDetailsService
- Aggiorna securityFilterChain:
    * /sostitutoincloud/api/public/** → permitAll()
    * /sostitutoincloud/api/admin/** → hasRole("SUPER_ADMIN")
    * /sostitutoincloud/api/** → authenticated()
    * /** → permitAll()
- Mantieni httpBasic e csrf disable
- Mantieni cors

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. UTILITY — ricava tenantId dal SecurityContext
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea src/main/java/it/gavia/sostitutoincloud/util/SecurityUtils.java:
- @Component @Log4j2
- Metodi statici:

  public static Integer getCurrentTenantId()
    - legge Authentication da SecurityContextHolder
    - se principal è CustomUserDetails → return getTenantId()
    - altrimenti lancia RuntimeException("Utente non autenticato")

  public static Integer getCurrentUtenteId()
    - come sopra ma return getUtenteId()

  public static String getCurrentUserEmail()
    - return authentication.getName()

  public static boolean hasRole(String role)
    - verifica se l'utente corrente ha il ruolo specificato

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. AGGIORNA I CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In TUTTI i Controller esistenti sostituisci:
// TODO: SecurityContext
Integer tenantId = 1;

Con:
Integer tenantId = SecurityUtils.getCurrentTenantId();

I Controller da aggiornare sono:
- OwnerController
- PropertyController
- BookingController
- DocumentController
- SettlementController
- F24Controller
- CuController
- AuditLogController
  NON TenantController — quello è /api/admin/ solo super_admin

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Dopo aver creato i file lancia:
mvn -Plocal clean package

Poi esegui PasswordHashGenerator, aggiorna il DB,
riavvia Tomcat e testa:

# Login con utente dal DB
curl -u admin@casavacanze.it:atena \
"http://localhost:8081/sostitutoincloud/api/owners"

# Verifica che admin non possa accedere a /api/admin/
curl -u admin@casavacanze.it:atena \
"http://localhost:8081/sostitutoincloud/api/admin/tenants"

# Verifica 401 senza credenziali
curl "http://localhost:8081/sostitutoincloud/api/owners"

Riporta l'output del build e dei curl.