Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- service/FiscalDocumentService.java
- service/DocumentGenerationService.java
- service/SdiXmlService.java
- service/DocumentPdfService.java
- service/WithholdingLedgerService.java
- dao/FiscalDocumentDAO.java
- dao/mapper/FiscalDocumentRowMapper.java
- model/FiscalDocument.java
- dto/fiscal/FiscalDocumentDetailDTO.java
- service/BookingService.java
  metodo toDetailDTO()
- dao/BookingSplitEconomicoDAO.java
  prima di procedere.

Implementa le note di credito (NDC)
per le fatture PM.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SCHEMA GENERALE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Una NDC storna parzialmente o totalmente
una fattura PM già emessa.

- tipo documento: 'nota_credito'
  (già in tipo_documento)
- document_number: NC-YYYY-NNNN
  progressivo separato per leggibilità
- progressivo SDI: stesso sdi_progressivo
  globale (incrementa come le fatture)
- fk_documento_collegato_id: la fattura
  originale
- tipo SDI: TD04
- Stato booking dopo NDC emessa:
  'stornata'
- Annullamento NDC: solo se non inviata
  allo SDI → booking torna 'doc_issued'

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MIGRATION DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica l'ultimo numero migration
e crea il file successivo.

CREATE TABLE fiscal_document_riga_ndc (
id                    SERIAL PRIMARY KEY,
fk_fiscal_document_id INTEGER NOT NULL
REFERENCES fiscal_document(id)
ON DELETE CASCADE,
fk_tenant_id          INTEGER NOT NULL
REFERENCES tenant(id)
ON DELETE RESTRICT,
fk_split_economico_id INTEGER
REFERENCES booking_split_economico(id)
ON DELETE SET NULL,
← NULL per NDC totale o riga
senza riferimento a split
descrizione           VARCHAR(255)
NOT NULL,
importo_stornato      DECIMAL(10,2)
NOT NULL,
← positivo nel DB, negativo in fattura
imponibile_stornato   DECIMAL(10,2),
aliquota_iva          DECIMAL(5,2)
NOT NULL DEFAULT 0,
ordinamento           SMALLINT
NOT NULL DEFAULT 0,
created_at            TIMESTAMP
NOT NULL DEFAULT NOW(),
updated_at            TIMESTAMP
NOT NULL DEFAULT NOW(),
created_by            INTEGER
);

COMMENT ON TABLE fiscal_document_riga_ndc
IS 'Righe della nota di credito.
importo_stornato è positivo nel DB
e negativo nel documento fiscale.
fk_split_economico_id indica la riga
di booking_split_economico stornata,
NULL per storno totale o riga libera.';

CREATE INDEX idx_fdrndc_document
ON fiscal_document_riga_ndc(
fk_fiscal_document_id);

CREATE INDEX idx_fdrndc_tenant
ON fiscal_document_riga_ndc(
fk_tenant_id);

-- Trigger updated_at
CREATE TRIGGER trg_fdrndc_updated_at
BEFORE UPDATE ON fiscal_document_riga_ndc
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- Aggiungi stato 'stornata' a booking
-- se non già presente
-- Verifica se fk_stato_prenotazione_id
-- usa una lookup o una colonna testuale
-- e aggiungi il valore appropriato

-- Aggiungi contatore NDC al tenant
-- per il document_number NC-YYYY-NNNN
ALTER TABLE tenant_settings
ADD COLUMN IF NOT EXISTS
ndc_counter INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN
tenant_settings.ndc_counter IS
'Contatore progressivo per il
document_number delle note di credito.
Formato: NC-YYYY-NNNN';

Aggiorna schema-target.sql.
Esegui sul DB locale.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. MODEL, ROWMAPPER, DAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea model/FiscalDocumentRigaNdc.java:
Integer id
Integer fkFiscalDocumentId
Integer fkTenantId
Integer fkSplitEconomicoId  ← nullable
String descrizione
BigDecimal importoStornato
BigDecimal imponibileStornato
BigDecimal aliquotaIva
Integer ordinamento
LocalDateTime createdAt
LocalDateTime updatedAt
Integer createdBy

Crea dao/mapper/
FiscalDocumentRigaNdcRowMapper.java

