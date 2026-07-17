Leggi il file CLAUDE.md e questi file:
- src/main/java/it/gavia/sostitutoincloud/
  service/DocumentGenerationService.java
- src/main/java/it/gavia/sostitutoincloud/
  service/ContrattoCalcolatoreService.java

Correggi la logica fiscale dei documenti
e dello split economico.

I valori che arrivano dalle OTA (commissione,
pulizie, commissione PM) sono già LORDI
IVA inclusa. L'IVA va SCORPORATA, non
aggiunta sopra.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
LOGICA CORRETTA
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Dati:
gross = €200
ota = €30 (lordo IVA inclusa)
pulizie = €60 (lordo IVA inclusa)
pmFee = €40 (lordo IVA inclusa)

FATTURA PM:
lordo_servizi = ota + pulizie + pmFee = €130
imponibile = lordo_servizi / (1 + aliquota_iva)
= 130 / 1.22 = €106,56
iva = lordo_servizi - imponibile
= 130 - 106,56 = €23,44
total_amount = lordo_servizi = €130
(NON imponibile + iva calcolata sopra)
vat_amount = iva scorporata = €23,44
aliquota_iva = 22.00

RICEVUTA OWNER:
canone = gross - lordo_servizi
= 200 - 130 = €70
bollo = canone > 77.47 ? 2.00 : 0.00
total_amount = canone + bollo = €70
ritenuta = canone * aliquota_ritenuta
= 70 * 0.21 = €14,70
imponibile = canone = €70
vat_amount = 0
aliquota_iva = 0

QUADRATURA:
fattura PM total_amount + ricevuta canone
= 130 + 70 = 200 = gross ✅

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. CORREGGI DocumentGenerationService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi costante:
DIVISORE_IVA_22 = new BigDecimal("1.22")

Per "fattura_pm":
BigDecimal lordoServizi =
nz(ota) + nz(cleaning) + nz(pmFee)
BigDecimal imponibile =
lordoServizi.divide(DIVISORE_IVA_22, 2, HALF_UP)
BigDecimal iva =
lordoServizi.subtract(imponibile)
.setScale(2, HALF_UP)
BigDecimal totaleFattura = lordoServizi
← NON imponibile + iva
BigDecimal ritenuta =
booking.getWithholdingAmount()
BigDecimal bollo = ZERO
aliquotaIva = 22.00
vatAmount = iva

Per "ricevuta_owner":
- Cerca fattura PM già emessa per booking:
  fiscalDocumentDAO.findByBookingId(bookingId)
  filtra tipo "fattura"
- Se fattura PM esiste:
  canone = gross - fattura.getTotalAmount()
- Se NO:
  canone = gross - (nz(ota)+nz(cleaning)+nz(pmFee))
  (fallback senza fattura)
- bollo = canone > bolloSoglia ? bolloImporto : 0
- total_amount = canone + bollo
- ritenuta = canone * aliquotaRitenuta
- imponibile = canone
- vatAmount = 0
- aliquotaIva = 0
- canoneLocazione = canone

IMPORTANTE — regime forfettario RF19:
Se settings.getRegimeFiscalePm() = "RF19":
- imponibile = lordoServizi (nessuno scorporo)
- iva = BigDecimal.ZERO
- vatAmount = BigDecimal.ZERO
- aliquotaIva = 0.00
- totaleFattura = lordoServizi

Lo scorporo IVA (lordo / 1.22) va fatto
SOLO per regime RF01 ordinario.

Stessa condizione in
ContrattoCalcolatoreService:
RF01 → ivaScorporata = lordo - lordo/1.22
RF19 → ivaScorporata = 0

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. CORREGGI ContrattoCalcolatoreService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Il calcolo dello split deve usare i valori
LORDI — non scorporare l'IVA qui.

Modifica:
imponibilePm = lordo_servizi
(ota + cleaning + pmFee)
← questi sono già lordi IVA inclusa

ivaPm = lordo_servizi -
lordo_servizi.divide(1+aliquota, 2, HALF_UP)
← IVA scorporata (solo informativa)

fatturaPmTotale = lordo_servizi
← totale lordo della fattura PM

ownerNetAmount = rimanenza dal contratto
← NON gross - fatturaPmTotale
← è il valore calcolato dalla regola
is_remainder del contratto

ownerNetAmount deve essere uguale a:
gross - fatturaPmTotale
= gross - lordo_servizi
= gross - (ota + cleaning + pmFee)

Questo coincide con la rimanenza
contrattuale ✅

withholdingAmount = ownerNetAmount *
(aliquotaRitenuta / 100)
liquidazioneOwner = ownerNetAmount -
withholdingAmount

Aggiungi al ContrattoCalcoloResult:
- BigDecimal imponibileFatturaPm
  ← lordo_servizi / (1 + aliquota_iva)
- BigDecimal ivaScorporata
  ← lordo_servizi - imponibileFatturaPm

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. AGGIORNA SplitEconomicoDTO e frontend
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In SplitEconomicoDTO.java:
- Rinomina ivaPm → ivaScorporataPm
  (per chiarire che è IVA scorporata
  non aggiunta)
- Aggiungi imponibileFatturaPm

In bookingApi.ts:
- Aggiorna SplitEconomico con i nuovi campi

In BookingDetail.tsx:
- La riga IVA servizi PM deve mostrare:
  "IVA 22% (scorporata sui servizi PM)"
  con valore ivaScorporataPm
  come nota informativa (non detrazione)
  → usa un colore grigio/neutro invece
  di rosso, senza segno −
  → es. "di cui IVA: €23,44"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

Dopo riavvio Tomcat verifica con curl
booking 1:

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena"}' \
| python3 -c \
"import sys,json; \
print(json.load(sys.stdin)['token'])")

curl -s -H "Authorization: Bearer $TOKEN" \
http://localhost:8081/sostitutoincloud/\
api/bookings/1 \
| python3 -m json.tool \
| grep -A 35 "splitEconomico"

Verifica che:
- ownerNetAmount = 70.00
  (rimanenza contrattuale)
- fatturaPmTotale = 130.00
  (lordo servizi)
- ivaScorporataPm = 23.44
  (130 - 130/1.22)
- imponibileFatturaPm = 106.56
  (130 / 1.22)
- withholdingAmount = 14.70
  (70 * 0.21)
- liquidazioneOwner = 55.30
  (70 - 14.70)

Poi genera un nuovo documento per
booking 1 (elimina prima quelli esistenti)
e verifica nel DB:

psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -c "
SELECT document_number, total_amount,
vat_amount, imponibile,
ritenuta_amount, canone_locazione
FROM fiscal_document
WHERE fk_booking_id = 1
ORDER BY id;"

Verifica che:
Fattura PM:
total_amount = 130.00
vat_amount = 23.44
imponibile = 106.56

Ricevuta:
total_amount = 70.00
imponibile = 70.00
canone_locazione = 70.00
ritenuta_amount = 14.70

Riporta output di entrambi i build
e delle verifiche.