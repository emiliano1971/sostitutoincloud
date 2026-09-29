Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- service/ContrattoCalcolatoreService.java
- service/BookingService.java
  metodi updateSplit(), popolaSplitEconomico()
- service/BookingImportService.java
- dto/booking/BookingUpdateSplitDTO.java
- dto/ContrattoCalcoloResult.java
- frontend/src/pages/tenant/BookingDetail.tsx
- frontend/src/api/bookingApi.ts
  prima di procedere.

Separa la voce aggregata "pulizie"
in due voci distinte: pulizie e
cambio_biancheria, ognuna con la
sua regola contratto e il suo override.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — ContrattoCalcoloResult
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a ContrattoCalcoloResult.java:

BigDecimal pulizieAmount
← importo solo pulizie
BigDecimal cambioBiancheriaAmount
← importo solo cambio biancheria
Integer fkRegolaPulizieId
← FK regola pulizie (null se override)
Integer fkRegolaCambioBiancheriaId
← FK regola cambio biancheria
(null se override o assente)

cleaningAmount rimane come somma
dei due per backward compat:
cleaningAmount = pulizieAmount
+ cambioBiancheriaAmount

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — ContrattoCalcolatoreService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In ContrattoCalcolatoreService.java
modifica la logica di calcolo pulizie:

DA:
Aggrega pulizie + cambio_biancheria
in cleaningAmount con fkRegolaCleaningId
che punta alla prima regola trovata

A:
Calcola separatamente:

Per cleaningOverride (parametro esistente):
SE cleaningOverride != null:
← comportamento legacy invariato:
pulizieAmount = cleaningOverride
cambioBiancheriaAmount = 0
fkRegolaPulizieId = null
fkRegolaCambioBiancheriaId = null
cleaningAmount = cleaningOverride
← NON usare cleaningOverride se
arrivano pulizieOverride o
cambioBiancheriaOverride separati

Per pulizieOverride:
SE pulizieOverride != null:
pulizieAmount = pulizieOverride
fkRegolaPulizieId = null
source = 'manuale'
ALTRIMENTI:
Cerca regola tipo='pulizie':
SE trovata:
calcola importo dalla regola
fkRegolaPulizieId = regola.id
ALTRIMENTI:
pulizieAmount = 0
fkRegolaPulizieId = null

Per cambioBiancheriaOverride:
SE cambioBiancheriaOverride != null:
cambioBiancheriaAmount =
cambioBiancheriaOverride
fkRegolaCambioBiancheriaId = null
ALTRIMENTI:
Cerca regola tipo='cambio_biancheria':
SE trovata:
calcola importo dalla regola
fkRegolaCambioBiancheriaId =
regola.id
ALTRIMENTI:
cambioBiancheriaAmount = 0
fkRegolaCambioBiancheriaId = null

cleaningAmount = pulizieAmount
+ cambioBiancheriaAmount

Aggiungi i nuovi parametri a calcola():

DA:
calcola(...,
BigDecimal cleaningOverride,
...)

A:
calcola(...,
BigDecimal cleaningOverride,
BigDecimal pulizieOverride,
BigDecimal cambioBiancheriaOverride,
...)

Aggiorna tutti i chiamanti di calcola()
aggiungendo null, null per i nuovi
parametri.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — BookingUpdateSplitDTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a BookingUpdateSplitDTO.java:

BigDecimal pulizieOverride
← nullable, se null usa regola
BigDecimal cambioBiancheriaOverride
← nullable, se null usa regola

cleaningOverride rimane per
backward compat ma se presenti
pulizieOverride o cambioBiancheriaOverride
hanno precedenza.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BACKEND — BookingService
   popolaSplitEconomico()
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In BookingService.popolaSplitEconomico()
sostituisci la riga unica pulizie con
due righe separate:

DA:
Inserisci riga 'pulizie' con
cleaningAmount aggregato
fkPropertyContractRuleId =
calcolo.getFkRegolaCleaningId()

