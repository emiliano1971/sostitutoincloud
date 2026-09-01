Leggi il file CLAUDE.md prima di procedere.

Implementa recupero password via email
e cambio password per utente autenticato.

Stack email: JavaMailSender → Postfix locale
(localhost:25) → relay Office 365.

NB: documento allineato all'implementazione reale
il 2026-09-01. Dove il prompt iniziale divergeva dal
codice scritto, vale quanto riportato qui — le
differenze sono annotate nelle singole sezioni.
La più rilevante: l'endpoint di cambio password è
/api/auth/change-password, non /api/users/me/change-password.
Esiti dei test nella sezione 11 in coda.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DIPENDENZA MAVEN
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi in pom.xml:

<!-- ── Mail: JavaMailSender → Postfix locale (localhost:25) → relay Office 365 ── -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-logging</artifactId>
        </exclusion>
    </exclusions>
</dependency>

NB: l'esclusione di spring-boot-starter-logging è obbligatoria —
il progetto usa Log4j2, senza l'esclusione lo starter mail
ritira Logback in classpath.

JAR risultanti in WEB-INF/lib/:
spring-boot-starter-mail-3.5.0.jar, jakarta.mail-2.0.3.jar

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. CONFIGURAZIONE SMTP
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

application.yml (base comune) — valori validi per tutti i profili:

app:
  mail:
    from: emiliano.zerbinati@gaviatech.it
    reset-token-expiry-minutes: 60

Blocco SMTP identico nei tre application-<profilo>.yml
(local, test, prod) — Postfix locale, nessuna autenticazione:

spring:
  mail:
    host: localhost
    port: 25
    properties:
      mail.smtp.auth: false
      mail.smtp.starttls.enable: false

Inoltre OGNI profilo deve definire app.mail.reset-link-base-url:
è il base URL del FRONTEND usato per costruire il link di reset.
NON si può riusare app.base-url perché in locale React gira sul
dev server Vite (5173) mentre app.base-url punta a Tomcat (8081).

application-local.yml:
app:
  mail:
    reset-link-base-url: http://localhost:5173

application-test.yml (frontend e backend sulla stessa origine
→ coincide con app.base-url):
app:
  mail:
    reset-link-base-url: https://sostitutoincloud-test.intranet.gavia.tech/sostitutoincloud

application-prod.yml (idem):
app:
  mail:
    reset-link-base-url: https://prodpms.siv.cloud.it:8443/sostitutoincloud

NB: application-local.yml NON è tracciato da git — la property
va aggiunta a mano su ogni postazione di sviluppo.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. MIGRATION DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea docs/db/migrations/
012_utente_password_reset.sql:

ALTER TABLE utente
ADD COLUMN IF NOT EXISTS
reset_token VARCHAR(64),
ADD COLUMN IF NOT EXISTS
reset_token_expires_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS
must_change_password BOOLEAN
NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS
idx_utente_reset_token
ON utente(reset_token)
WHERE reset_token IS NOT NULL;

Indice PARZIALE: i token sono pochi e transitori,
si indicizzano solo le righe valorizzate.

must_change_password è solo predisposizione per forzare
il cambio al primo accesso: nessun flusso lo imposta a
true al momento, e updatePassword lo riporta a false.

Esegui sul DB:
psql -U sostitutoincloud -d sostitutoincloud \
-h localhost \
-f docs/db/migrations/012_utente_password_reset.sql

Aggiorna docs/db/schema-target.sql.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. MODEL + MAPPER + DAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

model/Utente.java — 3 nuovi campi:
String resetToken                  ← @JsonIgnore
LocalDateTime resetTokenExpiresAt  ← @JsonIgnore
Boolean mustChangePassword

@JsonIgnore su resetToken e resetTokenExpiresAt è OBBLIGATORIO
per sicurezza: TestController (@Profile local, path
/api/public/test) serializza Utente grezzo su un endpoint NON
autenticato — senza l'annotation il token di reset sarebbe
leggibile da chiunque.
Aggiunto @JsonIgnore anche su passwordHash, che non era protetto.

