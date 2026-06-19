Leggi il file CLAUDE.md e docs/db/schema-target.sql
prima di procedere.

Implementa la generazione documenti fiscali
(ricevuta proprietario e fattura PM).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. AGGIORNA BookingDetailDTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dto/booking/BookingDetailDTO.java
i campi mancanti necessari per i dialog:

// Dati immobile (per dialog)
String propertyAddress
String propertyCity
String propertyInternalCode

// Dati proprietario (per dialog)
String ownerTaxCode
String ownerIban
String ownerEmail

// Dati tenant (per dialog fattura PM)
String tenantLegalName
String tenantVatNumber
String tenantTaxCode
String tenantLegalAddress
String tenantPec

Aggiorna BookingService.java per popolare
questi campi aggiuntivi:
- propertyAddress/City/InternalCode:
  carica da PropertyDAO.findById(fkPropertyId)
- ownerTaxCode/Iban/Email:
  carica da OwnerProfileDAO.findById(fkOwnerId)
- tenantLegalName/VatNumber/TaxCode/
  LegalAddress/Pec:
  carica da TenantDAO.findById(fkTenantId)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. DTO GENERAZIONE DOCUMENTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/document/DocumentGenerateRequestDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- Integer bookingId (obbligatorio)
- String tipoDocumento ← "ricevuta_owner" | "fattura_pm"
- LocalDate dataEmissione (se null usa today)

Crea dto/document/DocumentGenerateResponseDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Integer documentId
- String documentNumber  ← es. "RIC-2026-0003"
- String tipoDocumento
- LocalDate dataEmissione
- BigDecimal importoTotale
- BigDecimal importoBollo   ← 2.00 se > 77.47, else 0
- BigDecimal imponibile
- BigDecimal iva            ← solo per fattura_pm
- BigDecimal ritenuta
- String statoDocumento     ← "draft"
- String bookingExternalId
- String guestName
- String ownerName
- String propertyName

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. NUMERAZIONE PROGRESSIVA
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/FiscalDocumentDAO.java:

Integer getNextProgressiveNumber(
Integer tenantId, String tipoDocumento,
Integer anno)
- SELECT COUNT(*) + 1 FROM fiscal_document
  WHERE fk_tenant_id = ?
  AND tipo_documento = ?
  AND EXTRACT(YEAR FROM data_emissione) = ?
- Restituisce il prossimo numero progressivo

String generateDocumentNumber(
Integer tenantId, String tipoDocumento,
Integer anno)
- Usa getNextProgressiveNumber()
- Formato:
    * ricevuta_owner → "RIC-{anno}-{numero:04d}"
      es. "RIC-2026-0003"
    * fattura_pm → "FT-{anno}-{numero:04d}"
      es. "FT-2026-0002"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. AGGIORNA FiscalDocumentDAO — insert
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/FiscalDocumentDAO.java:

FiscalDocument insert(FiscalDocument doc)
- INSERT INTO fiscal_document con tutti i campi
- Usa KeyHolder per id generato
- Usa Types.OTHER per colonne enum
  (tipo_documento, stato_documento)
