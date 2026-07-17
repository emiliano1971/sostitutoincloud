Leggi il file CLAUDE.md e questi file:
- src/main/java/it/gavia/sostitutoincloud/
  service/WithholdingLedgerService.java
- src/main/java/it/gavia/sostitutoincloud/
  dao/WithholdingLedgerDAO.java
- src/main/java/it/gavia/sostitutoincloud/
  model/F24Record.java
- src/main/java/it/gavia/sostitutoincloud/
  dao/F24RecordDAO.java
- docs/db/schema-target.sql
  (tabelle f24_record e withholding_ledger)

Implementa la generazione del modello F24
mensile per il versamento delle ritenute.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
LOGICA DI BUSINESS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Il F24 aggrega tutte le ritenute
da_versare di un periodo (mese/anno)
e genera un versamento con:
- Codice tributo: 1919
- Scadenza: giorno 16 del mese successivo
- Importo: somma di tutte le ritenute
  del periodo

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. AGGIORNA F24RecordDAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Leggi F24RecordDAO.java e aggiungi
i metodi mancanti:

F24Record insert(F24Record record)
- INSERT con KeyHolder
- Usa Types.OTHER per colonne enum
  (stato)
- Log INFO: "F24RecordDAO.insert() -
  periodo={}/{} importo={}"

F24Record findById(Integer id)
- SELECT WHERE id = ?
- Lancia RuntimeException se non trovato

List<F24Record> findByTenant(
Integer tenantId)
- SELECT WHERE fk_tenant_id = ?
  ORDER BY periodo_anno DESC,
  periodo_mese DESC

