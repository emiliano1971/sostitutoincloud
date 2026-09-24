Leggi il file CLAUDE.md e i file esistenti:
- service/DocumentPdfService.java
  (come riferimento per OpenHTMLToPDF)
- service/SettlementService.java
- dto/settlement/SettlementDetailDTO.java
- dto/settlement/SettlementBookingDTO.java
  prima di procedere.

Implementa la generazione PDF del rendiconto
liquidazione usando OpenHTMLToPDF.
Stesso approccio già usato per fattura/ricevuta.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. TEMPLATE HTML
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea src/main/resources/templates/
rendiconto-liquidazione.html

Layout (CSS con display:table per
compatibilità OpenHTMLToPDF CSS 2.1):

<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8"/>
<style>
  body {
    font-family: Arial, sans-serif;
    font-size: 10px;
    margin: 20px;
    color: #333;
  }
  .header {
    display: table;
    width: 100%;
    margin-bottom: 20px;
    border-bottom: 2px solid #333;
    padding-bottom: 10px;
  }
  .header-left { display: table-cell; }
  .header-right {
    display: table-cell;
    text-align: right;
  }
  .company-name {
    font-size: 14px;
    font-weight: bold;
  }
  .doc-title {
    font-size: 18px;
    font-weight: bold;
    color: #1a1a2e;
  }
  .section {
    margin-bottom: 15px;
  }
  .section-title {
    font-size: 9px;
    font-weight: bold;
    text-transform: uppercase;
    color: #666;
    border-bottom: 1px solid #eee;
    padding-bottom: 3px;
    margin-bottom: 5px;
    letter-spacing: 1px;
  }
  .info-grid {
    display: table;
    width: 100%;
  }
  .info-cell {
    display: table-cell;
    width: 33%;
    vertical-align: top;
  }
  .info-label {
    color: #666;
    font-size: 9px;
  }
  .info-value {
    font-weight: bold;
    font-size: 11px;
  }
  table {
    width: 100%;
    border-collapse: collapse;
    margin-bottom: 10px;
    font-size: 9px;
  }
  th {
    background: #f5f5f5;
    padding: 5px 4px;
    text-align: left;
    border-bottom: 1px solid #ddd;
    font-size: 8px;
    text-transform: uppercase;
    color: #666;
  }
  th.text-right { text-align: right; }
  td {
    padding: 4px;
    border-bottom: 1px solid #f0f0f0;
  }
  td.text-right { text-align: right; }
  td.negative { color: #cc0000; }
  .totals-row {
    font-weight: bold;
    border-top: 2px solid #333;
    background: #f9f9f9;
  }
  .summary {
    display: table;
    width: 100%;
    margin-top: 15px;
  }
  .summary-cell {
    display: table-cell;
    width: 25%;
    text-align: center;
    padding: 8px;
    border: 1px solid #eee;
    border-radius: 4px;
  }
  .summary-label {
    font-size: 8px;
    color: #666;
    text-transform: uppercase;
  }
  .summary-value {
    font-size: 13px;
    font-weight: bold;
    margin-top: 3px;
  }
  .summary-value.negative { color: #cc0000; }
  .summary-value.positive { color: #166534; }
  .netto-box {
    margin-top: 10px;
    text-align: center;
    padding: 10px;
    background: #f0fdf4;
    border: 1px solid #bbf7d0;
  }
  .netto-label {
    font-size: 10px;
    color: #166534;
    text-transform: uppercase;
  }
  .netto-value {
    font-size: 20px;
    font-weight: bold;
    color: #166534;
  }
  .bollo-note {
    font-size: 8px;
    color: #666;
    font-style: italic;
    margin-top: 5px;
  }
  .footer {
    margin-top: 20px;
    font-size: 8px;
    color: #999;
    border-top: 1px solid #eee;
    padding-top: 8px;
  }
  .badge {
    display: inline-block;
    padding: 2px 6px;
    border-radius: 3px;
    font-size: 8px;
    font-weight: bold;
  }
  .badge-calculated { background:#dbeafe; color:#1e40af; }
  .badge-approved { background:#ffedd5; color:#c2410c; }
  .badge-paid { background:#dcfce7; color:#166534; }
</style>
</head>
<body>

<!-- INTESTAZIONE -->
<div class="header">
  <div class="header-left">
    <div class="company-name">{TENANT_LEGAL_NAME}</div>
    <div>P.IVA: {TENANT_VAT_NUMBER}</div>
    <div>C.F.: {TENANT_TAX_CODE}</div>
    <div>{TENANT_ADDRESS}</div>
    <div>PEC: {TENANT_PEC}</div>
  </div>
  <div class="header-right">
    <div class="doc-title">RENDICONTO</div>
    <div class="doc-title">LIQUIDAZIONE</div>
    <div style="font-size:12px; margin-top:5px;">
      Periodo: {PERIODO}
    </div>
    <div style="margin-top:3px;">
      <span class="badge badge-{STATO_CSS}">
        {STATO_LABEL}
      </span>
    </div>
  </div>
</div>

<!-- DESTINATARIO -->
<div class="section">
  <div class="section-title">
    Proprietario
  </div>
  <div class="info-grid">
    <div class="info-cell">
      <div class="info-label">Nome</div>
      <div class="info-value">
        {OWNER_NAME}
      </div>
    </div>
    <div class="info-cell">
      <div class="info-label">
        Codice Fiscale
      </div>
      <div class="info-value">
        {OWNER_TAX_CODE}
      </div>
    </div>
    <div class="info-cell">
      <div class="info-label">IBAN</div>
      <div class="info-value">
        {OWNER_IBAN}
      </div>
    </div>
  </div>
</div>

<!-- TABELLA PRENOTAZIONI -->
<div class="section">
  <div class="section-title">
    Dettaglio Prenotazioni
  </div>
  <table>
    <thead>
      <tr>
        <th>#</th>
        <th>ID Prenotazione</th>
        <th>Immobile</th>
        <th>Check-in</th>
        <th>Check-out</th>
        <th class="text-right">Lordo €</th>
        <th class="text-right">Comm. OTA €</th>
        <th class="text-right">Pulizie €</th>
        <th class="text-right">Provv. PM €</th>
        <th class="text-right">Canone €</th>
        <th class="text-right">Bollo €</th>
        <th class="text-right">Ritenuta €</th>
        <th class="text-right">Netto €</th>
      </tr>
    </thead>
    <tbody>
      {RIGHE_PRENOTAZIONI}
      <!-- riga totale -->
      <tr class="totals-row">
        <td colspan="5">TOTALE</td>
        <td class="text-right">{TOT_LORDO}</td>
        <td class="text-right negative">
          {TOT_OTA}
        </td>
        <td class="text-right negative">
          {TOT_PULIZIE}
        </td>
        <td class="text-right negative">
          {TOT_PM}
        </td>
        <td class="text-right">{TOT_CANONE}</td>
        <td class="text-right">{TOT_BOLLO}</td>
        <td class="text-right negative">
          {TOT_RITENUTA}
        </td>
        <td class="text-right positive">
          {TOT_NETTO}
        </td>
      </tr>
    </tbody>
  </table>
  <div class="bollo-note">
    * La marca da bollo è indicata a fini
    informativi e non è dedotta dal netto
    pagato al proprietario.
  </div>
</div>

<!-- RIEPILOGO CARD -->
<div class="summary">
  <div class="summary-cell">
    <div class="summary-label">
      Lordo totale
    </div>
    <div class="summary-value">
      € {TOT_LORDO}
    </div>
  </div>
  <div class="summary-cell">
    <div class="summary-label">
      Ritenute totali
    </div>
    <div class="summary-value negative">
      -€ {TOT_RITENUTA}
    </div>
  </div>
  <div class="summary-cell">
    <div class="summary-label">
      Bollo totale *
    </div>
    <div class="summary-value">
      € {TOT_BOLLO}
    </div>
  </div>
  <div class="summary-cell">
    <div class="summary-label">
      Prenotazioni
    </div>
    <div class="summary-value">
      {NUM_PRENOTAZIONI}
    </div>
  </div>
</div>

<div class="netto-box">
  <div class="netto-label">
    Netto da pagare al proprietario
  </div>
  <div class="netto-value">
    € {NETTO_DA_PAGARE}
  </div>
</div>

<div class="footer">
  Documento generato da Sostituto in Cloud
  il {DATA_GENERAZIONE}
</div>
</body>
</html>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. SettlementPdfService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/SettlementPdfService.java:
- @Service @Log4j2
- Costruttore con SettlementService,
  TenantDAO, TenantSettingsDAO,
  OwnerProfileDAO

@Value("${app.storage.templates-path:}")
private String templatesPath;

Metodo principale:
byte[] generaPdf(Integer tenantId,
Integer settlementId)

Logica:

1. Carica settlement con dettaglio:
   SettlementDetailDTO settlement =
   settlementService.findById(
   tenantId, settlementId)
   NoSuchElementException se non trovato

2. Carica tenant, tenant_settings,
   owner_profile

3. Costruisci mappa placeholder:

   Tenant/PM:
   {TENANT_LEGAL_NAME} = tenant.legalName
   {TENANT_VAT_NUMBER} = tenant.vatNumber
   {TENANT_TAX_CODE}   = tenant.taxCode
   {TENANT_ADDRESS}    = TenantAddressUtils
   .indirizzoCompleto(tenant)
   {TENANT_PEC}        = tenant.pec

   Proprietario:
   {OWNER_NAME}     = owner.firstName
    + " " + owner.lastName
      {OWNER_TAX_CODE} = owner.taxCode
      {OWNER_IBAN}     = owner.iban ?? "—"

   Liquidazione:
   {PERIODO}    = settlement.period
   {STATO_LABEL} = mappa:
   calculated → "Calcolato"
   approved   → "Approvato"
   paid       → "Pagato"
   {STATO_CSS}  = mappa:
   calculated → "calculated"
   approved   → "approved"
   paid       → "paid"

   Totali (formattati con 2 decimali
   e virgola come separatore decimale):
   {TOT_LORDO}    = Σ booking.grossAmount
   {TOT_OTA}      = Σ booking
   .otaCommissionAmount
   {TOT_PULIZIE}  = Σ booking.cleaningAmount
   {TOT_PM}       = Σ booking.pmFeeAmount
   {TOT_CANONE}   = settlement.totalAmount
   {TOT_BOLLO}    = Σ booking.bolloCents/100
   {TOT_RITENUTA} = settlement
   .withholdingAmount
   {TOT_NETTO}    = settlement.netAmount
   {NETTO_DA_PAGARE} = settlement.netAmount
   {NUM_PRENOTAZIONI} = settlement.bookings
   .size()
   {DATA_GENERAZIONE} = LocalDate.now()
   .format(dd/MM/yyyy)

4. Genera righe prenotazioni:
   StringBuilder righe = new StringBuilder()
   int i = 1;
   for (SettlementBookingDTO b :
   settlement.getBookings()) {
   BigDecimal netto = b.getOwnerNetAmount()
   .subtract(b.getWithholdingAmount());
   BigDecimal bollo = BigDecimal.valueOf(
   b.getBolloCents()).divide(
   new BigDecimal("100"));
   righe.append(String.format("""
   <tr>
   <td>%d</td>
   <td style="font-family:monospace;
   font-size:8px">%s</td>
   <td>%s</td>
   <td>%s</td>
   <td>%s</td>
   <td class="text-right">%s</td>
   <td class="text-right negative">
   -%s</td>
   <td class="text-right negative">
   -%s</td>
   <td class="text-right negative">
   -%s</td>
   <td class="text-right">%s</td>
   <td class="text-right">%s</td>
   <td class="text-right negative">
   -%s</td>
   <td class="text-right positive">
   %s</td>
   </tr>
   """,
   i++,
   b.getExternalBookingId(),
   b.getPropertyName(),
   b.getCheckinDate(),
   b.getCheckoutDate(),
   fmt(b.getGrossAmount()),
   fmt(b.getOtaCommissionAmount()),
   fmt(b.getCleaningAmount()),
   fmt(b.getPmFeeAmount()),
   fmt(b.getOwnerNetAmount()),
   fmt(bollo),
   fmt(b.getWithholdingAmount()),
   fmt(netto)));
   }

   Aggiungi metodo privato:
   private String fmt(BigDecimal val) {
   if (val == null) return "0,00";
   return String.format("%.2f", val)
   .replace(".", ",");
   }

5. Sostituisci {RIGHE_PRENOTAZIONI}
   con righe.toString()

6. Carica template con PdfTemplateLoader
   (già in util/) con filename
   "rendiconto-liquidazione.html"

7. Sostituisci tutti i placeholder
   con String.replace()

8. Genera PDF con PdfRendererBuilder
   (stesso pattern di DocumentPdfService)

9. Log INFO "SettlementPdfService
   .generaPdf() - settlementId={}
   ownerName={} bytes={}"

10. Return byte[]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. SettlementController — endpoint PDF
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/SettlementController.java:

GET /api/settlements/{id}/pdf
- tenantId da SecurityUtils
- chiama settlementPdfService.generaPdf()
- ResponseEntity con headers:
  Content-Type: application/pdf
  Content-Disposition: attachment;
  filename="Rendiconto_{periodo}
  _{ownerName}.pdf"
  (sostituisci spazi con _)
- catch NoSuchElementException → 404
- catch Exception → 500 con log ERROR
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/settlementApi.ts:

export async function downloadSettlementPdf(
id: number,
periodo: string,
ownerName: string
): Promise<void>
// GET /api/settlements/{id}/pdf
// fetch blob + download automatico
// filename = "Rendiconto_{periodo}
//   _{ownerName}.pdf"

In frontend/src/pages/tenant/
SettlementDetail.tsx aggiungi pulsante
"⬇ Scarica PDF" in cima alla pagina
accanto al badge stato:

onClick → downloadSettlementPdf(
settlement.id,
settlement.period,
settlement.ownerName)

Con spinner durante download e
toast errore se fallisce.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package
cd frontend && npm run build &&
npm run typecheck

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena2026"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/settlements/3/pdf" \
--output /tmp/rendiconto-test.pdf

file /tmp/rendiconto-test.pdf
ls -la /tmp/rendiconto-test.pdf

pdftotext /tmp/rendiconto-test.pdf - \
| head -50

Verifica che:
- PDF generato senza errori
- Contiene dati PM nell'intestazione
- Contiene nome proprietario
- Contiene tabella prenotazioni
  con importi corretti
- Netto da pagare coincide con
  settlement.netAmount

Riporta output build e testo estratto.