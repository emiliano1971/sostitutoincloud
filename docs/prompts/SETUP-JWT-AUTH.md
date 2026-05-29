Leggi il file CLAUDE.md prima di procedere.

Implementa JWT authentication per sostituire Basic Auth
e risolvere il problema del logout al refresh pagina.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DIPENDENZA MAVEN
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi in pom.xml nella sezione dependencies:
```xml
<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. CONFIGURAZIONE YML
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi in application-local.yml:
```yml
app:
  jwt:
    secret: sostitutoincloud-jwt-secret-key-local-dev-2026
    expiration-ms: 86400000  # 24 ore
```

Aggiungi in application-test.yml e application-prod.yml:
```yml
app:
  jwt:
    secret: CHANGE_ME_USE_A_LONG_RANDOM_SECRET_IN_PRODUCTION
    expiration-ms: 86400000  # 24 ore
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. JWT UTILITY
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea src/main/java/it/gavia/sostitutoincloud/
config/JwtUtils.java:
- @Component @Log4j2
- @Value("${app.jwt.secret}") String jwtSecret
- @Value("${app.jwt.expiration-ms}") Long jwtExpirationMs

- String generateToken(CustomUserDetails userDetails)
    - crea JWT con claims:
        * subject: email
        * claim "tenantId": userDetails.getTenantId()
        * claim "utenteId": userDetails.getUtenteId()
        * claim "ownerId": userDetails.getOwnerId()
        * claim "roles": lista ruoli come stringhe
        * issuedAt: now
        * expiration: now + jwtExpirationMs
    - firma con HS256 e jwtSecret

- String getEmailFromToken(String token)
- Integer getTenantIdFromToken(String token)
- Integer getUtenteIdFromToken(String token)
- Integer getOwnerIdFromToken(String token)
- boolean validateToken(String token)
    - verifica firma e scadenza
    - logga WARN se token non valido
    - return false se eccezione

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. JWT FILTER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea config/JwtAuthFilter.java:
- extends OncePerRequestFilter
- @Component @Log4j2
- Costruttore con JwtUtils, DatabaseUserDetailsService

- doFilterInternal():
    * legge header Authorization
    * se inizia con "Bearer ": estrae token
    * valida token con jwtUtils.validateToken()
    * se valido: carica utente da email
      e imposta Authentication nel SecurityContext
    * se non presente o non valido: prosegue senza auth
      (Spring Security gestirà il 401)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. LOGIN ENDPOINT
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/auth/LoginRequestDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- String email
- String password

Crea dto/auth/LoginResponseDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- String token
- UserMeDTO user

Aggiungi a controller/AuthController.java:

POST /api/public/login
- @RequestBody LoginRequestDTO
- carica utente con DatabaseUserDetailsService
  .loadUserByUsername(email)
- verifica password con passwordEncoder.matches()
- se valida: genera token con jwtUtils.generateToken()
- restituisce LoginResponseDTO con token e dati utente
- se non valida: 401 con messaggio "Credenziali non valide"
- Log INFO: "Login riuscito per: {email}"
- Log WARN: "Login fallito per: {email}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. AGGIORNA SecurityConfig
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica config/SecurityConfig.java:
- Aggiungi costruttore con JwtAuthFilter
- Aggiungi JwtAuthFilter PRIMA di
  UsernamePasswordAuthenticationFilter:
  .addFilterBefore(jwtAuthFilter,
  UsernamePasswordAuthenticationFilter.class)
- Aggiungi AuthenticationManager bean:
```java
  @Bean
  public AuthenticationManager authenticationManager(
    AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
  }
```
- Rimuovi .httpBasic() — non serve più con JWT
- Mantieni tutto il resto invariato
  (cors, csrf disable, path rules)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. AGGIORNA FRONTEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/lib/apiClient.ts:
- Rimuovi setCredentials() e clearCredentials()
  e getAuthHeader() con Basic Auth
- Aggiungi:
    * setToken(token: string) — salva in sessionStorage
    * getToken(): string | null — legge da sessionStorage
    * clearToken() — rimuove da sessionStorage
    * buildHeaders() — usa "Bearer {token}" invece di Basic

Modifica frontend/src/contexts/AuthContext.tsx:
- login(email, password):
    * chiama POST /api/public/login con { email, password }
    * riceve { token, user }
    * chiama setToken(token)
    * salva user nello stato
    * NON serve più chiamare /api/auth/me separatamente
      — i dati utente arrivano già nella risposta login

- logout():
    * chiama clearToken()
    * pulisce stato

- checkAuth() — chiamato all'avvio app:
    * legge token da sessionStorage con getToken()
    * se presente: chiama GET /api/auth/me con il token
    * se 200: ripristina stato utente — utente rimane loggato
    * se 401: chiama clearToken() e reindirizza al login
    * Questo risolve il problema del logout al refresh!

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima lancia il build backend:
mvn -Plocal clean