Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- service/F24Service.java
- dao/WithholdingLedgerDAO.java
- dao/F24RecordDAO.java
- model/WithholdingLedger.java
- dao/mapper/WithholdingLedgerRowMapper.java
- frontend/src/pages/tenant/F24List.tsx
  (dialog "Genera F24")
- frontend/src/api/f24Api.ts
  (o equivalente)
  prima di procedere.

Implementa la gestione dei crediti
d'imposta da NDC nel dialog "Genera F24"
con spezzamento riga per compensazione
parziale.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SCHEMA SPEZZAMENTO RIGA
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Compensazione parziale €20
su credito €44,79:

PRIMA:
id=42: credito_imposta
ritenuta_amount = -44,79
fk_f24_record_id = NULL
fk_ndc_id = {NDC_ID}
fk_ledger_origine_id = NULL

DOPO:
id=42 (residuo):
stato = 'credito_imposta'
ritenuta_amount = -24,79
fk_f24_record_id = NULL
fk_ndc_id = {NDC_ID}
fk_ledger_origine_id = NULL

id=43 (compensato):
stato = 'compensato'
ritenuta_amount = -20,00
fk_f24_record_id = {F24_ID}
fk_ndc_id = {NDC_ID}
fk_ledger_origine_id = 42
← traccia da dove è spezzato

Compensazione totale:
id=42:
stato = 'compensato'
ritenuta_amount = -44,79
fk_f24_record_id = {F24_ID}
fk_ndc_id = {NDC_ID}
← nessuna nuova riga

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MIGRATION DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica l'ultimo numero migration
e crea il file successivo.

-- Traccia da quale riga è stato
-- spezzato il credito
ALTER TABLE withholding_ledger
ADD COLUMN IF NOT EXISTS
fk_ledger_origine_id INTEGER
REFERENCES withholding_ledger(id)
ON DELETE SET NULL;

COMMENT ON COLUMN
withholding_ledger.fk_ledger_origine_id
IS 'Se valorizzato, questa riga è stata
creata per compensazione parziale di
fk_ledger_origine_id. La riga origine
viene aggiornata con il residuo.';

-- Aggiunge stato 'compensato'
-- a withholding_ledger se non esiste
-- (verifica prima i valori ammessi)

-- Nuove colonne su f24_record
-- per i campi credito nel PDF AcroForm
ALTER TABLE f24_record
ADD COLUMN IF NOT EXISTS
importo_credito DECIMAL(10,2)
DEFAULT 0;

ALTER TABLE f24_record
ADD COLUMN IF NOT EXISTS
codice_tributo_credito VARCHAR(10);

ALTER TABLE f24_record
ADD COLUMN IF NOT EXISTS
anno_credito INTEGER;

ALTER TABLE f24_record
ADD COLUMN IF NOT EXISTS
saldo_netto DECIMAL(10,2);

COMMENT ON COLUMN
f24_record.importo_credito IS
'Totale crediti compensati in questo F24.';
COMMENT ON COLUMN
f24_record.saldo_netto IS
'total_amount - importo_credito.
Importo effettivamente da versare.';

-- Codice tributo credito
-- configurabile per tenant
ALTER TABLE tenant_settings
ADD COLUMN IF NOT EXISTS
codice_tributo_credito VARCHAR(10)
NOT NULL DEFAULT '6782';

COMMENT ON COLUMN
tenant_settings.codice_tributo_credito
IS 'Codice tributo per crediti da NDC
nel modello F24. Default 6782
(recupero eccedenze locazioni brevi).';

Aggiorna schema-target.sql.
Esegui sul DB locale.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — model e DAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a model/WithholdingLedger.java:
Integer fkLedgerOrigineId

Aggiorna mapper e DAO:
SELECT, INSERT per fk_ledger_origine_id

Aggiungi a model/F24Record.java:
BigDecimal importoCredito
String codiceTributoCreditoImposta
Integer annoCredito
BigDecimal saldoNetto

Aggiorna F24RecordRowMapper e
F24RecordDAO (SELECT, UPDATE).

Aggiungi a model/TenantSettings.java:
String codiceTributoCreditoImposta

Aggiorna TenantSettingsDAO,
mapper e DTO.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — WithholdingLedgerDAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi metodo:

List<WithholdingLedger>
findCreditiDisponibili(
Integer tenantId)

