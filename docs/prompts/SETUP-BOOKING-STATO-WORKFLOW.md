Leggi il file CLAUDE.md e i file esistenti:
- service/BookingImportService.java
- service/DocumentGenerationService.java
- service/SettlementService.java
- service/ContrattoCalcolatoreService.java
- dao/BookingDAO.java
  prima di procedere.

Implementa l'avanzamento automatico di
fk_stato_prenotazione_id nel flusso booking.

Gli stati in tabella stato_prenotazione:
1 = imported   → creazione/import
2 = enriched   → CF ospite calcolato
3 = ready      → split economico calcolato
4 = doc_issued → documenti fiscali emessi
5 = settled    → liquidazione pagata
6 = cancelled  → annullato

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BookingDAO — metodo updateStato
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/BookingDAO.java:

void updateStato(Integer bookingId,
Integer statoId)
- UPDATE booking
  SET fk_stato_prenotazione_id = ?,
  updated_at = NOW()
  WHERE id = ?
- Log INFO "BookingDAO.updateStato()
    - bookingId={} statoId={}"

Aggiungi costanti statiche per gli ID stato
(per evitare magic numbers nel codice):
public static final int STATO_IMPORTED   = 1;
public static final int STATO_ENRICHED   = 2;
public static final int STATO_READY      = 3;
public static final int STATO_DOC_ISSUED = 4;
public static final int STATO_SETTLED    = 5;
public static final int STATO_CANCELLED  = 6;

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BookingService — metodo resolveStato
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a service/BookingService.java
un metodo privato:

int resolveStatoId(Booking booking)

Logica (in ordine di priorità):
1. Se booking.stato = 'cancelled'
   → return STATO_CANCELLED
2. Se fk_settlement presente e
   settlement.stato = 'paid'
   → return STATO_SETTLED
3. Se fiscal_document emesso
   (esiste fiscal_document con
   fk_booking_id = booking.id)
   → return STATO_DOC_ISSUED
4. Se split calcolabile:
   contrattoCalcolatoreService
   .calcola(booking) non lancia eccezione
   E booking.pmFeeAmount != null
   → return STATO_READY
5. Se guestTaxCode != null
   → return STATO_ENRICHED
6. → return STATO_IMPORTED

Aggiungi metodo pubblico:
void aggiornaStato(Integer bookingId)
- carica booking con findById()
- calcola resolveStatoId()
- chiama bookingDAO.updateStato()
- Log INFO "BookingService.aggiornaStato()
    - bookingId={} → statoId={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BookingImportService — aggiorna stato
   dopo confirm
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica service/BookingImportService.java
nel metodo confirmImport():

Dopo aver persistito ogni booking,
chiama bookingService.aggiornaStato(
booking.getId())

Questo assegna automaticamente
imported/enriched/ready a seconda
dei dati disponibili al momento
dell'import.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. DocumentGenerationService — doc_issued
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica se DocumentGenerationService
aggiorna già fk_stato_prenotazione_id
a doc_issued dopo l'emissione.

Se non lo fa, aggiungi dopo l'emissione
di entrambi i documenti (fattura PM +
ricevuta owner):
bookingDAO.updateStato(bookingId,
BookingDAO.STATO_DOC_ISSUED)

Log INFO "DocumentGenerationService:
booking {} → doc_issued"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. SettlementService — settled
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica service/SettlementService.java
nel metodo updateStatus():

Quando nuovoStato = 'paid':
- carica i booking del settlement
  tramite settlementBookingDAO
  .findBySettlementId(settlementId)
- per ogni bookingId:
  bookingDAO.updateStato(bookingId,
  BookingDAO.STATO_SETTLED)

Log INFO "SettlementService: {} booking
→ settled per settlement {}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. ALLINEAMENTO DATI ESISTENTI
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Esegui sul DB per allineare i booking
esistenti al loro stato corretto:

psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -c "
-- 5: settled (booking in settlement paid)
UPDATE booking b
SET fk_stato_prenotazione_id = 5,
updated_at = NOW()
FROM settlement_booking sb
JOIN settlement s ON s.id = sb.fk_settlement_id
WHERE sb.fk_booking_id = b.id
AND s.stato = 'paid';

-- 4: doc_issued (booking con fiscal_document)
UPDATE booking b
SET fk_stato_prenotazione_id = 4,
updated_at = NOW()
FROM fiscal_document fd
WHERE fd.fk_booking_id = b.id
AND b.fk_stato_prenotazione_id < 4;

-- 3: ready (booking con pmFeeAmount valorizzato
--    non ancora a doc_issued/settled)
UPDATE booking b
SET fk_stato_prenotazione_id = 3,
updated_at = NOW()
WHERE b.pm_fee_amount IS NOT NULL
AND b.pm_fee_amount > 0
AND b.fk_stato_prenotazione_id < 3;

-- 2: enriched (booking con guest_tax_code
--    non ancora a ready/doc_issued/settled)
UPDATE booking b
SET fk_stato_prenotazione_id = 2,
updated_at = NOW()
WHERE b.guest_tax_code IS NOT NULL
AND b.fk_stato_prenotazione_id < 2;
"

Verifica distribuzione stati dopo update:
psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -c "
SELECT sp.codice, sp.descrizione,
COUNT(b.id) AS num_booking
FROM stato_prenotazione sp
LEFT JOIN booking b
ON b.fk_stato_prenotazione_id = sp.id
GROUP BY sp.id, sp.codice, sp.descrizione
ORDER BY sp.id;"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. FRONTEND — BookingsList.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Il filtro "Da completare" in BookingsList
probabilmente filtra per stati < ready.
Verifica che il filtro stato esistente
usi i codici corretti dopo l'allineamento.

Se esiste un badge/colonna stato booking
nella lista, aggiorna le label e colori:
imported   → grigio    "Importata"
enriched   → blu       "Arricchita"
ready      → azzurro   "Pronta"
doc_issued → verde     "Doc. emesso"
settled    → verde scuro "Liquidata"
cancelled  → rosso     "Annullata"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. TEST
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
api/bookings" \
| python3 -m json.tool \
| grep -E '"id"|"statoPrenotazione"' \
| head -30

Verifica che:
- booking con fiscal_document → doc_issued
- booking in settlement paid → settled
- booking con pmFeeAmount → ready
- booking con solo CF → enriched
- booking senza nulla → imported

Riporta output build, verifica DB
distribuzione stati e curl.