Crea dao/FiscalDocumentRigaNdcDAO.java:

List<FiscalDocumentRigaNdc>
findByFiscalDocumentId(Integer docId)

List<FiscalDocumentRigaNdc>
findByBookingId(Integer bookingId)
← JOIN con fiscal_document
WHERE fk_booking_id = ?
AND tipo = 'nota_credito'
AND stato != 'annullata'

FiscalDocumentRigaNdc insert(
FiscalDocumentRigaNdc riga)

void deleteByFiscalDocumentId(
Integer docId)
← usato per annullamento NDC

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. DTO NDC
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/fiscal/
EmettNdcDTO.java:
Integer fkFiscalDocumentId
← la fattura da stornare
List<RigaNdcRequest> righe

Crea dto/fiscal/RigaNdcRequest.java:
Integer fkSplitEconomicoId
← nullable per NDC totale
String descrizione
← es. "Storno commissione OTA"
BigDecimal importoStornato
← positivo, <= importo riga originale
BigDecimal imponibileStornato
← nullable, calcolato se null

Crea dto/fiscal/RigaNdcDTO.java:
Integer id
Integer fkSplitEconomicoId
String descrizione
BigDecimal importoStornato
BigDecimal imponibileStornato
BigDecimal aliquotaIva
Integer ordinamento
← usato nel DTO split economico
con segno positivo

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. NdcService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/NdcService.java:
- @Service @Transactional @Log4j2
- Costruttore con:
  FiscalDocumentDAO,
  FiscalDocumentRigaNdcDAO,
  WithholdingLedgerService,
  BookingDAO,
  TenantSettingsDAO,
  SdiProgressivoDAO,
  BookingService,
  DocumentPdfService,
  SdiXmlService

Metodo principale:

FiscalDocument emettiNdc(
Integer tenantId,
Integer utenteId,
EmettNdcDTO dto)

Logica:

1. Carica fattura originale:
   FiscalDocument fattura =
   fiscalDocumentDAO.findById(
   dto.getFkFiscalDocumentId())
   Verifica:
    - appartiene al tenant
    - tipo = 'fattura_pm'
    - stato != 'annullata'
      SE già esiste una NDC attiva
      collegata → throw
      "Esiste già una nota di credito
      per questa fattura"

2. Valida le righe:
   Per ogni RigaNdcRequest:
    - importoStornato > 0
    - SE fkSplitEconomicoId valorizzato:
      carica la riga split e verifica
      che importoStornato <=
      riga.getImporto()
    - Calcola imponibileStornato se null:
      imponibile = importoStornato
      / (1 + aliquota/100)

3. Calcola totale NDC:
   BigDecimal totaleNdc =
   dto.getRighe().stream()
   .map(RigaNdcRequest
   ::getImportoStornato)
   .reduce(ZERO, BigDecimal::add)
   BigDecimal totaleImponibile =
   dto.getRighe().stream()
   .map(r -> r
   .getImponibileStornato())
   .reduce(ZERO, BigDecimal::add)

4. Genera document_number NDC:
   int counter =
   tenantSettingsDAO
   .incrementNdcCounter(tenantId)
   String docNumber = String.format(
   "NC-%d-%04d",
   LocalDate.now().getYear(),
   counter)

5. Genera progressivo SDI:
   String sdiProgressivo =
   sdiProgressivoDAO
   .nextProgressivo(tenantId)

6. Crea fiscal_document NDC:
   FiscalDocument ndc =
   FiscalDocument.builder()
   .fkTenantId(tenantId)
   .fkBookingId(fattura
   .getFkBookingId())
   .fkTipoDocumentoId(
   ← id di 'nota_credito')
   .fkDocumentoCollegatoId(
   fattura.getId())
   .documentNumber(docNumber)
   .sdiProgressivo(sdiProgressivo)
   .totalAmount(
   totaleNdc.negate())
   .vatAmount(totaleNdc
   .subtract(totaleImponibile)
   .negate())
   .imponibile(
   totaleImponibile.negate())
   .dataEmissione(LocalDate.now())
   .stato('emessa')
   .build()
   fiscalDocumentDAO.insert(ndc)