dao/mapper/UtenteRowMapper.java:
mapping dei 3 nuovi campi:
  .resetToken(rs.getString("reset_token"))
  .resetTokenExpiresAt(rs.getObject(
      "reset_token_expires_at", LocalDateTime.class))
  .mustChangePassword(rs.getBoolean("must_change_password"))
password_hash resta volutamente NON mappato: l'hash non entra
nel model.

dao/UtenteDAO.java — estendere SELECT_COLS:
le 3 nuove colonne vanno aggiunte alla costante SELECT_COLS,
non solo alle query nuove: UtenteRowMapper le legge SEMPRE,
quindi ogni query che usa il mapper deve averle nel ResultSet.

    private static final String SELECT_COLS =
        "SELECT id, fk_tenant_id, email, first_name, last_name, " +
        "ruolo, attivo, last_login, created_at, updated_at, " +
        "fk_owner_id, reset_token, reset_token_expires_at, " +
        "must_change_password FROM utente";

Optional<Utente> findByResetToken(
String token)
- SELECT_COLS + " WHERE reset_token = ?"
  (non SELECT *: serve il set di colonne del mapper)
- Log DEBUG senza il token nel messaggio

Optional<String> findPasswordHashById(Integer id)
- SELECT password_hash FROM utente WHERE id = ?
- RowMapper inline, NON UtenteRowMapper
- serve per verificare la password corrente in changePassword():
  l'hash non passa dal model, si legge solo qui
- Log DEBUG

void saveResetToken(Integer id,
String token, LocalDateTime expiresAt)
- UPDATE utente
  SET reset_token = ?,
  reset_token_expires_at = ?,
  updated_at = NOW()
  WHERE id = ?
- Log INFO

void clearResetToken(Integer id)
- UPDATE utente
  SET reset_token = NULL,
  reset_token_expires_at = NULL,
  updated_at = NOW()
  WHERE id = ?
- Log INFO

void updatePassword(Integer id,
String hashedPassword)
- UPDATE utente
  SET password_hash = ?,
  must_change_password = false,
  reset_token = NULL,
  reset_token_expires_at = NULL,
  updated_at = NOW()
  WHERE id = ?
- Log INFO
- azzerando il token rende il reset monouso

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Tutti e tre con @Data @Builder @NoArgsConstructor
@AllArgsConstructor (come gli altri DTO del progetto).

Nessuna annotation di bean validation (@NotBlank, @Size):
spring-boot-starter-validation NON è tra le dipendenze del
progetto. I vincoli sotto sono applicati dal
PasswordResetService, non dal binding.

Crea dto/auth/PasswordResetRequestDTO.java:
- String email  ← obbligatorio

Crea dto/auth/PasswordResetConfirmDTO.java:
- String token       ← obbligatorio, 64 hex
- String newPassword ← obbligatorio, min 8 chars

Crea dto/auth/ChangePasswordDTO.java:
- String currentPassword ← obbligatorio
- String newPassword     ← obbligatorio, min 8 chars

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. PasswordResetService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/PasswordResetService.java:
- @Service @Log4j2
- Costruttore con UtenteDAO,
  JavaMailSender, PasswordEncoder

@Value("${app.mail.from}")
private String mailFrom;

@Value("${app.mail.reset-token-expiry-minutes}")
private int tokenExpiryMinutes;

@Value("${app.mail.reset-link-base-url}")
private String resetLinkBaseUrl;
  ← base URL del FRONTEND, non del backend: in locale React
    gira su Vite (5173) mentre app.base-url punta a Tomcat
    (8081). Senza default: se la property manca il contesto
    non parte, meglio del fallback silenzioso a un URL sbagliato.

Costante di classe:
private static final int MIN_PASSWORD_LENGTH = 8;

void requestReset(String email)
- SE email null o blank: log WARN e return
- cerca utente per email (con .trim())
- SE non trovato: NON rivelare l'errore
  → log WARN e return silenziosamente
  (sicurezza: non svelare se email esiste)
