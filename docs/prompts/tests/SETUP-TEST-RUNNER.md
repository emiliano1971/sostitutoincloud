Leggi il file CLAUDE.md prima di procedere.

Crea una pagina Test Runner accessibile
solo in environment=local con pannello
interattivo per eseguire le fasi di test
una alla volta con dati parametrizzabili.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — TestRunnerController
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea controller/TestRunnerController.java:
- @RestController
- @Profile("local")  ← solo in locale
- @RequestMapping("/api/test")
- @Log4j2

Tutti gli endpoint sono sotto /api/test/**
già protetti da hasAnyRole(TENANT_ADMIN,
SUPER_ADMIN) dalla SecurityConfig.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DTO parametri globali
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/test/TestParamsDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- String suffix       ← es. "TEST-001"
  default: "TEST-" + LocalDate.now()
- String tenantName   ← es. "Rossi Immobili"
  default: "Test Tenant SRL"
- String tenantPiva   ← 11 cifre
  default: "99999999901"
- String tenantCf     ← 16 chars
  default: "TSTCMP80A01H501Z"
- String tenantEmail  ← email admin tenant
  default: "admin@testTenant.it"
- String tenantPassword
  default: "Test2026!"
- String ownerFirstName
  default: "Mario"
- String ownerLastName
  default: "Verdi"
- String ownerCf
  default: "VRDMRA80A01H501Z"
- String ownerIban
  default: "IT60X0542811101000000123456"
- String propertyName
  default: "Appartamento Test"
- String propertyCity
  default: "Roma"
- String guestFirstName
  default: "Marco"
- String guestLastName
  default: "Bianchi"
- String guestCf       ← se vuoto → calcola
  default: ""
- String checkinDate   ← yyyy-MM-dd
  default: primo giorno mese prossimo
- Integer nights
  default: 5
- BigDecimal grossAmount
  default: 500.00
- String channelName
  default: "Booking.com"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DTO risultato fase
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/test/TestFaseResultDTO.java:
- @Data @Builder
- String fase          ← "FASE-01"
- boolean success
- String message       ← descrizione esito
- Map<String,Object> data
  ← dati creati (ids, valori calcolati)
- List<String> checks
  ← lista verifiche eseguite con ✅/❌
- String errorDetail   ← stacktrace se fallito
- LocalDateTime executedAt

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Crea service/TestRunnerService.java:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
- @Service @Profile("local") @Log4j2
- Costruttore con tutti i DAO e Service
  necessari (aggiungi man mano)

Helper per i check:
private List<String> checks =
new ArrayList<>()

private void check(String label,
boolean condition) {
checks.add(condition
? "✅ " + label
: "❌ " + label)
}

private void resetChecks() {
checks = new ArrayList<>()
}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
FASE 01 — Onboarding Tenant
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

POST /api/test/fase-01
@RequestBody TestParamsDTO params
→ TestFaseResultDTO

Logica in TestRunnerService.eseguiFase01():

1. Crea tenant:
   tenantService.create(TenantCreateDTO
   .builder()
   .legalName(params.getTenantName()
   + " " + params.getSuffix())
   .displayName(params.getTenantName())
   .vatNumber(params.getTenantPiva())
   .taxCode(params.getTenantCf())
   .administrativeEmail(
   params.getTenantEmail())
   .legalAddress("Via Test 1")
   .cap("00100")
   .comune("Roma")
   .provincia("RM")
   .build())

2. check("Tenant creato con stato draft",
   tenant.getStato().equals("draft"))

3. Attiva tenant:
   tenantService.updateStatus(
   tenant.getId(), "active")

4. check("Tenant attivato",
   tenantService.findById(tenant.getId())
   .getStato().equals("active"))

5. Crea utente tenant_admin:
   userManagementService
   .createTenantAdmin(tenant.getId(),
   UtenteCreateDTO.builder()
   .email(params.getTenantEmail())
   .firstName("Admin")
   .lastName(params.getSuffix())
   .password(params.getTenantPassword())
   .build())

6. check("Utente admin creato",
   utente != null
   && utente.getRuolo()
   .equals("tenant_admin"))

7. Verifica login con nuove credenziali:
   Carica utente da DB con
   utenteDAO.findByEmail(email)
   e verifica passwordEncoder.matches(
   params.getTenantPassword(),
   utente.getPasswordHash())

8. check("Login verificato",
   loginOk)

9. Return TestFaseResultDTO con:
    - success = checks senza ❌
    - data: {tenantId, utenteId}
    - checks: lista completa

In caso di eccezione:
return TestFaseResultDTO con
success=false,
errorDetail=e.getMessage()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CLEANUP
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

DELETE /api/test/cleanup
@RequestBody TestParamsDTO params
→ Map<String, Integer> righe eliminate

Logica in TestRunnerService.cleanup():

Elimina in ordine (rispettando FK):
1. audit_log dove entity_id in
   (SELECT id FROM tenant
   WHERE legal_name LIKE '%'+suffix+'%')
2. utente WHERE email LIKE '%'+suffix+'%'
   OR (fk_tenant_id IN
   (SELECT id FROM tenant
   WHERE legal_name LIKE '%'+suffix))
3. tenant_settings WHERE fk_tenant_id IN
   (SELECT id FROM tenant
   WHERE legal_name LIKE '%'+suffix+'%')
4. tenant WHERE legal_name
   LIKE '%' + suffix + '%'

Restituisce count per ogni tabella.
Log INFO "TestRunnerService.cleanup()
- suffix={} eliminati={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. FRONTEND — pagina TestRunner
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/pages/TestRunner.tsx
Visibile SOLO se getConfig().environment
=== 'local'

Layout con due colonne:

COLONNA SINISTRA — Parametri
Card "Parametri Test" con form:
Suffisso *     [TEST-001        ]
─── Tenant ───────────────────
Ragione sociale [Test Tenant SRL]
P.IVA          [99999999901     ]
Email admin    [admin@test.it   ]
Password       [Test2026!       ]
─── Proprietario ─────────────
Nome           [Mario           ]
Cognome        [Verdi           ]
CF             [VRDMRA80A01H501Z]
IBAN           [IT60X05428...   ]
─── Immobile ─────────────────
Nome           [Appartamento Test]
Città          [Roma            ]
─── Prenotazione ─────────────
Canale OTA     [Booking.com     ]
Check-in       [2026-10-01      ]
Notti          [5               ]
Lordo €        [500.00          ]
Ospite nome    [Marco           ]
Ospite cognome [Bianchi         ]
Ospite CF      [                ]
(vuoto = calcola automaticamente)

[Reset parametri ai default]

COLONNA DESTRA — Fasi
Per ogni fase una card con:

┌─────────────────────────────────────┐
│ FASE 01 — Onboarding Tenant         │
│ [▶ Esegui]                          │
│                                     │
│ Stato: — Non eseguita               │
└─────────────────────────────────────┘

Dopo esecuzione:
┌─────────────────────────────────────┐
│ FASE 01 — Onboarding Tenant    ✅   │
│ [▶ Riesegui]                        │
│ Eseguita: 07/09/2026 10:30:15       │
│                                     │
│ ✅ Tenant creato con stato draft    │
│ ✅ Tenant attivato                  │
│ ✅ Utente admin creato              │
│ ✅ Login verificato                 │
│                                     │
│ Dati: tenantId=15, utenteId=8       │
└─────────────────────────────────────┘

In caso di errore:
┌─────────────────────────────────────┐
│ FASE 01 — Onboarding Tenant    ❌   │
│ [▶ Riesegui]                        │
│                                     │
│ ✅ Tenant creato con stato draft    │
│ ❌ Tenant attivato                  │
│                                     │
│ Errore: IllegalStateException:      │
│ Tenant non trovato                  │
└─────────────────────────────────────┘

In fondo alla pagina:
[🗑 Cleanup — elimina dati TEST]
← chiama DELETE /api/test/cleanup
← mostra toast con righe eliminate

Fasi da mostrare (placeholder per ora,
implementate nelle sessioni successive):
- FASE 01 — Onboarding Tenant ← implementata
- FASE 02 — Anagrafica (proprietario+immobile)
- FASE 03 — Import Booking
- FASE 04 — Documenti Fiscali
- FASE 05 — F24
- FASE 06 — Liquidazione
- FASE 07 — CU
- FASE 08 — Security

Le fasi 02-08 mostrano il pulsante
"▶ Esegui" ma chiamano un endpoint
che restituisce:
{ success: false,
message: "Fase non ancora implementata" }

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. ROUTING e MENU
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi in App.tsx:
/test-runner → TestRunner
(pubblica, accessibile a tutti i ruoli
in local — il componente mostra
"Ambiente non locale" se environment
!= 'local')

Aggiungi in AppSidebar.tsx
nella sezione bottom (dopo Audit Log
o in fondo) voce visibile solo in local:

{getConfig().environment === 'local' && (
<SidebarMenuItem>
<SidebarMenuButton asChild>
<Link to="/test-runner">
<FlaskConical />
Test Runner
</Link>
</SidebarMenuButton>
</SidebarMenuItem>
)}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package
cd frontend && npm run build &&
npm run typecheck

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena2026"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Test FASE 01
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"suffix": "TEST-001",
"tenantName": "Rossi Immobili",
"tenantPiva": "99999999901",
"tenantCf": "TSTCMP80A01H501Z",
"tenantEmail": "admin@rossitest.it",
"tenantPassword": "Test2026!"
}' \
"http://localhost:8081/sostitutoincloud/\
api/test/fase-01" \
| python3 -m json.tool

# Cleanup
curl -s -X DELETE \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"suffix":"TEST-001"}' \
"http://localhost:8081/sostitutoincloud/\
api/test/cleanup" \
| python3 -m json.tool

Verifica che:
- FASE 01 restituisce success=true
  con tutti i check ✅
- I dati vengono creati nel DB
- Il cleanup elimina tutti i dati
  con suffix TEST-001
- La pagina /test-runner è visibile
  nel menu solo in local

Riporta output build e curl.