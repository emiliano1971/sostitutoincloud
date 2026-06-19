Leggi il file CLAUDE.md e
frontend/src/pages/admin/SuperAdminDashboard.tsx
prima di procedere.

Implementa la SuperAdmin Dashboard collegata
al backend reale.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — DTO e endpoint
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/admin/SuperAdminDashboardDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Integer totalTenant
- Integer tenantAttivi
- Integer tenantSospesi
- Integer tenantDraft
- Integer totalProprietari
- Integer totalImmobili
- Integer totalPrenotazioni
- List<TenantSummaryDTO> ultimiTenant
  ← ultimi 5 tenant per created_at DESC

Crea dto/admin/TenantSummaryDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Integer id
- String displayName
- String legalName
- String stato
- Integer propertiesCount
- Integer ownersCount
- Integer bookingsCount
- LocalDate createdAt

Aggiungi a service/TenantService.java:

SuperAdminDashboardDTO getDashboard()
- carica tutti i tenant con tenantDAO.findAll()
- calcola KPI in memoria:
    * totalTenant = lista.size()
    * tenantAttivi = count stato "active"
    * tenantSospesi = count stato "suspended"
    * tenantDraft = count stato "draft"
    * totalProprietari = SUM propertiesCount
      (usa TenantDetailDTO che ha già i count)
    * totalImmobili = SUM ownersCount
    * totalPrenotazioni = SUM bookingsCount
- ultimiTenant = primi 5 per created_at DESC
  mappati su TenantSummaryDTO
- Log DEBUG

Aggiungi a controller/TenantController.java:

GET /api/admin/dashboard
- Solo ROLE_SUPER_ADMIN
- ResponseEntity<SuperAdminDashboardDTO>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. FRONTEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/tenantApi.ts:

```ts
export interface TenantSummary {
  id: number;
  displayName: string;
  legalName: string;
  stato: string;
  propertiesCount: number;
  ownersCount: number;
  bookingsCount: number;
  createdAt: string;
}

export interface SuperAdminDashboard {
  totalTenant: number;
  tenantAttivi: number;
  tenantSospesi: number;
  tenantDraft: number;
  totalProprietari: number;
  totalImmobili: number;
  totalPrenotazioni: number;
  ultimiTenant: TenantSummary[];
}

export async function getSuperAdminDashboard():
  Promise<SuperAdminDashboard>
// GET /api/admin/dashboard
```

Modifica frontend/src/pages/admin/SuperAdminDashboard.tsx:
- Mantieni TUTTO il layout e UI esistente
- Sostituisci mockTenants con:
  useEffect → getSuperAdminDashboard()
- Aggiungi loading/error state
- KPI cards da SuperAdminDashboard:
    * Tenant Attivi → tenantAttivi / totalTenant
    * Proprietari → totalProprietari
    * Immobili → totalImmobili
    * Prenotazioni → totalPrenotazioni
- Tabella "Ultimi Tenant" da ultimiTenant
- Per i "Log di Piattaforma" usa
  getAuditLog() da auditApi.ts già esistente
  filtrando i primi 5 risultati
  (per ora senza filtro tenant — mostra tutti)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package

curl -s -H "Authorization: Bearer $TOKEN_SUPER" \
http://localhost:8081/sostitutoincloud/api/admin/dashboard \
| python3 -m json.tool

cd frontend && npm run build

Riporta output di entrambi.