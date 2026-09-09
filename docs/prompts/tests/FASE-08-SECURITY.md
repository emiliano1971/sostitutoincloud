Leggi il file CLAUDE.md prima di procedere.
Leggi i file esistenti:
- frontend/tests/e2e/helpers/auth.ts
- frontend/tests/e2e/helpers/api.ts
  prima di procedere.

Crea il test E2E Fase 08 — Security:
verifica che ogni ruolo acceda solo
alle risorse autorizzate e che i
tentativi cross-tenant e cross-owner
siano bloccati.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
STRATEGIA
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

I test di security sono principalmente
API-level (via helpers/api.ts) —
non richiedono navigazione browser
per la maggior parte dei casi.
Solo i test di redirect UI usano
page di Playwright.

Tre ruoli da testare:
- superAdmin: superadmin@sostitutoincloud.it
- tenantAdmin: admin@casavacanze.it
- ownerUser: proprietario@email.it

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TEST CASES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

test.beforeAll:
- Ottieni token per tutti e tre i ruoli:
  tokenSuper, tokenAdmin, tokenOwner

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SEZIONE A — Endpoint tenant protetti
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

8.1 — owner_user non accede
agli endpoint tenant
→ GET /api/bookings → 403
→ GET /api/settlements → 403
→ GET /api/documents → 403
→ GET /api/cu → 403
→ GET /api/owners → 403
→ GET /api/properties → 403
→ GET /api/f24 → 403
→ GET /api/users → 403
→ GET /api/dashboard → 403
→ GET /api/settings → 403
Tutti con tokenOwner
→ check "✅ owner_user bloccato
su tutti gli endpoint tenant"

8.2 — owner_user accede
agli endpoint owner portal
→ GET /api/owner/bookings → 200
→ GET /api/owner/settlements → 200
→ GET /api/owner/cu → 200
→ GET /api/owner/dashboard → 200
→ GET /api/auth/me → 200
Tutti con tokenOwner
→ check "✅ owner_user accede
al portale owner"

8.3 — tenant_admin non accede
al portale owner
→ GET /api/owner/bookings → 403
→ GET /api/owner/settlements → 403
→ GET /api/owner/cu → 403
→ GET /api/owner/dashboard → 403
Tutti con tokenAdmin
→ check "✅ tenant_admin bloccato
sul portale owner"

8.4 — tenant_admin non accede
agli endpoint admin
→ GET /api/admin/tenants → 403
Tutti con tokenAdmin
→ check "✅ tenant_admin bloccato
su /api/admin/**"

8.5 — super_admin accede
agli endpoint admin
→ GET /api/admin/tenants → 200
Con tokenSuper
→ check "✅ super_admin accede
a /api/admin/**"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SEZIONE B — Isolamento tenant
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

8.6 — tenant_admin non vede
risorse di altri tenant
→ GET /api/admin/tenants con tokenAdmin
→ 403 (non è super_admin)
→ GET /api/bookings con tokenAdmin
→ 200 ma verifica che tutti i booking
abbiano fkTenantId == 1
(tenant di admin@casavacanze.it)
→ check "✅ booking filtrati
per tenant corrente"

8.7 — Documento di altro tenant
non accessibile
→ con tokenAdmin cerca un documento
che non esiste nel suo tenant
GET /api/documents/999999 → 404
→ check "✅ documento inesistente
→ 404 non 403"
(non rivela esistenza di altri tenant)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SEZIONE C — Isolamento owner
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

8.8 — owner_user vede solo
i propri booking
→ GET /api/owner/bookings
con tokenOwner
→ verifica che tutti i booking
abbiano ownerName ==
"Anna Moretti" (owner 1)
→ check "✅ owner vede solo
i propri booking"

8.9 — owner_user non accede
alla CU di un altro proprietario
→ GET /api/owner/cu/{cuId_owner4}/pdf
con tokenOwner (owner 1)
→ verifica 403
(cuId appartiene a owner 4,
non a owner 1)
→ check "✅ CU di altro owner → 403"

8.10 — owner_user non accede
ai settlement di altri
→ GET /api/owner/settlements
con tokenOwner
→ verifica che tutti i settlement
abbiano fkOwnerId == 1
(o lista vuota se owner 1
non ha settlement)
→ check "✅ settlement filtrati
per owner corrente"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SEZIONE D — Redirect UI per ruolo
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

8.11 — owner_user rediretto
a /owner dopo login
→ page.goto('/login')
→ login come proprietario@email.it
→ verifica URL == /owner
→ verifica che NON sia /dashboard

8.12 — owner_user non accede
alle rotte tenant via URL diretto
→ login come owner
→ page.goto('/bookings')
→ verifica redirect a /owner
(o pagina di accesso negato)
→ page.goto('/dashboard')
→ verifica redirect a /owner

8.13 — tenant_admin rediretto
a /dashboard dopo login
→ login come admin@casavacanze.it
→ verifica URL == /dashboard
→ verifica che NON sia /owner

8.14 — super_admin rediretto
a /admin dopo login
→ login come superadmin
→ verifica URL contiene /admin

8.15 — Accesso senza token → 401/403
→ GET /api/bookings senza token
→ 403 (Spring Security)
→ GET /api/auth/me senza token
→ 403
→ check "✅ endpoint protetti
senza autenticazione → 403"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SEZIONE E — Endpoint test protetti
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

8.16 — owner_user non accede
agli endpoint di test
→ DELETE /api/test/cleanup-anagrafica
con tokenOwner → 403
→ check "✅ endpoint test
protetti da owner_user"

8.17 — Endpoint test non esistono
in produzione
→ verifica che @Profile("local")
sia presente su TestRunnerController
→ questo è un check sul codice,
non una chiamata HTTP —
verifica leggendo il file
controller/TestRunnerController.java
e cercando @Profile("local")
→ log "✅ TestRunnerController
annotato con @Profile(local)"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
BACKEND — nessuna modifica
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Nessun nuovo endpoint backend necessario
per questa fase — i test verificano
comportamenti già implementati.

Nessun cleanup necessario —
i test di security non creano dati.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ESECUZIONE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Assicurati che siano attivi:
- Tomcat su :8081
- Vite su :5173

cd frontend
npx playwright test \
tests/e2e/fase08-security.spec.ts

Riporta output con ✅/❌ per ogni test.
Se fallisce riporta screenshot e errore
senza correggere — aspetta istruzioni.