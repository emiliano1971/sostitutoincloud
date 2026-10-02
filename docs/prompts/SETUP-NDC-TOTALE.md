Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- service/NdcService.java
- service/BookingService.java
- service/WithholdingLedgerService.java
- service/F24Service.java
- controller/NdcController.java
- dao/BookingDAO.java
- dao/FiscalDocumentDAO.java
- dao/WithholdingLedgerDAO.java
- frontend/src/components/booking/
  NdcDialog.tsx
- frontend/src/pages/tenant/
  BookingDetail.tsx
- frontend/src/pages/tenant/BookingNew.tsx
  prima di procedere.

Aggiorna le note di credito per gestire
solo lo storno totale della fattura PM,
con gestione ritenuta, copia booking
e tracciabilità origine.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MIGRATION DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica l'ultimo numero migration
e crea il file successivo.

ALTER TABLE booking
ADD COLUMN IF NOT EXISTS
fk_booking_origine_id INTEGER
REFERENCES booking(id)
ON DELETE SET NULL;

COMMENT ON COLUMN
booking.fk_booking_origine_id IS
'Booking di origine da cui questa
prenotazione è stata copiata.
Valorizzato quando lo stato del
booking originale era stornata.';

Aggiorna schema-target.sql.
Esegui sul DB locale.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — NdcService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica NdcService.emettiNdc():

A) Forza sempre NDC totale:
Le righe vengono caricate
automaticamente dalla fattura
originale — non dal DTO:

List<FiscalDocumentRigaNdc> righe =
fiscalDocumentRigaNdcDAO
.findByFiscalDocumentId(
fattura.getId())

SE righe vuote:
← carica le righe dalla fattura
tramite VociFatturaPmService
e crea le righe NDC con
importo = riga.getImporto()
imponibile = riga.getImponibile()

totaleNdc = Σ importi righe
← sempre totale fattura

B) Blocco invio SDI se fattura
NON inviata:
SE fattura.stato NON IN
('sent_sdi', 'accepted'):
← non generare XML SDI
← non chiamare generaEInvia()
← la NDC resta in stato 'ready'
← log INFO "NDC senza invio SDI:
fattura non inviata"
ALTRIMENTI:
← genera e invia XML TD04
come già implementato

C) Gestione ritenuta:

Carica withholding_ledger
del booking:
List<WithholdingLedger> ledger =
withholdingLedgerDAO
.findByBookingId(
fattura.getFkBookingId())

Per ogni riga ledger:
SE riga.fkF24RecordId != null:
← la ritenuta è già in un F24
SE f24.stato == 'pagato':
← crea riga credito_imposta
withholdingLedgerService
.registraCredito(
tenantId,
fattura.getFkBookingId(),
ndc.getId(),
riga.getRitenutaAmount()
.negate())
log WARN "NdcService - ritenuta
già versata: credito imposta
€{} per booking {}"
ALTRIMENTI:
← F24 non pagato: rimuovi
la ritenuta dal F24
withholdingLedgerService
.stornaRitenutaDaF24(
tenantId,
riga.getId(),
ndc.getId())
ALTRIMENTI:
← ritenuta non ancora in F24
← segna come stornata
withholdingLedgerService
.stornaRitenuta(
tenantId,
riga.getId(),
ndc.getId())

D) Stato booking → 'stornata':
(già implementato, invariato)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — WithholdingLedgerService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi metodi:

void stornaRitenuta(
Integer tenantId,
Integer ledgerId,
Integer ndcId)
← ritenuta non ancora in F24:
UPDATE withholding_ledger SET
stato = 'stornata',
fk_ndc_id = ndcId,
updated_at = NOW()
WHERE id = ledgerId

void stornaRitenutaDaF24(
Integer tenantId,
Integer ledgerId,
Integer ndcId)
← F24 non pagato: rimuovi dal F24
UPDATE withholding_ledger SET
stato = 'stornata',
fk_ndc_id = ndcId,
fk_f24_record_id = NULL,
updated_at = NOW()
WHERE id = ledgerId

← Ricalcola totale F24:
f24Service.ricalcolaTotale(
withholdingLedger.fkF24RecordId)

void registraCredito(
Integer tenantId,
Integer bookingId,
Integer ndcId,
BigDecimal importoCredito)
← F24 già pagato: crea riga credito
INSERT INTO withholding_ledger (
fk_tenant_id,
fk_booking_id,
fk_ndc_id,
canone_locazione,
ritenuta_amount,
stato,
note)
VALUES (
tenantId, bookingId, ndcId,
0, importoCredito,
'credito_imposta',
'Credito da NDC ' || ndcDocNumber)

