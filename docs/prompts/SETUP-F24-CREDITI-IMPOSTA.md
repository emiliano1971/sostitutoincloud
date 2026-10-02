Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- frontend/src/pages/tenant/F24List.tsx
  (dialog "Genera F24" e RitenuteTable)
- service/F24Service.java
- dao/F24RecordDAO.java
- dao/WithholdingLedgerDAO.java
- model/TenantSettings.java
- dao/TenantSettingsDAO.java
  prima di procedere.

Implementa la gestione dei crediti
d'imposta da NDC nel dialog
"Genera F24".

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MIGRATION DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica l'ultimo numero migration
e crea il file successivo.

-- Codice tributo credito configurabile
-- per tenant (default 6782 =
-- recupero eccedenze locazioni brevi)
ALTER TABLE tenant_settings
ADD COLUMN IF NOT EXISTS
codice_tributo_credito VARCHAR(10)
NOT NULL DEFAULT '6782';

COMMENT ON COLUMN
tenant_settings.codice_tributo_credito
IS 'Codice tributo da usare per i
crediti d imposta da NDC nel modello
F24. Default 6782 (recupero eccedenze
locazioni brevi). Anno di riferimento
calcolato automaticamente dall anno
di emissione della NDC.';

-- Collega withholding_ledger credito
-- all'F24 in cui viene compensato
ALTER TABLE withholding_ledger
ADD COLUMN IF NOT EXISTS
fk_f24_compensazione_id INTEGER
REFERENCES f24_record(id)
ON DELETE SET NULL;

COMMENT ON COLUMN
withholding_ledger.fk_f24_compensazione_id
IS 'F24 in cui il credito d imposta
è stato compensato. NULL se il credito
non è ancora stato usato.';

Aggiorna schema-target.sql.
Esegui sul DB locale.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — TenantSettings
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a model/TenantSettings.java:
String codiceTributoCreditoImposta

Aggiorna mapper e DAO per leggere
e scrivere codice_tributo_credito.

Aggiorna TenantSettingsService e DTO
per esporre e aggiornare il campo.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — WithholdingLedgerDAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi metodo:

List<WithholdingLedger>
findCreditiDisponibili(
Integer tenantId)
← righe con stato='credito_imposta'
AND fk_f24_compensazione_id IS NULL
ORDER BY created_at ASC

Aggiungi metodo:

void collegaCreditoAF24(
Integer ledgerId,
Integer f24Id,
BigDecimal importoUsato)
← UPDATE withholding_ledger SET
fk_f24_compensazione_id = f24Id,
importo_compensato = importoUsato,
stato = 'compensato',
updated_at = NOW()
WHERE id = ledgerId

Aggiungi colonna importo_compensato
a withholding_ledger:
ADD COLUMN IF NOT EXISTS
importo_compensato DECIMAL(10,2)
← quanto del credito è stato usato
(può essere < ritenuta_amount
se il credito supera le ritenute)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BACKEND — F24Service
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a F24GenerazioneResult
(o DTO equivalente restituito
da /api/f24/genera):

List<CreditoDisponibile> crediti
← crediti da NDC disponibili
per compensazione

Crea CreditoDisponibile DTO:
Integer ledgerId
String ndcDocumentNumber
← numero della NDC (NC-2026-0001)
LocalDate ndcDataEmissione
BigDecimal importoCredito
← Math.abs(ritenuta_amount)
Integer annoRiferimento
← anno della NDC

In F24Service.genera():
Dopo aver calcolato le ritenute
del periodo, carica i crediti
disponibili:

List<WithholdingLedger> crediti =
withholdingLedgerDAO
.findCreditiDisponibili(tenantId)

Mappa in CreditoDisponibile:
Per ogni riga credito:
← carica il documento NDC
per avere documentNumber
e dataEmissione
CreditoDisponibile.builder()
.ledgerId(riga.getId())
.ndcDocumentNumber(
ndc.getDocumentNumber())
.ndcDataEmissione(
ndc.getDataEmissione())
.importoCredito(
riga.getRitenutaAmount()
.abs())
.annoRiferimento(
ndc.getDataEmissione()
.getYear())
.build()