SELECT wl.*,
ABS(wl.ritenuta_amount) AS importo_credito
FROM withholding_ledger wl
WHERE wl.stato = 'credito_imposta'
AND wl.fk_tenant_id = ?
ORDER BY wl.created_at ASC

← Le righe credito_imposta hanno
ritenuta_amount negativo.
Il residuo è ABS(ritenuta_amount)
perché ogni riga è già "atomica"
(le parziali vengono spezzate).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BACKEND — F24Service
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna F24GenerazioneResult
(o DTO equivalente):

List<CreditoDisponibileDTO> crediti

Crea dto/f24/CreditoDisponibileDTO.java:
Integer ledgerId
String ndcDocumentNumber
LocalDate ndcDataEmissione
BigDecimal importoCredito
← ABS(ritenuta_amount)
Integer annoRiferimento
← anno NDC emissione

In F24Service.genera():
Dopo aver calcolato ritenute
del periodo carica i crediti:

List<WithholdingLedger> crediti =
withholdingLedgerDAO
.findCreditiDisponibili(tenantId)

Per ogni credito carica la NDC
tramite fk_ndc_id per avere
documentNumber e dataEmissione.

result.setCrediti(creditiDTO)

Aggiungi metodo:

F24Record applicaCrediti(
Integer tenantId,
Integer utenteId,
Integer f24Id,
List<CreditoCompensazioneRequest>
compensazioni)

Crea dto/f24/
CreditoCompensazioneRequest.java:
Integer ledgerId
BigDecimal importoUsato

Logica applicaCrediti():

1. Carica F24 e verifica:
    - appartiene al tenant
    - stato != 'paid' e != 'sent'
      SE già pagato → throw
      "F24 già pagato: impossibile
      modificare i crediti"

2. Carica i crediti disponibili
   e valida:
   Per ogni CreditoCompensazioneRequest:
    - Carica riga ledger
    - Verifica stato='credito_imposta'
    - Verifica importoUsato > 0
    - Verifica importoUsato <=
      ABS(riga.getRitenutaAmount())

3. Verifica che Σimporti <=
   f24.getTotalAmount():
   SE Σimporti > totalAmount:
   throw "Il credito compensato
   (€X) supera le ritenute
   del periodo (€Y)"

4. Per ogni compensazione:

   SE importoUsato ==
   ABS(riga.getRitenutaAmount()):
   ← compensazione totale:
   aggiorna la riga esistente
   UPDATE withholding_ledger SET
   stato = 'compensato',
   fk_f24_record_id = f24Id,
   updated_at = NOW()
   WHERE id = ledgerId

   ALTRIMENTI:
   ← compensazione parziale:
   aggiorna riga origine con residuo
   UPDATE withholding_ledger SET
   ritenuta_amount =
   ritenuta_amount + importoUsato,
   ← es. -44,79 + 20 = -24,79
   updated_at = NOW()
   WHERE id = ledgerId

       ← crea nuova riga compensata
       INSERT INTO withholding_ledger (
         fk_tenant_id,
         fk_booking_id,
         fk_ndc_id,
         fk_f24_record_id,
         fk_ledger_origine_id,
         canone_locazione,
         ritenuta_amount,
         stato,
         created_by)
       VALUES (
         tenantId,
         riga.fkBookingId,
         riga.fkNdcId,
         f24Id,
         ledgerId,
         0,
         -importoUsato,
         'compensato',
         utenteId)

5. Determina anno riferimento:
   ← anno della NDC più recente
   tra quelle compensate
   Integer annoCredito =
   compensazioni.stream()
   .map(c -> caricaNdc(
   ledger.fkNdcId)
   .getDataEmissione()
   .getYear())
   .max(Integer::compareTo)
   .orElse(
   LocalDate.now().getYear())

6. Aggiorna F24:
   BigDecimal totaleCompensato =
   Σimporti (capped a totalAmount)
   BigDecimal saldoNetto =
   f24.getTotalAmount()
   .subtract(totaleCompensato)

   UPDATE f24_record SET
   importo_credito = totaleCompensato,
   codice_tributo_credito =
   tenant.getCodiceTributoCreditoImposta(),
   anno_credito = annoCredito,
   saldo_netto = saldoNetto,
   updated_at = NOW()
   WHERE id = f24Id

7. Rigenera PDF F24:
   f24PdfService.genera(f24Id)
   ← con i nuovi campi credito