7. Crea righe NDC:
   int ordine = 10;
   for (RigaNdcRequest r :
   dto.getRighe()) {
   fiscalDocumentRigaNdcDAO
   .insert(FiscalDocumentRigaNdc
   .builder()
   .fkFiscalDocumentId(ndc.getId())
   .fkTenantId(tenantId)
   .fkSplitEconomicoId(
   r.getFkSplitEconomicoId())
   .descrizione(r.getDescrizione())
   .importoStornato(
   r.getImportoStornato())
   .imponibileStornato(
   r.getImponibileStornato())
   .aliquotaIva(
   ← dalla riga split se
   fkSplitEconomicoId
   valorizzato, altrimenti 22)
   .ordinamento(ordine)
   .createdBy(utenteId)
   .build())
   ordine += 10;
   }

8. Aggiorna withholding_ledger:
   BigDecimal ritenutaStornata =
   totaleImponibile
   .multiply(aliquotaRitenuta
   .divide(CENTO, 10, HALF_UP))
   .setScale(2, HALF_UP)
   .negate()

   SE f24 della ritenuta originale
   è già in stato 'pagato':
   ← crea riga withholding_ledger
   con tipo='credito_imposta'
   e importo negativo
   (compensabile nel prossimo F24)
   ALTRIMENTI:
   ← crea riga withholding_ledger
   con importo negativo
   (riduce il totale F24)

   withholdingLedgerService
   .registraStornoRitenuta(
   tenantId,
   fattura.getFkBookingId(),
   ndc.getId(),
   ritenutaStornata,
   f24Pagato)

9. Aggiorna stato booking:
   bookingDAO.updateStato(
   fattura.getFkBookingId(),
   tenantId,
   'stornata')

10. Genera PDF NDC
    documentPdfService.generaNdc(ndc)

11. Genera XML SDI (TD04):
    sdiXmlService.generaNdc(ndc)
    SE sdi_auto_send attivo:
    invia allo SDI

12. Log INFO "NdcService.emettiNdc()
    - tenant={} fattura={} ndc={}
      totale={}"

13. Return ndc

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. NdcService — annullamento
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

FiscalDocument annullaNdc(
Integer tenantId,
Integer ndcId)

1. Carica NDC e verifica:
    - appartiene al tenant
    - tipo = 'nota_credito'
    - stato != 'inviata_sdi'
      SE inviata → throw
      "Impossibile annullare NDC
      già inviata allo SDI"

2. Aggiorna stato NDC:
   fiscalDocumentDAO.updateStato(
   ndcId, 'annullata')

3. Cancella righe NDC:
   fiscalDocumentRigaNdcDAO
   .deleteByFiscalDocumentId(ndcId)

4. Storna withholding_ledger:
   cancella o annulla le righe
   create dalla NDC

5. Aggiorna stato booking:
   bookingDAO.updateStato(
   ndc.getFkBookingId(),
   tenantId,
   'doc_issued')
   ← la fattura originale
   torna attiva

6. Log INFO "NdcService.annullaNdc()
    - tenant={} ndc={}"

7. Return ndc aggiornata

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. WithholdingLedgerService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi metodo:

void registraStornoRitenuta(
Integer tenantId,
Integer bookingId,
Integer ndcId,
BigDecimal ritenutaStornata,
boolean f24Pagato)

Crea riga in withholding_ledger:
fk_booking_id = bookingId
fk_fiscal_document_id = ndcId
canone_locazione = importo netto
stornato (negativo)
ritenuta_amount = ritenutaStornata
(negativo)
SE f24Pagato:
stato = 'credito_imposta'
← compensabile nel prossimo F24
ALTRIMENTI:
stato = 'da_versare'
← riduce il totale F24

Aggiungi campo 'credito_imposta'
ai valori di stato withholding_ledger
se non già presente.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. DocumentPdfService — PDF NDC
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi metodo generaNdc() in
DocumentPdfService.java:

Stesso layout della fattura PM ma:
- Intestazione: "NOTA DI CREDITO"
  invece di "FATTURA"
- Riferimento: "A storno della
  fattura {fattura.documentNumber}
  del {fattura.dataEmissione}"
- Righe con importi negativi
- Totale con segno negativo