A:
// Riga pulizie
SE calcolo.getPulizieAmount() > 0
O calcolo.getFkRegolaPulizieId() != null:
Inserisci riga:
tipo_voce = 'pulizie'
descrizione = 'Riaddebito pulizie'
importo = calcolo.getPulizieAmount()
aliquota_iva = 22.00
include_in_fattura_pm = true
ordinamento = 20
source = calcolo
.getFkRegolaPulizieId() != null
? 'calcolato' : 'manuale'
fkPropertyContractRuleId =
calcolo.getFkRegolaPulizieId()

// Riga cambio biancheria
SE calcolo
.getCambioBiancheriaAmount() > 0
O calcolo
.getFkRegolaCambioBiancheriaId()
!= null:
Inserisci riga:
tipo_voce = 'cambio_biancheria'
descrizione = 'Cambio biancheria'
importo = calcolo
.getCambioBiancheriaAmount()
aliquota_iva = 22.00
include_in_fattura_pm = true
ordinamento = 25
source = calcolo
.getFkRegolaCambioBiancheriaId()
!= null ? 'calcolato' : 'manuale'
fkPropertyContractRuleId = calcolo
.getFkRegolaCambioBiancheriaId()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BACKEND — BookingImportService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In BookingImportService.confirm()
dove viene chiamato calcola():

SE la riga ha otaCommissionAmount
(commissione dal file) → passa come
cleaningOverride → pulizieOverride:

DA:
calcola(..., cleaningOverride, ...)

A:
// Importo pulizie dal file →
// va come pulizieOverride
// il cambio biancheria resta
// dalla regola contratto
calcola(...,
null,           ← cleaningOverride
row.getCleaningAmount(), ← pulizieOverride
null,           ← cambioBiancheriaOverride
...)

Verifica che row.getCleaningAmount()
esista in BookingImportRowDTO —
se non esiste usa il valore
già disponibile nell'import.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. BACKEND — BookingService.updateSplit()
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In BookingService.updateSplit()
passa i nuovi override al calcolatore:

calcola(...,
dto.getCleaningOverride(),
dto.getPulizieOverride(),
dto.getCambioBiancheriaOverride(),
...)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. FRONTEND — bookingApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna BookingUpdateSplitRequest:

export interface BookingUpdateSplitRequest {
touristTaxIncludedInGross?: boolean
otaCommissionOverride?: number | null
cleaningOverride?: number | null
pulizieOverride?: number | null
cambioBiancheriaOverride?: number | null
pmFeeOverride?: number | null
}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. FRONTEND — BookingDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Nel loop delle righe split:

La voce 'pulizie' mostra l'editor
con override pulizieOverride:
→ Check invia:
handleUpdateSplit({
pulizieOverride:
parseFloat(pulizieValue),
cambioBiancheriaOverride:
cambioBiancheriaManuale
? parseFloat(cambioBiancheriaValue)
: undefined
})
← mantieni anche gli altri override
manuali attuali

La voce 'cambio_biancheria' mostra
il proprio editor separato con
override cambioBiancheriaOverride:
→ Check invia:
handleUpdateSplit({
cambioBiancheriaOverride:
parseFloat(cambioBiancheriaValue),
pulizieOverride:
pulizieManuale
? parseFloat(pulizieValue)
: undefined
})

Icona ripristina (↺) per pulizie:
onClick → handleUpdateSplit({
pulizieOverride: null
})

Icona ripristina (↺) per
cambio biancheria:
onClick → handleUpdateSplit({
cambioBiancheriaOverride: null
})

labelTipoVoce aggiorna:
'cambio_biancheria':
'Cambio biancheria'

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
9. MIGRAZIONE DATI ESISTENTI
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

I booking esistenti con una riga
'pulizie' aggregata in
booking_split_economico vanno aggiornati.