8. Log INFO "F24Service.applicaCrediti()
    - f24={} totaleCompensato={}
      saldoNetto={}"

9. Return f24 aggiornato

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BACKEND — F24 PDF AcroForm
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In F24PdfService.java (o equivalente)
aggiorna la generazione del PDF AcroForm.

Verifica i nomi esatti dei campi
nel template F24 Semplificato leggendo
il file PDF con PDFBox:
- Stampa tutti i campi AcroForm
  per trovare i nomi corretti
  dei campi credito

Valorizza i campi credito quando
f24.importoCredito > 0:

← sezione "Importi a credito compensati"
motivo_importo_credito_1 =
f24.codiceTributoCreditoImposta
anno_credito_1 = f24.annoCredito
(formato: "YYYY")
importo_credito_1 =
f24.importoCredito
(formato: centesimi come altri campi
es. 4479 per €44,79)

← il totale da versare diventa
il saldo netto
totale_da_versare = f24.saldoNetto
invece di f24.totalAmount

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. BACKEND — Controller
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a F24Controller.java
(o dove sono gli endpoint F24):

POST /api/f24/{id}/crediti
- @RequestBody List<Credito
  CompensazioneRequest>
- tenantId, utenteId da SecurityUtils
- chiama f24Service.applicaCrediti()
- 200 F24Record aggiornato con PDF
- 400 se validazione fallisce
- 400 se F24 già pagato
- 404 se F24 non trovato
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. FRONTEND — f24Api.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi interfacce:

export interface CreditoDisponibile {
ledgerId: number
ndcDocumentNumber: string
ndcDataEmissione: string
importoCredito: number
annoRiferimento: number
}

export interface CreditoCompensazione {
ledgerId: number
importoUsato: number
}

Aggiorna F24GenerazioneResult:
crediti?: CreditoDisponibile[]

Aggiorna F24Record:
importoCredito?: number
codiceTributoCreditoImposta?: string
annoCredito?: number
saldoNetto?: number

Aggiungi funzione:
export async function applicaCrediti(
f24Id: number,
compensazioni: CreditoCompensazione[]
): Promise<F24Record>
// POST /api/f24/{id}/crediti

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. FRONTEND — F24List.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Nel dialog "Genera F24" dopo la
tabella RitenuteTable aggiungi
la sezione crediti se disponibili.

Stato locale:
const [compensazioni, setCompensazioni]
= useState<Record<number, string>>({})
← ledgerId → importo stringa

const totaleCompensato =
Object.entries(compensazioni)
.reduce((s, [, v]) =>
s + (parseFloat(v) || 0), 0)

const saldoNetto = Math.max(0,
risultato.totaleRitenute
- totaleCompensato)

SE risultato.crediti?.length > 0:

<div className="mt-4 border rounded-md
  p-3 space-y-3 bg-green-50/30">
  <h4 className="text-sm font-medium
    flex items-center gap-2
    text-green-800">
    Crediti d'imposta disponibili
    <span className="text-xs
      text-muted-foreground font-normal">
      (da note di credito)
    </span>
  </h4>

{risultato.crediti.map(c => (
<div key={c.ledgerId}
className="flex items-center
gap-3 text-sm">
<div className="flex-1">
<span className="font-medium">
{c.ndcDocumentNumber}
</span>
<span className="text-xs
text-muted-foreground ml-2">
{formatDate(c.ndcDataEmissione)}
— anno {c.annoRiferimento}
</span>
</div>
<span className="text-xs
text-muted-foreground w-24
text-right">
max €{formatAmount(
c.importoCredito)}
</span>
<Input
type="number"
placeholder="€"
value={compensazioni[
c.ledgerId] ?? ''}
onChange={e => {
const val = parseFloat(
e.target.value) || 0
const max = Math.min(
c.importoCredito,
risultato.totaleRitenute)
setCompensazioni({
...compensazioni,
[c.ledgerId]: val > max
? String(max)
: e.target.value
})
}}
className="w-28 h-7 text-xs"
min={0}
max={Math.min(
c.importoCredito,
risultato.totaleRitenute)}
step={0.01}
/>
</div>
))}

