Leggi il file CLAUDE.md e i file esistenti:
- service/SdiXmlService.java
- dao/FiscalDocumentDAO.java
- src/main/resources/application.yml
  prima di procedere.

Implementa:
1. Spostamento fattura XML in elaborati
   al momento della generazione
2. Servizio di lettura risposte SDI
   da incoming/ con aggiornamento stato
   e archiviazione in elaborati/

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. application.yml — aggiungi path elaborati
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi in application.yml sotto app.storage:

sdi-elaborati-path: /opt/sostitutoincloud/storage/sdi/elaborati

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. SdiXmlService — sposta fattura in elaborati
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi @Value:
@Value("${app.storage.sdi-elaborati-path}")
private String sdiElaboratiPath;

Modifica generaEInvia() dopo il salvataggio
del file XML in outgoing/:

Crea la cartella elaborati per questo invio:
Path elaboratiDir = Path.of(
sdiElaboratiPath,
tenantPiva,          ← es. "12345678901"
progressivo)         ← es. "00001"
Files.createDirectories(elaboratiDir)

Copia il file XML della fattura in elaborati:
Path elaboratiFile = elaboratiDir
.resolve(nomeFile)
Files.copy(filePath, elaboratiFile,
StandardCopyOption.REPLACE_EXISTING)

NON rimuovere il file da outgoing/ —
il tunnel SDI deve ancora leggerlo.
Il file in outgoing/ viene rimosso
dal servizio di risposta dopo
che la risposta è arrivata.

Aggiorna sdi_file_path su fiscal_document
con il percorso in elaborati (non outgoing):
fiscalDocumentDAO.updateSdiInfo(
fiscalDocumentId,
progressivo,
elaboratiFile.toString())  ← path elaborati

Log INFO "SdiXmlService: fattura copiata
in elaborati: {}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. SdiRispostaService — nuovo servizio
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/SdiRispostaService.java:
- @Service @Log4j2

@Value("${app.storage.sdi-incoming-path}")
private String sdiIncomingPath;

@Value("${app.storage.sdi-outgoing-path}")
private String sdiOutgoingPath;

@Value("${app.storage.sdi-elaborati-path}")
private String sdiElaboratiPath;

Costruttore con FiscalDocumentDAO,
AuditService.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Metodo principale:
SdiElaborazioneResultDTO elaboraRisposte()
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/sdi/SdiElaborazioneResultDTO.java:
- @Data @Builder
- int elaborati
- int accettati
- int scartati
- int metadati
- int errori
- List<String> dettagli

Logica elaboraRisposte():

1. Leggi tutti i file *.xml in incoming/:
   Files.list(Path.of(sdiIncomingPath))
   .filter(p -> p.toString()
   .endsWith(".xml"))
   .sorted()  ← elabora in ordine

2. Per ogni file:

   a) Parsa il nome file per estrarre:
   formato: IT{PIVA}_{PROG}_{ESITO}_{N}.xml
    - piva = parte dopo "IT" fino a "_"
    - progressivo = parte dopo primo "_"
      fino al secondo "_"
    - esito = parte dopo secondo "_"
      fino al terzo "_" (RC/NS/MC/MT)

   Se il nome non matcha il formato:
   log WARN e salta il file.

   b) Leggi il contenuto XML con
   DocumentBuilderFactory (no validazione
   firma — la firma è già verificata
   dal tunnel SDI):

   String xml = Files.readString(
   filePath, StandardCharsets.UTF_8)
   DocumentBuilder db =
   DocumentBuilderFactory.newInstance()
   .newDocumentBuilder()
   Document doc = db.parse(
   new InputSource(
   new StringReader(xml)))
   doc.getDocumentElement().normalize()

   c) Estrai NomeFile dal XML:
   String nomeFileFattura =
   doc.getElementsByTagName("NomeFile")
   .item(0).getTextContent()
   ← es. "IT12345678901_00001.xml"

   Estrai progressivo dal NomeFile:
   String progFattura = nomeFileFattura
   .replace(".xml","")
   .split("_")[1]
   ← es. "00001"

   Estrai piva dal NomeFile:
   String pivaFattura = nomeFileFattura
   .split("_")[0]
   .substring(2)
   ← es. "12345678901"

   d) Cerca fiscal_document per
   sdi_progressivo = progFattura:
   fiscalDocumentDAO
   .findBySdiProgressivo(progFattura)

   Se non trovato:
   log WARN "fiscal_document non trovato
   per progressivo {}" e salta

   e) Determina esito e aggiorna stato:

   SE esito = "RC":
   → stato_documento = 'accepted'
   → sdi_error_msg = null
   Estrai DataOraConsegna dal XML:
   doc.getElementsByTagName(
   "DataOraConsegna")
   Aggiorna fiscal_document con
   updateSdiAccepted(id, dataConsegna)

   SE esito = "NS":
   → stato_documento = 'rejected'
   Estrai errori da ListaErrori:
   NodeList errori = doc
   .getElementsByTagName("Errore")
   StringBuilder msg = new StringBuilder()
   for each errore:
   codice = errore
   .getElementsByTagName("Codice")
   .item(0).getTextContent()
   descrizione = errore
   .getElementsByTagName("Descrizione")
   .item(0).getTextContent()
   msg.append("[" + codice + "] "
   + descrizione + "; ")
   Aggiorna con updateSdiError(
   id, msg.toString())

   SE esito = "MC":
   → stato_documento = 'error'
   → sdi_error_msg =
   "Mancata consegna SDI"
   Aggiorna con updateSdiError(
   id, "Mancata consegna SDI")

   SE esito = "MT":
   → nessun aggiornamento stato
   Log INFO "MT ricevuto per {}"

   f) Archivia in elaborati/:
   Path elaboratiDir = Path.of(
   sdiElaboratiPath,
   pivaFattura,
   progFattura)
   Files.createDirectories(elaboratiDir)

   Copia risposta in elaborati:
   Files.copy(filePath,
   elaboratiDir.resolve(
   filePath.getFileName()),
   StandardCopyOption.REPLACE_EXISTING)

   SE esito != "MT":
   Rimuovi file originale da outgoing/
   (il file della fattura inviata):
   Path outgoingFile = Path.of(
   sdiOutgoingPath,
   nomeFileFattura)
   if (Files.exists(outgoingFile)):
   Files.delete(outgoingFile)

   Rimuovi file risposta da incoming/:
   Files.delete(filePath)

   g) Audit:
   auditService.log(
   "sdi.risposta." + esito.toLowerCase(),
   "FiscalDocument", docId,
   "Ricevuto esito SDI " + esito
   + " per progressivo " + progFattura)

   h) Aggiorna contatori result DTO

