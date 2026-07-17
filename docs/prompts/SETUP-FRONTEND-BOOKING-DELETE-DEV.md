Leggi il file CLAUDE.md e docs/db/schema-target.sql
prima di procedere.

Aggiungi la possibilità di cancellare booking
(con cascata su tutti i dati collegati)
dalla lista prenotazioni.
Disponibile SOLO nei profili local e test.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — BookingDAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/BookingDAO.java:

void deleteById(Integer id)
- DELETE FROM booking WHERE id = ?
- Log INFO "BookingDAO.deleteById() - id={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — BookingService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a service/BookingService.java:

void deleteWithCascade(Integer tenantId,
Integer bookingId)

Logica (ordine preciso per rispettare i FK):
1. Verifica esistenza booking e appartenenza
   al tenant — NoSuchElementException se non trovato
2. DELETE FROM settlement_booking
   WHERE fk_booking_id = ?
3. DELETE FROM withholding_ledger
   WHERE fk_booking_id = ?
4. DELETE FROM fiscal_document
   WHERE fk_booking_id = ?
5. DELETE FROM booking WHERE id = ?
6. Audit:
   auditService.log("booking.delete",
   "Booking", bookingId,
   "Cancellazione cascata booking id=" + bookingId)
7. Log INFO "BookingService.deleteWithCascade()
    - id={}"

Le DELETE ai punti 2-4 vanno eseguite
tramite i DAO già esistenti aggiungendo
un metodo deleteByBookingId(Integer bookingId)
a ciascuno:
- SettlementBookingDAO.deleteByBookingId()
- WithholdingLedgerDAO.deleteByBookingId()
- FiscalDocumentDAO.deleteByBookingId()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — BookingController
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/BookingController.java:

DELETE /api/bookings/{id}
- Annotato con @Profile({"local","test"})
  per garantire che non sia disponibile
  in produzione
- tenantId = SecurityUtils.getCurrentTenantId()
- chiama bookingService.deleteWithCascade()
- ResponseEntity.ok(Map.of(
  "message", "Booking eliminato",
  "id", id))
- catch NoSuchElementException → 404
- catch Exception → 500 con log ERROR
- Log INFO "BookingController.delete() - id={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND — bookingApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/bookingApi.ts:

export async function deleteBooking(
id: number
): Promise<void>
// DELETE /api/bookings/{id}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. FRONTEND — BookingsList.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/tenant/
BookingsList.tsx:

- Aggiungi stato:
  const [selectedIds, setSelectedIds] =
  useState<Set<number>>(new Set())

- Aggiungi checkbox nella prima colonna
  di ogni riga (prima di "ID / Canale"):
    * checked = selectedIds.has(booking.id)
    * onChange → toggle id nel Set
    * stopPropagation (la riga naviga al dettaglio)

- Aggiungi checkbox "seleziona tutti"
  nell'intestazione della prima colonna:
    * checked = selectedIds.size === sorted.length
      && sorted.length > 0
    * onChange → seleziona/deseleziona tutti

- Mostra barra azioni in cima alla lista
  SOLO quando selectedIds.size > 0:
  "[N] selezionati"
    + pulsante rosso "🗑 Elimina selezionati"
      con confirm():
      "Eliminare [N] booking e tutti i dati
      collegati (documenti, ritenute,
      liquidazioni)? Operazione irreversibile."
    + pulsante "Annulla selezione"

- onClick "Elimina selezionati":
    * chiama deleteBooking() per ogni id
      in sequenza (await in loop)
    * toast di successo "N booking eliminati"
    * svuota selectedIds
    * ricarica lista

- Gestione errori: se una delete fallisce
  mostra toast di errore con l'id del
  booking problematico e continua con
  gli altri

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Conta booking prima
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/api/bookings" \
| python3 -c "import sys,json; \
print('Booking totali:', len(json.load(sys.stdin)))"

# Elimina booking id=1
curl -s -X DELETE \
-H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/api/bookings/1" \
| python3 -m json.tool

# Conta booking dopo
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/api/bookings" \
| python3 -c "import sys,json; \
print('Booking dopo delete:', len(json.load(sys.stdin)))"

# Verifica cascata sul DB
psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -c "
SELECT 'fiscal_document' AS tabella,
COUNT(*) FROM fiscal_document
WHERE fk_booking_id = 1
UNION ALL
SELECT 'withholding_ledger',
COUNT(*) FROM withholding_ledger
WHERE fk_booking_id = 1
UNION ALL
SELECT 'settlement_booking',
COUNT(*) FROM settlement_booking
WHERE fk_booking_id = 1;"

Verifica che tutte e 3 le tabelle
restituiscano COUNT = 0.
Riporta output build e curl.