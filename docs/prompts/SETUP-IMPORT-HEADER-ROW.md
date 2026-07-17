Leggi il file CLAUDE.md e i file esistenti:
- service/BookingImportService.java
- frontend/src/pages/tenant/ImportBookings.tsx
- frontend/src/api/importApi.ts
  prima di procedere.

Aggiungi campo "Riga intestazioni" nello
Step 0 del wizard di import, per gestire
file con righe di intestazione prima
dell'header reale (es. Airbnb export).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — BookingImportService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica il metodo uploadFiles() in
service/BookingImportService.java:

- Aggiungi parametro Integer headerRow
  (default 0 se null)

- Per file XLSX:
  Usa headerRow come indice di riga
  (0-based) da cui leggere le intestazioni.
  Le righe precedenti vengono saltate.
  Esempio: headerRow=6 → legge riga 7
  del foglio come header, i dati
  iniziano dalla riga 8.

- Per file CSV:
  headerRow indica quante righe saltare
  prima dell'header. Esempio: headerRow=2
  → salta 2 righe, la terza è l'header.

- Salva headerRow nella sessione cache
  insieme al file raw, in modo che
  previewWithMapping() usi lo stesso
  valore quando legge i dati.
  Aggiungi a ImportSessionCache:
  void storeHeaderRow(String sessionId,
  int headerRow)
  int getHeaderRow(String sessionId)
  ← default 0 se non trovato

- Log INFO "uploadFiles() - headerRow={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — Controller
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica POST /api/bookings/import/upload
in BookingController (o ImportController):

- Aggiungi @RequestParam(required=false,
  defaultValue="0") Integer headerRow
- Passa headerRow a importService.uploadFiles()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. FRONTEND — importApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica uploadImportFiles() in importApi.ts:
- Aggiungi parametro headerRow: number
  (default 0)
- Passa headerRow come campo nel
  FormData o come query param:
  formData.append('headerRow',
  headerRow.toString())

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND — ImportBookings.tsx Step 0
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi stato React:
const [headerRow, setHeaderRow] =
useState<number>(0)

Nello Step 0, sotto le drop zone dei file
e prima del bottone "Avanti", aggiungi
una riga di opzione avanzata:

[▼ Opzioni avanzate]  ← collassabile,
chiuso di default

Quando aperto mostra:
Label: "Riga intestazioni (0 = prima riga)"
Input numerico:
- type="number"
- min=0, max=20
- value={headerRow}
- onChange → setHeaderRow()
- width contenuto (~80px)
Testo aiuto sotto:
"Imposta un valore > 0 se il file
contiene righe di titolo prima
delle intestazioni delle colonne.
Es: Airbnb export → 6"

Se è selezionato un template e il template
ha headerRow > 0:
- apri automaticamente "Opzioni avanzate"
- precompila headerRow dal template
  (già salvato in import_template.header_row)

Passa headerRow a uploadImportFiles()
al click "Avanti".

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. TEMPLATE — aggiorna salvataggio
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che quando l'utente salva un
template nello Step 1, il valore headerRow
corrente venga incluso nel
ImportTemplateSaveDTO inviato al backend.
Se non lo è già, aggiungilo.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

Verifica con Reservations_1782985997.xlsx:
- Step 0: apri "Opzioni avanzate" e
  imposta headerRow=6
- Clicca Avanti
- Step 1: devono comparire le colonne
  reali del file:
  "Cod. Prenotazione", "Res ID", "Stato",
  "Origine", "Nome Alloggio", "Importo" ecc.
- Il suggestedMapping deve proporre
  match automatici dove possibile

Verifica anche con
Prenotazioni_in_data_20260623_1.xlsx
con headerRow=0 (default) — deve
continuare a funzionare come prima.

Riporta output build e colonne rilevate
per entrambi i file.