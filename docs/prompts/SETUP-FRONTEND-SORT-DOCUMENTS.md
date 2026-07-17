Leggi il file CLAUDE.md e docs/analisi-frontend.md
prima di procedere.

Aggiungi ordinamento dinamico per colonna alla
pagina DocumentsList.tsx.
L'ordinamento è lato frontend (in memoria sui dati
già caricati) — nessuna modifica al backend.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. LOGICA DI ORDINAMENTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi due stati:
const [sortKey, setSortKey] =
useState<string>('issueDate')
const [sortDir, setSortDir] =
useState<'asc' | 'desc'>('desc')

Ordine di default: issueDate DESC
(documenti più recenti in cima)

Logica toggle:
- click su colonna già attiva → inverte la direzione
- click su colonna diversa → imposta quella colonna
  con direzione 'asc' (eccetto issueDate che parte
  da 'desc')

Applica l'ordinamento DOPO i filtri (stato, search)
e PRIMA della render della tabella, con useMemo:

const sorted = useMemo(() => {
return [...filtered].sort((a, b) => {
let valA = a[sortKey];
let valB = b[sortKey];
// date: confronto string ISO funziona
//   direttamente con < >
// number: confronto numerico
// string: localeCompare
const dir = sortDir === 'asc' ? 1 : -1;
if (valA == null) return 1;
if (valB == null) return -1;
if (typeof valA === 'number')
return (valA - valB) * dir;
return String(valA).localeCompare(
String(valB), 'it') * dir;
});
}, [filtered, sortKey, sortDir]);

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. INTESTAZIONI CLICCABILI
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea un componente locale SortableTh:

interface SortableThProps {
label: string;
colKey: string;
sortKey: string;
sortDir: 'asc' | 'desc';
onSort: (key: string) => void;
align?: 'left' | 'right';
}

Comportamento visivo:
- colonna inattiva: label + icona ↕ in grigio chiaro
- colonna attiva asc: label + icona ↑ colorata
- colonna attiva desc: label + icona ↓ colorata
- cursor: pointer su tutto il th
- nessuna libreria esterna — usa caratteri
  Unicode: ↕ ↑ ↓  oppure i componenti
  lucide-react già usati nel progetto
  (ChevronsUpDown, ChevronUp, ChevronDown)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. COLONNE ORDINABILI
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Sostituisci le intestazioni statiche con
SortableTh per TUTTE le colonne visibili:

Numero       → colKey='documentNumber'
Tipo         → colKey='documentType'
Destinatario → colKey='recipientName'
Proprietario → colKey='ownerName'
Immobile     → colKey='propertyName'
Data         → colKey='issueDate'
Totale €     → colKey='totalAmount'  align='right'
Stato SDI    → colKey='statoDocumento'

L'ultima colonna (icona occhio / azioni) non
è ordinabile — lasciala come th statico.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

cd frontend && npm run build

Verifica manualmente in dev (npm run dev):
- click su "Data" → ordina desc→asc→desc
- click su "Proprietario" → raggruppa per owner
- click su "Totale €" → ordina per importo
- click su colonna già attiva → inverte direzione
- i filtri (stato, search) continuano a funzionare
  combinati con l'ordinamento

Riporta output del build.