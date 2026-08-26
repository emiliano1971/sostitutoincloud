import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { Input } from '@/components/ui/input';
import { Loader2 } from 'lucide-react';
import { cercaComuni, type ComuneItaliano } from '@/api/comuneApi';

interface ComuneAutocompleteProps {
  value: string;
  onChange: (nome: string, belfiore: string) => void;
  /** Callback opzionale con il comune completo selezionato (nome, sigla provincia, regione, belfiore). */
  onSelect?: (comune: ComuneItaliano) => void;
  placeholder?: string;
  disabled?: boolean;
  /**
   * Valore già esistente (es. il comune salvato di un immobile in modifica): viene mostrato
   * come testo iniziale SENZA avviare la ricerca automatica al mount. Resta modificabile.
   */
  initialValue?: string;
  /**
   * Se true il valore deve provenire dalla lista: al blur il testo libero non
   * selezionato viene scartato (onChange('', '')) e viene mostrato un errore.
   * Default false — comportamento invariato, testo libero ammesso.
   */
  requireValidComune?: boolean;
}

const ComuneAutocomplete = ({
  value,
  onChange,
  onSelect,
  placeholder,
  disabled,
  initialValue,
  requireValidComune = false,
}: ComuneAutocompleteProps) => {
  const [query, setQuery] = useState(initialValue ?? value ?? '');
  const [results, setResults] = useState<ComuneItaliano[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [isValid, setIsValid] = useState(true);
  // Il testo mostrato corrisponde a un comune della lista. Parte da true quando il
  // valore arriva già valorizzato dal parent (record esistente): quel comune è stato
  // scelto in precedenza, un focus/blur senza modifiche non deve cancellarlo.
  const [hasSelected, setHasSelected] = useState(Boolean((initialValue ?? value ?? '').trim()));
  // Il dropdown è legato al focus: senza interazione dell'utente non si apre mai,
  // nemmeno se una ricerca viene avviata da un aggiornamento programmatico.
  const [hasFocus, setHasFocus] = useState(false);
  // Risultato evidenziato per la navigazione da tastiera; -1 = nessuno.
  const [activeIndex, setActiveIndex] = useState<number>(-1);
  const containerRef = useRef<HTMLDivElement>(null);
  const blurTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([]);
  // Le ricerche partono SOLO dalla digitazione. Al mount vale sempre true (anche con
  // initialValue o value già valorizzati) e viene rialzato a ogni sync esterna.
  const skipNextSearch = useRef(true);
  const isFirstSync = useRef(true);
  // Ultimo testo mostrato, per capire se una sync esterna cambia davvero qualcosa
  // senza dover aggiungere `query` alle dipendenze dell'effetto di sync.
  const queryRef = useRef(query);

  useEffect(() => { queryRef.current = query; }, [query]);

  // Sincronizza il testo quando il valore esterno cambia: reset del form oppure dati
  // caricati in modo asincrono (es. impostazioni tenant, dialog precompilati).
  // Non deve avviare la ricerca: il valore non arriva dall'utente.
  useEffect(() => {
    if (isFirstSync.current) { isFirstSync.current = false; return; }
    const next = value ?? '';
    if (next === queryRef.current) return;
    skipNextSearch.current = true;
    setQuery(next);
    // Valore impostato dal parent (dati caricati, reset): è già validato a monte.
    setHasSelected(next.trim().length > 0);
    setIsValid(true);
  }, [value]);

  // Ricerca con debounce 300ms a partire da 2 caratteri
  useEffect(() => {
    if (skipNextSearch.current) { skipNextSearch.current = false; return; }
    if (query.trim().length < 2) {
      setResults([]);
      setSearched(false);
      return;
    }
    const handle = setTimeout(() => {
      setLoading(true);
      cercaComuni(query)
        .then(res => { setResults(res); setSearched(true); })
        .catch(() => { setResults([]); setSearched(true); })
        .finally(() => setLoading(false));
    }, 300);
    return () => clearTimeout(handle);
  }, [query]);

  useEffect(() => () => { if (blurTimer.current) clearTimeout(blurTimer.current); }, []);

  // Nuova lista di risultati: nessun elemento evidenziato e refs ridimensionati.
  useEffect(() => {
    setActiveIndex(-1);
    optionRefs.current.length = results.length;
  }, [results]);

  const handleSelect = (c: ComuneItaliano) => {
    skipNextSearch.current = true;
    queryRef.current = c.nome;
    setQuery(c.nome);
    setResults([]);
    setSearched(false);
    setHasFocus(false);
    setActiveIndex(-1);
    setHasSelected(true);
    setIsValid(true);
    if (blurTimer.current) clearTimeout(blurTimer.current);
    onChange(c.nome, c.codiceBelfiore);
    onSelect?.(c);
  };

  /**
   * Con requireValidComune il testo digitato ma non confermato dalla lista viene
   * scartato all'uscita dal campo. Gira dentro il timer del blur, che handleSelect
   * annulla: se l'utente ha appena cliccato un risultato, non scatta.
   */
  const validateOnBlur = () => {
    if (!requireValidComune || hasSelected || query.trim().length === 0) {
      return;
    }
    setIsValid(false);
    setQuery('');
    queryRef.current = '';
    setResults([]);
    setSearched(false);
    onChange('', '');
  };

  // Visibile solo con focus e almeno 2 caratteri digitati: `searched` tiene il
  // messaggio "Nessun comune trovato" quando la ricerca non produce risultati.
  const showDropdown = hasFocus && query.trim().length >= 2 && (results.length > 0 || searched);

  // Dropdown chiuso: azzera l'evidenziazione, così alla riapertura si riparte da capo.
  useEffect(() => {
    if (!showDropdown) setActiveIndex(-1);
  }, [showDropdown]);

  // Tiene visibile il risultato evidenziato nella lista scrollabile.
  // Chiamata opzionale: jsdom non implementa scrollIntoView.
  useEffect(() => {
    if (activeIndex < 0) return;
    optionRefs.current[activeIndex]?.scrollIntoView?.({ block: 'nearest' });
  }, [activeIndex]);

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      setHasFocus(false);
      setActiveIndex(-1);
      return;
    }
    if (!showDropdown) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIndex(prev => Math.min(prev + 1, results.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIndex(prev => Math.max(prev - 1, 0));
    } else if (e.key === 'Enter') {
      // preventDefault a dropdown aperto: evita il submit di un eventuale form padre
      // mentre l'utente sta scegliendo un comune.
      e.preventDefault();
      if (activeIndex >= 0 && results[activeIndex]) {
        handleSelect(results[activeIndex]);
      }
    }
  };

  return (
    <div ref={containerRef} className="relative">
      <div className="relative">
        <Input
          value={query}
          onChange={e => {
            setQuery(e.target.value);
            // Modificando il testo l'eventuale selezione precedente non vale più,
            // e l'errore si azzera per non restare rosso mentre si digita.
            setHasSelected(false);
            setIsValid(true);
          }}
          onFocus={() => {
            if (blurTimer.current) clearTimeout(blurTimer.current);
            setHasFocus(true);
          }}
          // Ritardo di 200ms: il blur precede il click sul risultato, senza attesa
          // il dropdown si smonterebbe prima che handleSelect venga invocato.
          onBlur={() => {
            blurTimer.current = setTimeout(() => {
              setHasFocus(false);
              validateOnBlur();
            }, 200);
          }}
          onKeyDown={handleKeyDown}
          placeholder={placeholder ?? 'Comune di nascita…'}
          disabled={disabled}
          autoComplete="off"
          aria-invalid={!isValid}
          className={!isValid ? 'border-destructive focus:ring-destructive' : undefined}
        />
        {loading && (
          <Loader2 className="absolute right-2 top-1/2 -translate-y-1/2 h-4 w-4 animate-spin text-muted-foreground" />
        )}
      </div>

      {showDropdown && (
        <div
          role="listbox"
          className="absolute z-50 mt-1 w-full rounded-md border bg-popover shadow-md max-h-60 overflow-auto"
        >
          {results.length > 0 ? (
            results.map((c, i) => (
              <button
                key={c.id}
                type="button"
                role="option"
                aria-selected={i === activeIndex}
                ref={el => { optionRefs.current[i] = el; }}
                className={`w-full text-left px-3 py-2 text-sm hover:bg-accent hover:text-accent-foreground ${
                  i === activeIndex ? 'bg-accent text-accent-foreground' : ''
                }`}
                onClick={() => handleSelect(c)}
                onMouseEnter={() => setActiveIndex(i)}
              >
                {c.nome} ({c.siglaProvincia})
              </button>
            ))
          ) : searched && !loading ? (
            <div className="px-3 py-2 text-sm text-muted-foreground">Nessun comune trovato</div>
          ) : null}
        </div>
      )}

      {!isValid && (
        <p className="mt-1 text-xs text-destructive">Seleziona un comune dalla lista</p>
      )}
    </div>
  );
};

export default ComuneAutocomplete;