Aggiungi colonna fk_ndc_id
a withholding_ledger se non esiste:
ALTER TABLE withholding_ledger
ADD COLUMN IF NOT EXISTS
fk_ndc_id INTEGER
REFERENCES fiscal_document(id)
ON DELETE SET NULL;

Aggiungi stato 'stornata' e
'credito_imposta' a withholding_ledger
se non già presenti.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BACKEND — F24Service
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi metodo:

void ricalcolaTotale(Integer f24Id)
← ricalcola total_amount dell'F24
sommando le ritenute attive:

UPDATE f24_record SET
total_amount = (
SELECT COALESCE(SUM(ritenuta_amount), 0)
FROM withholding_ledger
WHERE fk_f24_record_id = f24Id
AND stato != 'stornata'
),
updated_at = NOW()
WHERE id = f24Id

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BACKEND — BookingService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi metodo:

BookingDetailDTO copiaBooking(
Integer tenantId,
Integer bookingId,
Integer utenteId)

1. Carica booking originale
   Verifica stato = 'stornata'
   SE stato != 'stornata':
   throw IllegalStateException(
   "Solo i booking stornati
   possono essere copiati")

2. Verifica che non esista già
   una copia attiva:
   Optional<Booking> copiaEsistente =
   bookingDAO.findByOrigine(
   bookingId, tenantId)
   SE presente e stato != 'stornata':
   throw IllegalStateException(
   "Esiste già una copia attiva:
   " + copia.externalBookingId)

3. Crea nuovo booking con:
    - Stessi dati dell'originale
      (property, canale, ospite,
      gross, date, notti, ospiti)
    - externalBookingId generato
      come inserimento manuale
      (MAN-{timestamp})
    - stato = 'imported'
    - fkBookingOrigineId = bookingId
    - touristTaxAmount = 0
      (verrà ricalcolato)
    - withholding = 0
      (verrà ricalcolato)

4. Chiama popolaSplitEconomico()
   per il nuovo booking

5. aggiornaStato()

6. Log INFO "BookingService
   .copiaBooking() - origine={}
   nuovoId={}"

7. Return findById(tenantId, newId)

Aggiungi a BookingDAO:
Optional<Booking> findByOrigine(
Integer fkBookingOrigineId,
Integer tenantId)
SELECT * FROM booking
WHERE fk_booking_origine_id = ?
AND fk_tenant_id = ?
AND stato != 'stornata'
LIMIT 1

Aggiungi fkBookingOrigineId
a model/Booking.java,
BookingRowMapper, BookingDAO
(SELECT, INSERT).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. BACKEND — Controller
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a BookingController.java:

POST /api/bookings/{id}/copia
- tenantId, utenteId da SecurityUtils
- chiama bookingService.copiaBooking()
- 201 BookingDetailDTO
- 400 se non stornata o copia
  già esistente
- 404 se non trovato
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. BACKEND — BookingDetailDTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a BookingDetailDTO.java:
Integer fkBookingOrigineId
String bookingOrigineCodice
← externalBookingId del booking
di origine (per il link)
Integer fkBookingCopiaId
← se esiste una copia attiva
String bookingCopiaCodice
← externalBookingId della copia

In BookingService.toDetailDTO():
Carica queste info aggiuntive:
- se fkBookingOrigineId valorizzato:
  carica bookingOrigineCodice
- cerca copia attiva con
  findByOrigine():
  se trovata → fkBookingCopiaId
  e bookingCopiaCodice

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. FRONTEND — NdcDialog.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna NdcDialog per NDC totale:

- Carica le righe dalla fattura
  originale (già nel dettaglio
  documento passato come prop)

- Mostra le righe con checkbox
  TUTTI selezionati e DISABILITATI:

  {righeFattura.map(r => (
    <div key={r.id}
      className="flex items-center
        gap-3 py-2 border-b
        opacity-75">
      <Checkbox
        checked={true}
        disabled={true}
      />
      <span className="flex-1 text-sm">
        {r.descrizione}
      </span>
      <span className="text-sm
        font-medium">
        -{formatAmount(r.importo)}
      </span>
    </div>
  ))}

- Rimuovi input importo parziale
  (NDC sempre totale)

- Mostra totale NDC:
  <div className="flex justify-between
    font-medium pt-2 border-t">
    <span>Totale storno</span>
    <span className="text-destructive">
      -{formatAmount(totaleFattura)}
    </span>
  </div>

- Messaggio informativo se fattura
  NON inviata SDI:
  <p className="text-xs
    text-muted-foreground mt-2">
    ⓘ La fattura non è stata inviata
    allo SDI: la nota di credito
    non verrà inviata automaticamente.
  </p>

- Messaggio se F24 già pagato
  (warning):
  SE booking ha ritenuta in F24 pagato:
  <p className="text-xs
    text-amber-600 mt-2">
    ⚠ La ritenuta è già stata versata.
    Verrà registrato un credito
    d'imposta compensabile nel
    prossimo F24.
  </p>

- Pulsante "Emetti NDC" invariato
  ← non passa più le righe nel body
  (le carica il backend dalla fattura)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
9. FRONTEND — bookingApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna BookingDetail:
fkBookingOrigineId?: number
bookingOrigineCodice?: string
fkBookingCopiaId?: number
bookingCopiaCodice?: string

Aggiungi:
export async function copiaBooking(
id: number
): Promise<BookingDetail>
// POST /api/bookings/{id}/copia

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
10. FRONTEND — BookingDetail.tsx
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

A) Se stato = 'stornata':
Mostra pulsante "Copia prenotazione":

SE booking.fkBookingCopiaId:
← esiste già una copia attiva
<p className="text-xs
text-muted-foreground">
Copiata in:
<Link to={`/bookings/
         ${booking.fkBookingCopiaId}`}>
{booking.bookingCopiaCodice}
</Link>
</p>
ALTRIMENTI:
<Button
onClick={handleCopia}
variant="outline"
size="sm">
<Copy className="h-4 w-4 mr-1" />
Copia prenotazione
</Button>

handleCopia:
const nuovoBooking =
await copiaBooking(booking.id)
navigate(
`/bookings/${nuovoBooking.id}`)
toast({
title: "Prenotazione copiata",
description: nuovoBooking
.externalBookingId
})

B) Se booking.fkBookingOrigineId:
← questa è una copia
Mostra in alto nella card info:

   <div className="text-xs
     text-muted-foreground">
     Copiata da:
     <Link to={`/bookings/
       ${booking.fkBookingOrigineId}`}>
       {booking.bookingOrigineCodice}
     </Link>
   </div>

