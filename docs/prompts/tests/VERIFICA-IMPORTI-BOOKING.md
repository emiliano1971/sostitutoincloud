Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- frontend/tests/e2e/helpers/auth.ts
- frontend/tests/e2e/helpers/api.ts
  prima di procedere.

Crea frontend/tests/e2e/
verifica-importi-booking.spec.ts

Test parametrico che verifica la coerenza
degli importi fiscali di una prenotazione.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PARAMETRO BOOKING
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

const bookingId: number = process.env
.BOOKING_ID
? parseInt(process.env.BOOKING_ID)
: 0  // 0 = trova automaticamente

In beforeAll:
- Ottieni token tenantAdmin
- Se bookingId > 0:
  usa quello
- Altrimenti:
  GET /api/bookings → trova il primo
  con statoPrenotazione === 'doc_issued'
  o === 'settled'
  Se non trovato → test.skip()
  con messaggio "Nessun booking con
  documenti fiscali emessi trovato"

- Carica tutti i dati necessari:
  booking = GET /api/bookings/{id}
  documents = GET /api/documents
  filtra per fkBookingId === id
  ricevuta = documents.find(
  d => d.documentType === 'ricevuta_owner'
  || d.tipo === 'ricevuta_owner')
  fattura = documents.find(
  d => d.documentType === 'fattura_pm'
  || d.tipo === 'fattura_pm')

  Se ricevuta presente:
  ricevutaDetail = GET /api/documents
  /{ricevuta.id}

  Se ricevuta ha settlementId:
  settlement = GET /api/settlements
  /{ricevutaDetail.settlementId}

  Se ricevuta ha f24RecordId:
  f24 = GET /api/f24
  /{ricevutaDetail.f24RecordId}

  Se ricevuta ha cuRecordId:
  cu = GET /api/cu
  /{ricevutaDetail.cuRecordId}

  Carica withholding_ledger:
  ledger = GET /api/withholding-ledger
  filtra per fkBookingId === id
  (verifica se l'endpoint supporta
  il filtro, altrimenti carica tutto
  e filtra lato test)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TOLLERANZA
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Definisci helper per confronto decimali:
const EPS = 0.02  // tolleranza 2 centesimi

const approxEqual = (
a: number, b: number,
eps = EPS): boolean =>
Math.abs(a - b) <= eps

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TEST CASES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

test.describe con nome dinamico:
`Verifica importi booking ${bookingId}`

Tutti i test sono mode: serial e usano
i dati caricati in beforeAll.
Se un dato non è disponibile (es. nessun
settlement) il test viene skippato con
test.skip() e messaggio esplicativo.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
V.1 — Coerenza split booking
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che il lordo sia spiegato
dalle voci dello split:

const tassa = booking.touristTaxAmount ?? 0
const tassaInclusa =
booking.touristTaxIncludedInGross ?? false

const baseCalcolo = tassaInclusa
? booking.grossAmount - tassa
: booking.grossAmount

const sommaVoci =
(booking.otaCommissionAmount ?? 0)
+ (booking.cleaningAmount ?? 0)
+ (booking.pmFeeAmount ?? 0)
+ (booking.ownerNetAmount ?? 0)

// Se tassa inclusa: baseCalcolo = sommaVoci
// Se tassa esclusa: gross = sommaVoci
const atteso = tassaInclusa
? baseCalcolo : booking.grossAmount

expect(approxEqual(sommaVoci, atteso))
.toBeTruthy()

Log: `Split: gross=${gross}
  tassa=${tassa} inclusa=${tassaInclusa}
  base=${baseCalcolo}
  OTA+pulizie+PM+netto=${sommaVoci}
  delta=${sommaVoci - atteso}`

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
V.2 — Coerenza ritenuta booking
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

const ritenutaAttesa =
booking.ownerNetAmount
* booking.aliquotaRitenuta / 100

expect(approxEqual(
booking.withholdingAmount,
ritenutaAttesa)).toBeTruthy()

Log: `Ritenuta: netto=${ownerNet}
  aliquota=${aliquota}%
  attesa=${ritenutaAttesa}
  db=${withholdingAmount}
  delta=${delta}`

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
V.3 — Ricevuta owner vs booking
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