Aggiungi crediti al risultato:
result.setCrediti(creditiDTO)

Aggiungi metodo:

F24Record applicaCrediti(
Integer tenantId,
Integer f24Id,
List<CreditoCompensazioneRequest>
compensazioni)

Dove CreditoCompensazioneRequest:
Integer ledgerId
BigDecimal importoUsato
← <= importo del credito
← <= totale ritenute F24

Logica:

1. Carica F24 e verifica stato
   != 'pagato' e != 'sent'

2. Verifica che Σimporti non superi
   il totale ritenute F24:
   SE Σimporti > f24.totalAmount:
   throw "Il credito compensato
   supera le ritenute del periodo"

3. Per ogni compensazione:
   withholdingLedgerDAO
   .collegaCredito(
   ledgerId, f24Id, importoUsato)

4. Aggiorna F24:
   BigDecimal saldo =
   f24.getTotalAmount()
   .subtract(Σimporti)
   ← saldo >= 0 sempre

   UPDATE f24_record SET
   importo_credito = Σimporti,
   codice_tributo_credito =
   tenant.codiceTributoCreditoImposta,
   anno_credito = annoNdc,
   saldo_netto = saldo,
   updated_at = NOW()
   WHERE id = f24Id

   Aggiorna schema f24_record
   con nuove colonne se necessario:
    - importo_credito DECIMAL(10,2)
    - codice_tributo_credito VARCHAR(10)
    - anno_credito INTEGER
    - saldo_netto DECIMAL(10,2)

5. Rigenera PDF F24 con i nuovi campi

6. Log INFO "F24Service.applicaCrediti()
    - f24={} credito={} saldo={}"

7. Return f24 aggiornato

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BACKEND — F24 PDF AcroForm
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica in F24PdfService.java
(o equivalente) i campi AcroForm
del modello F24 Semplificato.

Aggiungi valorizzazione dei campi
credito quando importo_credito > 0:

motivo_importo_credito_1 =
f24.codiceTributoCreditoImposta
anno_credito_1 =
f24.annoCredito
importo_credito_1 =
f24.importoCredito
(formato: "NNNN,NN" senza €)

← il totale saldo netto va nel campo
totale da versare del modello F24

Verifica i nomi esatti dei campi
nel file PDF AcroForm leggendo
il template esistente.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. BACKEND — Controller
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a F24Controller.java:

POST /api/f24/{id}/crediti
- @RequestBody List<CreditoCompensazione
  Request>
- tenantId da SecurityUtils
- chiama f24Service.applicaCrediti()
- 200 F24Record aggiornato
- 400 se credito > ritenute
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

export interface
CreditoCompensazioneRequest {
ledgerId: number
importoUsato: number
}

Aggiorna F24GenerazioneResult:
crediti?: CreditoDisponibile[]

Aggiungi funzione:
export async function applicaCrediti(
f24Id: number,
compensazioni:
CreditoCompensazioneRequest[]
): Promise<F24Record>
// POST /api/f24/{id}/crediti

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. FRONTEND — F24List.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Nel dialog "Genera F24" dopo
la tabella RitenuteTable aggiungi
la sezione crediti se disponibili:

Stato locale:
const [compensazioni, setCompensazioni]
= useState<Record<number, string>>({})
← ledgerId → importo stringa

SE risultato.crediti?.length > 0:

<div className="mt-4 border rounded-md
  p-3 space-y-3">
  <h4 className="text-sm font-medium
    flex items-center gap-2">
    <BadgeMinus className="h-4 w-4
      text-green-600" />
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
del {formatDate(
c.ndcDataEmissione)}
— anno {c.annoRiferimento}
</span>
</div>
<span className="text-xs
text-muted-foreground">
max €{formatAmount(
c.importoCredito)}
</span>
<Input
type="number"
placeholder="€ da compensare"
value={compensazioni[
c.ledgerId] ?? ''}
onChange={e =>
setCompensazioni({
...compensazioni,
[c.ledgerId]: e.target.value
})}
className="w-32 h-7 text-xs"
min={0}
max={c.importoCredito}
step={0.01}
/>
</div>
))}

