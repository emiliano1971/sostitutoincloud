Leggi il file CLAUDE.md e docs/analisi-frontend.md
prima di procedere.

Collega le pagine owner al backend reale
sostituendo i dati mock.
Segui lo stesso pattern già usato per le altre pagine.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. ANALISI PRELIMINARE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima leggi e descrivi brevemente:
- frontend/src/pages/owner/OwnerDashboard.tsx
- frontend/src/pages/owner/OwnerBookings.tsx
- frontend/src/pages/owner/OwnerSettlements.tsx
- frontend/src/pages/owner/OwnerCU.tsx
- frontend/src/contexts/AuthContext.tsx
  (per capire come ricavare fkOwnerId dell'utente loggato)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. ENDPOINT BACKEND — /api/owner/dashboard
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/owner/OwnerDashboardDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Campi:
    * BigDecimal ricaviTotali    ← SUM gross_amount
    * Integer prenotazioniCount ← count booking
    * BigDecimal totalRitenute  ← SUM withholding_amount
    * BigDecimal totalLiquidato ← SUM owner_net_amount
      dei settlement con stato "paid"
    * List<MensileDTO> ricaviMensili
      ← ultimi 12 mesi (riusa MensileDTO già esistente)

Crea service/OwnerDashboardService.java:
- @Service @Log4j2
- Costruttore con BookingDAO, SettlementDAO,
  OwnerProfileDAO
- Metodo:
  OwnerDashboardDTO getDashboard(Integer ownerId,
  Integer tenantId)
    - verifica che owner appartenga al tenant
    - carica booking del owner
    - calcola KPI in memoria
    - costruisce ricaviMensili ultimi 12 mesi
      (stesso pattern di DashboardService)
    - Log INFO

Aggiungi a controller/OwnerController.java:

GET /api/owners/{id}/dashboard
- verifica che id == getCurrentUtenteId() owner
  oppure che l'utente sia tenant_admin
- chiama ownerDashboardService.getDashboard(id, tenantId)
- ResponseEntity<OwnerDashboardDTO>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. AGGIORNA ownerApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/ownerApi.ts:

```ts
export interface OwnerDashboardDTO {
  ricaviTotali: number;
  prenotazioniCount: number;
  totalRitenute: number;
  totalLiquidato: number;
  ricaviMensili: MensileDTO[];
}

export async function getOwnerDashboard(
  ownerId: number
): Promise<OwnerDashboardDTO>
// GET /api/owners/{ownerId}/dashboard
```

Importa MensileDTO da dashboardApi.ts

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. AGGIORNA OwnerDashboard.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/owner/OwnerDashboard.tsx:
- Mantieni TUTTO il layout e UI esistente
- Ricava ownerId dall'utente loggato:
  const { currentUser } = useAuth()
  const ownerId = currentUser?.fkOwnerId
  oppure currentUser?.id se è owner_user
- useEffect → getOwnerDashboard(ownerId)
- Aggiungi loading/error state
- KPI cards da OwnerDashboardDTO
- Grafico ricavi mensili da ricaviMensili

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. AGGIORNA OwnerBookings.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/owner/OwnerBookings.tsx:
- Mantieni layout e UI esistente
- Ricava ownerId dall'utente loggato
- useEffect → getBookings() filtrando per owner
  Per ora usa getBookings() e filtra in memoria
  per ownerName === currentUser.firstName + ' ' + lastName
  (finché non c'è endpoint dedicato /api/owner/bookings)
- Aggiungi loading/error state
- Adatta campi mock → API come in BookingsList

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. AGGIORNA OwnerSettlements.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/owner/OwnerSettlements.tsx:
- Mantieni layout e UI esistente
- Ricava ownerId dall'utente loggato
- useEffect → getSettlements({ ownerId })
  usando settlementApi.ts già esistente
- Aggiungi loading/error state
- Adatta campi mock → API

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. AGGIORNA OwnerCU.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/owner/OwnerCU.tsx:
- Mantieni layout e UI esistente
- Ricava ownerId dall'utente loggato
- useEffect → getCuList({ ownerId })
  usando cuApi.ts già esistente
- Aggiungi loading/error state
- Adatta campi mock → API

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima lancia il build backend:
mvn -Plocal clean package

Verifica endpoint:
curl -u proprietario@email.it:atena \
http://localhost:8081/sostitutoincloud/api/owners/1/dashboard

Poi verifica frontend:
cd frontend && npm run build

Riporta output di entrambi.