SE ricevutaDetail assente → skip

expect(approxEqual(
ricevutaDetail.canoneLocazione,
booking.ownerNetAmount)).toBeTruthy()

expect(approxEqual(
ricevutaDetail.ritenutaAmount,
booking.withholdingAmount)).toBeTruthy()

Log valori e delta

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
V.4 — Fattura PM vs booking
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

SE fattura assente → skip

const serviziPm =
(booking.otaCommissionAmount ?? 0)
+ (booking.cleaningAmount ?? 0)
+ (booking.pmFeeAmount ?? 0)

expect(approxEqual(
fattura.totalAmount,
serviziPm)).toBeTruthy()

Log valori e delta

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
V.5 — Withholding ledger vs ricevuta
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

SE ledger o ricevuta assente → skip

const rigaLedger = ledger.find(
l => l.fkBookingId === bookingId)

SE rigaLedger assente → skip
"Nessuna riga ledger per questo booking"

expect(approxEqual(
rigaLedger.canoneLocazione,
ricevutaDetail.canoneLocazione))
.toBeTruthy()

expect(approxEqual(
rigaLedger.ritenutaAmount,
ricevutaDetail.ritenutaAmount))
.toBeTruthy()

Log valori e delta

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
V.6 — F24 vs Σ ritenute del periodo
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

SE f24 assente → skip
"Nessun F24 collegato a questo booking"

// Carica le righe del dettaglio F24
f24Detail = GET /api/f24/{f24Id}
(con le righe ritenute)

const sommaRitenute = f24Detail
.ritenute
.reduce((s, r) =>
s + r.ritenutaAmount, 0)

expect(approxEqual(
f24Detail.totalAmount,
sommaRitenute)).toBeTruthy()

// Verifica che la ritenuta del booking
// sia tra le righe dell'F24
const rigaF24 = f24Detail.ritenute
.find(r => r.bookingId === bookingId
|| r.fkBookingId === bookingId)

expect(rigaF24).toBeDefined()

Log valori e delta

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
V.7 — Settlement vs Σ canoni e ritenute
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

SE settlement assente → skip
"Nessuna liquidazione collegata"

const sommaCanoni = settlement
.bookings
.reduce((s, b) =>
s + (b.ownerNetAmount
?? b.canoneLocazione ?? 0), 0)

const sommaRitenute = settlement
.bookings
.reduce((s, b) =>
s + (b.withholdingAmount ?? 0), 0)

expect(approxEqual(
settlement.totalAmount,
sommaCanoni)).toBeTruthy()

expect(approxEqual(
settlement.withholdingAmount,
sommaRitenute)).toBeTruthy()

expect(approxEqual(
settlement.netAmount,
sommaCanoni - sommaRitenute))
.toBeTruthy()

// Verifica che il booking
// sia tra quelli del settlement
const bkInSettlement = settlement
.bookings.find(b =>
b.bookingId === bookingId
|| b.externalBookingId ===
booking.externalBookingId)

expect(bkInSettlement).toBeDefined()

Log valori e delta

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
V.8 — CU vs Σ compensi e ritenute anno
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

SE cu assente → skip
"Nessuna CU collegata a questo booking"

// I totali CU devono essere >= ai valori
// del singolo booking (la CU aggrega
// tutti i booking dell'anno)
expect(cu.totalCompensi)
.toBeGreaterThanOrEqual(
booking.ownerNetAmount - EPS)

expect(cu.totalRitenute)
.toBeGreaterThanOrEqual(
booking.withholdingAmount - EPS)

Log valori CU e booking

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ESECUZIONE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Assicurati che siano attivi:
- Tomcat su :8081
- Vite su :5173

# Con booking specifico:
BOOKING_ID=200 npx playwright test \
tests/e2e/verifica-importi-booking.spec.ts

# Automatico (trova primo con doc emessi):
npx playwright test \
tests/e2e/verifica-importi-booking.spec.ts

Riporta output con ✅/⏭/❌
per ogni verifica V.1-V.8
e i valori numerici di ogni delta.
Se un test fallisce riporta i valori
esatti senza correggere — aspetta
istruzioni.