F24Record updateStato(Integer id,
String stato, LocalDate paymentDate)
- UPDATE SET stato = ?,
  payment_date = ?,
  updated_at = NOW()
  WHERE id = ?
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. CREA F24Service
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/F24Service.java:
- @Service @Log4j2
- Costruttore con F24RecordDAO,
  WithholdingLedgerDAO,
  WithholdingLedgerService,
  AuditService

  F24GenerazioneResultDTO generaF24(
  Integer tenantId,
  Integer anno,
  Integer mese)

    1. Verifica che non esista già un F24
       per questo tenant/periodo:
       SELECT FROM f24_record
       WHERE fk_tenant_id = ?
       AND periodo_anno = ?
       AND periodo_mese = ?
       Se esiste → lancia IllegalStateException
       "F24 già generato per periodo {mese}/{anno}"

    2. Carica ritenute da_versare del periodo:
       withhholdingLedgerDAO
       .findByTenantAndPeriodo(
       tenantId, anno, mese)
       Filtra solo stato = "da_versare"

    3. Se nessuna ritenuta → lancia
       IllegalArgumentException
       "Nessuna ritenuta da versare per
       il periodo {mese}/{anno}"

    4. Calcola totale:
       BigDecimal totale = ritenute.stream()
       .map(WithholdingLedger::getRitenutaAmount)
       .reduce(BigDecimal.ZERO, BigDecimal::add)

    5. Calcola scadenza:
       deadline = LocalDate.of(anno, mese, 16)
       .plusMonths(1)
       ← giorno 16 del mese successivo

    6. Carica codice tributo 1919:
       cerca in tabella codice_tributo
       WHERE codice = '1919'
       (usa CodiceТributоDAO o query diretta)

    7. Crea e salva F24Record:
        - fkTenantId = tenantId
        - fkCodiceTributoId = id del 1919
        - periodoMese = mese
        - periodoAnno = anno
        - referenceYear = anno
        - totalAmount = totale
        - withholdingsCount = ritenute.size()
        - stato = "ready"
        - deadlineDate = deadline
        - period = String.format("%02d%d", mese, anno)
          ← formato "062026"

    8. Aggiorna ogni riga withholding_ledger:
        - stato → "versata"
        - fkF24RecordId → f24Record.getId()
          Per ognuna:
          withhholdingLedgerDAO.updateF24Record(
          riga.getId(), f24Record.getId())
          withhholdingLedgerDAO.updateStato(
          riga.getId(), "versata")

    9. Audit:
       auditService.log("f24.generate",
       "F24", f24Record.getId(),
       "Generato F24 periodo {mese}/{anno}
       importo €{totale}
       ({count} ritenute)")

    10. Restituisce F24GenerazioneResultDTO

  List<F24RecordDTO> findByTenant(
  Integer tenantId)
    - carica tutti gli F24 del tenant
    - mappa su F24RecordDTO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/fiscal/F24GenerazioneResultDTO.java:
- @Data @Builder @NoArgsConstructor
  @AllArgsConstructor
- Integer f24RecordId
- Integer periodoMese
- Integer periodoAnno
- BigDecimal totaleRitenute
- Integer numeroRitenute
- LocalDate scadenza
- String stato
- List<WithholdingLedgerDTO> ritenute

Crea dto/fiscal/F24RecordDTO.java:
- @Data @Builder @NoArgsConstructor
  @AllArgsConstructor
- Integer id
- Integer periodoMese
- Integer periodoAnno
- BigDecimal totalAmount
- Integer withholdingsCount
- String stato
- LocalDate deadlineDate
- LocalDate paymentDate
- String codiceTributo ← "1919"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea controller/F24Controller.java:
- @RestController @Log4j2
- @RequestMapping("/api/f24")
- tenantId = SecurityUtils.getCurrentTenantId()

  GET /api/f24
  → List<F24RecordDTO>
  → ResponseEntity.ok(result)

  POST /api/f24/genera
  @RequestBody: {
  "anno": 2026,
  "mese": 6
  }
  → ResponseEntity.status(201)
  .body(F24GenerazioneResultDTO)
  → gestisce IllegalStateException → 400
  → gestisce IllegalArgumentException → 400

  GET /api/f24/{id}
  → F24GenerazioneResultDTO con dettaglio
  ritenute collegate

  PATCH /api/f24/{id}/pagato
  → segna F24 come pagato
  → aggiorna stato → "paid"
  → aggiorna payment_date = today
  → ResponseEntity.ok(result)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. FRONTEND — f24Api.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/api/f24Api.ts:

```ts
export interface F24Record {
  id: number;
  periodoMese: number;
  periodoAnno: number;
  totalAmount: number;
  withholdingsCount: number;
  stato: string;
  deadlineDate: string;
  paymentDate?: string;
  codiceTributo: string;
}

export interface F24GenerazioneResult {
  f24RecordId: number;
  periodoMese: number;
  periodoAnno: number;
  totaleRitenute: number;
  numeroRitenute: number;
  scadenza: string;
  stato: string;
  ritenute: WithholdingLedgerItem[]
}

export interface WithholdingLedgerItem {
  id: number;
  ownerName: string;
  bookingExternalId: string;
  documentNumber: string;
  dataEvento: string;
  canoneLocazione: number;
  aliquotaRitenuta: number;
  ritenutaAmount: number;
  stato: string;
}

export async function getF24List():
  Promise<F24Record[]>
// GET /api/f24

export async function generaF24(
  anno: number, mese: number
): Promise<F24GenerazioneResult>
// POST /api/f24/genera

export async function getF24Detail(
  id: number
): Promise<F24GenerazioneResult>
// GET /api/f24/{id}

export async function marcaF24Pagato(
  id: number
): Promise<F24Record>
// PATCH /api/f24/{id}/pagato
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. PAGINA F24 — frontend
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/pages/tenant/F24List.tsx:

Layout:
- Header: "Modelli F24" + bottone
  "Genera F24" che apre un dialog

- Dialog "Genera F24":
    * Select mese (1-12)
    * Input anno (default anno corrente)
    * Bottone "Genera" → chiama generaF24()
    * Mostra risultato con totale e
      lista ritenute incluse

- Tabella F24:
  Colonne: Periodo, Codice Tributo,
  Importo, N° Ritenute, Scadenza,
  Stato, Pagato il, Azioni

  Stati con colori:
    * draft → grigio
    * ready → blu
    * sent → arancio
    * paid → verde
    * error → rosso

  Azioni per riga:
    * 👁 dettaglio → mostra ritenute
      collegate in un dialog
    * ✅ "Marca Pagato" → solo se stato
      = ready o sent

Aggiorna frontend/src/App.tsx:
- Aggiungi route /f24 → F24List

Aggiorna il menu laterale se esiste
un file di navigazione per aggiungere
la voce "F24".

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

Dopo riavvio Tomcat testa con curl:

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena"}' \
| python3 -c \
"import sys,json; \
print(json.load(sys.stdin)['token'])")

curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"anno": 2026, "mese": 6}' \
http://localhost:8081/sostitutoincloud/\
api/f24/genera \
| python3 -m json.tool

Verifica che:
- F24 generato con totale corretto
- Ritenute collegate
- Stato withholding_ledger → "versata"
- fk_f24_record_id popolato

NOTA: backend Tomcat e frontend Vite
sono già in esecuzione — non fermarli.
Dopo il build Maven le nuove classi
sono già in WEB-INF/classes/.
Serve riavvio Tomcat prima del test.

Riporta output di entrambi i build
e del curl.