- Genera token sicuro con metodo privato generateToken():
  return UUID.randomUUID().toString().replace("-", "")
       + UUID.randomUUID().toString().replace("-", "");
      ← 64 chars esadecimali, sta nel VARCHAR(64)
- Salva con scadenza:
  LocalDateTime expiresAt =
  LocalDateTime.now()
  .plusMinutes(tokenExpiryMinutes)
- utenteDAO.saveResetToken(id, token, expiresAt)
- Costruisci il link:
  String link = resetLinkBaseUrl
      + "/reset-password?token=" + token;
- Invia email:
  SimpleMailMessage msg = new SimpleMailMessage()
  msg.setFrom(mailFrom)
  msg.setTo(utente.getEmail())
    ← l'email dal DB, non quella in input
  msg.setSubject("Sostituto in Cloud — Reset password")
  msg.setText("""
  Hai richiesto il reset della password.

  Clicca il link seguente per impostare una nuova
  password (valido %d minuti):

  %s

  Se non hai richiesto il reset,
  ignora questa email.

  Il Team di Sostituto in Cloud
  """.formatted(tokenExpiryMinutes, link))
    ← i minuti vengono dalla property, non "60" hardcoded
- mailSender.send(msg) dentro try/catch:
    * successo → log INFO "PasswordResetService
      .requestReset() - email={} token generato e email inviata"
    * errore → log ERROR e NON rilanciare: il token resta
      valido e l'utente può richiedere di nuovo il reset

void confirmReset(String token,
String newPassword)
- SE token null o blank → IllegalArgumentException
  "Token non valido o scaduto"
  (stesso messaggio del token inesistente: non distinguere
  i casi lato client)
- utenteDAO.findByResetToken(token)
  .orElseThrow → IllegalArgumentException
  "Token non valido o scaduto"
- Verifica scadenza (anche il caso NULL):
  if (utente.getResetTokenExpiresAt() == null
  || utente.getResetTokenExpiresAt()
  .isBefore(LocalDateTime.now())) {
      log WARN
      utenteDAO.clearResetToken(id)
        ← ripulisce subito il token scaduto
      throw IllegalArgumentException(
      "Token scaduto — richiedere un nuovo reset")
  }
- Valida nuova password con validateNewPassword()
- utenteDAO.updatePassword(id,
  passwordEncoder.encode(newPassword))
- Log INFO "PasswordResetService
  .confirmReset() - utenteId={}"

void changePassword(Integer utenteId,
String currentPassword,
String newPassword)
- Leggi l'hash con
  utenteDAO.findPasswordHashById(utenteId)
  .orElseThrow → IllegalArgumentException
  "Utente non trovato"
    ← NON findById(): UtenteRowMapper non mappa password_hash,
      il model non contiene mai l'hash
- Verifica password corrente:
  if (currentPassword == null
  || !passwordEncoder.matches(
  currentPassword, currentHash))
  throw IllegalArgumentException(
  "Password corrente non corretta")
- Valida nuova password con validateNewPassword()
- utenteDAO.updatePassword(utenteId,
  passwordEncoder.encode(newPassword))
- Log INFO "PasswordResetService
  .changePassword() - utenteId={}"

private void validateNewPassword(String newPassword)
- if (newPassword == null
  || newPassword.length() < MIN_PASSWORD_LENGTH)
  throw IllegalArgumentException(
  "La password deve essere di almeno "
  + MIN_PASSWORD_LENGTH + " caratteri")
- usato sia da confirmReset() che da changePassword():
  regola in un solo punto

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. AuthController — nuovi endpoint
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/AuthController.java:
- Aggiungi PasswordResetService al costruttore

