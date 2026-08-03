Leggi il file CLAUDE.md e il file esistente:
- frontend/src/pages/tenant/BookingsList.tsx
  prima di procedere.

Sostituisci la select singola dello stato
con un dropdown multi-check che permette
di selezionare più stati contemporaneamente.
Modifica SOLO il frontend — nessuna modifica
al backend.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. STATO REACT
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Sostituisci lo stato singolo del filtro stato
(es. statoFilter: string) con:

const [statiSelezionati, setStatiSelezionati] =
useState<Set<string>>(new Set())

Set vuoto = nessun filtro attivo
(mostra tutti, equivale all'attuale "Tutti gli stati")

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. LOGICA FILTRO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Nel filtro in memoria sostituisci il check
su statoFilter con:

.filter(b => {
if (statiSelezionati.size === 0) return true;
return statiSelezionati.has(
b.statoPrenotazione);
})

NOTA: il filtro speciale "da_completare"
non è uno stato diretto — è una combinazione
(checkout <= oggi AND stato NOT IN
doc_issued/settled/cancelled).
Mantienilo come opzione separata nel dropdown
con logica dedicata:

if (statiSelezionati.has('da_completare')) {
const oggi = new Date().toISOString()
.split('T')[0];
return b.checkoutDate <= oggi &&
!['doc_issued','settled','cancelled']
.includes(b.statoPrenotazione);
}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. COMPONENTE DROPDOWN MULTI-CHECK
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Sostituisci la select con un Popover
(shadcn/ui già nel progetto) che apre
una lista di checkbox:

Trigger del Popover:
- Se statiSelezionati.size === 0:
  "Tutti gli stati ▼"
- Se statiSelezionati.size === 1:
  "{label stato} ▼"
- Se statiSelezionati.size > 1:
  "{N} stati ▼"
- Con icona Filter (lucide-react)

Contenuto del Popover:
Lista di voci con checkbox, ognuna con
label e badge colorato:

☐ Da completare   (grigio scuro — speciale)
─────────────────
☐ Importata       (grigio)
☐ Arricchita      (blu)
☐ Pronta          (azzurro)
☐ Doc. emesso     (verde)
☐ Liquidata       (verde scuro)
☐ Annullata       (rosso)

Separatore visivo tra "Da completare"
e gli stati singoli.

In fondo al popover:
[Deseleziona tutto] — svuota il Set

Comportamento checkbox:
- click → toggle stato nel Set
- "Da completare" è esclusivo:
  se selezionato deseleziona gli altri
  e viceversa (non ha senso combinarlo
  con stati singoli)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BADGE COLORATI NEL TRIGGER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Quando statiSelezionati.size > 0 mostra
sotto il trigger (o inline) i badge degli
stati selezionati con la X per rimuovere
singolarmente, stesso stile già usato
per i badge stato nelle righe della tabella.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. CONTATORE AGGIORNATO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Il contatore "N prenotazioni trovate"
in cima alla lista deve riflettere
il numero di booking dopo TUTTI i filtri
attivi (stato + canale + date + search).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. BUILD
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

cd frontend && npm run build
Riporta output build.