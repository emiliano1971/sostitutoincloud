Leggi il file CLAUDE.md, docs/db/schema-target.sql e
docs/import/tracciato-csv-booking.md prima di procedere.

Implementa l'import prenotazioni da CSV.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DIPENDENZA MAVEN
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi in pom.xml:
```xml
<!-- CSV parsing -->
<dependency>
    <groupId>com.opencsv</groupId>
    <artifactId>opencsv</artifactId>
    <version>5.9</version>
</dependency>
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/import/BookingImportRowDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Rappresenta una riga del CSV parsata:
    * String externalBookingId
    * String channelCode
    * String propertyCode
    * String guestName
    * String guestEmail
    * String guestPhone
    * String guestTaxCode
    * LocalDate checkinDate
    * LocalDate checkoutDate
    * Integer nights
    * Integer guests
    * String status
    * BigDecimal grossAmount
    * BigDecimal otaCommissionAmount
    * BigDecimal cleaningAmount
    * BigDecimal touristTaxAmount
    * Boolean touristTaxIncluded
    * String currency

Crea dto/import/BookingImportPreviewRowDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Rappresenta una riga nella preview con esito validazione:
    * Integer rowNumber
    * String externalBookingId
    * String guestName
    * String propertyCode
    * String propertyName  ← null se property non trovata
    * String channelCode
    * String channelName   ← null se canale non trovato
    * LocalDate checkinDate
    * LocalDate checkoutDate
    * BigDecimal grossAmount
    * String status        ← "nuova"|"duplicata"|"errore"
    * String errorMessage  ← null se ok
    * BookingImportRowDTO rawData ← dati originali per confirm

Crea dto/import/BookingImportPreviewDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- String fileName
- Integer totalRows
- Integer newCount      ← righe con status "nuova"
- Integer dupeCount     ← righe con status "duplicata"
- Integer errorCount    ← righe con status "errore"
- List<BookingImportPreviewRowDTO> rows
- String importSessionId ← UUID generato per la sessione
  (serve per il confirm — evita di riprocessare il file)

Crea dto/import/BookingImportConfirmDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- String importSessionId
- List<String> selectedExternalIds
  ← solo gli id da importare (esclude duplicati/errori)

Crea dto/import/BookingImportResultDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Integer imported    ← righe salvate con successo
- Integer skipped     ← righe saltate (duplicati)
- Integer errors      ← righe con errore al salvataggio
- List<String> errorMessages

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. IMPORT SESSION CACHE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea config/ImportSessionCache.java:
- @Component
- Usa una ConcurrentHashMap<String, List<BookingImportRowDTO>>
  per tenere in memoria le righe parsate tra upload e confirm
- Metodi:
    * void store(String sessionId,
      List<BookingImportRowDTO> rows)
    * List<BookingImportRowDTO> get(String sessionId)
    * void remove(String sessionId)
- Le sessioni scadono dopo 30 minuti
  (usa un ScheduledExecutorService o
  semplicemente ignora la scadenza per ora
  — nota nel codice: "TODO: aggiungere TTL")

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. AGGIUNGI METODI WRITE A BookingDAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/BookingDAO.java:

Booking insert(Booking booking)
- INSERT INTO booking con tutti i campi
  (esclusi id, created_at, updated_at)
- Usa KeyHolder per id generato
- Per le colonne enum (payment_status,
  settlement_status) usa Types.OTHER
- Dopo insert: rileggi con findById()
- Log INFO: "BookingDAO.insert() -
  externalId={} tenantId={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. IMPORT SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/BookingImportService.java:
- @Service @Log4j2
- Costruttore con BookingDAO, PropertyDAO,
  CanaleOtaDAO, StatoPrenotazioneDAO,
  ImportSessionCache

- BookingImportPreviewDTO preview(
  Integer tenantId,
  MultipartFile file)

    1. Parsa il CSV con OpenCSV:
        - Prima riga = header
        - Colonne: vedi tracciato in
          docs/import/tracciato-csv-booking.md
        - Encoding UTF-8
        - Separatore virgola

    2. Per ogni riga:
        - Valida campi obbligatori
          (externalBookingId, channelCode, propertyCode,
          guestName, checkinDate, checkoutDate,
          nights, guests, status, grossAmount)
        - Cerca property per propertyCode e tenantId
        - Cerca canale OTA per channelCode
        - Verifica duplicato:
          bookingDAO.findByExternalBookingId(
          externalId) non vuoto
          → status = "duplicata"
        - Se errore validazione → status = "errore"
          con errorMessage
        - Altrimenti → status = "nuova"

    3. Genera UUID come importSessionId
    4. Salva le righe "nuova" nella cache
       con importSessionId
    5. Restituisce BookingImportPreviewDTO

  Log INFO: "BookingImportService.preview() -
  tenantId={} file={} rows={} new={} dupe={} err={}"

- BookingImportResultDTO confirm(
  Integer tenantId,
  BookingImportConfirmDTO dto)

    1. Recupera righe dalla cache con importSessionId
    2. Per ogni riga in selectedExternalIds:
        - Costruisce oggetto Booking:
            * fkTenantId = tenantId
            * fkPropertyId = property trovata per propertyCode
            * fkCanaleOtaId = canale trovato per channelCode
            * fkStatoPrenotazioneId = id di "imported"
              (carica da StatoPrenotazioneDAO
              .findByCodice("imported"))
            * paymentStatus = "pending"
            * settlementStatus = "pending"
            * externalBookingId, guestName, guestEmail,
              guestPhone, guestTaxCode
            * checkinDate, checkoutDate, nights, guests
            * grossAmount, otaCommissionAmount,
              cleaningAmount, touristTaxAmount,
              touristTaxIncluded
            * touristTaxCollection = canale
              .getTouristTaxCollection()
        - Chiama bookingDAO.insert(booking)
        - Conta successi/errori
    3. Rimuove sessione dalla cache
    4. Restituisce BookingImportResultDTO

  Log INFO: "BookingImportService.confirm() -
  tenantId={} imported={} errors={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/BookingController.java:

POST /api/bookings/import
- @RequestParam MultipartFile file
- tenantId = SecurityUtils.getCurrentTenantId()
- chiama bookingImportService.preview(tenantId, file)
- ResponseEntity<BookingImportPreviewDTO>
- gestisce Exception → 400 con messaggio

POST /api/bookings/import/confirm
- @RequestBody BookingImportConfirmDTO
- tenantId = SecurityUtils.getCurrentTenantId()
- chiama bookingImportService.confirm(tenantId, dto)
- ResponseEntity<BookingImportResultDTO>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. CREA FILE CSV DI TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea docs/import/test-import.csv con 5 righe
di test basate sui dati del seed:
- 3 prenotazioni nuove (property ROM-001/ROM-002,
  channel airbnb/booking)
- 1 duplicata (usa external_booking_id già presente
  nel seed: AIRBNB-20260523-0001)
- 1 con errore (property_code inesistente)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. FRONTEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/api/importApi.ts:

```ts
export interface ImportPreviewRow {
  rowNumber: number;
  externalBookingId: string;
  guestName: string;
  propertyCode: string;
  propertyName?: string;
  channelCode: string;
  channelName?: string;
  checkinDate: string;
  checkoutDate: string;
  grossAmount: number;
  status: 'nuova' | 'duplicata' | 'errore';
  errorMessage?: string;
}

