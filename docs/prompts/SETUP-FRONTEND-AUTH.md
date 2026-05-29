Leggi il file CLAUDE.md e docs/analisi-frontend.md
prima di procedere.

Implementa l'autenticazione React collegata al backend
Spring Security Basic Auth.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. ANALISI PRELIMINARE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima di tutto leggi questi file esistenti e capisci
come funziona l'autenticazione attuale:
- frontend/src/contexts/AuthContext.tsx
- frontend/src/pages/Login.tsx (o simile)
- frontend/src/App.tsx (routing e protezione route)

Descrivi brevemente cosa trovano prima di procedere.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. AGGIORNA apiClient.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/lib/apiClient.ts per supportare
Basic Auth:

- Aggiungi funzione setCredentials(email: string,
  password: string) che salva le credenziali in memoria
  (NON in localStorage — solo in variabile del modulo)
- Aggiungi funzione clearCredentials()
- Aggiungi funzione getAuthHeader(): string | null
  che restituisce "Basic " + btoa(email + ":" + password)
  oppure null se non ci sono credenziali
- Aggiorna get(), post(), put(), del() per includere
  l'header Authorization se presente:
  headers: {
  'Content-Type': 'application/json',
  'Accept': 'application/json',
  ...(getAuthHeader() ?
  { 'Authorization': getAuthHeader()! } : {})
  }
- Aggiungi gestione speciale per 401:
  se response.status === 401 lancia
  new Error('UNAUTHORIZED') — sarà intercettato da AuthContext

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. AGGIORNA AuthContext.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Riscrivi frontend/src/contexts/AuthContext.tsx
mantenendo la stessa interfaccia esterna (stesso
nome hook, stessi campi) ma collegandolo al backend:

Interfaccia User da mantenere compatibile con
il frontend esistente — analizza i campi usati
nelle pagine e mantienili tutti.

Aggiungi al tipo User i campi che arrivano dal backend:
* id: number
* email: string
* ruolo: string  ← "super_admin"|"tenant_admin"|
  "pm_user"|"owner_user"
* fkTenantId: number
* firstName?: string
* lastName?: string

Implementa:

login(email: string, password: string): Promise<void>
- chiama setCredentials(email, password) in apiClient
- verifica le credenziali chiamando
  GET /api/auth/me (da creare nel backend)
- se 200: salva l'utente nello stato
- se 401: chiama clearCredentials() e lancia errore
  "Credenziali non valide"
- salva email in sessionStorage per ripristino
  al refresh della pagina (NON la password)

logout(): void
- chiama clearCredentials()
- pulisce sessionStorage
- reimposta stato utente a null

checkAuth(): Promise<void>
- chiamato all'avvio dell'app
- se trova email in sessionStorage:
  NON può ripristinare senza password
  → pulisce sessionStorage e reindirizza al login
- NOTA: con Basic Auth non c'è token da ripristinare
  — l'utente dovrà fare login ad ogni refresh pagina.
  Questo è il comportamento corretto per ora.
  In futuro si potrà aggiungere JWT o session cookie.

isAuthenticated: boolean
currentUser: User | null
isLoading: boolean

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. ENDPOINT BACKEND — /api/auth/me
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea src/main/java/it/gavia/sostitutoincloud/
controller/AuthController.java:
- @RestController @Log4j2
- @RequestMapping("/api/auth")
- Costruttore con UtenteDAO

- GET /api/auth/me
    - endpoint protetto (richiede autenticazione)
    - legge utente corrente da SecurityUtils
    - chiama utenteDAO.findById(getCurrentUtenteId())
    - restituisce:
      {
      "id": 1,
      "email": "admin@casavacanze.it",
      "ruolo": "tenant_admin",
      "fkTenantId": 1,
      "firstName": "Laura",
      "lastName": "Bianchi",
      "attivo": true
      }
    - 200 OK con dati utente
    - Log INFO: "AuthController.me() - user={}"

Crea dto/auth/UserMeDTO.java con i campi sopra.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. AGGIORNA Login.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/Login.tsx:
- Mantieni il layout e lo stile esistente
- Sostituisci la logica mock con:
    * Form con campo email E password
      (il mock aveva solo password — ora servono entrambi)
    * Al submit chiama authContext.login(email, password)
    * Gestisci loading state durante la chiamata
    * Mostra errore se login fallisce
    * Reindirizza alla dashboard dopo login riuscito

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima lancia il build backend:
mvn -Plocal clean package

Poi verifica il nuovo endpoint:
curl -u admin@casavacanze.it:atena \
http://localhost:8081/sostitutoincloud/api/auth/me

Poi verifica che il frontend compili:
cd frontend && npm run build

Riporta l'output di entrambi.