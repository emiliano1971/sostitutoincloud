import { useEffect, useRef, useState } from 'react';
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
}

const ComuneAutocomplete = ({ value, onChange, onSelect, placeholder, disabled }: ComuneAutocompleteProps) => {
  const [query, setQuery] = useState(value ?? '');
  const [results, setResults] = useState<ComuneItaliano[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [searched, setSearched] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const skipNextSearch = useRef(false);

  // Sincronizza il testo quando il valore esterno cambia (es. reset del form)
  useEffect(() => { setQuery(value ?? ''); }, [value]);

  // Ricerca con debounce 300ms a partire da 2 caratteri
  useEffect(() => {
    if (skipNextSearch.current) { skipNextSearch.current = false; return; }
    if (query.trim().length < 2) {
      setResults([]);
      setOpen(false);
      setSearched(false);
      return;
    }
    const handle = setTimeout(() => {
      setLoading(true);
      cercaComuni(query)
        .then(res => { setResults(res); setSearched(true); setOpen(true); })
        .catch(() => { setResults([]); setSearched(true); setOpen(true); })
        .finally(() => setLoading(false));
    }, 300);
    return () => clearTimeout(handle);
  }, [query]);

  // Chiudi il dropdown al click esterno
  useEffect(() => {
    const onClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  const handleSelect = (c: ComuneItaliano) => {
    skipNextSearch.current = true;
    setQuery(c.nome);
    setOpen(false);
    setResults([]);
    onChange(c.nome, c.codiceBelfiore);
    onSelect?.(c);
  };

  return (
    <div ref={containerRef} className="relative">
      <div className="relative">
        <Input
          value={query}
          onChange={e => setQuery(e.target.value)}
          onFocus={() => { if (results.length > 0) setOpen(true); }}
          placeholder={placeholder ?? 'Comune di nascita…'}
          disabled={disabled}
          autoComplete="off"
        />
        {loading && (
          <Loader2 className="absolute right-2 top-1/2 -translate-y-1/2 h-4 w-4 animate-spin text-muted-foreground" />
        )}
      </div>

      {open && (
        <div className="absolute z-50 mt-1 w-full rounded-md border bg-popover shadow-md max-h-60 overflow-auto">
          {results.length > 0 ? (
            results.map(c => (
              <button
                key={c.id}
                type="button"
                className="w-full text-left px-3 py-2 text-sm hover:bg-accent hover:text-accent-foreground"
                onClick={() => handleSelect(c)}
              >
                {c.nome} ({c.siglaProvincia})
              </button>
            ))
          ) : searched && !loading ? (
            <div className="px-3 py-2 text-sm text-muted-foreground">Nessun comune trovato</div>
          ) : null}
        </div>
      )}
    </div>
  );
};

export default ComuneAutocomplete;
