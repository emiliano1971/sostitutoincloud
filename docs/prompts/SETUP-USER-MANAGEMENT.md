Leggi il file CLAUDE.md e docs/db/schema-target.sql
prima di procedere.

Implementa la gestione utenti del tenant
(invita pm_user e owner_user, elimina, toggle stato).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. AGGIORNA UtenteDAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Leggi dao/UtenteDAO.java per capire cosa esiste già.

Aggiungi i metodi mancanti:

List<Utente> findByTenantId(Integer tenantId)
- SELECT * FROM utente WHERE fk_tenant_id = ?
  AND ruolo != 'super_admin'
  ORDER BY created_at DESC
- Log DEBUG

Utente insert(Utente utente)
- INSERT INTO utente con tutti i campi
  (esclusi id, last_login, created_at, updated_at)
- Usa KeyHolder per id generato
- Usa Types.OTHER per colonna enum ruolo
- Dopo insert: rileggi con findById()
- Log INFO: "UtenteDAO.insert() - email={} ruolo={}"

Utente updateStatus(Integer id, Boolean attivo)
- UPDATE utente SET attivo=?, updated_at=NOW()
  WHERE id=?
- Dopo update: rileggi con findById()
- Log INFO

void delete(Integer id)
- DELETE FROM utente WHERE id=?
- Log INFO: "UtenteDAO.delete() - id={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/user/UtenteListDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Integer id
- String email
- String firstName
- String lastName
- String ruolo
- Boolean attivo
- String ownerName  ← null se non è owner_user
  (carica da OwnerProfileDAO se fkOwnerId != null)
- LocalDateTime createdAt
- LocalDateTime lastLogin

Crea dto/user/UtenteCreateDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- String email (obbligatorio)
- String firstName (obbligatorio)
- String lastName (obbligatorio)
- String password (obbligatorio, min 8 char)
- String ruolo (obbligatorio)
  ← solo "pm_user" o "owner_user" ammessi
- Integer fkOwnerId
  ← obbligatorio se ruolo = "owner_user"
  ← null se ruolo = "pm_user"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. USER SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/UserManagementService.java:
- @Service @Log4j2
- Costruttore con UtenteDAO, OwnerProfileDAO,
  PasswordEncoder

  List<UtenteListDTO> findByTenantId(
  Integer tenantId)
    - carica utenti del tenant
    - per ogni owner_user carica ownerName
      da OwnerProfileDAO.findById(fkOwnerId)
    - mappa su UtenteListDTO
    - Log DEBUG

  UtenteListDTO create(Integer tenantId,
  UtenteCreateDTO dto)
    - Valida ruolo: solo "pm_user" o "owner_user"
      lancia IllegalArgumentException se altro
    - Valida email univoca:
      se esiste già → IllegalArgumentException
      "Email già registrata"
    - Se ruolo = "owner_user":
      verifica che fkOwnerId sia presente e
      appartenga al tenant
      lancia IllegalArgumentException se mancante
    - Verifica che fkOwnerId non abbia già
      un utente owner_user associato:
      lancia IllegalArgumentException
      "Questo proprietario ha già un utente"
    - Hasha la password con passwordEncoder.encode()
    - Costruisce Utente:
        * fkTenantId = tenantId
        * email, firstName, lastName
        * passwordHash = hashed
        * ruolo = dto.ruolo
        * attivo = true
        * fkOwnerId = dto.fkOwnerId (null per pm_user)
    - Chiama utenteDAO.insert(utente)
    - Mappa e restituisce UtenteListDTO
    - Log INFO: "UserManagementService.create() -
      email={} ruolo={} tenantId={}"

  UtenteListDTO updateStatus(Integer tenantId,
  Integer utenteId, Boolean attivo)
    - Verifica esistenza e appartenenza al tenant
    - Verifica che non sia tenant_admin
      (non si può disattivare l'admin)
    - Chiama utenteDAO.updateStatus(utenteId, attivo)
    - Log INFO

  void delete(Integer tenantId, Integer utenteId)
    - Verifica esistenza e appartenenza al tenant
    - Verifica che non sia tenant_admin
      (non si può eliminare l'admin)
    - Chiama utenteDAO.delete(utenteId)
    - Log INFO: "UserManagementService.delete() -
      id={} tenantId={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea controller/UserManagementController.java:
- @RestController @Log4j2
- @RequestMapping("/api/users")
- Solo ROLE_TENANT_ADMIN può accedere
  (aggiungere @PreAuthorize o verificare nel service)
- tenantId = SecurityUtils.getCurrentTenantId()

  GET /api/users
    - ResponseEntity<List<UtenteListDTO>>

  POST /api/users
    - @RequestBody UtenteCreateDTO
    - ResponseEntity.status(201).body(result)
    - gestisce IllegalArgumentException → 400

  PATCH /api/users/{id}/status
    - @RequestBody: { "attivo": true/false }
    - ResponseEntity.ok(result)

  DELETE /api/users/{id}
    - ResponseEntity.noContent()
    - gestisce IllegalArgumentException → 400

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. FRONTEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/api/userApi.ts:

```ts
export interface UtenteListItem {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  ruolo: string;
  attivo: boolean;
  ownerName?: string;
  createdAt: string;
  lastLogin?: string;
}

export interface UtenteCreateRequest {
  email: string;
  firstName: string;
  lastName: string;
  password: string;
  ruolo: string;
  fkOwnerId?: number;
}

export async function getUsers(): 
  Promise<UtenteListItem[]>
// GET /api/users

export async function createUser(
  data: UtenteCreateRequest
): Promise<UtenteListItem>
// POST /api/users

export async function updateUserStatus(
  id: number, attivo: boolean
): Promise<UtenteListItem>
// PATCH /api/users/{id}/status

export async function deleteUser(
  id: number
): Promise<void>
// DELETE /api/users/{id}
```

Modifica frontend/src/pages/tenant/UsersList.tsx:
- Mantieni TUTTO il layout e UI esistente
- Sostituisci mockUsersList con
  useEffect → getUsers()
- Aggiungi loading/error state
- Bottone "Invita Utente" → apre dialog:

  Dialog "Invita Utente":
    - Input: Nome, Cognome, Email, Password
    - Select Ruolo: PM User / Owner User
    - Select Proprietario (visibile solo se
      ruolo = owner_user):
      carica da getOwners(true) — solo attivi
    - Validazione client: campi obbligatori,
      password min 8 char,
      se owner_user → proprietario obbligatorio
    - Submit → createUser() → ricarica lista

  Bottone cestino per riga:
    - Dialog conferma eliminazione
    - Se confermato → deleteUser(id) →
      ricarica lista

  Badge stato cliccabile:
    - Toggle → updateUserStatus(id, !attivo)
    - Ricarica lista

  Colonna "Scope":
    - pm_user → "Accesso completo"
    - owner_user → ownerName (es. "Anna Moretti")
    - tenant_admin → "Amministratore"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package

curl -s -H "Authorization: Bearer $TOKEN" \
http://localhost:8081/sostitutoincloud/api/users \
| python3 -m json.tool

cd frontend && npm run build

Riporta output di entrambi.