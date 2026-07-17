Leggi il file CLAUDE.md e docs/db/schema-target.sql
prima di procedere.

Correggi la logica fiscale dei documenti
seguendo lo schema Barbagallo.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. ALTER TABLE sul DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Esegui sul DB:

psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -c "
ALTER TABLE fiscal_document
ADD COLUMN IF NOT EXISTS
fk_documento_collegato_id INTEGER
REFERENCES fiscal_document(id)
ON DELETE SET NULL,
ADD COLUMN IF NOT EXISTS
canone_locazione DECIMAL(10,2);
"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. AGGIORNA SCHEMA E MODEL
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna docs/db/schema-target.sql:
- Aggiungi le 2 colonne a fiscal_document

Aggiorna model/FiscalDocument.java:
- Integer fkDocumentoCollegatoId
- BigDecimal canoneLocazione

Aggiorna dao/mapper/FiscalDocumentRowMapper.java:
- Aggiungi mapping per i 2 nuovi campi

Aggiorna dao/FiscalDocumentDAO.java:
- Aggiungi i 2 campi in SELECT_ALL e INSERT

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CORREGGI DocumentGenerationService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

La logica corretta secondo schema Barbagallo:

FATTURA PM (emessa PRIMA):
imponibile = ota + cleaning + pmFee
iva = imponibile * 0.22
totaleFattura = imponibile + iva
ritenuta = withholdingAmount
bollo = 0
canoneLocazione = null (non applicabile)

RICEVUTA OWNER (emessa DOPO la fattura PM):
- Cerca fattura PM già emessa per questo booking:
  fiscalDocumentDAO.findByBookingId(bookingId)
  filtra per tipo "fattura"
- Se fattura PM esiste:
  canone = gross_amount - fattura.totalAmount
- Se fattura PM NON esiste (ricevuta emessa prima):
  canone = ownerNetAmount (fallback)
- bollo = canone > 77.47 ? 2.00 : 0.00
- totaleRicevuta = canone + bollo
- ritenuta = canone * 0.21
- imponibile = canone
- canoneLocazione = canone (persisti nel documento)
- fkDocumentoCollegatoId = fattura.id (se trovata)

Aggiorna anche la FATTURA PM:
Dopo aver salvato la fattura, cerca ricevuta
già esistente per lo stesso booking e
aggiorna fkDocumentoCollegatoId della ricevuta
con l'id della fattura appena creata.
(Caso: ricevuta emessa prima della fattura)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. AGGIORNA DTO E FRONTEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna dto/document/DocumentDetailDTO.java:
- Integer fkDocumentoCollegatoId
- BigDecimal canoneLocazione

Aggiorna dto/document/FiscalDocumentSummaryDTO.java:
- Integer fkDocumentoCollegatoId
- BigDecimal canoneLocazione

Aggiorna frontend/src/api/documentApi.ts:
- Aggiungi a DocumentDetail:
  fkDocumentoCollegatoId?: number
  canoneLocazione?: number
- Aggiungi a FiscalDocumentSummary:
  fkDocumentoCollegatoId?: number
  canoneLocazione?: number

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. AGGIORNA ReceiptOwnerDialog.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In ReceiptOwnerDialog.tsx correggi il calcolo
del canone:

// Cerca fattura PM nei documenti associati
const fatturaPM = booking.documenti?.find(
d => d.tipoDocumento === 'fattura'
)

// Canone = gross - totale fattura PM
// oppure owner_net_amount come fallback
const canone = fatturaPM
? booking.gross_amount - fatturaPM.importoTotale
: (booking.owner_net_amount ?? 0)

const bollo = canone > 77.47 ? 2.00 : 0.00
const totale = canone + bollo
const ritenuta = canone * 0.21

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

Dopo il build testa con curl:
TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena"}' \
| python3 -c \
"import sys,json; \
print(json.load(sys.stdin)['token'])")

# Genera fattura PM per booking 2
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"bookingId": 2,
"tipoDocumento": "fattura_pm"}' \
http://localhost:8081/sostitutoincloud/\
api/documents/generate \
| python3 -m json.tool

# Poi genera ricevuta per booking 2
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"bookingId": 2,
"tipoDocumento": "ricevuta_owner"}' \
http://localhost:8081/sostitutoincloud/\
api/documents/generate \
| python3 -m json.tool

Verifica che:
- fattura.totalAmount = imponibile + IVA
- ricevuta.totalAmount = gross - fattura.totalAmount
- fattura.totalAmount + ricevuta.totalAmount
  = booking.grossAmount

Riporta output di entrambi i build e dei curl.