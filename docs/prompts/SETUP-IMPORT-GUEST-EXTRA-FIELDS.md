Leggi il file CLAUDE.md e i file esistenti:
- service/BookingImportService.java
- dto/importing/BookingImportRowDTO.java
  prima di procedere.

Aggiungi 3 nuovi campi mappabili nel file
ospiti e correggi la logica del CF.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — nuovi campi mapping ospiti
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a BookingImportService.java
nella mappa dei campi guest mappabili:

"Codice Fiscale" → CODICE_FISCALE
"Indirizzo"      → INDIRIZZO
"Telefono"       → TELEFONO

Nei suggestedMapping automatici aggiungi
il matching per questi nuovi campi:
- "Codice Fiscale", "CF", "CodiceFiscale",
  "codice_fiscale" → CODICE_FISCALE
- "Indirizzo", "indirizzo" → INDIRIZZO
- "Telefono", "telefono", "Phone",
  "phone" → TELEFONO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. GuestData — nuovi campi
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi alla classe GuestData
(o al DTO equivalente che raccoglie
i dati ospite durante il parsing):

String codiceFiscale   ← dal file
String indirizzo
String telefono

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. LOGICA CF — priorità al valore del file
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In BookingImportService nel punto dove
viene calcolato il CF (calcolaSafe),
modifica la logica:

DA:
// Calcola sempre il CF se i dati
// anagrafici sono presenti
String cf = codiceFiscaleService
.calcolaSafe(...)

A:
String cf = null;

// 1. Usa il CF dal file se presente
if (guestData.getCodiceFiscale() != null
&& !guestData.getCodiceFiscale()
.isBlank()) {
cf = guestData.getCodiceFiscale()
.trim().toUpperCase();
log.debug("CF dal file: {}", cf);
}

// 2. Solo se assente, calcola dal CF
if (cf == null) {
cf = codiceFiscaleService
.calcolaSafe(
cognome, nome, dataNascita,
sesso, comuneNascita)
.orElse(null);
if (cf != null) {
log.debug("CF calcolato: {}", cf);
}
}

Rimuovi il warning "CF non calcolabile"
se il CF è stato fornito direttamente
dal file.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BookingImportRowDTO — nuovi campi
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dto/importing/
BookingImportRowDTO.java:

String guestCodiceFiscale  ← CF dal file
String guestIndirizzo
String guestTelefono

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. Booking — propaga i nuovi campi
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che booking abbia i campi:
- guest_phone (già presente?)
- guest_address (presente?)

Se mancano aggiungili con migration:

docs/db/migrations/013_booking_guest_extra.sql:
ALTER TABLE booking
ADD COLUMN IF NOT EXISTS
guest_address VARCHAR(200),
ADD COLUMN IF NOT EXISTS
guest_phone   VARCHAR(30);

Esegui sul DB locale.
Aggiorna model/Booking.java,
BookingRowMapper, BookingDAO
(SELECT_ALL, INSERT, updateGuestAnagrafica).

In BookingImportService.confirm()
propaga i nuovi campi al Booking:
.guestAddress(row.getGuestIndirizzo())
.guestPhone(row.getGuestTelefono())

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. GuestEditDialog — aggiungi i nuovi campi
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In frontend/src/components/
GuestEditDialog.tsx aggiungi i campi:

- Indirizzo (text input, opzionale)
- Telefono (text input, opzionale)

Aggiorna bookingApi.ts:
GuestUpdateRequest aggiungi:
guestAddress?: string
guestPhone?: string

Aggiungi a BookingDetailDTO:
guestAddress
guestPhone

In PATCH /api/bookings/{id}/guest
aggiorna il BookingDAO.updateGuestAnagrafica
per includere anche i nuovi campi.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package
cd frontend && npm run build &&
npm run typecheck

Verifica importando un file ospiti
con colonna "Codice Fiscale" valorizzata:
- Il CF dal file deve essere usato
  senza ricalcolo
- Il warning "CF non calcolabile"
  non deve comparire se CF è nel file
- indirizzo e telefono vengono salvati

Riporta output build.