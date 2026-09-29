Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- service/ContrattoCalcolatoreService.java
- service/BookingService.java
  metodi popolaSplitEconomico(),
  aggiungiVoceExtra(), aggiornaVoceExtra()
- dto/ContrattoCalcoloResult.java
- dao/BookingSplitEconomicoDAO.java
- model/BookingSplitEconomico.java
- frontend/src/pages/tenant/BookingDetail.tsx
  prima di procedere.

Implementa il modello IVA corretto:
le regole contratto esprimono importi NETTI,
il sistema aggiunge IVA 22% per ottenere
il lordo che va in fattura.

Schema Barbagallo:
base_pm = gross - Σspese_nette
PM_netto = base_pm × % / 100
PM_lordo = PM_netto × 1.22
netto_proprietario = gross - PM_lordo
- Σspese_lorde

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MIGRATION DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica l'ultimo numero migration
in docs/db/migrations/ e crea il
file successivo:
020_booking_split_imponibile.sql

ALTER TABLE booking_split_economico
ADD COLUMN IF NOT EXISTS
imponibile DECIMAL(10,2) DEFAULT NULL;

COMMENT ON COLUMN
booking_split_economico.imponibile IS
'Importo netto senza IVA (valore dalla
regola contratto o inserito dal PM).
importo = imponibile × (1 + aliquota_iva/100).
NULL per righe create prima della
migration 020.';

Aggiorna schema-target.sql.
Esegui sul DB locale.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. MODEL e DAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a model/BookingSplitEconomico.java:
BigDecimal imponibile

Aggiorna dao/mapper/
BookingSplitEconomicoRowMapper.java:
.imponibile(rs.getBigDecimal("imponibile"))

Aggiorna dao/BookingSplitEconomicoDAO.java:
- Aggiungi imponibile in SELECT_ALL
- Aggiungi imponibile nell'INSERT
- Aggiungi imponibile nell'UPDATE

Aggiungi a dto/booking/
BookingSplitEconomicoDTO.java:
BigDecimal imponibile

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. ContrattoCalcoloResult
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a ContrattoCalcoloResult.java
i campi imponibile per ogni voce:

BigDecimal otaImponibile
← netto OTA (importo regola o override/1.22)
BigDecimal pulizieImponibile
← netto pulizie
BigDecimal cambioBiancheriaImponibile
← netto cambio biancheria
BigDecimal pmImponibile
← netto PM

I campi esistenti (otaCommissionAmount,
pulizieAmount ecc.) diventano i LORDI
(imponibile × 1.22).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. ContrattoCalcolatoreService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica la logica di calcolo seguendo
lo schema Barbagallo:

Costanti:
private static final BigDecimal IVA_22 =
new BigDecimal("0.22");
private static final BigDecimal UNO_PIU_IVA =
new BigDecimal("1.22");

Per ogni voce calcolata dalla regola:
imponibile = valore_regola (è già netto)
lordo = imponibile × 1.22
.setScale(2, HALF_UP)

Per ogni override (pulizieOverride,
cambioBiancheriaOverride, otaOverride):
SE override arriva dal file (source=import):
← l'override è già un lordo
lordo = override
imponibile = lordo / 1.22
.setScale(2, HALF_UP)
SE override è manuale (dal PM):
← il PM inserisce il netto
imponibile = override
lordo = override × 1.22
.setScale(2, HALF_UP)

NOTA: come distinguere import da manuale?
Aggiungi parametro booleano a calcola():
boolean overrideDaImport
← true quando chiamato da
BookingImportService.confirm()
← false per tutti gli altri

Calcolo PM (schema Barbagallo):

1. Calcola Σspese_nette:
   speseNette = otaImponibile
    + pulizieImponibile
    + cambioBiancheriaImponibile

2. Carica voci extra da parametro:
   (le extra vengono passate come
   List<BigDecimal> extraImponibili
   dal chiamante)
   speseNette += Σ(extraImponibili)

3. Calcola base PM:
   basePm = gross - speseNette

   SE basePm < 0:
   basePm = BigDecimal.ZERO
   aggiungi warning

