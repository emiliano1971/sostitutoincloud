Leggi il file CLAUDE.md prima di procedere.
Leggi i file esistenti:
- frontend/src/pages/tenant/PropertyCreate.tsx
- frontend/src/pages/tenant/PropertyEdit.tsx
- service/ContrattoCalcolatoreService.java
- service/BookingImportService.java
- dao/PropertyDAO.java
  prima di procedere.

Implementa la gestione dell'aliquota ritenuta
basata su primo_immobile della property,
usando le aliquote configurate in tenant_settings.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — verifica primo immobile
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/PropertyDAO.java:

Optional<Property> findPrimoImmobileByOwner(
Integer tenantId, Integer ownerId,
Integer excludePropertyId)
- SELECT * FROM property
  WHERE fk_tenant_id = ?
  AND fk_owner_id = ?
  AND primo_immobile = true
  AND attivo = true
  AND id != ?
  LIMIT 1
- excludePropertyId serve per escludere
  la property che si sta modificando
  (in modifica la property stessa
  non deve essere considerata duplicata)
- Se excludePropertyId = null usa
  AND id != -1 (nessuna esclusione)
- Log DEBUG

Aggiungi a controller/PropertyController.java
(o crea se non esiste):

GET /api/properties/check-primo-immobile
- Query params:
  ownerId (Integer, obbligatorio)
  excludePropertyId (Integer, opzionale)
- tenantId da SecurityUtils
- chiama propertyDAO.findPrimoImmobileByOwner()
- SE trovata:
  ResponseEntity.ok(Map.of(
  "exists", true,
  "propertyName",
  property.getDisplayName(),
  "propertyId", property.getId()))
- SE non trovata:
  ResponseEntity.ok(Map.of(
  "exists", false))
- Log DEBUG

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. FRONTEND — PropertyCreate.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In PropertyCreate.tsx:

Aggiungi a propertyApi.ts:
export async function checkPrimoImmobile(
ownerId: number,
excludePropertyId?: number
): Promise<{
exists: boolean
propertyName?: string
propertyId?: number
}>
// GET /api/properties/check-primo-immobile

Aggiungi stato:
const [primoImmobileWarning, setPrimoImmobileWarning] =
useState<string>('')

Il campo "Primo immobile" è già presente
come checkbox/toggle — verificalo e
aggiungilo se mancante con default true.

Quando l'utente seleziona il proprietario
(onSelect del ComuneAutocomplete owner
o onChange del select owner) O
quando cambia il toggle primo_immobile:

SE form.primoImmobile === true
E form.fkOwnerId è valorizzato:
chiama checkPrimoImmobile(
form.fkOwnerId,
undefined)  ← undefined in creazione
SE exists:
setPrimoImmobileWarning(
`⚠️ Attenzione: il proprietario ha già
un immobile censito come primo immobile
("${propertyName}"). Impostare questo
come secondo immobile comporta
l'applicazione della ritenuta al 26%.`)
ALTRIMENTI:
setPrimoImmobileWarning('')

Mostra il warning sotto il toggle
primo_immobile con stile giallo:
bg-yellow-50 border border-yellow-200
testo text-yellow-800
icona AlertTriangle

Il warning è solo informativo —
NON blocca il salvataggio.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. FRONTEND — PropertyEdit.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Stesso warning in PropertyEdit.tsx
ma passa excludePropertyId = property.id
(la property corrente non deve essere
considerata come "altro primo immobile"):

checkPrimoImmobile(
form.fkOwnerId,
property.id)  ← esclude se stessa

Mostra stesso warning giallo.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. ALIQUOTA RITENUTA — usa tenant_settings
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In service/ContrattoCalcolatoreService.java
verifica che l'aliquota ritenuta non sia
hardcodata ma venga letta da tenant_settings:

TenantSettingsDTO settings =
tenantSettingsService.getSettings(tenantId)

BigDecimal aliquotaRitenuta =
property.isPrimoImmobile()
? settings.getWithholdingRatePrimary()
.divide(CENTO)
: settings.getWithholdingRateSecondary()
.divide(CENTO)

Se già usa tenant_settings → verifica
e documenta nel codice con commento.
Se usa valori hardcodati → correggi.

Stessa verifica in BookingImportService:
quando calcola withholding_amount
deve usare:
property.isPrimoImmobile()
? settings.withholding_rate_primary
: settings.withholding_rate_secondary

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. ALIQUOTA SU BOOKING — aggiorna al cambio
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In service/PropertyService.java
nel metodo update() quando cambia
primo_immobile:

SE primo_immobile cambia (vecchio != nuovo):
Log INFO "PropertyService: primo_immobile
cambiato per property={} owner={} —
i booking futuri useranno aliquota {}%"

NON ricalcolare i booking esistenti —
l'aliquota sui booking già importati
è storica e non va modificata.
Solo i nuovi booking useranno
la nuova aliquota.

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
-d '{"email":"admin@casavacanze.it",
"password":"atena2026"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Verifica check primo immobile
# owner 4 (Emiliano Zerbinati) ha
# Ca' Serenella (property 6) come
# primo_immobile = true
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/properties/check-primo-immobile\
?ownerId=4" \
| python3 -m json.tool

# Stesso owner, escludi property 6
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/properties/check-primo-immobile\
?ownerId=4&excludePropertyId=6" \
| python3 -m json.tool

# Owner senza immobili come primo
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/properties/check-primo-immobile\
?ownerId=1" \
| python3 -m json.tool

Verifica che:
- ownerId=4 → exists=true con nome
  immobile
- ownerId=4 excludePropertyId=6 →
  exists=false (solo un primo immobile)
- ownerId=1 → exists=false
- ContrattoCalcolatoreService usa
  withholding_rate_primary/secondary
  da tenant_settings

Riporta output build e curl.