Esegui:
psql -U sostitutoincloud \
-d sostitutoincloud -h localhost -c "
-- Verifica booking con riga pulizie
-- aggregata (fk_property_contract_rule_id
-- punta a una regola di tipo pulizie)
SELECT bse.fk_booking_id,
bse.id as riga_id,
bse.importo,
bse.fk_property_contract_rule_id,
pcr.tipo
FROM booking_split_economico bse
JOIN property_contract_rule pcr
ON pcr.id =
bse.fk_property_contract_rule_id
WHERE bse.tipo_voce = 'pulizie'
AND bse.deleted_at IS NULL;"

Per ogni booking trovato:
Chiama popolaSplitEconomico()
ricalcolando le righe con la nuova
logica separata.

Oppure più semplicemente:
Il PM può cliccare "Ricalcola" in
BookingDetail per ogni booking —
popolaSplitEconomico() verrà chiamato
e creerà le righe separate.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
10. BUILD E TEST
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package
cd frontend && npm run build
npm run typecheck

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena2026"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Inserisci booking manuale
# Ca' Serenella ha pulizie €50
# e cambio biancheria €20/persona
# con 2 ospiti → €40
# totale atteso: pulizie 50 +
#   cambio biancheria 40 = 90
BOOKING=$(curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"fkPropertyId": 6,
"fkCanaleOtaId": 2,
"checkinDate": "2026-12-10",
"checkoutDate": "2026-12-14",
"guests": 2,
"grossAmount": 380.00,
"guestName": "Test Pulizie Separate"
}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings")
BK_ID=$(echo $BOOKING \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['id'])")
echo "Booking: $BK_ID"

# Verifica righe split
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings/$BK_ID" \
| python3 -c "
import sys,json
b=json.load(sys.stdin)
for r in b.get('righeSplit',[]):
print(f\"{r['tipoVoce']:25} \
{r['importo']:8.2f} \
{r['source']:10} \
fk={r.get('fkPropertyContractRuleId')}\")
"

# Test override pulizie → 60€
# cambio biancheria rimane dalla regola
curl -s -X PATCH \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"pulizieOverride": 60.00}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings/$BK_ID/split" \
| python3 -c "
import sys,json
b=json.load(sys.stdin)
for r in b.get('righeSplit',[]):
print(f\"{r['tipoVoce']:25} \
{r['importo']:8.2f} \
{r['source']:10}\")
"

# Test override cambio biancheria → 50€
curl -s -X PATCH \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"cambioBiancheriaOverride": 50.00}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings/$BK_ID/split" \
| python3 -c "
import sys,json
b=json.load(sys.stdin)
for r in b.get('righeSplit',[]):
print(f\"{r['tipoVoce']:25} \
{r['importo']:8.2f} \
{r['source']:10}\")
"

# Test ripristino pulizie → regola
curl -s -X PATCH \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"pulizieOverride": null}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings/$BK_ID/split" \
| python3 -c "
import sys,json
b=json.load(sys.stdin)
for r in b.get('righeSplit',[]):
print(f\"{r['tipoVoce']:25} \
{r['importo']:8.2f} \
{r['source']:10} \
fk={r.get('fkPropertyContractRuleId')}\")
"

# Cleanup
psql -U sostitutoincloud \
-d sostitutoincloud -h localhost -c "
DELETE FROM booking
WHERE id = $BK_ID
AND fk_tenant_id = 1;"

Verifica che:
- Booking Ca' Serenella ha 2 righe
  separate: pulizie €50 e cambio
  biancheria €40 (20 × 2 ospiti)
- Override pulizie → solo pulizie
  cambia, cambio biancheria invariato
- Override cambio biancheria → solo
  cambio biancheria cambia
- Ripristino → torna alla regola
  con FK valorizzata
- cleaningAmount = pulizie +
  cambio biancheria (backward compat)
- Immobile senza regola cambio
  biancheria → nessuna riga
  cambio_biancheria nello split

Riporta output build e curl.