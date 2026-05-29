Leggi il file CLAUDE.md, docs/db/schema-target.sql e
docs/analisi-frontend.md prima di procedere.

Crea DTO + Service + Controller per la gestione FiscalDocument.
Segui lo stesso pattern già esistente.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/document/DocumentListDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Campi:
    * Integer id
    * String documentNumber
    * String documentType      ← codice da tipo_documento
    * LocalDate issueDate
    * String recipientName
    * String recipientTaxCode
    * BigDecimal totalAmount
    * BigDecimal vatAmount
    * String statoDocumento    ← codice da stato_documento
    * String sdiIdentifier     ← nullable
    * String sdiEsito          ← codice da sdi_esito nullable
    * String propertyName      ← denormalizzato da booking→property
    * String channelName       ← denormalizzato da booking→canale_ota
    * Integer fkBookingId      ← nullable
    * LocalDateTime createdAt

Crea dto/document/DocumentDetailDTO.java:
- Stessi campi di DocumentListDTO più:
    * Integer fkTenantId
    * Integer fkTipoDocumentoId
    * Integer fkStatoDocumentoId
    * Boolean richiedeIva      ← da tipo_documento.richiede_iva
    * LocalDateTime updatedAt
    * List<DocumentRowDTO> righe ← righe dettaglio fattura

Crea dto/document/DocumentRowDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Campi:
    * String descrizione
    * BigDecimal importoNetto
    * BigDecimal aliquotaIva   ← 0.22 per fattura, 0 per ricevuta
    * BigDecimal importoIva
    * BigDecimal importoLordo

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/FiscalDocumentService.java:
- @Service @Log4j2
- Costruttore con FiscalDocumentDAO, BookingDAO,
  PropertyDAO, CanaleOtaDAO, TipoDocumentoDAO,
  StatoDocumentoDAO, SdiEsitoDAO

- Metodi privati helper:
  buildLookupMaps() → record con:
    * Map<Integer, TipoDocumento> tipiById
    * Map<Integer, StatoDocumento> statiById
    * Map<Integer, SdiEsito> sdiEsitiById
      Carica tutto una volta sola

  resolvePropertyName(Integer bookingId,
  Map<Integer,Booking> bookingsById,
  Map<Integer,Property> propertiesById) → String

  List<DocumentRowDTO> buildRighe(FiscalDocument doc,
  Booking booking, TipoDocumento tipo)
    - se tipo.richiede_iva = true (fattura):
        * Riga 1: "Riaddebito commissione OTA"
          netto=ota_commission_amount, iva=22%, lordo=netto*1.22
        * Riga 2: "Riaddebito pulizie"
          netto=cleaning_amount, iva=22%, lordo=netto*1.22
        * Riga 3: "Provvigione PM"
          netto=pm_fee_amount, iva=22%, lordo=netto*1.22
    - se tipo.richiede_iva = false (ricevuta):
        * Riga 1: "Compenso lordo ospite"
          netto=gross_amount, iva=0, lordo=gross_amount

- Metodi pubblici:

  List<DocumentListDTO> findByTenantId(Integer tenantId,
  String statoFilter, String q, Integer page, Integer size)
    - carica LookupMaps
    - carica tutti i documenti del tenant
    - carica tutti i booking del tenant (per denormalizzare)
    - carica tutte le property del tenant (per propertyName)
    - carica tutti i canali OTA (per channelName)
    - applica filtri IN MEMORIA:
        * se statoFilter != null: filtra per codice stato
        * se q != null: filtra su documentNumber CONTAINS q
          OR recipientName CONTAINS q (case-insensitive)
    - applica paginazione
    - mappa su DocumentListDTO
    - Log INFO con count

  Optional<DocumentDetailDTO> findById(Integer tenantId,
  Integer documentId)
    - verifica appartenenza al tenant
    - carica LookupMaps
    - recupera booking associato (se presente)
    - calcola righe dettaglio con buildRighe()
    - mappa su DocumentDetailDTO
    - Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea controller/DocumentController.java:
- @RestController @Log4j2
- @RequestMapping("/api/documents")
- Costruttore con FiscalDocumentService
- tenantId hardcoded a 1 // TODO: SecurityContext
- Endpoints:

  GET /api/documents
    - query params: stato, q, page, size
    - ResponseEntity<List<DocumentListDTO>>

  GET /api/documents/{id}
    - 200 o 404
    - ResponseEntity<DocumentDetailDTO>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Dopo aver creato i file lancia:
mvn -Plocal clean package

Poi riavvia Tomcat e testa:
curl -u admin:admin \
"http://localhost:8081/sostitutoincloud/api/documents"
curl -u admin:admin \
"http://localhost:8081/sostitutoincloud/api/documents/1"
curl -u admin:admin \
"http://localhost:8081/sostitutoincloud/api/documents/99"

Riporta l'output del build e dei curl.