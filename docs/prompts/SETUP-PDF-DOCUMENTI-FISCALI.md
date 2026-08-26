Leggi il file CLAUDE.md, pom.xml e i file:
- service/DocumentGenerationService.java
- components/booking/InvoicePMDialog.tsx
- components/booking/ReceiptOwnerDialog.tsx
  prima di procedere.

Implementa la generazione PDF server-side
per fattura PM e ricevuta owner usando
OpenHTMLToPDF. Il layout riproduce quello
già presente nei dialog frontend.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DIPENDENZA MAVEN
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi in pom.xml:

<dependency>
    <groupId>com.openhtmltopdf</groupId>
    <artifactId>openhtmltopdf-pdfbox</artifactId>
    <version>1.0.10</version>
</dependency>
<dependency>
    <groupId>com.openhtmltopdf</groupId>
    <artifactId>openhtmltopdf-slf4j</artifactId>
    <version>1.0.10</version>
</dependency>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. TEMPLATE HTML
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea due template HTML in
src/main/resources/templates/:

fattura-pm.html
ricevuta-owner.html

I template usano placeholder con sintassi
{NOME_CAMPO} per la sostituzione a runtime.
NON usare Thymeleaf o altri motori template —
sostituzione semplice con String.replace().

FATTURA PM (fattura-pm.html):
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8"/>
<style>
  body {
    font-family: Arial, sans-serif;
    font-size: 11px;
    margin: 20px;
    color: #333;
  }
  .header {
    display: flex;
    justify-content: space-between;
    margin-bottom: 30px;
    border-bottom: 2px solid #333;
    padding-bottom: 15px;
  }
  .company-name {
    font-size: 16px;
    font-weight: bold;
    margin-bottom: 5px;
  }
  .doc-title {
    font-size: 20px;
    font-weight: bold;
    text-align: right;
    color: #1a1a2e;
  }
  .doc-number {
    font-size: 14px;
    text-align: right;
    color: #666;
  }
  .section {
    margin-bottom: 20px;
  }
  .section-title {
    font-size: 10px;
    font-weight: bold;
    text-transform: uppercase;
    color: #666;
    letter-spacing: 1px;
    margin-bottom: 5px;
    border-bottom: 1px solid #eee;
    padding-bottom: 3px;
  }
  table {
    width: 100%;
    border-collapse: collapse;
    margin-bottom: 15px;
  }
  th {
    background: #f5f5f5;
    padding: 8px;
    text-align: left;
    font-size: 10px;
    text-transform: uppercase;
    color: #666;
    border-bottom: 1px solid #ddd;
  }
  td {
    padding: 8px;
    border-bottom: 1px solid #eee;
  }
  .text-right { text-align: right; }
  .totals {
    margin-left: auto;
    width: 300px;
  }
  .totals table td {
    padding: 5px 8px;
  }
  .total-row {
    font-weight: bold;
    font-size: 13px;
    border-top: 2px solid #333 !important;
  }
  .footer {
    margin-top: 40px;
    font-size: 9px;
    color: #999;
    border-top: 1px solid #eee;
    padding-top: 10px;
  }
</style>
</head>
<body>

<!-- INTESTAZIONE -->
<div class="header">
  <div>
    <div class="company-name">{TENANT_LEGAL_NAME}</div>
    <div>P.IVA: {TENANT_VAT_NUMBER}</div>
    <div>C.F.: {TENANT_TAX_CODE}</div>
    <div>{TENANT_LEGAL_ADDRESS}</div>
    <div>PEC: {TENANT_PEC}</div>
  </div>
  <div>
    <div class="doc-title">FATTURA</div>
    <div class="doc-number">{DOCUMENT_NUMBER}</div>
    <div class="doc-number">Data: {ISSUE_DATE}</div>
  </div>
</div>

<!-- DESTINATARIO + IMMOBILE -->
<div style="display:flex; gap:40px;
     margin-bottom:20px;">
  <div class="section" style="flex:1">
    <div class="section-title">Destinatario</div>
    <div><strong>{RECIPIENT_NAME}</strong></div>
    <div>C.F.: {RECIPIENT_TAX_CODE}</div>
  </div>
  <div class="section" style="flex:1">
    <div class="section-title">Immobile</div>
    <div><strong>{PROPERTY_NAME}</strong></div>
    <div>{PROPERTY_ADDRESS}</div>
    <div>{PROPERTY_CITY}</div>
  </div>
  <div class="section" style="flex:1">
    <div class="section-title">Soggiorno</div>
    <div>Check-in: {CHECKIN_DATE}</div>
    <div>Check-out: {CHECKOUT_DATE}</div>
    <div>Notti: {NIGHTS}</div>
    <div>Ospiti: {GUESTS}</div>
  </div>
</div>

<!-- RIGHE FATTURA -->
<div class="section">
  <div class="section-title">
    Dettaglio Servizi
  </div>
  <table>
    <thead>
      <tr>
        <th>Descrizione</th>
        <th class="text-right">Imponibile</th>
        <th class="text-right">IVA 22%</th>
        <th class="text-right">Totale</th>
      </tr>
    </thead>
    <tbody>
      {RIGHE_FATTURA}
    </tbody>
  </table>
