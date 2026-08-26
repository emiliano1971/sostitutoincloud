Leggi il file CLAUDE.md e i file esistenti:
- service/F24PdfService.java (come riferimento
  per lo stesso pattern)
- dao/CuRecordDAO.java
- dao/OwnerProfileDAO.java
- dao/PropertyDAO.java
  prima di procedere.

Implementa la generazione PDF della
Certificazione Unica (CU) compilando
i campi AcroForm del modello ufficiale AdE.
Stesso approccio già usato per F24PdfService.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
0. VERIFICA CAMPI ACROFORM
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima di tutto leggi i campi AcroForm
presenti nel template:

python3 -c "
import fitz
doc = fitz.open('/opt/sostitutoincloud/\
storage/templates/\
CU_modelloORDINARIO_2026.pdf')
for i, page in enumerate(doc):
for w in page.widgets():
print(f'P{i+1} | {w.field_name} | \
{w.field_type_string} | fs={w.text_fontsize}')
"

Riporta l'elenco completo dei campi trovati
prima di procedere con il codice.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DIPENDENZA MAVEN
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

PDFBox 3.0.3 è già presente nel pom.xml
(usato da F24PdfService) — nessuna aggiunta.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/pdf/CuPdfDataDTO.java:
- @Data @Builder @NoArgsConstructor
  @AllArgsConstructor

Dati sostituto (PM):
String sostitutoCf
String sostitutoDenominazione
String sostitutiIndirizzo
String sostitutiComune
String sostitutiProvincia
String sostitutiCap

Dati percipiente (proprietario):
String percipienteCf
String percipienteCognome
String percipientiNome
String percipientiDataNascitaGg
String percipientiDataNascitaMm
String percipientiDataNascitaAa
String percipientiSesso
String percipientiComuneNascita
String percipientiProvinciaNascita

Dati fiscali (pagina 14):
String lavAutoCausale      ← "B"
String lavAutoAnno         ← tax_year
String lavAutoAmmontareLordo ← total_compensi
String lavAutoImponibile   ← total_imponibile
String lavAutoRitenuteAcconto ← total_ritenute

Dati locazioni brevi (pagina 15):
List<CuLocazioneBreviDTO> locazioni
← max 5 (una per immobile del proprietario)

Crea dto/pdf/CuLocazioneBreviDTO.java:
- @Data @Builder @NoArgsConstructor
  @AllArgsConstructor
  String comune
  String codiceComuneBelfiore
  String importoCorrespettivo
  String ritenuta
  String codiceCin

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CuPdfService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/CuPdfService.java:
- @Service @Log4j2
- Costruttore con CuRecordDAO,
  OwnerProfileDAO, TenantDAO,
  TenantSettingsDAO, PropertyDAO,
  WithholdingLedgerDAO

@Value("${app.storage.templates-path:}")
private String templatesPath;

Metodo principale:
byte[] generaPdf(Integer tenantId,
Integer cuRecordId)

Logica:

1. Carica cu_record e verifica
   appartenenza tenant.
   Se stato = 'draft':
   throw IllegalStateException(
   "CU ancora in bozza")

2. Carica owner_profile, tenant,
   tenant_settings

3. Carica le property del proprietario
   per il tenant:
   propertyDAO.findByOwnerAndTenant(
   tenantId, cu.getFkOwnerId())
   Prendi max 5 property attive

4. Per ogni property carica le ritenute
   aggregate dell'anno da withholding_ledger:
   SELECT SUM(canone_locazione) AS importo,
   SUM(ritenuta_amount)  AS ritenuta
   FROM withholding_ledger
   WHERE fk_tenant_id = ?
   AND fk_owner_id = ?
   AND fk_booking_id IN (
   SELECT id FROM booking
   WHERE fk_property_id = ?)
   AND periodo_anno = ?
   Per ricavare codiceComuneBelfiore:
   cerca in comune_italiano WHERE
   LOWER(nome) = LOWER(property.city)

5. Costruisci CuPdfDataDTO con tutti
   i dati raccolti.
   Formatta importi con 2 decimali
   e virgola: stesso helper di F24PdfService.

6. Carica template PDF:
    - prima cerca in templatesPath/
      CU_modelloORDINARIO_2026.pdf
    - fallback classpath /templates/
      Stessa logica di loadTemplate()
      già in F24PdfService — estrai il metodo
      in una classe utilitaria condivisa
      PdfTemplateLoader se non esiste già,
      altrimenti duplica la logica.

7. Compila campi AcroForm con PDFBox:
    - Carica i campi dalla lista verificata
      al punto 0
    - Mappa CuPdfDataDTO → nomi campi
      AcroForm esatti trovati nel template
    - Per le locazioni brevi itera la lista
      e compila i campi loc_01_*, loc_02_*
      ecc. in base all'indice
    - Campi non valorizzati (es. loc_03_*
      se il proprietario ha solo 2 immobili)
      → lascia vuoti (non impostare nulla)
    - acroForm.flatten() prima del save

8. Log INFO "CuPdfService.generaPdf()
    - cuId={} ownerId={} bytes={}"

9. Return byte[]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. CuController — endpoint download PDF
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/CuController.java:

GET /api/cu/{id}/pdf
- tenantId da SecurityUtils
- chiama cuPdfService.generaPdf()
- ResponseEntity con headers:
  Content-Type: application/pdf
  Content-Disposition: attachment;
  filename="CU_{anno}_{ownerCf}.pdf"
- catch IllegalStateException → 400
- catch NoSuchElementException → 404
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. FRONTEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/cuApi.ts:

export async function downloadCuPdf(
id: number,
anno: number,
ownerName: string
): Promise<void>
// GET /api/cu/{id}/pdf
// fetch blob + download automatico
// filename = "CU_{anno}_{ownerName}.pdf"

In frontend/src/pages/tenant/CUList.tsx
aggiungi per ogni riga con stato
!= 'draft' un pulsante "⬇ PDF":
- onClick → downloadCuPdf(
  cu.id, cu.taxYear, cu.ownerName)
- spinner durante download
- toast errore se fallisce

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

# Scarica PDF CU
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/cu/4/pdf" \
--output /tmp/cu-test.pdf

file /tmp/cu-test.pdf
ls -la /tmp/cu-test.pdf

python3 -c "
import fitz
doc = fitz.open('/tmp/cu-test.pdf')
print('Pagine:', doc.page_count)
print(doc[2].get_text()[:300])
"

Verifica che:
- PDF generato senza errori
- Pagina 3 contiene CF sostituto
  e dati percipiente
- Pagina 14 contiene causale B,
  ammontare lordo e ritenute
- Pagina 15 contiene i dati
  degli immobili

Riporta output build, elenco campi
AcroForm trovati e testo estratto
dal PDF generato.