4. Per regola commissione_pm:
   SE calc_mode = 'percentuale_lordo'
   o 'percentuale_netto':
   pmImponibile = basePm
   × valore / 100
   .setScale(2, HALF_UP)
   pmLordo = pmImponibile × 1.22
   .setScale(2, HALF_UP)

   SE calc_mode = 'fisso':
   pmImponibile = valore_regola
   pmLordo = pmImponibile × 1.22
   .setScale(2, HALF_UP)

   SE pmFeeOverride != null:
   SE overrideManuale:
   pmImponibile = pmFeeOverride
   pmLordo = pmFeeOverride × 1.22
   SE overrideDaImport:
   pmLordo = pmFeeOverride
   pmImponibile = pmLordo / 1.22

5. Calcola totale fattura PM lordo:
   totaleFatturaPm =
   otaLordo + pulizieLordo
    + cambioBiancheriaLordo
    + Σ(extraLordi) + pmLordo

6. Netto proprietario:
   ownerNet = gross - totaleFatturaPm
   withholding = ownerNet
   × aliquotaRitenuta / 100

Aggiorna firma di calcola():
calcola(...,
List<BigDecimal> extraImponibili,
boolean overridesDaImport)

Aggiorna tutti i chiamanti:
- BookingImportService.confirm()
  → carica extra da splitEconomicoDAO
  prima di chiamare calcola()
  → passa overridesDaImport=true
- BookingService.createManuale()
  → extraImponibili = [] (nessuna extra
  alla creazione)
  → overridesDaImport=false
- BookingService.updateSplit()
  → carica extra da splitEconomicoDAO
  → overridesDaImport=false
- BookingService.toDetailDTO()
  → carica extra da splitEconomicoDAO
  → overridesDaImport=false

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BookingService.popolaSplitEconomico()
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna popolaSplitEconomico() per
salvare imponibile su ogni riga:

Riga OTA:
.imponibile(calcolo.getOtaImponibile())
.importo(calcolo.getOtaCommissionAmount())

Riga pulizie:
.imponibile(calcolo.getPulizieImponibile())
.importo(calcolo.getPulizieAmount())

Riga cambio biancheria:
.imponibile(calcolo
.getCambioBiancheriaImponibile())
.importo(calcolo
.getCambioBiancheriaAmount())

Riga PM:
.imponibile(calcolo.getPmImponibile())
.importo(calcolo.getPmFeeAmount())

Riga tassa soggiorno:
.imponibile(touristTaxAmount)
← tassa = netto = lordo (IVA 0)
.importo(touristTaxAmount)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. Voci extra — imponibile dal PM
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In BookingService.aggiungiVoceExtra()
e aggiornaVoceExtra():

Il PM inserisce il frontend l'imponibile
(netto). Il sistema calcola il lordo:

BigDecimal imponibile = dto.getImponibile()
← nuovo campo in BookingVoceExtraDTO
BigDecimal importo = imponibile
.multiply(UNO_PIU_IVA)
.setScale(2, RoundingMode.HALF_UP)

Aggiorna BookingVoceExtraDTO.java:
BigDecimal imponibile
← sostituisce o affianca importo
← il PM inserisce il netto,
il sistema calcola il lordo

Aggiorna ricalcolaNettoDaSplit():
Usa importo (lordo) per la somma
dei costi in fattura PM —
invariato rispetto a ora.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. FRONTEND — bookingApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna BookingSplitRiga:
imponibile?: number

Aggiorna VoceExtraRequest:
imponibile: number  ← il PM inserisce netto
← rimuovi o depreca importo

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. FRONTEND — BookingDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna la visualizzazione dello split:

Per le voci con aliquota_iva > 0
mostra tre valori invece di uno:

DA:
<span>-€{formatAmount(r.importo)}</span>

A:
  <div className="flex flex-col
    items-end text-sm">
    <span className="text-destructive">
      -{formatAmount(r.importo)}
    </span>
    <span className="text-xs
      text-muted-foreground">
      imp. €{formatAmount(r.imponibile)}
      + IVA €{formatAmount(
        r.importo - r.imponibile)}
    </span>
  </div>

Aggiungi riga totale IVA dopo
le voci calcolate e prima di
"Netto proprietario":

<div className="flex justify-between
  text-xs text-muted-foreground
  border-t pt-1 mt-1">
  <span>Totale servizi PM</span>
  <div className="flex flex-col items-end">
    <span>
      imp. €{formatAmount(totaleImponibile)}
    </span>
    <span>
      IVA €{formatAmount(totaleIva)}
    </span>
    <span className="font-medium
      text-foreground">
      tot. €{formatAmount(totaleLordo)}
    </span>
  </div>
</div>