Riusa il template PDF esistente
aggiungendo la sezione riferimento
alla fattura originale.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. SdiXmlService — XML TD04
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi metodo generaNdc() in
SdiXmlService.java:

Stesso schema FatturaPA ma:
- TipoDocumento: TD04
- Aggiunge DatiFattureCollegate:
  <DatiFattureCollegate>
  <IdDocumento>
  {fattura.documentNumber}
  </IdDocumento>
  <Data>
  {fattura.dataEmissione}
  </Data>
  </DatiFattureCollegate>
- ImportoTotaleDocumento negativo
- Righe con PrezzoTotale negativo

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
9. BookingService.toDetailDTO()
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi al DTO dello split economico
le righe NDC attive per il booking:

List<RigaNdcDTO> righeNdc =
fiscalDocumentRigaNdcDAO
.findByBookingId(bookingId)

← Le righe NDC appaiono in coda
alle righe split con segno positivo
(riducono i costi PM)
← Contribuiscono al ricalcolo
di totalCostiPm e ownerNetAmount

Aggiungi a BookingDetailDTO:
List<RigaNdcDTO> righeNdc

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
10. Controller
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a FiscalDocumentController.java
(o crea NdcController.java):

POST /api/ndc
- @RequestBody EmettNdcDTO
- tenantId, utenteId da SecurityUtils
- chiama ndcService.emettiNdc()
- 201 FiscalDocument
- 400 se validazione fallisce
- 404 se fattura non trovata
- Log INFO

DELETE /api/ndc/{id}
- chiama ndcService.annullaNdc()
- 200 FiscalDocument aggiornata
- 400 se già inviata SDI
- 404 se NDC non trovata
- Log INFO

GET /api/ndc/{id}/pdf
- chiama documentPdfService
  .generaNdc()
- 200 application/pdf
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
11. FRONTEND — bookingApi.ts e tipi
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi interfacce:

export interface RigaNdc {
id: number
fkSplitEconomicoId?: number
descrizione: string
importoStornato: number
imponibileStornato?: number
aliquotaIva: number
ordinamento: number
}

export interface EmettNdcRequest {
fkFiscalDocumentId: number
righe: {
fkSplitEconomicoId?: number
descrizione: string
importoStornato: number
imponibileStornato?: number
}[]
}

Aggiungi a BookingDetail:
righeNdc?: RigaNdc[]

Aggiungi funzioni API:
emettiNdc(data: EmettNdcRequest)
→ POST /api/ndc
annullaNdc(id: number)
→ DELETE /api/ndc/{id}
downloadNdcPdf(id: number)
→ GET /api/ndc/{id}/pdf

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
12. FRONTEND — BookingDetail.tsx
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Nello Split Economico mostra le righe NDC
in coda alle righe split con segno +:

{booking.righeNdc?.map(r => (
  <div key={r.id}
    className="grid
      grid-cols-[2fr_auto_auto_auto]
      gap-x-3 text-sm py-1
      text-green-600">
    <div className="flex flex-col">
      <span>{r.descrizione}</span>
      <span className="text-xs
        text-muted-foreground">
        Storno NDC
      </span>
    </div>
    <span className="text-right text-xs">
      €{formatAmount(
        r.imponibileStornato ?? 0)}
    </span>
    <span className="text-right text-xs">
      -{formatAmount(
        r.importoStornato
        - (r.imponibileStornato ?? 0))}
    </span>
    <span className="text-right
      font-medium">
      +{formatAmount(r.importoStornato)}
    </span>
  </div>
))}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
13. FRONTEND — Dialog emissione NDC
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/components/booking/
NdcDialog.tsx:

Props:
booking: BookingDetail
fattura: FiscalDocumentDetail
open: boolean
onClose: () => void
onSuccess: (ndc: FiscalDocument)
=> void

Il dialog mostra:
- Intestazione: "Emetti Nota di Credito"
- Sottotitolo: "Storno di {fattura
  .documentNumber}"
- Lista righe della fattura con
  checkbox e input importo:

