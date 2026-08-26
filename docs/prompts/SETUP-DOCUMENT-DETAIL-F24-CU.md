Leggi il file CLAUDE.md e i file esistenti:
- service/FiscalDocumentService.java
- dto/document/DocumentDetailDTO.java
- dao/WithholdingLedgerDAO.java
- dao/CuRecordDAO.java
  prima di procedere.

Aggiungi al dettaglio documento fiscale
(ricevuta owner) le informazioni su F24
e CU collegate.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DTO — nuovi campi
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dto/document/DocumentDetailDTO.java:

// Info F24 (solo per ricevuta owner)
Integer f24RecordId          ← null se non ancora in F24
String  f24Periodo           ← es. "06/2026"
String  f24Stato             ← ready/paid/sent ecc.
Boolean f24Pagato            ← f24_record.stato = 'paid'

// Info CU (solo per ricevuta owner)
Integer cuRecordId           ← null se CU non generata
Integer cuTaxYear
String  cuStato              ← draft/generated/delivered/sent
Boolean cuConsegnata         ← cuStato in (delivered/sent)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. WithholdingLedgerDAO — nuovo metodo
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/WithholdingLedgerDAO.java:

Optional<WithholdingLedger>
findByFiscalDocumentId(Integer fiscalDocumentId)
- SELECT * FROM withholding_ledger
  WHERE fk_fiscal_document_id = ?
- Log DEBUG

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. FiscalDocumentService — popola i campi
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica service/FiscalDocumentService.java:

Aggiungi WithholdingLedgerDAO,
F24RecordDAO, CuRecordDAO al costruttore.

Nel metodo findById() dopo aver costruito
il DocumentDetailDTO, se il documento
è una ricevuta owner:

// Info F24
withholdingLedgerDAO
.findByFiscalDocumentId(doc.getId())
.ifPresent(wl -> {
if (wl.getFkF24RecordId() != null) {
F24Record f24 = f24RecordDAO
.findById(wl.getFkF24RecordId());
dto.setF24RecordId(f24.getId());
dto.setF24Periodo(String.format(
"%02d/%d",
f24.getPeriodoMese(),
f24.getPeriodoAnno()));
dto.setF24Stato(f24.getStato());
dto.setF24Pagato(
"paid".equals(f24.getStato()));
}
});

// Info CU
// Ricava owner e anno dalla ricevuta
Integer ownerId = doc.getFkOwnerId();
Integer anno = doc.getIssueDate().getYear();

cuRecordDAO
.findByTenantOwnerYear(
tenantId, ownerId, anno)
.ifPresent(cu -> {
dto.setCuRecordId(cu.getId());
dto.setCuTaxYear(cu.getTaxYear());
dto.setCuStato(cu.getStato());
dto.setCuConsegnata(
"delivered".equals(cu.getStato())
|| "sent".equals(cu.getStato()));
});

Log DEBUG "FiscalDocumentService:
f24RecordId={} cuRecordId={} per doc={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND — DocumentDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/tenant/
DocumentDetail.tsx:

SE tipo documento = ricevuta_owner
aggiungi dopo la card "Importi Fiscali"
due nuove card:

─── Card "Versamento F24" ────────────────
SE f24RecordId != null:
Badge stato F24 colorato:
ready   → grigio   "In attesa"
sent    → blu      "Inviato"
paid    → verde    "Pagato ✓"
Periodo: {f24Periodo}
Card cliccabile → navigate('/f24')
con evidenziazione del periodo
(o /f24?anno=X&mese=Y se supportato)

SE f24RecordId == null:
Badge grigio "Non ancora in F24"
testo: "La ritenuta non è ancora
stata inclusa in nessun versamento F24"

─── Card "Certificazione Unica" ──────────
SE cuRecordId != null:
Badge stato CU colorato:
draft     → grigio    "Bozza"
generated → blu       "Generata"
delivered → arancione "Consegnata"
sent      → verde     "Inviata AdE ✓"
Anno fiscale: {cuTaxYear}
Card cliccabile → navigate('/cu')

SE cuRecordId == null:
Badge grigio "Non in CU"
testo: "Nessuna CU generata per
l'anno {annoDocumento}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. TEST
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

# Dettaglio ricevuta owner
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/documents/{id_ricevuta}" \
| python3 -m json.tool \
| grep -E '"f24|"cu'

Verifica che:
- ricevuta con ritenuta in F24 →
  f24RecordId valorizzato e f24Stato
- ricevuta con CU generata →
  cuRecordId valorizzato e cuStato
- fattura PM → f24RecordId e cuRecordId
  restano null (non applicabili)

Riporta output build e curl.