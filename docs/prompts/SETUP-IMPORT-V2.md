Leggi il file CLAUDE.md e questi file:
- frontend/src/pages/tenant/ImportBookings.tsx
- frontend/src/api/importApi.ts
- src/main/java/it/gavia/sostitutoincloud/
  service/BookingImportService.java
- src/main/java/it/gavia/sostitutoincloud/
  controller/BookingController.java

Implementa il nuovo wizard di import V2
con doppio file (prenotazioni + ospiti),
mapping colonne manuale e calcolo split
da regole contratto immobile.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
LOGICA DI BUSINESS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Il nuovo import accetta 2 file Excel/CSV:
1. File prenotazioni (obbligatorio)
2. File ospiti (opzionale ma consigliato)

I due file vengono mergati per BOOKING ID.

Match immobile:
canale_ota.nome = valore colonna ORIGINE
property_ota_code.external_id =
valore colonna STRUTTURA
→ ricava fk_property_id

Calcolo split: usa ContrattoCalcolatoreService
con override commissione OTA dal CSV.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CAMPI DI MAPPING (13 campi obbligatori
+ campi ospite dal secondo file)
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

File prenotazioni — campi mappabili:
BOOKING_ID → external_booking_id
ORIGINE → canale_ota match
STRUTTURA → property_ota_code match
CHECKIN → checkin_date
CHECKOUT → checkout_date
IMPORTO_TOTALE → gross_amount
ADULTI → guests (adulti)
BAMBINI → bambini
NEONATI → neonati
COMMISSIONE → ota_commission_amount
STATO → stato_prenotazione
CLIENTE_NOME → guest_name (se no file ospiti)
CLIENTE_COGNOME → guest_last_name

File ospiti — campi mappabili:
BOOKING_ID → chiave merge
NOME → guest_first_name
COGNOME → guest_last_name
DATA_NASCITA → guest_birth_date
SESSO → guest_gender
COMUNE_NASCITA → guest_birth_place
DOCUMENTO → guest_doc_type
NUM_DOCUMENTO → guest_doc_number
NAZIONE → guest_country

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP DEL WIZARD (5 step)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Step 0 — Upload Files
- Drop zone per file prenotazioni
  (xlsx, csv, max 10MB) OBBLIGATORIO
- Drop zone per file ospiti
  (xlsx, csv, max 10MB) OPZIONALE
- Bottone "Avanti" abilitato solo se
  file prenotazioni caricato

Step 1 — Mapping Colonne
Due sezioni: "Prenotazioni" e "Ospiti"
Per ogni campo di sistema mostra
un Select con le colonne del file.
Valori default suggeriti automaticamente
per matching esatto o parziale del nome.

Campi obbligatori marcati con *:
BOOKING_ID, ORIGINE, STRUTTURA,
CHECKIN, CHECKOUT, IMPORTO_TOTALE

Bottone "Avanti" abilitato solo se
tutti i campi obbligatori sono mappati.

Step 2 — Anteprima
Come ora: tabella con status per riga
(nuova/duplicata/errore)
Aggiunta colonna "Ospite" con nome
proveniente dal merge con file ospiti.
Aggiunta colonna "Warnings" se
split economico ha avvisi.

Step 3 — Conferma
Come ora.

Step 4 — Risultato
Come ora.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/importing/ImportColumnMappingDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- Map<String, String> bookingMapping
  ← chiave=campo sistema, valore=colonna file
  es. {"BOOKING_ID": "Id",
  "ORIGINE": "Origine",
  "STRUTTURA": "Struttura", ...}
- Map<String, String> guestMapping
  ← stessa struttura per file ospiti

Crea dto/importing/ImportUploadResponseDTO.java:
- @Data @Builder
- String bookingSessionId
  ← id sessione file prenotazioni
- String guestSessionId (nullable)
  ← id sessione file ospiti
- List<String> bookingColumns
  ← colonne rilevate nel file prenotazioni
- List<String> guestColumns (nullable)
  ← colonne rilevate nel file ospiti
- Map<String, String> suggestedBookingMapping
  ← mapping suggerito automaticamente
- Map<String, String> suggestedGuestMapping

Aggiorna dto/importing/BookingImportRowDTO.java:
- Aggiungi campi ospite:
  String guestFirstName
  String guestLastName
  String guestBirthDate
  String guestGender
  String guestBirthPlace
  String guestDocType
  String guestDocNumber
  String guestCountry
- Aggiungi List<String> splitWarnings

Aggiorna dto/importing/BookingImportPreviewDTO.java:
- Aggiungi Integer warningCount

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — ImportSessionCache
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Leggi service/ImportSessionCache.java
e aggiungi supporto per sessioni file raw:

void storeRawFile(String sessionId,
byte[] content, String fileName)
byte[] getRawFile(String sessionId)
← null se non trovato/scaduto
String getFileName(String sessionId)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — BookingImportService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi dipendenza Apache POI per
lettura Excel in pom.xml:
<dependency>
<groupId>org.apache.poi</groupId>
<artifactId>poi-ooxml</artifactId>
<version>5.2.5</version>
</dependency>

Aggiungi metodi a BookingImportService:

ImportUploadResponseDTO uploadFiles(
Integer tenantId,
MultipartFile bookingFile,
MultipartFile guestFile)
- Legge colonne (header riga 0) da
  entrambi i file
- Supporta CSV e XLSX:
    * CSV: usa OpenCSV
    * XLSX: usa Apache POI
- Salva file raw in ImportSessionCache
  con UUID separati
