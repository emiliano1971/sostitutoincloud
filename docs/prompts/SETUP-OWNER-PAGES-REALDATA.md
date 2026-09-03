Leggi il file CLAUDE.md e i file esistenti:
- dto/auth/UserMeDTO.java
- controller/AuthController.java
- config/CustomUserDetails.java
- frontend/src/contexts/AuthContext.tsx
- frontend/src/pages/owner/OwnerDashboard.tsx
- frontend/src/pages/owner/OwnerBookings.tsx
- frontend/src/pages/owner/OwnerSettlements.tsx
- frontend/src/pages/owner/OwnerCU.tsx
  prima di procedere.

Collega le pagine owner ai dati reali
sostituendo mock e dati hardcodati.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — aggiungi fkOwnerId al token
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dto/auth/UserMeDTO.java:
Integer fkOwnerId   ← null per non owner_user

Aggiungi a config/CustomUserDetails.java:
Integer fkOwnerId
getter getFkOwnerId()

Modifica config/DatabaseUserDetailsService.java
nella query SELECT aggiungi fk_owner_id:
SELECT id, email, password_hash, ruolo,
fk_tenant_id, fk_owner_id, attivo
FROM utente WHERE email = ?

Popola fkOwnerId in CustomUserDetails.

Aggiungi a util/SecurityUtils.java:
public static Integer getCurrentOwnerId()
- se principal è CustomUserDetails
  → return getFkOwnerId()
- altrimenti return null

Aggiorna AuthController.GET /api/auth/me:
popola dto.setFkOwnerId(
SecurityUtils.getCurrentOwnerId())

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. FRONTEND — AuthContext e currentUser
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a interfaccia User in AuthContext.tsx:
fkOwnerId?: number

Verifica che /api/auth/me venga chiamato
al login e al checkAuth() e che il campo
fkOwnerId venga salvato nel currentUser.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — endpoint per owner_user
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/OwnerController.java:

GET /api/owner/bookings
- Solo ROLE_OWNER_USER
- ownerId = SecurityUtils.getCurrentOwnerId()
- tenantId = SecurityUtils.getCurrentTenantId()
- chiama bookingDAO.findByOwnerAndTenant(
  ownerId, tenantId)
- ResponseEntity<List<BookingListDTO>>
- Log INFO

GET /api/owner/settlements
- Solo ROLE_OWNER_USER
- ownerId = SecurityUtils.getCurrentOwnerId()
- chiama settlementService.findByTenantId(
  tenantId, ownerId, null)
- ResponseEntity<List<SettlementListDTO>>

GET /api/owner/cu
- Solo ROLE_OWNER_USER
- ownerId = SecurityUtils.getCurrentOwnerId()
- chiama cuRecordDAO.findByTenantIdAndOwnerId(
  tenantId, ownerId)
- ResponseEntity<List<CuRecordDTO>>

GET /api/owner/dashboard
- Solo ROLE_OWNER_USER
- ownerId = SecurityUtils.getCurrentOwnerId()
- Calcola KPI dal booking e settlement:
  DTO con:
  totalGross: SUM gross_amount
  totalNet: SUM owner_net_amount
  totalRitenute: SUM withholding_amount
  bookingsCount: COUNT
  settlementsCount: COUNT
  netDaPagare: SUM netAmount
  dove settlement.stato != 'paid'

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND — ownerApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/ownerApi.ts:

export async function getOwnerBookings():
Promise<BookingListItem[]>
// GET /api/owner/bookings

export async function getOwnerSettlements():
Promise<SettlementListItem[]>
// GET /api/owner/settlements

export async function getOwnerCu():
Promise<CuListItem[]>
// GET /api/owner/cu

export interface OwnerDashboardData {
totalGross: number
totalNet: number
totalRitenute: number
bookingsCount: number
settlementsCount: number
netDaPagare: number
}

export async function getOwnerDashboard():
Promise<OwnerDashboardData>
// GET /api/owner/dashboard

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. FRONTEND — pagine owner
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

OwnerDashboard.tsx:
- Sostituisci mock con getOwnerDashboard()
- Mostra KPI: totale lordo, netto,
  ritenute, n. prenotazioni,
  n. liquidazioni, netto da pagare
- Loading/error state

OwnerBookings.tsx:
- Sostituisci mock con getOwnerBookings()
- Tabella: ID, Immobile, Check-in,
  Check-out, Notti, Lordo, Netto, Stato
- Ogni riga cliccabile →
  navigate(`/bookings/${b.id}`)
  (l'owner può vedere il dettaglio
  della propria prenotazione)
- Loading/error state

OwnerSettlements.tsx:
- Sostituisci mock con getOwnerSettlements()
- Tabella: Periodo, N. Prenotazioni,
  Lordo, Ritenuta, Netto, Stato
- Ogni riga cliccabile →
  navigate(`/settlements/${s.id}`)
- Loading/error state

OwnerCU.tsx:
- Sostituisci mock con getOwnerCu()
- Tabella: Anno, Compensi, Ritenute,
  Stato, Generata il
- Pulsante ⬇ PDF per ogni CU
  → downloadCuPdf() già esistente
- Loading/error state

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package
cd frontend && npm run build &&
npx tsc --noEmit

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"proprietario@email.it",
"password":"atena"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/auth/me" \
| python3 -m json.tool

curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/owner/bookings" \
| python3 -m json.tool | head -20

curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/owner/settlements" \
| python3 -m json.tool

Verifica che:
- /api/auth/me restituisce fkOwnerId
- /api/owner/bookings restituisce
  solo i booking del proprietario loggato
- /api/owner/settlements mostra
  le liquidazioni del proprietario
- tenant_admin che chiama /api/owner/*
  riceve 403

Riporta output build e curl.