Leggi il file CLAUDE.md e il file esistente:
- frontend/src/pages/tenant/BookingsList.tsx
  prima di procedere.

Aggiungi filtro per data check-in/check-out
alla lista prenotazioni.
Modifica SOLO il frontend — nessuna modifica
al backend.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. STATI REACT
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi stati:
const [dateFrom, setDateFrom] =
useState<string>('')
const [dateTo, setDateTo] =
useState<string>('')
const [datePreset, setDatePreset] =
useState<string>('')

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. LOGICA FILTRO DATE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Nel useMemo del filtro esistente aggiungi
dopo gli altri filtri:

// Filtro date check-in/check-out
.filter(b => {
if (!dateFrom && !dateTo) return true;
const checkin  = b.checkinDate;
const checkout = b.checkoutDate;
// includi il booking se il periodo
// si sovrappone con il range selezionato
const from = dateFrom || '0000-01-01';
const to   = dateTo   || '9999-12-31';
return checkin <= to && checkout >= from;
})

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. PRESET DATE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi funzione:
const applyPreset = (preset: string) => {
const today = new Date();
const fmt = (d: Date) =>
d.toISOString().split('T')[0];

setDatePreset(preset);
switch (preset) {
case 'ieri':
const ieri = new Date(today);
ieri.setDate(today.getDate() - 1);
setDateFrom(fmt(ieri));
setDateTo(fmt(ieri));
break;
case '3gg':
const d3 = new Date(today);
d3.setDate(today.getDate() - 3);
setDateFrom(fmt(d3));
setDateTo(fmt(today));
break;
case '7gg':
const d7 = new Date(today);
d7.setDate(today.getDate() - 7);
setDateFrom(fmt(d7));
setDateTo(fmt(today));
break;
case '14gg':
const d14 = new Date(today);
d14.setDate(today.getDate() - 14);
setDateFrom(fmt(d14));
setDateTo(fmt(today));
break;
case '':
setDateFrom('');
setDateTo('');
break;
}
};

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. UI
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi una seconda riga di filtri
sotto la search bar esistente:

┌─────────────────────────────────────────┐
│ Dal: [input date] Al: [input date]  [X] │
│ [Ieri] [3gg] [7gg] [14gg]               │
└─────────────────────────────────────────┘

- Input Dal/Al: type="date", value={dateFrom/dateTo}
  onChange → setDateFrom/setDateTo +
  setDatePreset('') (resetta il preset)
- Pulsante X: visibile solo se dateFrom
  o dateTo valorizzati → resetta tutto
  (setDateFrom(''), setDateTo(''),
  setDatePreset(''))
- Pulsanti preset: pill/badge cliccabili
  con stile attivo se datePreset === valore
  (stesso stile dei filtri stato già presenti)
- Quando un preset è attivo i due input
  date mostrano le date calcolate
  (readonly o editabili — editabili
  è più flessibile)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BUILD
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

cd frontend && npm run build
Riporta output build.