- Dopo insert: rileggi con findById()
- Log INFO: "FiscalDocumentDAO.insert() -
  number={} tenantId={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. DOCUMENT SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/DocumentGenerationService.java:
- @Service @Log4j2
- Costruttore con FiscalDocumentDAO, BookingDAO,
  BookingService, TenantDAO

  DocumentGenerateResponseDTO generate(
  Integer tenantId,
  DocumentGenerateRequestDTO request)

    1. Verifica esistenza booking e appartenenza
       al tenant
    2. Verifica che non esista già un documento
       per questo booking e tipo:
       se esiste → lancia IllegalArgumentException
       "Documento già emesso per questa prenotazione"
    3. Calcola importi in base a tipoDocumento:

       Per "ricevuta_owner":
        - canone = booking.grossAmount
            - booking.otaCommissionAmount
            - booking.cleaningAmount
            - booking.pmFeeAmount
        - bollo = canone > 77.47 ? 2.00 : 0.00
        - importoTotale = canone + bollo
        - ritenuta = canone * 0.21
          (o aliquota da tenant_settings)
        - imponibile = canone
        - iva = 0

       Per "fattura_pm":
        - imponibile = booking.otaCommissionAmount
            + booking.cleaningAmount
            + booking.pmFeeAmount
        - iva = imponibile * 0.22
        - importoTotale = imponibile + iva
        - ritenuta = booking.withholdingAmount
        - bollo = 0

    4. Genera numero documento:
       fiscalDocumentDAO.generateDocumentNumber(
       tenantId, tipoDocumento, anno)

    5. Crea e salva FiscalDocument:
        - fkTenantId = tenantId
        - fkBookingId = booking.id
        - fkOwnerId = booking.fkOwnerId
        - tipoDocumento = request.tipoDocumento
        - statoDocumento = "draft"
        - documentNumber = generato
        - dataEmissione = request.dataEmissione ?? today
        - recipientName = booking.guestName
        - recipientTaxCode = booking.guestTaxCode
        - grossAmount = importoTotale
        - netAmount = imponibile
        - withholdingAmount = ritenuta
        - bolloAmount = bollo

    6. NON aggiorna ancora stato booking →
       rimane "imported" o stato attuale
       (lo stato cambierà quando il documento
       viene confermato/inviato — futuro)

    7. Restituisce DocumentGenerateResponseDTO

  Log INFO: "DocumentGenerationService.generate()
    - tenantId={} booking={} tipo={} number={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea controller/DocumentController.java:
- @RestController @Log4j2
- @RequestMapping("/api/documents")
- Costruttore con DocumentGenerationService,
  FiscalDocumentService (già esistente)

  POST /api/documents/generate
    - @RequestBody DocumentGenerateRequestDTO
    - tenantId = SecurityUtils.getCurrentTenantId()
    - chiama documentGenerationService.generate()
    - ResponseEntity.status(201).body(result)
    - gestisce IllegalArgumentException → 400

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. FRONTEND — aggiorna bookingApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/bookingApi.ts
i nuovi campi nel BookingDetail:

```ts
// Dati immobile per dialog
propertyAddress?: string;
propertyCity?: string;
propertyInternalCode?: string;
// Dati proprietario per dialog
ownerTaxCode?: string;
ownerIban?: string;
ownerEmail?: string;
// Dati tenant per dialog fattura PM
tenantLegalName?: string;
tenantVatNumber?: string;
tenantTaxCode?: string;
tenantLegalAddress?: string;
tenantPec?: string;
```

Crea frontend/src/api/documentApi.ts:

```ts
export interface DocumentGenerateRequest {
  bookingId: number;
  tipoDocumento: 'ricevuta_owner' | 'fattura_pm';
  dataEmissione?: string;
}

export interface DocumentGenerateResponse {
  documentId: number;
  documentNumber: string;
  tipoDocumento: string;
  dataEmissione: string;
  importoTotale: number;
  importoBollo: number;
  imponibile: number;
  iva: number;
  ritenuta: number;
  statoDocumento: string;
  bookingExternalId: string;
  guestName: string;
  ownerName: string;
  propertyName: string;
}

export async function generateDocument(
  data: DocumentGenerateRequest
): Promise<DocumentGenerateResponse>
// POST /api/documents/generate
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. AGGIORNA BookingDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/tenant/BookingDetail.tsx:

1. La funzione toDialogBooking() deve usare
   i nuovi campi reali dal backend:
    - property.address → booking.propertyAddress
    - property.city → booking.propertyCity
    - property.internal_code → booking.propertyInternalCode
    - owner.tax_code → booking.ownerTaxCode
    - owner.iban → booking.ownerIban
    - tenantData hardcoded → sostituisci con:
      {
      legal_name: booking.tenantLegalName,
      vat_number: booking.tenantVatNumber,
      tax_code: booking.tenantTaxCode,
      address: booking.tenantLegalAddress,
      pec: booking.tenantPec
      }

2. Aggiungi stato: generatedDoc per tenere
   il DocumentGenerateResponse dopo la generazione

3. Nel dialog ReceiptOwnerDialog aggiungi
   pulsante "Emetti Documento" che:
    - chiama generateDocument({
      bookingId: booking.id,
      tipoDocumento: 'ricevuta_owner'
      })
    - se successo: aggiorna generatedDoc,
      mostra numero documento reale nel dialog,
      abilita window.print()
    - se errore 400 "già emesso": mostra messaggio
    - isSaving state durante la chiamata

4. Nel dialog InvoicePMDialog stesso pattern
   ma con tipoDocumento: 'fattura_pm'

5. Dopo emissione documento: ricarica booking
   con getBookingById(id) per aggiornare
   i badge di stato

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
9. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package

Verifica:
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"bookingId": 1,
"tipoDocumento": "ricevuta_owner"}' \
http://localhost:8081/sostitutoincloud/api/documents/generate \
| python3 -m json.tool

cd frontend && npm run build

Riporta output di entrambi.