</div>

<!-- TOTALI -->
<div class="totals">
  <table>
    <tr>
      <td>Totale imponibile</td>
      <td class="text-right">
        {TOTAL_IMPONIBILE} €
      </td>
    </tr>
    <tr>
      <td>IVA 22%</td>
      <td class="text-right">
        {TOTAL_IVA} €
      </td>
    </tr>
    <tr class="total-row">
      <td>TOTALE FATTURA</td>
      <td class="text-right">
        {TOTAL_AMOUNT} €
      </td>
    </tr>
  </table>
</div>

<div class="footer">
  Documento generato da Sostituto in Cloud
</div>
</body>
</html>

RICEVUTA OWNER (ricevuta-owner.html):
Stesso layout della fattura ma:
- Titolo: "RICEVUTA" invece di "FATTURA"
- Intestazione: dati proprietario
  (non del PM)
- Destinatario: dati PM
- Nessuna colonna IVA nella tabella
- Righe: solo canone locazione e bollo
- Totali senza IVA, con ritenuta:
  <tr>
    <td>Canone lordo</td>
    <td class="text-right">
      {CANONE_LORDO} €
    </td>
  </tr>
  <tr>
    <td>Ritenuta 21%</td>
    <td class="text-right" style="color:red">
      -{RITENUTA} €
    </td>
  </tr>
  <tr>
    <td>Marca da bollo</td>
    <td class="text-right">
      {BOLLO} €
    </td>
  </tr>
  <tr class="total-row">
    <td>NETTO A PAGARE</td>
    <td class="text-right">
      {NETTO_PAGARE} €
    </td>
  </tr>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. DocumentPdfService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/DocumentPdfService.java:
- @Service @Log4j2
- Costruttore con FiscalDocumentDAO,
  BookingDAO, TenantSettingsDAO,
  OwnerProfileDAO, PropertyDAO

@Value("${app.storage.pdf-path}")
private String pdfStoragePath;

@Value("${app.storage.templates-path:}")
private String templatesPath;

Metodo principale:
byte[] generaPdf(Integer tenantId,
Integer fiscalDocumentId)

Logica:
1. Carica fiscal_document + booking
    + tenant_settings + owner_profile
    + property

2. Determina tipo documento:
   se tipo = "fattura_pm" → usa
   fattura-pm.html
   se tipo = "ricevuta_owner" → usa
   ricevuta-owner.html

3. Carica template HTML:
    - prima cerca in templatesPath/
      (storage esterno, come F24)
    - fallback: classpath /templates/

4. Sostituisci placeholder con
   String.replace("{CAMPO}", valore)
   per tutti i campi del documento

   Per le righe fattura ({RIGHE_FATTURA})
   genera HTML dinamico:
   StringBuilder righe = new StringBuilder();
   per ogni riga del documento:
   righe.append("""
     <tr>
       <td>{desc}</td>
       <td class="text-right">{imponibile}</td>
       <td class="text-right">{iva}</td>
       <td class="text-right">{totale}</td>
     </tr>
   """);

5. Genera PDF con OpenHTMLToPDF:
   ByteArrayOutputStream baos =
   new ByteArrayOutputStream();
   PdfRendererBuilder builder =
   new PdfRendererBuilder();
   builder.withHtmlContent(html, null);
   builder.toStream(baos);
   builder.run();
   return baos.toByteArray();

6. Log INFO "DocumentPdfService
   .generaPdf() - docId={} tipo={} bytes={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FiscalDocumentController
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/DocumentController.java:

GET /api/documents/{id}/pdf
- tenantId da SecurityUtils
- chiama documentPdfService.generaPdf()
- ResponseEntity con headers:
  Content-Type: application/pdf
  Content-Disposition: attachment;
  filename="{documentNumber}.pdf"
- catch NoSuchElementException → 404
- catch IllegalStateException → 400
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. FRONTEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/documentApi.ts:

export async function downloadDocumentPdf(
id: number,
documentNumber: string
): Promise<void>
// GET /api/documents/{id}/pdf
// fetch blob + download automatico
// filename = "{documentNumber}.pdf"

In InvoicePMDialog.tsx e
ReceiptOwnerDialog.tsx:
- Se stato = 'doc_issued' aggiungi
  pulsante "⬇ Scarica PDF" accanto
  a "🖨 Stampa":
  onClick → downloadDocumentPdf(
  document.id, document.documentNumber)
  con spinner durante download

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

# Scarica PDF fattura
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/documents/1/pdf" \
--output /tmp/fattura-test.pdf

file /tmp/fattura-test.pdf
ls -la /tmp/fattura-test.pdf

# Verifica che sia un PDF valido
python3 -c "
import fitz
doc = fitz.open('/tmp/fattura-test.pdf')
print('Pagine:', doc.page_count)
print('Testo:', doc[0].get_text()[:200])
"

Verifica che:
- PDF generato senza errori
- Contiene ragione sociale PM
- Contiene numero documento
- Contiene importi corretti

Riporta output build, dimensione PDF
e testo estratto.