- Genera suggestedMapping:
  matching case-insensitive tra
  nome colonna file e nomi attesi:
  "Id" → BOOKING_ID
  "Origine" → ORIGINE
  "Struttura" → STRUTTURA
  "Arrivo" → CHECKIN
  "Partenza" → CHECKOUT
  "Importo totale" → IMPORTO_TOTALE
  "Adulti" → ADULTI
  "Bambini" → BAMBINI
  "Neonati" → NEONATI
  "Commissione del canale" → COMMISSIONE
  "Stato" → STATO
  "Cliente" → CLIENTE_NOME
  (file ospiti)
  "Nome" → NOME
  "Cognome" → COGNOME
  "Data di nascita" → DATA_NASCITA
  "Sesso" → SESSO
  "Comune emittente" → COMUNE_NASCITA
  "Documento" → DOCUMENTO
  "Nº Documento" → NUM_DOCUMENTO
  "Nazione" → NAZIONE
- Ritorna ImportUploadResponseDTO

BookingImportPreviewDTO previewWithMapping(
Integer tenantId,
String bookingSessionId,
String guestSessionId,
ImportColumnMappingDTO mapping)
- Carica file raw dalla cache
- Legge file prenotazioni applicando
  il mapping delle colonne
- Legge file ospiti (se presente)
  e costruisce mappa:
  Map<String, GuestData> guestByBookingId
  dove GuestData ha i campi ospite
- Per ogni riga prenotazione:
    * estrae campi usando mapping
    * ricerca immobile:
      SELECT p.id FROM property p
      JOIN property_ota_code poc
      ON poc.fk_property_id = p.id
      JOIN canale_ota c
      ON c.id = poc.fk_canale_ota_id
      WHERE c.nome ILIKE ?  ← ORIGINE
      AND poc.external_id ILIKE ?  ← STRUTTURA
      AND p.fk_tenant_id = ?
    * controlla duplicato su
      external_booking_id
    * merge con dati ospite se disponibili
    * calcola split con
      ContrattoCalcolatoreService
    * classifica nuova/duplicata/errore
- Salva righe in ImportSessionCache
- Ritorna BookingImportPreviewDTO

Mantieni il metodo confirm() esistente
aggiornandolo per usare anche i dati
ospite quando disponibili:
- guestName = firstName + " " + lastName
  se disponibili dal file ospiti,
  altrimenti usa CLIENTE_NOME dal mapping

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BACKEND — BookingController
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi endpoint a BookingController:

POST /api/bookings/import/upload
- @RequestParam bookingFile (MultipartFile)
- @RequestParam(required=false) guestFile
- Chiama importService.uploadFiles()
- ResponseEntity.ok(result)
- In caso di eccezione → 400

POST /api/bookings/import/preview-v2
- @RequestBody: {
  bookingSessionId,
  guestSessionId,
  mapping: ImportColumnMappingDTO
  }
- Chiama importService.previewWithMapping()
- ResponseEntity.ok(result)

Mantieni i vecchi endpoint
/import e /import/confirm invariati
per compatibilità con il wizard vecchio.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. FRONTEND — importApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/importApi.ts:

```ts
export interface ImportUploadResponse {
  bookingSessionId: string;
  guestSessionId?: string;
  bookingColumns: string[];
  guestColumns?: string[];
  suggestedBookingMapping: 
    Record<string, string>;
  suggestedGuestMapping?: 
    Record<string, string>;
}

export interface ImportColumnMapping {
  bookingMapping: Record<string, string>;
  guestMapping?: Record<string, string>;
}

export interface ImportPreviewV2Request {
  bookingSessionId: string;
  guestSessionId?: string;
  mapping: ImportColumnMapping;
}

export async function uploadImportFiles(
  bookingFile: File,
  guestFile?: File
): Promise<ImportUploadResponse>
// POST /api/bookings/import/upload
// multipart: bookingFile, guestFile?

export async function previewImportV2(
  data: ImportPreviewV2Request
): Promise<ImportPreview>
// POST /api/bookings/import/preview-v2
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. FRONTEND — ImportBookings.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Riscrivi ImportBookings.tsx mantenendo
lo stesso stile UI ma con 5 step:

Step 0 — Upload Files:
- Rimuovi le Tabs (airbnb/booking/ecc.)
- Due drop zone affiancate:
    * Sinistra: "File Prenotazioni *"
      (xlsx, csv, obbligatorio)
    * Destra: "File Ospiti"
      (xlsx, csv, opzionale)
- Bottone "Avanti" → chiama
  uploadImportFiles() → salva response
  in stato uploadResponse

Step 1 — Mapping Colonne:
- Due Card affiancate:
  "Mapping Prenotazioni" e "Mapping Ospiti"
  (la seconda visibile solo se guestFile
  caricato)
- Per ogni campo di sistema una riga con:
    * Label campo (con * se obbligatorio)
    * Select con opzioni:
        - "-- Non mappare --" (valore vuoto)
        - tutte le colonne del file
    * Valore default = suggestedMapping
- Campi obbligatori:
  BOOKING_ID, ORIGINE, STRUTTURA,
  CHECKIN, CHECKOUT, IMPORTO_TOTALE
- Bottone "Genera Anteprima" →
  chiama previewImportV2() con mapping
  corrente → salva preview → step 2

Step 2 — Anteprima (come ora ma con
colonne aggiuntive Ospite e Warnings)

Step 3 — Conferma (invariato)

Step 4 — Risultato (invariato)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

NOTA: backend Tomcat e frontend Vite
sono già in esecuzione — non fermarli.
Dopo il build Maven le nuove classi
sono già in WEB-INF/classes/.
Serve riavvio Tomcat prima del test.

Riporta output di entrambi.