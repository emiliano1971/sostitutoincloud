Leggi il file CLAUDE.md e i file esistenti:
- frontend/src/pages/tenant/DocumentsList.tsx
- frontend/src/pages/tenant/F24List.tsx
- frontend/src/pages/tenant/BookingsList.tsx
  (come riferimento per il pattern già usato)
  prima di procedere.

Aggiungi filtri temporali a DocumentsList
e F24List. Modifica SOLO il frontend —
nessuna modifica al backend.
Usa useSearchParams per persistere i filtri
nell'URL (stesso pattern di BookingsList).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DocumentsList.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi filtro date su issueDate
(stesso pattern IDENTICO di BookingsList):

Stati da useSearchParams:
dateFrom → searchParams.get('dateFrom') ?? ''
dateTo   → searchParams.get('dateTo') ?? ''
preset   → searchParams.get('preset') ?? ''

Logica filtro in memoria:
.filter(d => {
if (!dateFrom && !dateTo) return true;
const from = dateFrom || '0000-01-01';
const to   = dateTo   || '9999-12-31';
return d.issueDate >= from &&
d.issueDate <= to;
})

Funzione applyPreset identica a
BookingsList (ieri/3gg/7gg/14gg).

UI — aggiungi sotto la search bar
una seconda riga con:
Dal: [date input] Al: [date input] [X]
[Ieri] [3gg] [7gg] [14gg]

Stesso stile e comportamento di
BookingsList — pill attivo se preset
selezionato, X visibile solo se
dateFrom o dateTo valorizzati.

Persisti tutti i parametri nell'URL:
/documents?dateFrom=2026-07-01&dateTo=2026-07-31
/documents?preset=7gg

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. F24List.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

L'F24 ha periodo_mese + periodo_anno
(non una data singola) quindi usa
select Anno + select Mese invece
dei preset Ieri/3gg/7gg.

Stati da useSearchParams:
annoFilter → searchParams.get('anno') ?? ''
meseFilter → searchParams.get('mese') ?? ''

Logica filtro in memoria:
.filter(f => {
if (!annoFilter && !meseFilter)
return true;
const annoOk = !annoFilter ||
String(f.periodoAnno) === annoFilter;
const meseOk = !meseFilter ||
String(f.periodoMese) === meseFilter;
return annoOk && meseOk;
})

UI — aggiungi una riga di filtri
accanto al filtro stato esistente:

Anno: [select]   Mese: [select]   [X reset]

Select Anno:
opzione vuota "Tutti gli anni"
+ anni disponibili calcolati dai dati:
  [...new Set(f24List.map(f =>
  f.periodoAnno))].sort().reverse()
  (così mostra solo gli anni con dati)

Select Mese:
opzione vuota "Tutti i mesi"
+ opzioni 1-12 con label italiane:
  Gennaio, Febbraio, ... Dicembre

X reset: visibile solo se anno o mese
valorizzati → resetta entrambi

Persisti nell'URL:
/f24?anno=2026&mese=7

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BUILD
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

cd frontend && npm run build
npx tsc --noEmit

Verifica che:
- DocumentsList filtra per data emissione
  con preset e input manuali
- F24List filtra per anno e mese
- I filtri persistono nell'URL al refresh
- Il reset X pulisce i parametri dall'URL

Riporta output build.