← Riepilogo in tempo reale
{totaleCompensato > 0 && (
<div className="border-t pt-2
space-y-1 text-sm mt-2">
<div className="flex justify-between
text-muted-foreground">
<span>Totale ritenute</span>
<span>
€{formatAmount(
risultato.totaleRitenute)}
</span>
</div>
<div className="flex justify-between
text-green-700">
<span>Credito compensato</span>
<span>
-€{formatAmount(
totaleCompensato)}
</span>
</div>
<div className="flex justify-between
font-medium border-t pt-1">
<span>Saldo da versare</span>
<span className={saldoNetto === 0
? "text-green-600"
: ""}>
€{formatAmount(saldoNetto)}
</span>
</div>
</div>
)}
</div>

Aggiorna handleConfermaF24()
o handleSalvaF24() per passare
i crediti dopo la generazione F24:

const handleSalvaF24 = async () => {
setLoading(true)
try {
← 1. Genera F24
const f24 = await generaF24({
mese, anno, tenantId })

    ← 2. Applica crediti se presenti
    const comp = Object.entries(
      compensazioni)
      .filter(([, v]) =>
        parseFloat(v) > 0)
      .map(([id, v]) => ({
        ledgerId: parseInt(id),
        importoUsato: parseFloat(v)
      }))

    if (comp.length > 0) {
      await applicaCrediti(
        f24.id, comp)
    }

    ← 3. Ricarica lista F24
    await ricaricaF24()
    setDialogOpen(false)
    toast({ title: "F24 generato" +
      (comp.length > 0
        ? " con crediti compensati"
        : "")
    })
} catch (e: any) {
toast({
title: "Errore",
description: e.message,
variant: "destructive"
})
} finally {
setLoading(false)
}
}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
9. FRONTEND — Impostazioni Tenant
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Nella pagina impostazioni tenant
sezione "Parametri Fiscali" aggiungi:

<div className="space-y-1">
  <Label>Codice tributo credito
    d'imposta</Label>
  <p className="text-xs
    text-muted-foreground">
    Codice tributo per i crediti
    da note di credito nel modello F24.
    Default: 6782
    (recupero eccedenze locazioni brevi)
  </p>
  <Input
    value={settings
      .codiceTributoCreditoImposta
      ?? '6782'}
    onChange={e => setSettings({
      ...settings,
      codiceTributoCreditoImposta:
        e.target.value
    })}
    maxLength={10}
    className="w-32"
  />
</div>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
10. BUILD E TEST
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

# Crea scenario di test:
# 1. Booking con fattura e ricevuta
# 2. F24 pagato con quella ritenuta
# 3. NDC → crea credito_imposta
# 4. Nuovo booking → nuova ritenuta
# 5. Genera F24 → vedi credito
# 6. Applica credito parziale
# 7. Verifica spezzamento riga

# Verifica crediti disponibili
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"mese": 11,
"anno": 2026
}' \
"http://localhost:8081/sostitutoincloud/\
api/f24/genera" \
| python3 -m json.tool \
| grep -A 30 '"crediti"'

# Applica credito parziale
# (usa importo < credito disponibile)
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '[{
"ledgerId": {LEDGER_ID},
"importoUsato": {IMPORTO_PARZIALE}
}]' \
"http://localhost:8081/sostitutoincloud/\
api/f24/{F24_ID}/crediti" \
| python3 -m json.tool

# Verifica spezzamento riga nel DB
psql -U sostitutoincloud \
-d sostitutoincloud -h localhost -c "
SELECT id, stato,
ritenuta_amount,
fk_f24_record_id,
fk_ndc_id,
fk_ledger_origine_id
FROM withholding_ledger
WHERE fk_tenant_id = 1
AND stato IN (
'credito_imposta', 'compensato')
ORDER BY id;"

# Verifica che nel prossimo F24
# il credito residuo appaia ancora
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"mese": 12,
"anno": 2026
}' \
"http://localhost:8081/sostitutoincloud/\
api/f24/genera" \
| python3 -m json.tool \
| grep -A 10 '"crediti"'

# Verifica PDF F24 con campo credito
# (scarica e verifica manualmente)

# Cleanup dati test

Verifica che:
- Crediti appaiono nel dialog F24
- Compensazione totale →
  riga aggiornata a 'compensato'
- Compensazione parziale →
  riga origine aggiornata con residuo
    + nuova riga 'compensato'
- Saldo F24 = ritenute - credito
- Residuo appare nel prossimo F24
- PDF F24 ha campi credito valorizzati
- F24 già pagato → 400

Riporta output build e curl.