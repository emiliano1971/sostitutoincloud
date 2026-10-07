import { Button } from '@/components/ui/button';

interface PaginazioneProps {
  /** Pagina corrente, 0-based. */
  page: number;
  totalPages: number;
  totalElements: number;
  /** Etichetta del totale, es. "documenti" o "prenotazioni". */
  etichetta: string;
  onPageChange: (page: number) => void;
}

/**
 * Controlli di paginazione delle liste lato server: « ‹ 1…5 › » con al massimo 5 numeri
 * centrati sulla pagina corrente. Non mostra nulla con una sola pagina.
 */
const Paginazione = ({ page, totalPages, totalElements, etichetta, onPageChange }: PaginazioneProps) => {
  if (totalPages <= 1) return null;
  const inizio = Math.max(0, Math.min(page - 2, totalPages - 5));
  const pagine = Array.from({ length: Math.min(5, totalPages) }, (_, i) => inizio + i);
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 border-t px-4 py-3">
      <p className="text-sm text-muted-foreground">
        {totalElements} {etichetta} — Pagina {page + 1} di {totalPages}
      </p>
      <div className="flex items-center gap-1">
        <Button variant="outline" size="sm" onClick={() => onPageChange(0)} disabled={page === 0} aria-label="Prima pagina">«</Button>
        <Button variant="outline" size="sm" onClick={() => onPageChange(page - 1)} disabled={page === 0} aria-label="Pagina precedente">‹</Button>
        {pagine.map(p => (
          <Button key={p} variant={p === page ? 'default' : 'outline'} size="sm" onClick={() => onPageChange(p)}>
            {p + 1}
          </Button>
        ))}
        <Button variant="outline" size="sm" onClick={() => onPageChange(page + 1)} disabled={page >= totalPages - 1} aria-label="Pagina successiva">›</Button>
        <Button variant="outline" size="sm" onClick={() => onPageChange(totalPages - 1)} disabled={page >= totalPages - 1} aria-label="Ultima pagina">»</Button>
      </div>
    </div>
  );
};

export default Paginazione;
