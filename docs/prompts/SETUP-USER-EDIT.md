Leggi il file CLAUDE.md e i file esistenti:
- frontend/src/pages/tenant/UsersList.tsx
- frontend/src/api/userApi.ts
- controller/UserManagementController.java
- service/UserManagementService.java
- dao/UtenteDAO.java
  prima di procedere.

Aggiungi la modifica utente (email, nome,
stato) con dialog inline nella lista utenti.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — DTO e metodo update
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/user/UtenteUpdateDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- String email      ← opzionale
- String firstName  ← opzionale
- String lastName   ← opzionale

Aggiungi a dao/UtenteDAO.java:

Utente update(Integer id,
String email,
String firstName,
String lastName)
- UPDATE utente
  SET email = ?,
  first_name = ?,
  last_name = ?,
  updated_at = NOW()
  WHERE id = ?
- Dopo update: rileggi con findById()
- Log INFO "UtenteDAO.update() - id={} email={}"

Aggiungi a service/UserManagementService.java:

UtenteListDTO update(Integer tenantId,
Integer utenteId,
UtenteUpdateDTO dto)
- Verifica esistenza e appartenenza al tenant
- Se email cambia: verifica unicità
  (non deve esistere altro utente
  con la stessa email)
  lancia IllegalArgumentException
  "Email già registrata"
- Valida email formato base:
  deve contenere @ e almeno un punto
- Chiama utenteDAO.update()
- Log INFO "UserManagementService.update()
    - id={} tenantId={}"

Aggiungi a controller/
UserManagementController.java:

PUT /api/users/{id}
- @RequestBody UtenteUpdateDTO
- tenantId da SecurityUtils
- chiama userManagementService.update()
- ResponseEntity.ok(result)
- catch IllegalArgumentException → 400
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. FRONTEND — userApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/userApi.ts:

export interface UtenteUpdateRequest {
email: string
firstName: string
lastName: string
}

export async function updateUser(
id: number,
data: UtenteUpdateRequest
): Promise<UtenteListItem>
// PUT /api/users/{id}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. FRONTEND — UsersList.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a UsersList.tsx un dialog di modifica
utente che si apre cliccando sull'icona
matita (Edit) nella colonna azioni di ogni riga.

Stato React:
const [editingUser, setEditingUser] =
useState<UtenteListItem | null>(null)
const [editForm, setEditForm] = useState({
email: '',
firstName: '',
lastName: ''
})
const [editError, setEditError] =
useState<string>('')
const [isSaving, setIsSaving] =
useState(false)

Al click matita:
setEditingUser(user)
setEditForm({
email: user.email,
firstName: user.firstName,
lastName: user.lastName
})
setEditError('')

Dialog modifica (shadcn Dialog):
Titolo: "Modifica utente"
Sottotitolo: {editingUser?.email}

Form:
Nome *     → editForm.firstName
Cognome *  → editForm.lastName
Email *    → editForm.email
(tutti obbligatori)

Footer:
[Annulla] [Salva]

handleSave():
- Valida: tutti i campi obbligatori
- Valida: email contiene @
- setIsSaving(true)
- chiama updateUser(editingUser.id, editForm)
- successo:
* ricarica lista utenti
* chiude dialog (setEditingUser(null))
* toast "Utente aggiornato"
- errore 400:
* setEditError(messaggio dal server)
- finally: setIsSaving(false)

Aggiungi icona matita (Pencil da lucide-react)
nella colonna azioni accanto al badge stato
e al cestino, solo per utenti non tenant_admin.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package
cd frontend && npm run build &&
npx tsc --noEmit

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena2026"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Lista utenti
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/users" \
| python3 -m json.tool

# Modifica utente (usa id reale dalla lista)
curl -s -X PUT \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"email": "test.modifica@casavacanze.it",
"firstName": "Test",
"lastName": "Modifica"
}' \
"http://localhost:8081/sostitutoincloud/\
api/users/{id}" \
| python3 -m json.tool

# Ripristina dati originali
curl -s -X PUT \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"email": "originale@casavacanze.it",
"firstName": "Nome",
"lastName": "Originale"
}' \
"http://localhost:8081/sostitutoincloud/\
api/users/{id}" \
| python3 -m json.tool

Verifica che:
- Modifica email e nome funzionino
- Email duplicata → 400
- Utente di altro tenant → 400
- tenant_admin non ha icona matita

Riporta output build e curl.