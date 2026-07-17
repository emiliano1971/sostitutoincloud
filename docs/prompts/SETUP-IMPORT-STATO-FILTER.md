Leggi il file CLAUDE.md e i file esistenti:
- service/BookingImportService.java
- frontend/src/pages/tenant/ImportBookings.tsx
- frontend/src/api/importApi.ts
  prima di procedere.

Due miglioramenti al wizard di import:
1. Gestione interattiva valori colonna STATO
2. Bottoni navigazione wizard duplicati in cima

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — valori distinti colonna STATO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica dto/importing/ImportUploadResponseDTO.java:
- Aggiungi List<String> statoColumnValues
  ← valori distinti trovati nella colonna
  che si chiama "Stato" o simile nel file
  (rilevati durante uploadFiles())
  ← null se colonna STATO non rilevata

Modifica service/BookingImportService.java
nel metodo uploadFiles():
- Dopo aver letto le colonne e il
  suggestedMapping, se una colonna del file
  matcha STATO nel suggestedMapping:
    - leggi tutti i valori distinti di quella
      colonna (trim + ignora null/vuoti)
    - metti in statoColumnValues
    - ordina alfabeticamente
- Se STATO non è nel suggestedMapping:
  statoColumnValues = null

Modifica dto/importing/ImportColumnMappingDTO.java:
- Aggiungi Set<String> statiDaEscludere
  ← valori che l'utente vuole escludere
  ← null o vuoto = usa lista hardcodata
  esistente come fallback

Modifica service/BookingImportService.java
nel metodo previewWithMapping():
- Se mapping.statiDaEscludere != null
  e non vuoto:
  usa quell'insieme per il filtro
  (invece della costante STATI_CANCELLATI)
- Se mapping.statiDaEscludere è null
  o vuoto:
  usa STATI_CANCELLATI hardcodata
  come fallback (comportamento attuale)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. FRONTEND — importApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica ImportUploadResponse in importApi.ts:
- Aggiungi statoColumnValues?: string[]

Modifica ImportColumnMapping in importApi.ts:
- Aggiungi statiDaEscludere?: string[]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. FRONTEND — Step 1 Mapping Colonne
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi stato React:
const [statiDaEscludere, setStatiDaEscludere] =
useState<Set<string>>(new Set())

Quando uploadResponse arriva e
statoColumnValues è valorizzato:
- Pre-seleziona in statiDaEscludere
  i valori che matchano STATI_CANCELLATI
  hardcodata (stessa lista del backend,
  duplicala in una costante frontend):
  ["cancellata","cancelled","canceled",
  "annullata","annullato","annulled",
  "cancellata/a","no show","rifiutata",
  "rejected","expired"]
  Match: trim().toLowerCase()

Nella sezione del campo STATO nel mapping
(riga della select per la colonna STATO),
se statoColumnValues è valorizzato
aggiungi sotto la select una sotto-sezione:

"Valori trovati nel file — seleziona
quelli da ESCLUDERE dall'import:"

Per ogni valore in statoColumnValues
mostra un checkbox:
☑/☐  [valore]   → "Escludi" / "Importa"

- checked = statiDaEscludere.has(
  valore.trim().toLowerCase())
- onChange → toggle nel Set

Nota informativa sotto i checkbox:
"Le righe con i valori selezionati
non verranno importate."

Se statoColumnValues è null (colonna STATO
non rilevata nel file) non mostrare
nulla di aggiuntivo.

Al click "Genera Anteprima" passa
statiDaEscludere come array nel mapping:
statiDaEscludere: [...statiDaEscludere]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BOTTONI NAVIGAZIONE IN CIMA
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In ImportBookings.tsx ogni step ha
i bottoni "Indietro" / "Avanti" (o
"Genera Anteprima", "Conferma" ecc.)
in fondo al contenuto.

Duplicali in CIMA ad ogni step,
subito sotto la barra degli step numerati
e sopra il contenuto dello step.

Implementazione:
- Estrai i bottoni in un componente
  locale StepNav con props:
  onBack?: () => void
  onNext?: () => void
  nextLabel?: string   ← default "Avanti"
  nextDisabled?: boolean
  loading?: boolean
- Renderizza StepNav sia in cima
  (prima del contenuto) che in fondo
  (dopo il contenuto) di ogni step
- Stessa UI: stesso stile, stesse
  condizioni di disabilitazione
- Il bottone "Indietro" non compare
  allo step 0

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

Verifica con il file
Prenotazioni_in_data_20260623_1.xlsx:
- Step 1: sotto la select della colonna
  STATO compaiono i valori distinti del
  file con checkbox preselezionati
  correttamente
- I valori "CONFIRMADA" e simili devono
  risultare NON selezionati (da importare)
- I valori "Cancellata" e simili devono
  risultare selezionati (da escludere)
- Step 2: excludedCount coerente con
  la selezione fatta
- Bottoni navigazione visibili sia in
  cima che in fondo ad ogni step

Riporta output build e descrizione
dei valori trovati nel file di test.