Dove:
totaleImponibile = Σ(r.imponibile)
per righe include_in_fattura_pm=true
totaleIva = Σ(r.importo - r.imponibile)
totaleLordo = Σ(r.importo)

Per le voci extra il PM vede:
campo "Imponibile (€ netto IVA esclusa)"
il sistema mostra sotto:
"+ IVA 22% = €X → totale €Y"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
9. FRONTEND — form voci extra
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Nel form "Aggiungi voce" in BookingDetail:

DA:
<Input
placeholder="€"
value={voceForm.importo}
...
/>
label: "Importo €"

A:
<Input
placeholder="€ netto"
value={voceForm.imponibile}
onChange={e => setVoceForm({
...voceForm,
imponibile: e.target.value
})}
/>
<span className="text-xs
text-muted-foreground">
+ IVA 22% = €{(
parseFloat(voceForm.imponibile || '0')
* 0.22).toFixed(2)}
→ totale €{(
parseFloat(voceForm.imponibile || '0')
* 1.22).toFixed(2)}
</span>

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
# Ca' Serenella (property 6)
# lordo 1000, 2 ospiti, 4 notti
# OTA Booking.com
# Regole: OTA 18% netto, pulizie €50 netto
#   cambio €20/persona netto, PM 10% netto
#
# Atteso:
# OTA netto=180 lordo=219.60
# pulizie netto=50 lordo=61
# cambio netto=40 lordo=48.80
# base_pm = 1000 - 180 - 50 - 40 = 730
# PM netto=73 lordo=89.06
# totale fattura = 219.60+61+48.80+89.06
#               = 418.46
# netto proprietario = 1000 - 418.46
#                    = 581.54

BOOKING=$(curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"fkPropertyId": 6,
"fkCanaleOtaId": 2,
"checkinDate": "2026-12-10",
"checkoutDate": "2026-12-14",
"guests": 2,
"grossAmount": 1000.00,
"guestName": "Test IVA Schema"
}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings")
BK_ID=$(echo $BOOKING \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['id'])")
echo "Booking: $BK_ID"

# Verifica righe split con imponibile
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings/$BK_ID" \
| python3 -c "
import sys,json
b=json.load(sys.stdin)
print(f'grossAmount: {b[\"grossAmount\"]}')
print(f'ownerNetAmount: {b[\"ownerNetAmount\"]}')
print(f'totalCostiPm: {b[\"totalCostiPm\"]}')
print()
totale_lordo = 0
totale_netto = 0
for r in b.get('righeSplit',[]):
imp = r.get('imponibile') or 0
lord = r.get('importo') or 0
iva = lord - imp
if r['includeInFatturaPm']:
totale_lordo += lord
totale_netto += imp
print(f\"{r['tipoVoce']:25} \
netto={imp:8.2f} \
iva={iva:6.2f} \
lordo={lord:8.2f}\")
print()
print(f'Totale imponibile: {totale_netto:.2f}')
print(f'Totale IVA:        {totale_lordo-totale_netto:.2f}')
print(f'Totale fattura PM: {totale_lordo:.2f}')
print(f'Netto proprietario:{b[\"grossAmount\"]-totale_lordo:.2f}')
gross = b['grossAmount']
owner = b['ownerNetAmount']
print(f'Quadratura: {totale_lordo:.2f} + {owner:.2f} = {totale_lordo+owner:.2f} (atteso {gross:.2f})')
"

# Test voce extra con imponibile
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"descrizione": "Parcheggio",
"imponibile": 20.00,
"includeInFatturaPm": true
}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings/$BK_ID/split/extra" \
| python3 -c "
import sys,json
b=json.load(sys.stdin)
for r in b.get('righeSplit',[]):
if r['tipoVoce']=='extra':
print(f\"extra: imponibile={r.get('imponibile')} \
importo={r.get('importo')}\")
print(f'ownerNet: {b[\"ownerNetAmount\"]}')
"

# Cleanup
psql -U sostitutoincloud \
-d sostitutoincloud -h localhost -c "
DELETE FROM booking WHERE id = $BK_ID
AND fk_tenant_id = 1;"

Verifica che:
- Ogni riga split ha imponibile (netto)
  e importo (lordo = imponibile × 1.22)
- PM calcolato su base = gross - Σnetti
- Quadratura: totaleFattura + ownerNet
  = grossAmount
- Voce extra: imponibile inserito dal PM,
  importo calcolato dal sistema

Riporta output build e curl.