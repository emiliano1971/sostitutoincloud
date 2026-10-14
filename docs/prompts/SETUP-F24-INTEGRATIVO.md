Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- service/F24Service.java
  metodo generaF24() e ricalcola()
- dao/F24RecordDAO.java
- model/F24Record.java
- dao/mapper/F24RecordRowMapper.java
- frontend/src/pages/tenant/F24List.tsx
- frontend/src/api/f24Api.ts
  prima di procedere.

Implementa la generazione di F24
integrativi per periodi già pagati
con ritenute orfane.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MIGRATION DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica l'ultimo numero migration
e crea il file successivo.

ALTER TABLE f24_record
ADD COLUMN IF NOT EXISTS
integrativo BOOLEAN
NOT NULL DEFAULT FALSE;

ALTER TABLE f24_record
ADD COLUMN IF NOT EXISTS
fk_f24_principale_id INTEGER
REFERENCES f24_record(id)
ON DELETE SET NULL;

COMMENT ON COLUMN
f24_record.integrativo IS
'TRUE se è un F24 integrativo
per un periodo già pagato.
L AdE somma i versamenti per
lo stesso periodo.';

COMMENT ON COLUMN
f24_record.fk_f24_principale_id IS
'FK al primo F24 del periodo
(quello principale). NULL se
questo è il principale.';

Aggiorna schema-target.sql.
Esegui sul DB locale.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — model e DAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a model/F24Record.java:
boolean integrativo
Integer fkF24PrincipaleId

Aggiorna F24RecordRowMapper.java
e F24RecordDAO.java:
SELECT, INSERT per i nuovi campi

Aggiungi a F24RecordDAO:

List<F24Record> findByTenantAndPeriodo(
Integer tenantId,
Integer mese,
Integer anno)
← restituisce tutti gli F24
del periodo (principale + integrativi)
ordinati per id ASC

F24Record findPrincipaleByPeriodo(
Integer tenantId,
Integer mese,
Integer anno)
← il primo F24 del periodo
(integrativo = false)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — F24Service.generaF24()
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna la logica di generaF24():

Carica tutti gli F24 del periodo:
List<F24Record> f24Esistenti =
f24RecordDAO.findByTenantAndPeriodo(
tenantId, mese, anno)

SE f24Esistenti è vuoto:
← nessun F24 per il periodo
← comportamento attuale invariato
← crea F24 normale

SE f24Esistenti non vuoto:
F24Record principale =
f24Esistenti.stream()
.filter(f -> !f.isIntegrativo())
.findFirst()
.orElse(f24Esistenti.get(0))

SE principale.stato != 'paid':
← F24 non ancora pagato
← ricalcola come prima
return ricalcola(principale,
tenantId, mese, anno)

SE principale.stato == 'paid':
← F24 già pagato
← verifica deadline

    LocalDate oggi = LocalDate.now()
    LocalDate deadline =
      principale.getDeadlineDate()

    SE deadline != null
      && oggi.isAfter(deadline):
      throw IllegalStateException(
        "Impossibile generare F24
         integrativo: scadenza del
         periodo " + mese + "/" + anno
         + " superata ("
         + deadline.format(
           DateTimeFormatter
             .ofPattern("dd/MM/yyyy"))
         + "). Contattare il
         commercialista.")

    ← Cerca ritenute orfane del periodo
    List<WithholdingLedger> orfane =
      withholdingLedgerDAO
        .findDaVersareByPeriodo(
          tenantId, mese, anno)
        .stream()
        .filter(w ->
          w.getFkF24RecordId() == null)
        .collect(Collectors.toList())

    SE orfane è vuoto:
      throw IllegalStateException(
        "Nessuna ritenuta da versare
         per il periodo " + mese
         + "/" + anno
         + ": tutte già incluse
         in un F24.")

    ← Calcola totale orfane
    BigDecimal totaleOrfane =
      orfane.stream()
        .map(WithholdingLedger
          ::getRitenutaAmount)
        .reduce(BigDecimal.ZERO,
          BigDecimal::add)

    ← Carica crediti disponibili
      dello stesso periodo
    List<WithholdingLedger> crediti =
      withholdingLedgerDAO
        .findCreditiDisponibili(tenantId)
        .stream()
        .filter(w ->
          w.getPeriodoMese() != null
          && w.getPeriodoMese() == mese
          && w.getPeriodoAnno() != null
          && w.getPeriodoAnno() == anno)
        .collect(Collectors.toList())

    ← Crea F24 integrativo
    F24Record integrativo =
      F24Record.builder()
        .fkTenantId(tenantId)
        .periodoMese(mese)
        .periodoAnno(anno)
        .totalAmount(totaleOrfane)
        .saldoNetto(totaleOrfane)
        .stato("ready")
        .integrativo(true)
        .fkF24PrincipaleId(
          principale.getId())
        .deadlineDate(
          principale.getDeadlineDate())
          ← stessa deadline del principale
        .build()

    integrativo = f24RecordDAO
      .insert(integrativo)

    ← Aggancia le ritenute orfane
    for (WithholdingLedger w : orfane):
      withholdingLedgerDAO
        .collegaAF24(w.getId(),
          integrativo.getId())

    ← Carica crediti disponibili
      nel risultato per il dialog
    List<CreditoDisponibileDTO>
      creditiDTO = crediti.stream()
        .map(this::toCreditoDTO)
        .collect(Collectors.toList())

    F24GenerazioneResult result =
      new F24GenerazioneResult()
    result.setF24(integrativo)
    result.setCrediti(creditiDTO)
    result.setTotaleRitenute(
      totaleOrfane)
    result.setIntegrativo(true)

    log INFO "F24Service.generaF24()
      - tenant={} periodo={}/{} 
      F24 integrativo {} creato
      con {} ritenute orfane
      totale={}"

    return result

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BACKEND — F24GenerazioneResult
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a F24GenerazioneResult.java
(o DTO equivalente):
boolean integrativo
← true se è stato creato
un F24 integrativo

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BACKEND — PDF F24 integrativo
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In F24PdfService.java
quando genera il PDF di un F24
integrativo aggiungi una nota
visibile:

SE f24.isIntegrativo():
← aggiungi testo nel PDF:
"VERSAMENTO INTEGRATIVO
al modello F24 principale
del periodo {mese}/{anno}"

Verifica se il template AcroForm
ha un campo note o descrizione
dove inserirlo, altrimenti
aggiungi il testo come overlay
con PDFBox.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. FRONTEND — f24Api.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna F24Record:
integrativo?: boolean
fkF24PrincipaleId?: number

Aggiorna F24GenerazioneResult:
integrativo?: boolean

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. FRONTEND — F24List.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

A) Lista F24 — badge "Integrativo":

Nella riga di ogni F24 integrativo
aggiungi badge accanto al periodo:

{f24.integrativo && (
<Badge variant="outline"
className="text-amber-600
border-amber-600 text-xs ml-1">
Integrativo
</Badge>
)}

B) Dialog "Genera F24" —
messaggio dopo generazione
integrativo:

