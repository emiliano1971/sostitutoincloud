Leggi il file CLAUDE.md prima di procedere.

Il super_admin deve poter creare il primo
utente tenant_admin per un tenant appena
creato. Attualmente POST /api/users richiede
ROLE_TENANT_ADMIN — il super_admin non può
usarlo.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/TenantController.java:

POST /api/admin/tenants/{tenantId}/users
- Solo ROLE_SUPER_ADMIN
- @RequestBody UtenteCreateDTO
  (stesso DTO già usato da UserManagementController)
- Forza ruolo = "tenant_admin" indipendentemente
  da quello passato nel body
- chiama userManagementService.create(
  tenantId, dto)
- ResponseEntity.status(201).body(result)
- catch IllegalArgumentException → 400
- Log INFO "TenantController.createUser()
    - tenantId={} email={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. FRONTEND — TenantDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/tenantApi.ts:

export async function createTenantAdmin(
tenantId: number,
data: {
email: string
firstName: string
lastName: string
password: string
}
): Promise<UtenteListItem>
// POST /api/admin/tenants/{tenantId}/users

In frontend/src/pages/admin/TenantDetail.tsx
aggiungi una sezione "Utente Amministratore":

- Se il tenant non ha ancora utenti →
  mostra pulsante "+ Crea Admin"
  che apre un dialog con:
    * Email *
    * Nome *
    * Cognome *
    * Password * (min 8 caratteri)
      Al submit → createTenantAdmin()
      successo → toast "Utente admin creato"
      + ricarica dettaglio tenant

- Se il tenant ha già utenti →
  mostra lista utenti (solo email e stato)
  con nota "Gestione utenti disponibile
  dopo il login come tenant_admin"

Per verificare se il tenant ha utenti:
Aggiungi a GET /api/admin/tenants/{id}
il campo usersCount nel TenantDetailDTO
(SELECT COUNT(*) FROM utente
WHERE fk_tenant_id = ? AND attivo = true)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

TOKEN_SUPER=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"superadmin@sostitutoincloud.it",
"password":"atena"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Crea utente admin per tenant 2
curl -s -X POST \
-H "Authorization: Bearer $TOKEN_SUPER" \
-H "Content-Type: application/json" \
-d '{
"email": "admin@rossiimmobili.it",
"firstName": "Marco",
"lastName": "Rossi",
"password": "password123"
}' \
"http://localhost:8081/sostitutoincloud/\
api/admin/tenants/2/users" \
| python3 -m json.tool

# Verifica login con nuovo utente
curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@rossiimmobili.it",
"password":"password123"}' \
| python3 -m json.tool | grep -E \
'"token"|"ruolo"|"fkTenantId"'

Verifica che:
- Utente creato con ruolo tenant_admin
- Login funzionante con le nuove credenziali
- fkTenantId = id del tenant corretto

Riporta output build e curl.