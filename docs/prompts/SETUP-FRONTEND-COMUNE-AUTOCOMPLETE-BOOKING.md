Leggi il file CLAUDE.md e i file esistenti:
- frontend/src/pages/tenant/BookingDetail.tsx
- frontend/src/components/ComuneAutocomplete.tsx
- frontend/src/api/bookingApi.ts
  prima di procedere.

Collega ComuneAutocomplete al form anagrafica
ospite nel dettaglio booking, per il campo
"Comune di nascita" con calcolo CF automatico.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — verifica endpoint update ospite
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che esista un endpoint per
aggiornare i dati anagrafici dell'ospite
su un booking esistente.
Es. PATCH /api/bookings/{id}/guest
o PUT /api/bookings/{id}

Se non esiste crealo:

PATCH /api/bookings/{id}/guest
- @RequestBody GuestUpdateDTO
- GuestUpdateDTO:
  String guestName
  String guestTaxCode
  String guestBirthDate    ← ISO date
  String guestSesso        ← M/F
  String guestBirthPlace   ← nome comune
  String guestBirthBelfiore ← codice Belfiore
  String guestDocType
  String guestDocNumber
  String guestCountry
- UPDATE booking SET
  guest_name = ?,
  guest_tax_code = ?,
  guest_birth_date = ?,
  guest_sesso = ?,
  guest_birth_place = ?,
  guest_birth_belfiore = ?,
  guest_doc_type = ?,
  guest_doc_number = ?,
  guest_country = ?,
  updated_at = NOW()
  WHERE id = ? AND fk_tenant_id = ?
- Dopo update chiama
  bookingService.aggiornaStato(id)
  per ricalcolare lo stato (enriched/ready)
- ResponseEntity.ok(BookingDetailDTO)
- catch NoSuchElementException → 404
- Log INFO

Verifica che booking abbia i campi:
guest_birth_date, guest_sesso,
guest_birth_place, guest_birth_belfiore,
guest_doc_type, guest_doc_number,
guest_country nel DB e nel model.
Se mancano aggiungili con migration:

docs/db/migrations/007_booking_guest_anagrafica.sql:
ALTER TABLE booking
ADD COLUMN IF NOT EXISTS
guest_birth_date DATE,
ADD COLUMN IF NOT EXISTS
guest_sesso CHAR(1),
ADD COLUMN IF NOT EXISTS
guest_birth_place VARCHAR(100),
ADD COLUMN IF NOT EXISTS
guest_birth_belfiore CHAR(4),
ADD COLUMN IF NOT EXISTS
guest_doc_type VARCHAR(30),
ADD COLUMN IF NOT EXISTS
guest_doc_number VARCHAR(30),
ADD COLUMN IF NOT EXISTS
guest_country VARCHAR(50);

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. CodiceFiscaleService — endpoint REST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che esista
POST /api/cf/calcola (già creato in
SETUP-COMUNI-CF-AUTOMATICO).
Se esiste, nessuna modifica necessaria.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. FRONTEND — bookingApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/bookingApi.ts:

export interface GuestUpdateRequest {
guestName: string
guestTaxCode?: string
guestBirthDate?: string
guestSesso?: string
guestBirthPlace?: string
guestBirthBelfiore?: string
guestDocType?: string
guestDocNumber?: string
guestCountry?: string
}

export async function updateBookingGuest(
id: number,
data: GuestUpdateRequest
): Promise<BookingDetail>
// PATCH /api/bookings/{id}/guest

Aggiungi a bookingApi.ts:
export async function calcolaCodiceFiscale(
cognome: string,
nome: string,
dataNascita: string,
sesso: string,
comuneNascita: string
): Promise<string>
// POST /api/cf/calcola
// ritorna solo il CF come stringa

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND — GuestEditDialog.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/components/
GuestEditDialog.tsx:

Props:
bookingId: number
guest: {
guestName?: string
guestTaxCode?: string
guestBirthDate?: string
guestSesso?: string
guestBirthPlace?: string
guestBirthBelfiore?: string
guestDocType?: string
guestDocNumber?: string
guestCountry?: string
}
open: boolean
onClose: () => void
onSaved: (updated: BookingDetail) => void

CAMPI DEL FORM:
- Nome completo ospite (text input)
- Data di nascita (date input)
- Sesso (select: M/F)
- Comune di nascita:
  USA ComuneAutocomplete
  onChange → imposta guestBirthPlace
  (nome) e guestBirthBelfiore (codice)
- Tipo documento (select):
  CARTA_IDENTITA, PASSAPORTO,
  PATENTE, ALTRO
- Numero documento (text input)
- Nazione (text input, default "Italia")
- Codice Fiscale (text input readonly
  con pulsante "Calcola CF" accanto)

PULSANTE "Calcola CF":
- Abilitato solo se nome, cognome
  (split da guestName), data nascita,
  sesso e comune sono valorizzati
- onClick → chiama calcolaCodiceFiscale()
- successo → popola il campo CF
- errore → toast "Impossibile calcolare CF:
  {errore}"

Come splittare nome/cognome da guestName:
const parts = guestName.trim().split(' ')
const cognome = parts[0] ?? ''
const nome = parts.slice(1).join(' ')
|| parts[0] ?? ''

PULSANTE "Salva":
- chiama updateBookingGuest()
- successo → toast "Dati ospite aggiornati"
    + chiama onSaved(result)
- errore → messaggio inline

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. FRONTEND — BookingDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica la card "Ospite" in
BookingDetail.tsx:

- Aggiungi pulsante "Modifica" (icona Edit)
  nell'header della card ospite
- onClick → apre GuestEditDialog
- Mostra i dati anagrafici già presenti:
    * guestTaxCode (se presente)
    * guestBirthDate (se presente)
    * guestBirthPlace (se presente)
    * guestDocType + guestDocNumber
      (se presenti)
- Il badge "Dati fatturazione: Incompleti"
  diventa "Dati completi" (verde) quando
  guestTaxCode è valorizzato

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

# Aggiorna dati ospite booking 132
curl -s -X PATCH \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"guestName": "Laura Scenna",
"guestBirthDate": "1985-03-15",
"guestSesso": "F",
"guestBirthPlace": "Roma",
"guestBirthBelfiore": "H501",
"guestDocType": "CARTA_IDENTITA",
"guestDocNumber": "AB123456",
"guestCountry": "Italia"
}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings/132/guest" \
| python3 -m json.tool \
| grep -E '"guestTaxCode"|"guestBirth\
|"guestDoc|"statoPrenotazione"'

Verifica che:
- guestBirthPlace = "Roma"
- guestBirthBelfiore = "H501"
- guestTaxCode calcolato automaticamente
- statoPrenotazione aggiornato
  (enriched o ready)

Riporta output migration, build e curl.