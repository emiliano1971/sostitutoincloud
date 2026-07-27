Leggi il file CLAUDE.md e pom.xml
prima di procedere.

Implementa la generazione PDF del modello
F24 Semplificato compilando i campi AcroForm
del template ufficiale AdE con PDFBox.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
0. PREPARAZIONE FILE TEMPLATE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Copia il template PDF nella cartella risorse:
mkdir -p src/main/resources/templates
cp docs/templates/f24-semplificato-acroform.pdf \
src/main/resources/templates/

Il file si trova già in docs/templates/ —
se non esiste la cartella docs/templates/,
cercalo nella root del progetto con nome
*AcroForm*.pdf e copialo lì.

Verifica che my-build.xml copi la cartella
src/main/resources/templates/ in
WEB-INF/classes/templates/ durante il build.
Se non lo fa aggiungilo al task di copia
risorse, stesso pattern già usato per sql/.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DIPENDENZA MAVEN
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi in pom.xml:

<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.3</version>
</dependency>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/pdf/F24PdfDataDTO.java:
- @Data @Builder @NoArgsConstructor
  @AllArgsConstructor

Dati contribuente (PM — sostituto d'imposta):
String contribuenteCf
String contribuenteCognomeDenominazione
String contribuenteNome
String contribuenteDataNascitaGg   ← "01"
String contribuenteDataNascitaMm   ← "01"
String contribuenteDataNascitaAa   ← "1980"
String contribuenteSesso           ← "M" o "F"
String contribuenteComuneNascita
String contribuenteProvincia

Dati tributo (una riga):
String motivoSezione               ← "Erario"
String motivoCodTributo            ← "1919"
String motivoCodiceEnte            ← vuoto
String motivoMeseRif               ← "06"
String motivoAnnoRif               ← "2026"
String motivoImportoDebito         ← "97,44"
String motivoImportoCredito        ← vuoto

Saldo:
String saldoFinaleEuro             ← "97"
String saldoFinaleCent             ← "44"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. F24PdfService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/F24PdfService.java:
- @Service @Log4j2

Dipendenze:
- F24RecordDAO
- TenantSettingsDAO (per dati anagrafici PM)
- WithholdingLedgerDAO

Metodo principale:
byte[] generaPdf(Integer tenantId,
Integer f24RecordId)

Logica:
1. Carica f24Record con f24RecordDAO
   .findById(f24RecordId)
   NoSuchElementException se non trovato
   Verifica appartenenza tenant

2. Carica tenant_settings per dati PM:
   tenantSettingsDAO.findByTenantId(tenantId)
   I campi anagrafici del PM devono esistere
   in tenant_settings — verifica che ci siano:
   codice_fiscale, ragione_sociale,
   data_nascita, sesso, comune_nascita,
   provincia_nascita
   Se mancano lancia IllegalStateException(
   "Dati anagrafici PM incompleti in
   tenant_settings")

3. Costruisci F24PdfDataDTO:
    - contribuenteCf = tenantSettings
      .getCodiceFiscale()
    - contribuenteCognomeDenominazione =
      tenantSettings.getRagioneSociale()
    - contribuenteNome = "" (persona fisica:
      cognome in denominazione, nome vuoto;
      se società: denominazione in cognome)
    - contribuenteDataNascitaGg/Mm/Aa =
      split di tenantSettings.getDataNascita()
      (LocalDate → formato "dd","MM","yyyy")
    - contribuenteSesso =
      tenantSettings.getSesso()
    - contribuenteComuneNascita =
      tenantSettings.getComuneNascita()
    - contribuenteProvincia =
      tenantSettings.getProvinciaNascita()
    - motivoSezione = "Erario"
    - motivoCodTributo = f24Record
      .getCodiceTributo() ← es. "1919"
    - motivoMeseRif = String.format("%02d",
      f24Record.getPeriodoMese())
    - motivoAnnoRif = String.valueOf(
      f24Record.getPeriodoAnno())
    - motivoImportoDebito = formatta importo:
      BigDecimal → "97,44"
      (virgola come separatore decimale,
      solo parte intera se .00)
    - saldoFinaleEuro = parte intera
      dell'importo totale
    - saldoFinaleCent = parte decimale
      su 2 cifre

4. Carica il template PDF dal classpath:
   InputStream template = getClass()
   .getResourceAsStream(
   "/templates/f24-semplificato-acroform.pdf")
   if (template == null)
   throw new IllegalStateException(
   "Template F24 non trovato in classpath")

5. Compila i campi AcroForm con PDFBox:

   try (PDDocument doc =
   PDDocument.load(template)) {

   PDAcroForm acroForm =
   doc.getDocumentCatalog().getAcroForm();
   if (acroForm == null)
   throw new IllegalStateException(
   "Il PDF non contiene campi AcroForm");

   // Mappa nome campo → valore
   Map<String, String> campi = Map.of(
   "contribuente_cf",
   dto.getContribuenteCf(),
   "contribuente_cognome_denominazione",
   dto.getContribuenteCognomeDenominazione(),
   "contribuente_nome",
   dto.getContributenteNome() != null
   ? dto.getContributenteNome() : "",
   "contribuente_data_nascita_gg",
   dto.getContributenteDataNascitaGg(),
   "contribuente_data_nascita_mm",
   dto.getContributenteDataNascitaMm(),
   "contribuente_data_nascita_aa",
   dto.getContributenteDataNascitaAa(),
   "contribuente_sesso",
   dto.getContributenteSesso(),
   "contribuente_comune_nascita",
   dto.getContributenteComuneNascita(),
   "contribuente_provincia",
   dto.getContributenteProvincia()
   );

   // Aggiunge i campi motivo e saldo
   // (Map.of ha limite 10 entry)
   Map<String, String> campi2 = Map.of(
   "motivo_sezione_1",
   dto.getMotivoSezione(),
   "motivo_cod_tributo_1",
   dto.getMotivoCodTributo(),
   "motivo_codice_ente",
   dto.getMotivoCodiceEnte() != null
   ? dto.getMotivoCodiceEnte() : "",
   "motivo_mese_rif_1",
   dto.getMotivoMeseRif(),
   "motivo_anno_rif_1",
   dto.getMotivoAnnoRif(),
   "motivo_importo_debito_1",
   dto.getMotivoImportoDebito(),
   "motivo_importo_credito_1",
   dto.getMotivoImportoCredito() != null
   ? dto.getMotivoImportoCredito() : "",
   "saldo_finale_euro",
   dto.getSaldoFinaleEuro(),
   "saldo_finale_cent",
   dto.getSaldoFinaleCent()
   );

   // Compila tutti i campi
   for (var entry :
   Stream.concat(
   campi.entrySet().stream(),
   campi2.entrySet().stream())
   .toList()) {
   PDField field = acroForm
   .getField(entry.getKey());
   if (field != null) {
   field.setValue(entry.getValue());
   } else {
   log.warn("Campo AcroForm non trovato:
   {}", entry.getKey());
   }
   }

   // Appiattisce il form (rende i campi
   // non editabili nel PDF finale)
   acroForm.flatten();

   ByteArrayOutputStream baos =
   new ByteArrayOutputStream();
   doc.save(baos);
   return baos.toByteArray();
   }

Log INFO "F24PdfService.generaPdf()
- f24RecordId={} tenantId={} bytes={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. TenantSettings — campi anagrafici PM
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che tenant_settings abbia i campi
per i dati anagrafici del PM necessari
per il F24:
codice_fiscale
ragione_sociale
data_nascita    ← DATE
sesso           ← CHAR(1)
comune_nascita  ← VARCHAR(100)
provincia_nascita ← CHAR(2)

Se mancano aggiungili con migration:
docs/db/migrations/008_tenant_settings_pm_anagrafica.sql:

ALTER TABLE tenant_settings
ADD COLUMN IF NOT EXISTS
data_nascita DATE,
ADD COLUMN IF NOT EXISTS
sesso CHAR(1),
ADD COLUMN IF NOT EXISTS
comune_nascita VARCHAR(100),
ADD COLUMN IF NOT EXISTS
provincia_nascita CHAR(2);

Aggiorna model/TenantSettings.java e
dao/mapper/TenantSettingsRowMapper.java
con i nuovi campi.

Aggiorna il form di configurazione tenant
in frontend per permettere di inserire
questi dati (sezione "Dati anagrafici PM"
in TenantSettings o Configurazione).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. F24Controller — endpoint download PDF
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/F24Controller.java:

GET /api/f24/{id}/pdf
- tenantId da SecurityUtils
- chiama f24PdfService.generaPdf()
- ResponseEntity con headers:
  Content-Type: application/pdf
  Content-Disposition:
  attachment; filename="F24_{periodo}.pdf"
  dove periodo = "06_2026"
- catch IllegalStateException → 400
  body {"error": msg}
- catch NoSuchElementException → 404
- Log INFO "F24Controller.pdf() - id={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. FRONTEND — F24List.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/f24Api.ts:

export async function downloadF24Pdf(
id: number
): Promise<void>
// GET /api/f24/{id}/pdf
// Scarica il file PDF nel browser:
// - fetch con responseType blob
// - crea URL.createObjectURL(blob)
// - simula click su link <a download>
// - revoca URL dopo il download

Aggiungi a F24List.tsx per ogni riga
un pulsante "⬇ PDF" accanto alle
azioni esistenti:
- visibile solo se stato != 'draft'
- onClick → downloadF24Pdf(f24.id)
- mostra spinner durante download
- catch errore → toast "Errore
  generazione PDF: {msg}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. TEST
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

# Scarica PDF F24
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/f24/2/pdf" \
--output /tmp/f24-test.pdf

# Verifica che sia un PDF valido
file /tmp/f24-test.pdf
ls -la /tmp/f24-test.pdf

# Verifica campi compilati nel PDF
python3 -c "
import fitz
doc = fitz.open('/tmp/f24-test.pdf')
page = doc[0]
for widget in page.widgets():
if widget.field_value:
print(f'{widget.field_name}: {widget.field_value}')
"

Verifica che:
- Il PDF sia generato senza errori
- I campi contribuente siano compilati
  con i dati del PM
- Codice tributo = 1919
- Importo debito corrisponda al totale
  del F24 record

Riporta output build, dimensione PDF
e valori dei campi compilati.