POST /api/public/password-reset/request
- @RequestBody PasswordResetRequestDTO
- chiama passwordResetService.requestReset()
- Risponde SEMPRE 200 con
  {"message": "Se l'email esiste riceverai
  le istruzioni per il reset"}
  (non rivela se email esiste o no)
- catch Exception → log ERROR, 200 comunque
  (non esporre errori interni)
- Log INFO

POST /api/public/password-reset/confirm
- @RequestBody PasswordResetConfirmDTO
- chiama passwordResetService.confirmReset()
- 200 {"message": "Password aggiornata"}
- catch IllegalArgumentException → 400
- Log INFO

POST /api/auth/change-password
- @RequestBody ChangePasswordDTO
- utenteId da SecurityUtils.getCurrentUtenteId()
- chiama passwordResetService.changePassword()
- 200 {"message": "Password aggiornata"}
- catch IllegalArgumentException → 400
- Log INFO

Nessuna modifica alla SecurityChain:
- i due endpoint di reset stanno sotto /api/public/**,
  già permitAll()
- /api/auth/change-password ricade nella regola generale
  isAuthenticated() → senza JWT risponde 403

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. FRONTEND — pagine e componenti
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/api/authApi.ts:
- wrapper delle 3 chiamate, usa post() di @/lib/apiClient
  (che risolve il base URL da getConfig().apiBaseUrl —
  mai path hardcoded nei componenti):
    * requestPasswordReset(email)
      → POST /public/password-reset/request
    * confirmPasswordReset(token, newPassword)
      → POST /public/password-reset/confirm
    * changePassword(currentPassword, newPassword)
      → POST /auth/change-password
- interface MessageResponse { message: string }

Crea frontend/src/pages/ForgotPassword.tsx:
- Form con campo Email
- Submit → requestPasswordReset() di @/api/authApi
- Dopo submit mostra messaggio:
  "Se l'email è registrata riceverai
  le istruzioni per il reset"
  (stesso messaggio indipendentemente
  dall'esito — sicurezza: il catch è vuoto e setSent(true)
  sta nel finally, così anche un errore di rete mostra
  la stessa schermata)
- Link "← Torna al login"

Crea frontend/src/pages/ResetPassword.tsx:
- Legge token da useSearchParams()
- Se token mancante → redirect a /login
- Form con: Nuova password + Conferma password
- Validazione client: min 8 chars,
  le due password devono coincidere
- Submit → confirmPasswordReset() di @/api/authApi
- Successo → redirect a /login con
  toast "Password aggiornata — effettua il login"
- Errore 400 → messaggio "Token non valido
  o scaduto — richiedere un nuovo reset"

Aggiungi in frontend/src/pages/Login.tsx
sotto il form un link:
"Password dimenticata?" → /forgot-password

Crea frontend/src/components/ChangePasswordDialog.tsx:
- Dialog con:
    * Password corrente
    * Nuova password (min 8)
    * Conferma nuova password
- Validazione: le due nuove password
  devono coincidere
- Submit → changePassword() di @/api/authApi
  (POST /api/auth/change-password)
- Successo → toast "Password aggiornata"
    + chiude dialog
- Errore 400 → messaggio inline

Aggiungi il pulsante "Cambia password" (icona KeyRound)
che apre ChangePasswordDialog in ENTRAMBI i layout —
i due ruoli usano shell diverse:
- frontend/src/components/AppSidebar.tsx
  → in SidebarFooter, sopra il pulsante "Esci"
    (etichetta nascosta quando la sidebar è collassata)
- frontend/src/components/OwnerLayout.tsx
  → nell'header, accanto al pulsante di logout
    (solo icona con title="Cambia password")

Modifiche a Login.tsx oltre al link "Password dimenticata?"
(fatte nella stessa sessione, non richieste dal prompt):
- il campo email non è più precompilato con
  admin@casavacanze.it
- il riquadro "Account disponibili" è nascosto in produzione
  (getConfig().environment !== 'prod') e riporta la password
  per ciascun account, incluso superadmin

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
9. ROUTING
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi in App.tsx (o router):
/forgot-password → ForgotPassword
/reset-password  → ResetPassword
(entrambe pubbliche, senza auth)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
10. TEST
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package
cd frontend && npm run build &&
npx tsc --noEmit

# Test request reset
curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/password-reset/request \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it"}' \
| python3 -m json.tool

# Verifica token nel DB
psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -c "
SELECT id, email, reset_token,
reset_token_expires_at
FROM utente
WHERE email = 'admin@casavacanze.it';"

# Test confirm reset col token appena generato
TK=$(psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -tAc "SELECT reset_token FROM utente \
WHERE email = 'admin@casavacanze.it';")

curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/password-reset/confirm \
-H "Content-Type: application/json" \
-d "{\"token\":\"$TK\",
\"newPassword\":\"reset2026test\"}"

# Riuso dello stesso token → deve dare 400
# (updatePassword lo azzera: il reset è monouso)

# Test cambio password
# NB: la password di admin@casavacanze.it è atena2026
# (vedi "Credenziali test" in CLAUDE.md)
TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"reset2026test"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Ripristina la password documentata
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"currentPassword":"reset2026test",
"newPassword":"atena2026"}' \
http://localhost:8081/sostitutoincloud/\
api/auth/change-password \
| python3 -m json.tool

Verifica che:
- Token generato e salvato nel DB
- Email inviata (controlla log Postfix:
  sudo tail -f /var/log/mail.log)
- Cambio password funzionante
- Ripristino password funzionante

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
11. ESITO VERIFICA (2026-09-01)
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Tutti gli step eseguiti su ambiente local, utente
admin@casavacanze.it (id=1). Stato finale: reset_token NULL.
NB: la password dell'utente id=1 è quella impostata durante
la verifica di consegna descritta sotto, non più
necessariamente quella riportata in CLAUDE.md.

Esiti:
- POST /api/public/password-reset/request → 200, messaggio
  generico; token di 64 hex nel DB con scadenza +60 min
- invio JavaMailSender → Postfix su localhost:25 accettato,
  nessuna MailException, log INFO "token generato e email
  inviata"
- CONSEGNA dell'email verificata end-to-end: email ricevuta
  sulla casella reale e reset completato dal link contenuto
  nel messaggio (vedi sotto)
- POST /api/public/password-reset/confirm → 200
  "Password aggiornata"
- riuso dello stesso token → 400 "Token non valido o
  scaduto" (monouso confermato)
- login con la password impostata dal reset → 200 + JWT
- POST /api/auth/change-password → 200
- password corrente errata → 400 "Password corrente non
  corretta"
- nuova password < 8 caratteri → 400
- chiamata senza JWT → 403
- npx tsc --noEmit → 0 errori

Verifica della consegna (2026-09-01):
nessuno degli utenti in DB ha di serie un indirizzo
realmente raggiungibile (admin@casavacanze.it è un dominio
inesistente: Postfix accetta il messaggio ma il relay
Office 365 va in bounce). La consegna è stata provata
cambiando temporaneamente l'email dell'utente id=1 su un
indirizzo reale: email ricevuta, link di reset aperto,
nuova password impostata dal form, flusso completato.
L'email dell'utente id=1 è stata poi riportata ad
admin@casavacanze.it.

Da ricordare per i prossimi test manuali:
- usare un'email NON presente in utente produce solo un log
  WARN "email non registrata" e nessun invio — è il
  comportamento anti-enumeration previsto, non un guasto.
  È la trappola in cui è facile cadere: la risposta HTTP è
  200 identica a quella del caso riuscito, l'unico modo di
  distinguere è il log.
- per provare la consegna serve un utente con un indirizzo
  reale: cambiare temporaneamente l'email di un utente di
  test e ripristinarla dopo. NB: completare il reset
  riscrive password_hash, quindi la password dell'utente
  cambia — riallineare CLAUDE.md ("Credenziali test") se si
  usa l'account documentato.

Corretto in seguito: una richiesta su un path inesistente
sotto /api restituiva 500, perché NoResourceFoundException
finiva su GlobalExceptionHandler.handleGeneric(Exception).
Aggiunto handleNoResourceFound(NoResourceFoundException)
in GlobalExceptionHandler → ora risponde 404 con
{"status":404,"error":"Not Found",
"message":"Risorsa non trovata: <path>","timestamp":...}.

Riporta output build e curl.