← Riepilogo in tempo reale
{totaleCompensato > 0 && (
<div className="border-t pt-2
space-y-1 text-sm">
<div className="flex justify-between
text-muted-foreground">
<span>Totale ritenute</span>
<span>€{formatAmount(
risultato.totaleRitenute)}</span>
</div>
<div className="flex justify-between
text-green-600">
<span>Credito compensato</span>
<span>-€{formatAmount(
totaleCompensato)}</span>
</div>
<div className="flex justify-between
font-medium border-t pt-1">
<span>Saldo F24</span>
<span>€{formatAmount(
Math.max(0,
risultato.totaleRitenute
- totaleCompensato))}</span>
</div>
{totaleCompensato >
risultato.totaleRitenute && (
<p className="text-xs
text-destructive">
⚠ Il credito supera le ritenute.
Verrà usato solo €{formatAmount(
risultato.totaleRitenute)}.
</p>
)}
</div>
)}
</div>

Calcola totaleCompensato:
const totaleCompensato = Object.entries(
compensazioni)
.reduce((s, [, v]) =>
s + (parseFloat(v) || 0), 0)

Aggiorna handleConfermaF24()
(o handleGeneraF24()) per passare
i crediti selezionati:

SE totaleCompensato > 0:
← prima genera l'F24 normalmente
← poi chiama applicaCrediti():
const comp = Object.entries(
compensazioni)
.filter(([, v]) =>
parseFloat(v) > 0)
.map(([id, v]) => ({
ledgerId: parseInt(id),
importoUsato: Math.min(
parseFloat(v),
risultato.crediti.find(
c => c.ledgerId ===
parseInt(id))
?.importoCredito ?? 0)
}))
await applicaCrediti(f24Id, comp)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
9. FRONTEND — Impostazioni Tenant
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Nella pagina impostazioni tenant
sezione "Parametri Fiscali" aggiungi:

<div>
  <Label>Codice tributo credito
    d'imposta NDC</Label>
  <p className="text-xs
    text-muted-foreground mb-1">
    Codice tributo da usare per i
    crediti da note di credito
    nel modello F24.
    Default: 6782 (locazioni brevi)
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

# Verifica crediti disponibili
# dopo NDC con F24 pagato
psql -U sostitutoincloud \
-d sostitutoincloud -h localhost -c "
SELECT id, stato, ritenuta_amount,
fk_ndc_id,
fk_f24_compensazione_id
FROM withholding_ledger
WHERE stato = 'credito_imposta'
AND fk_tenant_id = 1;"

# Genera F24 e verifica che
# mostri i crediti disponibili
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"mese": 10,
"anno": 2026
}' \
"http://localhost:8081/sostitutoincloud/\
api/f24/genera" \
| python3 -m json.tool \
| grep -A 20 '"crediti"'

# Applica crediti all'F24 generato
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '[{
"ledgerId": {LEDGER_ID},
"importoUsato": {IMPORTO}
}]' \
"http://localhost:8081/sostitutoincloud/\
api/f24/{F24_ID}/crediti" \
| python3 -m json.tool \
| grep -E '"importoCredito|\
saldoNetto|codiceTributo"'

# Verifica PDF F24 con campo credito
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/f24/{F24_ID}/pdf" \
-o /tmp/f24_credito.pdf
echo "PDF salvato in /tmp/f24_credito.pdf"

# Verifica credito già compensato
# → non appare più nei disponibili
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
| grep '"crediti"'

Verifica che:
- Crediti disponibili appaiono
  nel dialog "Genera F24"
- Compensazione riduce il saldo F24
- Saldo F24 mai negativo
- PDF F24 ha i campi credito valorizzati
- Credito compensato non riappare
  nel prossimo F24
- Credito parziale → residuo
  resta disponibile

Riporta output build e curl.