Importa Copy da lucide-react
se non già importato.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
11. BUILD E TEST
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

# Trova una fattura PM emessa
# e il suo booking
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/documents?tipo=fattura_pm" \
| python3 -m json.tool | head -40

# Emetti NDC totale
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"fkFiscalDocumentId": {FATTURA_ID}
}' \
"http://localhost:8081/sostitutoincloud/\
api/ndc" \
| python3 -m json.tool \
| grep -E '"documentNumber|\
stato|totalAmount"'

# Verifica stato booking = stornata
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings/{BOOKING_ID}" \
| python3 -m json.tool \
| grep -E '"stato|withholding|\
credito"'

# Verifica withholding_ledger
# (stornata o credito_imposta)
psql -U sostitutoincloud \
-d sostitutoincloud -h localhost -c "
SELECT id, stato, ritenuta_amount,
fk_f24_record_id, fk_ndc_id
FROM withholding_ledger
WHERE fk_booking_id = {BOOKING_ID};"

# Copia booking stornato
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings/{BOOKING_ID}/copia" \
| python3 -m json.tool \
| grep -E '"id|externalBookingId|\
fkBookingOrigineId|stato"'

# Verifica link origine nel nuovo booking
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings/{NUOVO_BOOKING_ID}" \
| python3 -m json.tool \
| grep -E '"fkBookingOrigineId|\
bookingOrigineCodice"'

# Tenta seconda copia → 400
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings/{BOOKING_ID}/copia" \
| python3 -m json.tool

# Cleanup
psql -U sostitutoincloud \
-d sostitutoincloud -h localhost -c "
-- Cancella prima il booking copia
-- poi il booking originale con
-- docs/db/cleanup-booking-dbeaver.sql"

Verifica che:
- NDC totale creata automaticamente
  dalle righe della fattura
- Stato booking = stornata
- Withholding stornato o credito
  a seconda dello stato F24
- Copia booking creata con
  fkBookingOrigineId valorizzato
- Link bidirezionale tra originale
  e copia nel dettaglio
- Seconda copia → 400

Riporta output build e curl.