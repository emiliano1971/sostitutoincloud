Leggi il file CLAUDE.md e il file esistente:
- frontend/src/pages/tenant/BookingsList.tsx
  prima di procedere.

Persisti i filtri di BookingsList nell'URL
tramite useSearchParams in modo che i filtri
sopravvivano al refresh e al back/forward
del browser.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. SOSTITUISCI STATI CON SEARCHPARAMS
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Sostituisci tutti gli useState dei filtri
con lettura/scrittura da useSearchParams:

const [searchParams, setSearchParams] =
useSearchParams();

Mappa params → stati:
q          → searchParams.get('q') ?? ''
channel    → searchParams.get('channel') ?? 'all'
stati      → searchParams.get('stati')
?.split(',')
.filter(Boolean) ?? []
→ new Set<string>(...)
dateFrom   → searchParams.get('dateFrom') ?? ''
dateTo     → searchParams.get('dateTo') ?? ''
datePreset → searchParams.get('preset') ?? ''

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. AGGIORNA I SETTER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Ogni volta che un filtro cambia aggiorna
i searchParams invece dello stato locale.

Crea un helper:
const updateFilter = (
key: string,
value: string | null
) => {
setSearchParams(prev => {
const next = new URLSearchParams(prev);
if (value === null || value === '') {
next.delete(key);
} else {
next.set(key, value);
}
return next;
}, { replace: true });
// replace:true evita di riempire
// la history ad ogni keystroke
};

Usa updateFilter per ogni filtro:
- search q:
  updateFilter('q', value || null)
- channel:
  updateFilter('channel',
  value === 'all' ? null : value)
- stati (Set → stringa CSV):
  updateFilter('stati',
  statiSet.size > 0
  ? [...statiSet].join(',') : null)
- dateFrom:
  updateFilter('dateFrom', value || null)
- dateTo:
  updateFilter('dateTo', value || null)
- datePreset:
  updateFilter('preset', value || null)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. RICERCA Q CON DEBOUNCE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

La search testuale aggiorna l'URL ad ogni
carattere con replace:true — questo è ok
ma potrebbe essere rumoroso nella history.

Aggiungi uno stato locale solo per il
valore visualizzato nell'input:
const [qInput, setQInput] = useState(
searchParams.get('q') ?? '')

E un useEffect con debounce 400ms che
aggiorna il searchParam:
useEffect(() => {
const t = setTimeout(() =>
updateFilter('q', qInput || null),
400);
return () => clearTimeout(t);
}, [qInput]);

Così l'input è fluido ma l'URL si aggiorna
solo quando l'utente smette di scrivere.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. RESET FILTRI
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Il pulsante "Deseleziona tutto" (stati)
e la X delle date devono pulire i loro
parametri dall'URL:
setSearchParams(new URLSearchParams(),
{ replace: true })
← per reset completo di tutti i filtri

Oppure deleteKey per singoli filtri.

Aggiungi un pulsante "Reset filtri"
visibile solo se almeno un filtro è attivo
(searchParams.toString() !== ''):
onClick → setSearchParams(
new URLSearchParams(), {replace:true})
+ reset qInput → ''

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BUILD
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

cd frontend && npm run build

Verifica che:
- Impostando un filtro l'URL si aggiorni
  es. /bookings?stati=ready,doc_issued
- Refreshando la pagina i filtri restino
- Il back del browser ripristini i filtri
  precedenti
- Il reset pulisca l'URL a /bookings

Riporta output build.