{righeFattura.map(r => (
  <div key={r.id}
    className="flex items-center
      gap-3 py-2 border-b">
    <Checkbox
      checked={righeSelezionate
        .has(r.id)}
      onCheckedChange={v =>
        toggleRiga(r.id, v)}
    />
    <span className="flex-1 text-sm">
      {r.descrizione}
    </span>
    <span className="text-xs
      text-muted-foreground">
      max €{formatAmount(r.importo)}
    </span>
    <Input
      type="number"
      value={importiStorno[r.id] ?? ''}
      onChange={e =>
        setImportoStorno(
          r.id, e.target.value)}
      disabled={!righeSelezionate
        .has(r.id)}
      placeholder="€ storno"
      className="w-24 h-7 text-xs"
      max={r.importo}
      min={0.01}
      step={0.01}
    />
  </div>
))}

Pulsante "Storna tutto" che precompila
tutti i checkbox e gli importi con
i valori originali della fattura.

Totale NDC calcolato in tempo reale.

Pulsante "Emetti NDC" → chiama
emettiNdc() → onSuccess.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
14. FRONTEND — DocumentsList e dettaglio
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In DocumentsList.tsx:
- Mostra le NDC nella lista con
  badge "NDC" e colore distintivo
- Pulsante "Annulla NDC" visibile
  se stato != 'inviata_sdi'

In DocumentDetail.tsx per le NDC:
- Mostra "Nota di Credito"
  nell'intestazione
- Link alla fattura originale
- Pulsante download PDF NDC
- Pulsante "Annulla NDC" se
  non inviata SDI

In DocumentDetail.tsx per le fatture:
- SE esiste una NDC collegata:
  mostra badge "Stornata parzialmente"
  o "Stornata totalmente"
  con link alla NDC

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
15. F24 — credito d'imposta
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In F24List.tsx nel dialog dettaglio F24:
SE esistono righe withholding_ledger
con stato='credito_imposta':

<div className="mt-4 p-3
  bg-green-50 rounded-md">
  <p className="text-sm font-medium
    text-green-800">
    Crediti d'imposta compensabili
  </p>
  {righeCredito.map(r => (
    <div key={r.id}
      className="flex justify-between
        text-xs text-green-700 mt-1">
      <span>
        NDC {r.ndcDocumentNumber}
        - {r.bookingExternalId}
      </span>
      <span>
        -{formatAmount(
          Math.abs(r.ritenutaAmount))}
      </span>
    </div>
  ))}
  <div className="flex justify-between
    text-sm font-medium
    text-green-800 mt-2 border-t pt-1">
    <span>Totale crediti</span>
    <span>
      -{formatAmount(totaleCrediti)}
    </span>
  </div>
</div>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
16. BUILD E TEST
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package
cd frontend && npm run build
npm run typecheck

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena2026"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Trova una fattura PM emessa
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/documents?tipo=fattura_pm" \
| python3 -m json.tool | head -30

# Emetti NDC totale
# (sostituisci {FATTURA_ID} con id reale)
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"fkFiscalDocumentId": {FATTURA_ID},
"righe": [
{
"descrizione":
"Storno commissione OTA",
"importoStornato": 55.00,
"fkSplitEconomicoId": {SPLIT_ID_OTA}
},
{
"descrizione":
"Storno pulizie",
"importoStornato": 61.00,
"fkSplitEconomicoId": {SPLIT_ID_PUL}
}
]
}' \
"http://localhost:8081/sostitutoincloud/\
api/ndc" \
| python3 -m json.tool

# Verifica stato booking → stornata
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings/{BOOKING_ID}" \
| python3 -m json.tool \
| grep "statoPrenotazione"

# Verifica righe NDC nello split
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings/{BOOKING_ID}" \
| python3 -m json.tool \
| grep -A 20 "righeNdc"

# Annulla NDC
curl -s -X DELETE \
-H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/ndc/{NDC_ID}" \
| python3 -m json.tool

# Verifica stato booking → doc_issued
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings/{BOOKING_ID}" \
| python3 -m json.tool \
| grep "statoPrenotazione"

# Cleanup se necessario

Verifica che:
- NDC creata con document_number
  NC-YYYY-0001
- Progressivo SDI incrementato
- Stato booking = stornata
- Righe NDC visibili nello split
  con segno positivo
- Annullamento NDC → booking
  torna doc_issued
- NDC inviata SDI → annullamento
  bloccato con 400

Riporta output build e curl.