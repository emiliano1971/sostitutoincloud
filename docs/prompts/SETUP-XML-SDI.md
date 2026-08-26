Leggi il file CLAUDE.md, docs/db/schema-target.sql
e i file:
- service/DocumentGenerationService.java
- dao/FiscalDocumentDAO.java
- src/main/resources/application.yml
  prima di procedere.

Implementa la generazione XML SDI per le
fatture PM (fattura_pm). Solo le fatture PM
vanno allo SDI — le ricevute owner sono
documenti interni tra PM e proprietario.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MIGRATION DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea docs/db/migrations/
010_fiscal_document_sdi_fields.sql:

ALTER TABLE fiscal_document
ADD COLUMN IF NOT EXISTS
sdi_progressivo VARCHAR(20),
ADD COLUMN IF NOT EXISTS
sdi_file_path   VARCHAR(500),
ADD COLUMN IF NOT EXISTS
sdi_sent_at     TIMESTAMP,
ADD COLUMN IF NOT EXISTS
sdi_error_msg   VARCHAR(500);

Crea anche la tabella per i progressivi SDI
(garantisce unicità per tenant+anno):

CREATE TABLE IF NOT EXISTS sdi_progressivo (
id              SERIAL PRIMARY KEY,
fk_tenant_id    INTEGER NOT NULL
REFERENCES tenant(id)
ON DELETE CASCADE,
anno            INTEGER NOT NULL,
ultimo_valore   INTEGER NOT NULL DEFAULT 0,
updated_at      TIMESTAMP NOT NULL
DEFAULT NOW(),
CONSTRAINT uq_sdi_progressivo
UNIQUE (fk_tenant_id, anno)
);

Esegui sul DB:
psql -U sostitutoincloud -d sostitutoincloud \
-h localhost \
-f docs/db/migrations/010_fiscal_document_sdi_fields.sql

Aggiorna docs/db/schema-target.sql.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. application.yml — path outgoing SDI
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In src/main/resources/application.yml
aggiungi sotto app.storage:

app:
storage:
base-path: /opt/sostitutoincloud/storage
pdf-path: /opt/sostitutoincloud/storage/pdf
templates-path: /opt/sostitutoincloud/storage/templates
sdi-outgoing-path: /opt/sostitutoincloud/storage/sdi/outgoing
sdi-incoming-path: /opt/sostitutoincloud/storage/sdi/incoming

In application-local.yml aggiungi override:
app:
storage:
sdi-outgoing-path: /opt/sostitutoincloud/storage/sdi/outgoing
sdi-incoming-path: /opt/sostitutoincloud/storage/sdi/incoming

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. SdiProgressivoDAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dao/SdiProgressivoDAO.java:

String getNextProgressivo(Integer tenantId,
Integer anno)
- Usa INSERT ... ON CONFLICT UPDATE
  per incrementare atomicamente:
  INSERT INTO sdi_progressivo
  (fk_tenant_id, anno, ultimo_valore)
  VALUES (?, ?, 1)
  ON CONFLICT (fk_tenant_id, anno)
  DO UPDATE SET
  ultimo_valore = sdi_progressivo
  .ultimo_valore + 1,
  updated_at = NOW()
  RETURNING ultimo_valore
- Converti il numero in stringa
  alfanumerica di 5 caratteri:
  formato base36 (0-9, A-Z)
  oppure più semplice: formato numerico
  zero-padded su 5 cifre es. "00001"
  → "00001", "00002", ... "99999"
