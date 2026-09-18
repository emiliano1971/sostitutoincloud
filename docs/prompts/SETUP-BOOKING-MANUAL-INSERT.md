Leggi il file CLAUDE.md prima di procedere.
Leggi i file esistenti:
- service/BookingService.java
- service/BookingImportService.java
- service/ContrattoCalcolatoreService.java
- dao/BookingDAO.java
- frontend/src/pages/tenant/BookingsList.tsx
- frontend/src/components/booking/GuestEditDialog.tsx
  prima di procedere.

Implementa l'inserimento manuale di una
prenotazione tramite pagina dedicata.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/booking/BookingCreateDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor

Dati prenotazione:
Integer fkPropertyId      ← obbligatorio
Integer fkCanaleOtaId     ← opzionale (null = generico)
String  externalBookingId ← opzionale
(se null → genera "MAN-{timestamp}")
LocalDate checkinDate     ← obbligatorio
LocalDate checkoutDate    ← obbligatorio
Integer guests            ← obbligatorio, min 1
BigDecimal grossAmount    ← obbligatorio, > 0

Dati ospite:
String guestName          ← obbligatorio
String guestTaxCode       ← opzionale
(se null e dati anagrafici presenti → calcola)
LocalDate guestBirthDate  ← opzionale
String guestSesso         ← opzionale (M/F)
String guestBirthPlace    ← opzionale
String guestDocType       ← opzionale
String guestDocNumber     ← opzionale
String guestCountry       ← opzionale
String guestAddress       ← opzionale
String guestPhone         ← opzionale

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — BookingService.createManuale()
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a service/BookingService.java:

BookingDetailDTO createManuale(
Integer tenantId,
BookingCreateDTO dto)

Logica:

1. Validazioni:
    - checkinDate < checkoutDate
      altrimenti IllegalArgumentException
      "Check-out deve essere dopo check-in"
    - grossAmount > 0
    - guests >= 1
    - Verifica che property appartenga
      al tenant:
      propertyDAO.findById(dto.getFkPropertyId())
      .filter(p -> tenantId.equals(
      p.getFkTenantId()))
      .orElseThrow(() ->
      new IllegalArgumentException(
      "Immobile non trovato"))

2. Calcola notti:
   int nights = (int) ChronoUnit.DAYS
   .between(dto.getCheckinDate(),
   dto.getCheckoutDate())

3. Genera externalBookingId se assente:
   String extId = dto.getExternalBookingId()
   != null && !dto.getExternalBookingId()
   .isBlank()
   ? dto.getExternalBookingId()
   : "MAN-" + System.currentTimeMillis()

4. Calcola CF ospite se assente:
   String cf = dto.getGuestTaxCode()
   if (cf == null || cf.isBlank()) {
   if (dto.getGuestBirthDate() != null
   && dto.getGuestSesso() != null
   && dto.getGuestBirthPlace() != null) {
   cf = codiceFiscaleService.calcolaSafe(
   cognome, nome,
   dto.getGuestBirthDate(),
   dto.getGuestSesso(),
   dto.getGuestBirthPlace())
   .orElse(null)
   }
   }

   Estrai nome e cognome da guestName:
   String[] parts = dto.getGuestName()
   .trim().split("\\s+", 2)
   String nome = parts.length > 1
   ? parts[0] : dto.getGuestName()
   String cognome = parts.length > 1
   ? parts[1] : ""

5. Calcola split economico:
   Property property = propertyDAO
   .findById(dto.getFkPropertyId())
   TenantSettingsDTO settings =
   tenantSettingsService
   .getSettings(tenantId)

   SplitEconomicoDTO split =
   contrattoCalcolatoreService.calcola(
   tenantId,
   dto.getFkPropertyId(),
   dto.getFkCanaleOtaId(),
   dto.getGrossAmount(),
   nights,
   dto.getGuests(),
   null)  ← otaCommissionOverride null

6. Determina stato:
   String stato = (cf != null
   && !cf.isBlank())
   ? "enriched" : "imported"

7. Calcola tassa soggiorno:
   BigDecimal touristTax =
   touristTaxService.calcolaPerBooking(
   tenantId,
   property.getCity(),
   dto.getCheckinDate(),
   nights,
   dto.getGuests(),
   false)  ← non inclusa nel lordo

8. Costruisci e inserisci Booking:
   Booking booking = Booking.builder()
   .fkTenantId(tenantId)
   .fkPropertyId(dto.getFkPropertyId())
   .fkCanaleOtaId(dto.getFkCanaleOtaId())
   .fkOwnerId(property.getFkOwnerId())
   .externalBookingId(extId)
   .checkinDate(dto.getCheckinDate())
   .checkoutDate(dto.getCheckoutDate())
   .nights(nights)
   .guests(dto.getGuests())
   .grossAmount(dto.getGrossAmount())
   .otaCommissionAmount(
   split.getOtaCommissionAmount())
   .cleaningAmount(
   split.getCleaningAmount())
   .pmFeeAmount(split.getPmFeeAmount())
   .ownerNetAmount(
   split.getOwnerNetAmount())
   .withholdingAmount(
   split.getWithholdingAmount())
   .aliquotaRitenuta(
   property.isPrimoImmobile()
   ? settings.getWithholdingRatePrimary()
   : settings.getWithholdingRateSecondary())
   .touristTaxAmount(touristTax)
   .guestName(dto.getGuestName())
   .guestTaxCode(cf)
   .guestBirthDate(dto.getGuestBirthDate())
   .guestSesso(dto.getGuestSesso())
   .guestBirthPlace(dto.getGuestBirthPlace())
   .guestDocType(dto.getGuestDocType())
   .guestDocNumber(dto.getGuestDocNumber())
   .guestCountry(dto.getGuestCountry())
   .guestAddress(dto.getGuestAddress())
   .guestPhone(dto.getGuestPhone())
   .build()

   Booking saved = bookingDAO.insert(booking)

