Leggi il file CLAUDE.md e i file esistenti:
- service/BookingService.java
- dto/booking/BookingDetailDTO.java
- dao/SettlementBookingDAO.java
- dao/SettlementDAO.java
  prima di procedere.

Aggiungi al BookingDetailDTO il campo
settlementStato derivato dal settlement
reale associato al booking, invece di
leggere booking.settlement_status.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. SettlementBookingDAO — nuovo metodo
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/SettlementBookingDAO.java:

Optional<Integer> findSettlementIdByBookingId(
Integer bookingId)
- SELECT fk_settlement_id
  FROM settlement_booking
  WHERE fk_booking_id = ?
  LIMIT 1
- Log DEBUG "SettlementBookingDAO
  .findSettlementIdByBookingId()
    - bookingId={} found={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BookingDetailDTO — nuovo campo
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dto/booking/BookingDetailDTO.java:

String settlementStato
← stato del settlement reale se esiste
("pending","calculated","approved","paid")
← null se nessun settlement trovato

Integer settlementId
← id del settlement se esiste
← null se nessun settlement trovato

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BookingService — popola i nuovi campi
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica service/BookingService.java:
- Aggiungi SettlementBookingDAO e
  SettlementDAO al costruttore

Nel metodo che costruisce BookingDetailDTO
(findById o equivalente), dopo aver
popolato gli altri campi:

Optional<Integer> settlementId =
settlementBookingDAO
.findSettlementIdByBookingId(booking.getId())

if (settlementId.isPresent()) {
settlementDAO.findById(settlementId.get())
.ifPresent(s -> {
dto.setSettlementStato(s.getStato())
dto.setSettlementId(s.getId())
})
}

Log DEBUG "BookingService: settlementStato={}
per bookingId={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND — bookingApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a BookingDetail in
frontend/src/api/bookingApi.ts:
- settlementStato?: string
- settlementId?: number

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. FRONTEND — BookingDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica la card "Liquidazione" in
frontend/src/pages/tenant/BookingDetail.tsx
(o componente equivalente):

Sostituisci la lettura di
booking.settlementStatus con
booking.settlementStato.

Mapping label:
null        → "In attesa"
"pending"   → "In attesa"
"calculated"→ "Calcolata"
"approved"  → "Approvata"
"paid"      → "Pagata"

Mapping colori (badge):
null/pending    → grigio
calculated      → blu
approved        → arancione
paid            → verde

Se settlementId != null rendi la card
cliccabile → naviga a
/settlements?id={settlementId}
(o /liquidazioni se è il path usato)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings/131" \
| python3 -m json.tool \
| grep -E '"settlementStato"|"settlementId"'

Verifica che:
- booking 131 (Laura Scenna) mostri
  settlementStato="approved" e
  settlementId valorizzato
- un booking senza settlement mostri
  settlementStato=null

Riporta output build e curl.