SE result.integrativo:
toast({
title: "F24 integrativo creato",
description:
"Sono state incluse le ritenute
orfane del periodo " +
mese + "/" + anno +
". Stesso periodo del principale,
l'AdE somma i versamenti.",
duration: 6000
})

C) Dialog crediti — per F24
integrativo mostra i crediti
del periodo già selezionati
di default:

SE result.integrativo
&& result.crediti?.length > 0:
← precompila compensazioni
con l'importo massimo
compensabile per ogni credito
(min tra importoCredito
e totaleRitenute)

setCompensazioni(
Object.fromEntries(
result.crediti.map(c => [
c.ledgerId,
String(Math.min(
c.importoCredito,
result.totaleRitenute))
])
)
)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. FRONTEND — avviso emissione ricevuta
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In DocumentGenerationService.java
o nel controller di emissione
documenti, quando si emette una
ricevuta verifica se l'F24 del
periodo è già pagato:

Integer mese = issueDate.getMonthValue()
Integer anno = issueDate.getYear()
F24Record f24 =
f24RecordDAO.findPrincipaleByPeriodo(
tenantId, mese, anno)

SE f24 != null
&& "paid".equals(f24.getStato()):
← non bloccare, ma aggiungi
avviso nella risposta

Aggiorna il DTO di risposta
emissione documento per includere:
String avviso
← "Attenzione: l'F24 del periodo
{mese}/{anno} è già stato pagato.
La ritenuta sarà inclusa in un
F24 integrativo."

Nel frontend (BookingDetail.tsx
o DocumentGenerationService):
SE response.avviso:
toast({
title: "Attenzione",
description: response.avviso,
variant: "warning",
duration: 8000
})

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
9. BUILD E TEST
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

# Genera F24 integrativo ottobre
# (F24 35 è paid, ci sono ritenute
#  orfane 96 e 107)
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"mese": 10, "anno": 2026}' \
"http://localhost:8081/sostitutoincloud/\
api/f24/genera" \
| python3 -m json.tool \
| grep -E '"id|integrativo|\
totalAmount|stato|crediti"'

# Verifica ritenute agganciate
psql -U sostitutoincloud \
-d sostitutoincloud -h localhost -c "
SELECT id, stato, ritenuta_amount,
fk_f24_record_id
FROM withholding_ledger
WHERE id IN (95, 96, 107);"

# Verifica F24 in lista
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/f24" \
| python3 -m json.tool \
| grep -E '"id|periodo|integrativo|\
stato|totalAmount"'

# Testa deadline superata
# (imposta deadline nel passato
#  e riprova — deve dare 400)

# Cleanup se necessario

Verifica che:
- F24 integrativo creato con
  ritenute orfane di ottobre
- Badge "Integrativo" visibile
  in lista
- Crediti selezionati di default
  nel dialog
- Deadline superata → 400
- F24 principale paid non modificabile

Riporta output build e curl.