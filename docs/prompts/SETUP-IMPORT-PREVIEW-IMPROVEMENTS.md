Leggi il file CLAUDE.md e i file esistenti:
- service/BookingImportService.java
- frontend/src/pages/tenant/ImportBookings.tsx
- frontend/src/api/importApi.ts
  prima di procedere.

Migliora il wizard di import con 3 feature:
1. Colonna ID prenotazione in anteprima
2. Warning dati anagrafici incompleti
3. Filtro automatico prenotazioni cancellate

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DTO — aggiornamenti
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica dto/importing/BookingImportRowDTO.java:
- Aggiungi String externalBookingId
  (se non già presente come campo esposto
  nel DTO di preview)

Modifica dto/importing/BookingImportPreviewDTO.java:
- Aggiungi Integer excludedCount
  ← righe escluse perché cancellate

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — BookingImportService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica service/BookingImportService.java
nel metodo previewWithMapping():

A) FILTRO STATI CANCELLATI
Dopo aver letto ogni riga dal file,
se la colonna STATO è mappata:
- leggi il valore della colonna STATO
- normalizza: trim() + toLowerCase()
- se il valore è uno di:
  "cancellata", "cancelled", "canceled",
  "annullata", "annullato", "annulled",
  "cancellata/a", "no show",
  "confirmada" NO (questa è confermata),
  "rifiutata", "rejected", "expired"
  → incrementa excludedCount e SALTA
  la riga (non la processa, non la
  aggiunge alla preview)
- se il valore NON è in lista cancellati
  → processa normalmente
  Se STATO non è mappato → processa tutto
  come ora.

Lista valori CONFERMATI accettati
(per riferimento, non serve validare):
"confermata", "confirmed", "confirmada",
"confirmée", "bestätigt", "ok",
"confirmed - paid", "prepagata"

B) WARNING DATI ANAGRAFICI
Per ogni riga processata (non cancellata),
dopo il merge con dati ospite,
aggiungi warning a splitWarnings
(lista già esistente) se:

- comune di nascita mancante o vuoto:
  "CF non calcolabile: comune di nascita
  mancante"
- data di nascita mancante o vuota:
  "CF non calcolabile: data di nascita
  mancante"
- numero documento mancante o vuoto:
  "Documento identificativo mancante"
- nome ospite mancante o vuoto:
  "Nome ospite mancante"

I warning non cambiano lo stato della riga
(resta "nuova") — sono informativi.
Aggiorna warningCount in
BookingImportPreviewDTO contando le righe
che hanno almeno un warning.

C) ID PRENOTAZIONE
Assicurati che externalBookingId sia
popolato in BookingImportRowDTO per
ogni riga (incluse duplicati ed errori).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. FRONTEND — importApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica l'interfaccia ImportPreview
(o nome equivalente) in importApi.ts:
- Aggiungi excludedCount: number

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND — ImportBookings.tsx Step 2
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica lo Step 2 (Anteprima) di
ImportBookings.tsx:

A) CONTATORI IN CIMA
Aggiungi un quinto contatore accanto
agli esistenti (nuove/duplicate/errori/
avvisi):
- "N escluse" in grigio
- Sottotitolo: "Prenotazioni cancellate"
- Visibile solo se excludedCount > 0

B) COLONNA ID PRENOTAZIONE
Aggiungi colonna "ID Prenotazione"
come prima colonna della tabella
(prima di "#"):
- mostra externalBookingId
- font monospace, testo piccolo
- se lungo (>15 chars) tronca con
  tooltip sul valore completo

C) COLONNA WARNINGS
La colonna Warnings esiste già —
verifica che mostri tutti i warning
della riga come lista (uno per riga)
con icona ⚠️ gialla.
Se nessun warning: cella vuota.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

Verifica con il file
Prenotazioni_in_data_20260623_1.xlsx
già caricato in sessioni precedenti
(o ricaricalo) usando il template
"Booking.com Standard":

- Il contatore "escluse" deve mostrare
  le righe con Stato=CANCELLATA/Cancelled
- Le righe rimanenti devono mostrare
  l'ID prenotazione nella prima colonna
- Le righe senza comune emittente devono
  mostrare warning "CF non calcolabile:
  comune di nascita mancante"

Riporta:
- output build
- quante righe escluse per stato
  cancellato nel file di test
- esempio di riga con warning