- Log INFO "SdiProgressivoDAO
  .getNextProgressivo() - tenantId={}
  anno={} progressivo={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FiscalDocumentDAO — aggiorna campi SDI
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/FiscalDocumentDAO.java:

void updateSdiInfo(Integer id,
String sdiProgressivo,
String sdiFilePath)
- UPDATE fiscal_document
  SET sdi_progressivo = ?,
  sdi_file_path = ?,
  stato_documento = 'sent_sdi',
  sdi_sent_at = NOW(),
  updated_at = NOW()
  WHERE id = ?
- Log INFO "FiscalDocumentDAO
  .updateSdiInfo() - id={} prog={}"

void updateSdiError(Integer id,
String errorMsg)
- UPDATE fiscal_document
  SET stato_documento = 'error',
  sdi_error_msg = ?,
  updated_at = NOW()
  WHERE id = ?
- Log WARN

Aggiungi sdi_progressivo, sdi_file_path,
sdi_sent_at, sdi_error_msg al SELECT_ALL
e al RowMapper.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. SdiXmlService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/SdiXmlService.java:
- @Service @Log4j2
- Costruttore con FiscalDocumentDAO,
  BookingDAO, TenantDAO, TenantSettingsDAO,
  OwnerProfileDAO, PropertyDAO,
  SdiProgressivoDAO

@Value("${app.storage.sdi-outgoing-path}")
private String sdiOutgoingPath;

@Value("${app.storage.templates-path:}")
private String templatesPath;

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Metodo principale:
String generaEInvia(Integer tenantId,
Integer fiscalDocumentId)
returns: percorso file XML generato

Logica:

1. Carica fiscal_document e verifica:
    - appartiene al tenant
    - tipo = fattura_pm (solo fatture al SDI)
    - stato = 'ready' (non già inviato)
      Se stato già 'sent_sdi':
      throw IllegalStateException(
      "Fattura già inviata allo SDI")

2. Carica booking, tenant, tenant_settings,
   owner_profile, property

3. Genera progressivo:
   String progressivo =
   sdiProgressivoDAO.getNextProgressivo(
   tenantId,
   fiscalDocument.getIssueDate().getYear())

4. Nome file XML (formato AdE):
   IT{TENANT_CF_OR_VAT}_{PROGRESSIVO}.xml
   es. IT12345678901_00001.xml
   dove TENANT_CF_OR_VAT = partita IVA
   del tenant (senza spazi, uppercase)

5. Costruisci XML con JAXB o StringBuilder:
   Usa StringBuilder per semplicità —
   stesso approccio del codice esistente.

   Struttura XML (FatturaPA FPR12):

   <?xml version="1.0" encoding="UTF-8"?>
   <FatturaElettronica versione="FPR12"
   xmlns="http://ivaservizi.agenziaentrate
   .gov.it/docs/xsd/fatture/v1.2">

   <!-- HEADER -->
   <FatturaElettronicaHeader>
     <DatiTrasmissione>
       <IdTrasmittente>
         <IdPaese>IT</IdPaese>
         <IdCodice>{TENANT_PIVA}</IdCodice>
       </IdTrasmittente>
       <ProgressivoInvio>{PROGRESSIVO}
       </ProgressivoInvio>
       <FormatoTrasmissione>FPR12
       </FormatoTrasmissione>
       <CodiceDestinatario>
         SE guest_country = 'Italia'
           → "0000000"
         ALTRIMENTI
           → "XXXXXXX"
       </CodiceDestinatario>
       SE tenant_settings.pec != null:
       <PECDestinatario>{TENANT_PEC}
       </PECDestinatario>
     </DatiTrasmissione>

     <!-- CEDENTE/PRESTATORE = PM -->
     <CedentePrestatore>
       <DatiAnagrafici>
         <IdFiscaleIVA>
           <IdPaese>IT</IdPaese>
           <IdCodice>{TENANT_PIVA}</IdCodice>
         </IdFiscaleIVA>
         <CodiceFiscale>{TENANT_CF}
         </CodiceFiscale>
         <Anagrafica>
           SE tenant.legal_name != null:
             <Denominazione>{LEGAL_NAME}
             </Denominazione>
           ALTRIMENTI:
             <Nome>{NOME}</Nome>
             <Cognome>{COGNOME}</Cognome>
         </Anagrafica>
         <RegimeFiscale>
           {tenant_settings.regime_fiscale_pm
            ?? "RF01"}
         </RegimeFiscale>
       </DatiAnagrafici>
       <Sede>
         <Indirizzo>{TENANT_ADDRESS}
         </Indirizzo>
         <CAP>{TENANT_CAP ?? "00000"}</CAP>
         <Comune>{TENANT_CITY}</Comune>
         <Provincia>{TENANT_PROVINCE}
         </Provincia>
         <Nazione>IT</Nazione>
       </Sede>
     </CedentePrestatore>

     <!-- CESSIONARIO = OSPITE -->
     <CessionarioCommittente>
       <DatiAnagrafici>
         SE guest_country = 'Italia':
           <CodiceFiscale>{GUEST_TAX_CODE}
           </CodiceFiscale>
         ALTRIMENTI (straniero):
           SE guest_tax_code NOT LIKE 'EST%':
             <IdFiscaleIVA>
               <IdPaese>
                 {codice ISO nazione ospite
                  ?? "XX"}
               </IdPaese>
               <IdCodice>{GUEST_TAX_CODE}
               </IdCodice>
             </IdFiscaleIVA>
           ALTRIMENTI (CF fittizio EST...):
             nessun elemento CF
             (ospite straniero senza CF)
         <Anagrafica>
           SE guest_name contiene spazio:
             split in Nome/Cognome
           ALTRIMENTI:
             <Denominazione>{GUEST_NAME}
             </Denominazione>
         </Anagrafica>
       </DatiAnagrafici>
       <Sede>
         <Indirizzo>Via generica 1
         </Indirizzo>
         <CAP>
           SE italiano → "00000"
           SE straniero → "00000"
         </CAP>
         <Comune>{property.city}</Comune>
         SE italiano:
           <Provincia>
             {property.province ?? "RM"}
           </Provincia>
         <Nazione>
           SE guest_country = 'Italia' → "IT"
           ALTRIMENTI → "XX"
         </Nazione>
       </Sede>
     </CessionarioCommittente>
   </FatturaElettronicaHeader>

   <!-- BODY -->
   <FatturaElettronicaBody>
     <DatiGenerali>
       <DatiGeneraliDocumento>
         <TipoDocumento>TD01</TipoDocumento>
         <Divisa>EUR</Divisa>
         <Data>{issueDate yyyy-MM-dd}</Data>
         <Numero>{documentNumber}</Numero>
         SE bollo_amount > 0:
           <DatiBollo>
             <BolloVirtuale>SI</BolloVirtuale>
             <ImportoBollo>{bollo_amount 0.00}
             </ImportoBollo>
           </DatiBollo>
         <ImportoTotaleDocumento>
           {total_amount 0.00}
         </ImportoTotaleDocumento>
       </DatiGeneraliDocumento>
     </DatiGenerali>

     <DatiBeniServizi>
       <!-- Una riga per ogni voce
            della fattura PM -->
       SE fiscal_document ha righe
       in fiscal_document_line:
         per ogni riga:
         <DettaglioLinee>
           <NumeroLinea>{n}</NumeroLinea>
           <Descrizione>{descrizione}
           </Descrizione>
           <Quantita>1.00</Quantita>
           <PrezzoUnitario>{imponibile 0.00}
           </PrezzoUnitario>
           <PrezzoTotale>{imponibile 0.00}
           </PrezzoTotale>
           <AliquotaIVA>{aliquota 0.00}
           </AliquotaIVA>
           SE aliquota = 0:
             <Natura>
               {tenant_settings
                .natura_iva_esente ?? "N2.1"}
             </Natura>
         </DettaglioLinee>

       ALTRIMENTI (nessuna riga):
         genera una riga sintetica:
         <DettaglioLinee>
           <NumeroLinea>1</NumeroLinea>
           <Descrizione>Servizi di gestione
             locazione turistica breve
           </Descrizione>
           <Quantita>1.00</Quantita>
           <PrezzoUnitario>{imponibile 0.00}
           </PrezzoUnitario>
           <PrezzoTotale>{imponibile 0.00}
           </PrezzoTotale>
           <AliquotaIVA>22.00</AliquotaIVA>
         </DettaglioLinee>

       <DatiRiepilogo>
         <AliquotaIVA>22.00</AliquotaIVA>
         SE aliquota = 0:
           <Natura>N2.1</Natura>
         <ImponibileImporto>{imponibile 0.00}
         </ImponibileImporto>
         <Imposta>{iva_amount 0.00}</Imposta>
         <EsigibilitaIVA>I</EsigibilitaIVA>
       </DatiRiepilogo>
     </DatiBeniServizi>

     <DatiPagamento>
       <CondizioniPagamento>TP02
       </CondizioniPagamento>
       <DettaglioPagamento>
         <ModalitaPagamento>MP05
         </ModalitaPagamento>
         <ImportoPagamento>{total_amount 0.00}
         </ImportoPagamento>
       </DettaglioPagamento>
     </DatiPagamento>
   </FatturaElettronicaBody>
   </FatturaElettronica>

6. Crea directory se non esiste:
   Files.createDirectories(
   Path.of(sdiOutgoingPath))

7. Salva il file XML:
   Path filePath = Path.of(sdiOutgoingPath,
   nomeFile);
   Files.writeString(filePath, xml,
   StandardCharsets.UTF_8);

8. Aggiorna fiscal_document:
   fiscalDocumentDAO.updateSdiInfo(
   fiscalDocumentId,
   progressivo,
   filePath.toString())

9. Audit:
   auditService.log("sdi.genera",
   "FiscalDocument", fiscalDocumentId,
   "Generato XML SDI: " + nomeFile)

10. Log INFO "SdiXmlService.generaEInvia()
    - docId={} file={}"

11. Return filePath.toString()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. DocumentController — endpoint SDI
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/DocumentController.java:

POST /api/documents/{id}/sdi
- Solo fatture PM (tipo fattura_pm)
- tenantId da SecurityUtils
- chiama sdiXmlService.generaEInvia()
- ResponseEntity.ok(Map.of(
  "message", "File SDI generato",
  "filePath", result,
  "progressivo", doc.getSdiProgressivo()))
- catch IllegalStateException → 422
- catch NoSuchElementException → 404
- catch Exception → 500 con log ERROR
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. FRONTEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/documentApi.ts:

export async function inviaSdi(
id: number
): Promise<{
message: string,
filePath: string,
progressivo: string
}>
// POST /api/documents/{id}/sdi

In DocumentDetail.tsx:
- Se tipo = fattura_pm E stato = 'ready':
  mostra pulsante "📤 Invia SDI"
  onClick → inviaSdi(doc.id)
    * successo → toast "File SDI generato:
      {progressivo}" + ricarica documento
    * errore 422 → toast con messaggio
    * spinner durante invio

- Se stato = 'sent_sdi':
  mostra badge verde "Inviato SDI"
  con data invio (sdi_sent_at)
  e progressivo (sdi_progressivo)

- Se stato = 'error':
  mostra badge rosso "Errore SDI"
  con messaggio errore (sdi_error_msg)
  e pulsante "Riprova" che richiama
  inviaSdi() (il service permette retry
  solo se stato != 'sent_sdi')

Aggiorna DocumentListDTO e l'interfaccia
TypeScript con i nuovi campi:
sdiProgressivo?: string
sdiFilePath?: string
sdiSentAt?: string
sdiErrorMsg?: string

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

# Crea directory SDI
mkdir -p /opt/sostitutoincloud/storage/sdi/outgoing
mkdir -p /opt/sostitutoincloud/storage/sdi/incoming

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Genera XML SDI per fattura PM
# (usa l'id di una fattura PM esistente)
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/documents/{id_fattura_pm}/sdi" \
| python3 -m json.tool

# Verifica file generato
ls -la /opt/sostitutoincloud/storage/\
sdi/outgoing/

# Verifica XML
cat /opt/sostitutoincloud/storage/\
sdi/outgoing/*.xml | head -50

Verifica che:
- File XML generato nella directory outgoing
- Nome file formato IT{PIVA}_{PROG}.xml
- XML contiene CF tenant nel CedentePrestatore
- XML contiene CF ospite nel
  CessionarioCommittente (o IdFiscaleIVA
  per stranieri)
- stato_documento aggiornato a 'sent_sdi'
- sdi_progressivo e sdi_file_path salvati
  in fiscal_document

Riporta output build, nome file generato
e prime 50 righe XML.