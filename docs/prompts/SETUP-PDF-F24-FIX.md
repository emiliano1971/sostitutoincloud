Leggi il file CLAUDE.md e i file esistenti:
- service/F24PdfService.java
- src/main/resources/application.yml
  prima di procedere.

Due fix a F24PdfService:
1. Importo centesimi mancanti
2. Template PDF da storage esterno
   con fallback classpath

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. FIX CENTESIMI
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In service/F24PdfService.java trova il
metodo che formatta BigDecimal → String
per i campi importo e correggilo:

Aggiungi metodo privato:
private String formattaImporto(
BigDecimal val) {
if (val == null ||
val.compareTo(BigDecimal.ZERO) == 0)
return "";
return String.format("%.2f", val)
.replace(".", ",");
}

private String formattaCent(BigDecimal val) {
if (val == null) return "00";
// estrae solo i centesimi su 2 cifre
int cent = val.remainder(BigDecimal.ONE)
.multiply(new BigDecimal("100"))
.abs()
.setScale(0, RoundingMode.HALF_UP)
.intValue();
return String.format("%02d", cent);
}

private String formattaEuro(BigDecimal val) {
if (val == null) return "0";
return String.valueOf(val.toBigInteger());
}

Usa questi metodi nella costruzione
di F24PdfDataDTO:
- motivoImportoDebito = formattaImporto(
  f24Record.getTotalAmount())
  → "104,00"
- motivoImportoCredito = ""
- saldoFinaleEuro = formattaEuro(
  f24Record.getTotalAmount())
  → "104"
- saldoFinaleCent = formattaCent(
  f24Record.getTotalAmount())
  → "00"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. TEMPLATE DA STORAGE ESTERNO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In application.yml (base comune) aggiungi
sotto la sezione app.storage già esistente:

app:
storage:
base-path: /opt/sostitutoincloud/storage
pdf-path: /opt/sostitutoincloud/storage/pdf
templates-path: /opt/sostitutoincloud/storage/templates

In application-local.yml aggiungi override:

app:
storage:
base-path: /tmp/sostitutoincloud/storage
pdf-path: /tmp/sostitutoincloud/storage/pdf
templates-path: /tmp/sostitutoincloud/storage/templates

In service/F24PdfService.java:

Aggiungi @Value per il path templates:
@Value("${app.storage.templates-path:}")
private String templatesStoragePath;

Sostituisci il caricamento del template
con questo metodo privato:

private byte[] loadTemplate(
String filename) throws IOException {

// 1. Prova storage esterno
if (templatesStoragePath != null
&& !templatesStoragePath.isBlank()) {
File external = new File(
templatesStoragePath + "/" + filename);
if (external.exists()
&& external.isFile()) {
log.info("F24PdfService: template "
+ "da storage esterno: {}",
external.getAbsolutePath());
return Files.readAllBytes(
external.toPath());
}
}

// 2. Fallback classpath
//    (sviluppo locale / primo avvio)
String classpathPath =
"/templates/" + filename;
try (InputStream is = getClass()
.getResourceAsStream(classpathPath)) {
if (is == null) {
throw new IllegalStateException(
"Template F24 non trovato né in "
+ "storage (" + templatesStoragePath
+ ") né in classpath ("
+ classpathPath + ")");
}
log.warn("F24PdfService: template "
+ "da classpath (fallback): {}",
classpathPath);
return is.readAllBytes();
}
}

Usa loadTemplate() nel metodo generaPdf():
byte[] templateBytes = loadTemplate(
"f24-semplificato-acroform.pdf");
// poi: Loader.loadPDF(templateBytes)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. README-INSTALL.md — nota template
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi in README-INSTALL.md nella
sezione "Deploy su Coolify" una nota:

### Template PDF
Copiare i template PDF nella directory
storage del container:
/opt/sostitutoincloud/storage/templates/
└── f24-semplificato-acroform.pdf

Se la directory non esiste o il file
non è presente, il sistema usa il
template incluso nel WAR come fallback.
Per aggiornare il template F24 (es. nuova
versione AdE) sostituire il file nella
directory senza necessità di rebuild.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/f24/5/pdf" \
--output /tmp/f24-fix.pdf

python3 -c "
import fitz
doc = fitz.open('/tmp/f24-fix.pdf')
text = doc[0].get_text()
# cerca importo e saldo
import re
matches = re.findall(
r'(104|Erario|1919|2026|,\d{2})',
text)
print('Valori trovati:', matches)
print()
print(text[text.find('Erario')-20:
text.find('Erario')+100]
if 'Erario' in text else 'Erario non trovato')
"

Verifica che:
- importo_debito mostri "104,00"
- saldo_finale_euro = "104"
- saldo_finale_cent = "00"
- log mostri "template da classpath
  (fallback)" in locale
  (perché /tmp/sostitutoincloud/storage/
  templates/ non esiste ancora)

Riporta output build e valori trovati
nel PDF.