9. Aggiorna stato:
   bookingDAO.updateStato(saved.getId(),
   stato, tenantId)

10. Audit:
    auditService.log("booking.create.manual",
    "Booking", saved.getId(),
    "Inserimento manuale: " + extId)

11. Log INFO "BookingService
    .createManuale() - id={} stato={}"

12. Return toDetailDTO(saved, tenantId)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — Controller
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/BookingController.java:

POST /api/bookings
- Solo TENANT_ADMIN e PM_USER
- @RequestBody BookingCreateDTO
- tenantId da SecurityUtils
- chiama bookingService.createManuale()
- ResponseEntity.status(201).body(result)
- catch IllegalArgumentException → 400
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND — pagina BookingNew.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/pages/tenant/BookingNew.tsx

Header:
← (back a /bookings)
"Nuova Prenotazione"

Layout due colonne (lg:grid-cols-2):

COLONNA SINISTRA — "Dati Prenotazione"
Immobile *
→ Select con le property del tenant
→ carica da GET /api/properties
→ onChange → resetta canale OTA
e ricalcola (se implementato)

Canale OTA
→ Select con i canali OTA
→ carica da GET /api/canali-ota
→ opzione "Nessun canale" (null)

ID Prenotazione
→ text input opzionale
→ placeholder "Lascia vuoto per
generazione automatica (MAN-...)"

Check-in *
→ date input (type="date")
→ min = oggi

Check-out *
→ date input (type="date")
→ min = checkin + 1

N. Ospiti *
→ number input, min=1, default=1

Lordo Ospite (€) *
→ number input con decimali
→ min=0.01

COLONNA DESTRA — "Dati Ospite"
Nome e Cognome *
→ text input
→ placeholder "Nome Cognome"

Codice Fiscale
→ text input opzionale
→ se vuoto viene calcolato
automaticamente dal backend
→ hint: "Se vuoto viene calcolato
automaticamente se presenti
i dati anagrafici"

Data di nascita
→ date input opzionale

Sesso
→ Select: Uomo (M) / Donna (F)
→ opzionale

Comune di nascita
→ ComuneAutocomplete opzionale
→ requireValidComune={false}
(ospite straniero possibile)

Tipo documento
→ Select opzionale:
"Carta di identità"
"Passaporto"
"Patente"

N. Documento
→ text input opzionale

Paese
→ text input opzionale
→ default "Italia"

Indirizzo
→ text input opzionale

Telefono
→ text input opzionale

Footer (full width):
[Annulla → /bookings]
[Salva prenotazione]
→ spinner durante salvataggio
→ successo → navigate(
`/bookings/${result.id}`)
→ errore → toast con messaggio

Validazioni frontend:
- Immobile obbligatorio
- Check-in obbligatorio
- Check-out > Check-in
- N. ospiti >= 1
- Lordo > 0
- Nome ospite obbligatorio

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. FRONTEND — aggiungi funzioni API
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/bookingApi.ts:

export interface BookingCreateRequest {
fkPropertyId: number
fkCanaleOtaId?: number
externalBookingId?: string
checkinDate: string
checkoutDate: string
guests: number
grossAmount: number
guestName: string
guestTaxCode?: string
guestBirthDate?: string
guestSesso?: string
guestBirthPlace?: string
guestDocType?: string
guestDocNumber?: string
guestCountry?: string
guestAddress?: string
guestPhone?: string
}

export async function createBooking(
data: BookingCreateRequest
): Promise<BookingDetail>
// POST /api/bookings

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. FRONTEND — routing e menu
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi in App.tsx:
/bookings/new → BookingNew

Aggiungi pulsante "+ Nuova prenotazione"
in BookingsList.tsx accanto a "+ Import":
onClick → navigate('/bookings/new')

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package
cd frontend && npm run build &&
npx tsc --noEmit

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena2026"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Test inserimento manuale completo
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"fkPropertyId": 6,
"fkCanaleOtaId": 2,
"checkinDate": "2026-11-01",
"checkoutDate": "2026-11-05",
"guests": 2,
"grossAmount": 400.00,
"guestName": "Mario Rossi",
"guestBirthDate": "1980-01-15",
"guestSesso": "M",
"guestBirthPlace": "Roma"
}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings" \
| python3 -m json.tool \
| grep -E '"id"|"stato"|"guestTaxCode"|\
"externalBookingId"|"nights"'

# Test senza CF e senza anagrafica
# → stato imported
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"fkPropertyId": 6,
"checkinDate": "2026-11-10",
"checkoutDate": "2026-11-12",
"guests": 1,
"grossAmount": 200.00,
"guestName": "John Smith"
}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings" \
| python3 -m json.tool \
| grep -E '"stato"|"guestTaxCode"|\
"externalBookingId"'

# Cleanup
psql -U sostitutoincloud \
-d sostitutoincloud -h localhost -c "
DELETE FROM booking
WHERE external_booking_id
LIKE 'MAN-%'
AND fk_tenant_id = 1;"

Verifica che:
- Booking con anagrafica → stato enriched
  con CF calcolato automaticamente
- Booking senza anagrafica → stato imported
- externalBookingId generato come MAN-{ts}
- notti calcolate correttamente
- split economico calcolato

Riporta output build e curl.