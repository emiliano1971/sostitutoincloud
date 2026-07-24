Leggi il file CLAUDE.md e i file esistenti:
- frontend/src/pages/tenant/ImportBookings.tsx
- frontend/src/api/importApi.ts
- service/BookingImportService.java
- controller/BookingImportController.java
  (o dove è definito POST /import/confirm)
  prima di procedere.

Aggiungi selezione righe nell'anteprima import:
tutte pre-selezionate di default, deselezionabili
singolarmente. Solo le righe "nuova" sono
selezionabili — errori e duplicate hanno
checkbox disabilitato.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — confirmImport
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica il DTO usato da
POST /api/bookings/import/confirm
(es. ImportConfirmRequestDTO o simile).

Aggiungi campo:
List<Integer> selectedRowNumbers
← numeri di riga (campo # della preview)
da importare
← se null o vuoto: importa tutto
(comportamento attuale — backward
compatible)

Modifica service/BookingImportService.java
nel metodo confirmImport():

Se selectedRowNumbers != null e non vuoto:
filtra le righe della sessione mantenendo
solo quelle il cui rowNumber è in
selectedRowNumbers
(usa il campo # già presente in
BookingImportPreviewRowDTO)

Se selectedRowNumbers è null o vuoto:
importa tutto come ora (fallback)

Log INFO "BookingImportService.confirm()
- sessionId={} selectedRows={} totalRows={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. FRONTEND — importApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica il tipo ImportConfirmRequest
(o nome equivalente) in importApi.ts:

Aggiungi:
selectedRowNumbers?: number[]

Passa selectedRowNumbers nella chiamata
a POST /api/bookings/import/confirm.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. FRONTEND — ImportBookings.tsx Step 2
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi stato React:
const [selectedRows, setSelectedRows] =
useState<Set<number>>(new Set())

Al caricamento della preview (quando
arriva la risposta da previewImportV2):
inizializza selectedRows con tutti i
rowNumber delle righe con stato='nuova':
new Set(preview.rows
.filter(r => r.stato === 'nuova')
.map(r => r.rowNumber))

CHECKBOX HEADER:
Aggiungi come prima colonna dell'header:
<Checkbox
checked={
tutteLeNuoveSelezionate &&
nuoveRows.length > 0
}
onCheckedChange={(checked) => {
if (checked) selezionaTutte()
else deselezionaTutte()
}}
/>
dove tutteLeNuoveSelezionate =
nuoveRows.every(r =>
selectedRows.has(r.rowNumber))

CHECKBOX PER RIGA:
Aggiungi come prima colonna di ogni riga:
- stato='nuova':
  <Checkbox
  checked={selectedRows.has(r.rowNumber)}
  onCheckedChange={() =>
  toggleRow(r.rowNumber)}
  />
- stato='errore' o 'duplicata':
  <Checkbox disabled checked={false} />
  (visivamente presente ma non
  selezionabile — grigio)

CONTATORE DINAMICO:
Il pulsante "Conferma Import" deve
mostrare il conteggio aggiornato:
"Conferma Import
({selectedRows.size} prenotazioni)"

Se selectedRows.size === 0:
disabilita il pulsante con tooltip
"Seleziona almeno una prenotazione"

AL CONFIRM:
Passa selectedRowNumbers:
[...selectedRows]
alla funzione confirmImport().

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

Verifica con i file di test E2E-CF:
- Step 2: entrambe le righe "nuova"
  pre-selezionate di default
- Deseleziona riga E2E-CF-002
- Pulsante mostra "Conferma Import
  (1 prenotazioni)"
- Conferma → solo E2E-CF-001 importata
- Verifica DB:
  SELECT external_booking_id
  FROM booking
  WHERE external_booking_id IN
  ('E2E-CF-001','E2E-CF-002')
  ORDER BY external_booking_id;
  → deve restituire solo E2E-CF-001

Riporta output build e verifica DB.