export interface ImportPreview {
  fileName: string;
  totalRows: number;
  newCount: number;
  dupeCount: number;
  errorCount: number;
  rows: ImportPreviewRow[];
  importSessionId: string;
}

export interface ImportResult {
  imported: number;
  skipped: number;
  errors: number;
  errorMessages: string[];
}

export async function uploadImportFile(
  file: File
): Promise<ImportPreview>
// POST /api/bookings/import
// multipart/form-data con campo "file"
// NON usare apiClient.post() — usa fetch diretto
// con FormData per multipart

export async function confirmImport(
  importSessionId: string,
  selectedExternalIds: string[]
): Promise<ImportResult>
// POST /api/bookings/import/confirm
```

Modifica frontend/src/pages/tenant/ImportBookings.tsx:
- Mantieni TUTTO il layout e UI esistente
- Rimuovi sourceConfig mock
- Mantieni le 4 tab (airbnb, booking, alloggiati,
  channelmanager) ma implementa solo airbnb e booking
  per ora — le altre mostrano "Coming soon"
- Step 0 (Upload):
    * Input file reale con accept=".csv"
    * Drag & drop funzionante
    * Al file selezionato: chiama uploadImportFile(file)
    * Mostra spinner durante upload
    * Se successo → vai a step 1 con i dati preview
    * Se errore → mostra messaggio
- Step 1 (Anteprima):
    * Mostra dati reali da ImportPreview
    * Tabella con righe colorate per status:
      nuova=verde, duplicata=arancio, errore=rosso
    * Mostra solo le righe "nuova" e "duplicata"
      (errori in sezione separata)
- Step 2 (Conferma):
    * Mostra riepilogo: N nuove da importare,
      M duplicate da saltare, K errori
    * Al click "Procedi": chiama confirmImport()
      con importSessionId e tutti gli externalIds
      delle righe "nuova"
- Step 3 (Risultato):
    * Mostra risultato reale da ImportResult
    * imported, skipped, errors

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
9. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package

Riavvia Tomcat e testa con il file di test:
TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena"}' \
| python3 -c \
"import sys,json; \
print(json.load(sys.stdin)['token'])")

curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-F "file=@docs/import/test-import.csv" \
http://localhost:8081/sostitutoincloud/api/bookings/import \
| python3 -m json.tool

cd frontend && npm run build

Riporta output del build e del curl.