3. Log INFO "SdiRispostaService
   .elaboraRisposte() - elaborati={}
   accettati={} scartati={} errori={}"

4. Return SdiElaborazioneResultDTO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FiscalDocumentDAO — nuovi metodi
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/FiscalDocumentDAO.java:

Optional<FiscalDocument>
findBySdiProgressivo(String progressivo)
- SELECT * FROM fiscal_document
  WHERE sdi_progressivo = ?
  LIMIT 1
- Log DEBUG

void updateSdiAccepted(Integer id,
String dataConsegna)
- UPDATE fiscal_document
  SET fk_stato_documento_id =
  (SELECT id FROM stato_documento
  WHERE codice = 'accepted'),
  sdi_error_msg = NULL,
  updated_at = NOW()
  WHERE id = ?
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. SdiController — endpoint manuale
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea controller/SdiController.java:

POST /api/sdi/elabora-risposte
- Solo ROLE_TENANT_ADMIN o ROLE_SUPER_ADMIN
- chiama sdiRispostaService.elaboraRisposte()
- ResponseEntity.ok(result)
- Log INFO "SdiController
  .elaboraRisposte() chiamato"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. FRONTEND — pulsante in DocumentsList
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/documentApi.ts:

export async function elaboraRisposteSdi():
Promise<SdiElaborazioneResult>
// POST /api/sdi/elabora-risposte

interface SdiElaborazioneResult {
elaborati: number
accettati: number
scartati: number
metadati: number
errori: number
dettagli: string[]
}

In frontend/src/pages/tenant/DocumentsList.tsx
aggiungi pulsante "🔄 Verifica risposte SDI"
accanto al filtro stato:
- onClick → elaboraRisposteSdi()
- spinner durante elaborazione
- toast con risultato:
  "Elaborati {N}: {accettati} accettati,
  {scartati} scartati"
- se dettagli.length > 0 mostra lista
  errori in un dialog
- dopo elaborazione ricarica lista
  documenti

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. FILE FITTIZI PER TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea i file di test fittizi basandoti
sugli esempi reali forniti (struttura
identica ai file ITCOV1234567890_*):

Crea /opt/sostitutoincloud/storage/
sdi/incoming/
IT12345678901_00008_RC_001.xml
con struttura RicevutaConsegna:
<NomeFile>IT12345678901_00008.xml</NomeFile>
<IdentificativoSdI>99999001</IdentificativoSdI>
<DataOraRicezione>2026-08-01T10:00:00.000+01:00</DataOraRicezione>
<DataOraConsegna>2026-08-01T10:05:00.000+01:00</DataOraConsegna>
<Destinatario><Codice>0000000</Codice></Destinatario>
<MessageId>999001</MessageId>

Crea IT12345678901_00007_NS_001.xml
con struttura RicevutaScarto:
<NomeFile>IT12345678901_00007.xml</NomeFile>
<ListaErrori>
<Errore>
<Codice>00300</Codice>
<Descrizione>Test errore fittizio
</Descrizione>
<Suggerimento>Test suggerimento
</Suggerimento>
</Errore>
</ListaErrori>

Per creare i file fittizi usa i file
XML reali come template (struttura
identica, sostituendo solo i valori).
NON includere la firma digitale
(ds:Signature) nei file fittizi —
non viene verificata.

IMPORTANTE: il progressivo nei file
fittizi (00008, 00007) deve corrispondere
a fiscal_document esistenti con
sdi_progressivo valorizzato nel DB.
Verifica quale progressivo esiste:
SELECT id, document_number,
sdi_progressivo
FROM fiscal_document
WHERE sdi_progressivo IS NOT NULL
ORDER BY id;
E usa quei valori nei file fittizi.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

# Crea directory se non esistono
mkdir -p /opt/sostitutoincloud/storage/\
sdi/incoming
mkdir -p /opt/sostitutoincloud/storage/\
sdi/elaborati

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Verifica stato prima
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/documents" \
| python3 -m json.tool \
| grep -E '"documentNumber"|"statoDocumento"\
|"sdiProgressivo"'

# Elabora risposte
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/sdi/elabora-risposte" \
| python3 -m json.tool

# Verifica stato dopo
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/documents" \
| python3 -m json.tool \
| grep -E '"documentNumber"|"statoDocumento"'

# Verifica struttura elaborati
find /opt/sostitutoincloud/storage/\
sdi/elaborati/ -type f

Verifica che:
- RC → fiscal_document stato = 'accepted'
- NS → fiscal_document stato = 'rejected'
  con sdi_error_msg valorizzato
- File risposta spostato in elaborati/
  {piva}/{progressivo}/
- File fattura rimosso da outgoing/
- incoming/ vuota dopo elaborazione

Riporta output build, curl e struttura
directory elaborati.