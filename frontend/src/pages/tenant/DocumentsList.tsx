import { useState, useEffect, useMemo } from 'react';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Search, Filter, Eye, Loader2, AlertCircle, ChevronsUpDown, ChevronUp, ChevronDown, Info, X, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { getDocuments, elaboraRisposteSdi, type DocumentListItem } from '@/api/documentApi';
import { useToast } from '@/hooks/use-toast';
import { useNavigate, useSearchParams } from 'react-router-dom';

// Data locale in formato yyyy-MM-dd. NON usare .toISOString(): converte in UTC e
// nelle ore notturne (Europe/Rome = UTC+1/+2) restituirebbe il giorno precedente.
const toLocalISO = (d: Date): string => {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const g = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${g}`;
};

const statusColors: Record<string, string> = {
  draft: 'bg-muted text-muted-foreground',
  ready: 'bg-primary/10 text-primary',
  sent_sdi: 'bg-warning/10 text-warning',
  accepted: 'bg-success/10 text-success',
  rejected: 'bg-destructive/10 text-destructive',
  error: 'bg-destructive/10 text-destructive',
};

type SortDir = 'asc' | 'desc';

interface SortableThProps {
  label: string;
  colKey: string;
  sortKey: string;
  sortDir: SortDir;
  onSort: (key: string) => void;
  align?: 'left' | 'right';
}

const SortableTh = ({ label, colKey, sortKey, sortDir, onSort, align = 'left' }: SortableThProps) => {
  const active = sortKey === colKey;
  const Icon = active ? (sortDir === 'asc' ? ChevronUp : ChevronDown) : ChevronsUpDown;
  return (
    <TableHead
      className={`cursor-pointer select-none ${align === 'right' ? 'text-right' : ''}`}
      onClick={() => onSort(colKey)}
    >
      <span className={`inline-flex items-center gap-1 ${align === 'right' ? 'flex-row-reverse' : ''}`}>
        {label}
        <Icon className={`h-3.5 w-3.5 ${active ? 'text-primary' : 'text-muted-foreground/40'}`} />
      </span>
    </TableHead>
  );
};

const DocumentsList = () => {
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [docs, setDocs] = useState<DocumentListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [sortKey, setSortKey] = useState<string>('issueDate');
  const [sortDir, setSortDir] = useState<SortDir>('desc');
  const [isProcessingSdi, setIsProcessingSdi] = useState(false);
  const [sdiDettagli, setSdiDettagli] = useState<string[] | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const { toast } = useToast();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  // Filtro per data emissione persistito nell'URL, stesso pattern di BookingsList.
  const dateFrom = searchParams.get('dateFrom') ?? '';
  const dateTo = searchParams.get('dateTo') ?? '';
  const datePreset = searchParams.get('preset') ?? '';
  const ownerIdParam = searchParams.get('ownerId');
  const ownerIdFilter = ownerIdParam ? parseInt(ownerIdParam) : null;
  // Filtro per tipo documento: i valori sono i codice della lookup tipo_documento
  // ('fattura' | 'ricevuta' | 'nota_credito'), come restituiti da documentType.
  const tipoFilter = searchParams.get('tipo') ?? '';
  // Input date locali: scrivere l'URL a ogni keystroke rimonterebbe il valore
  // mentre l'utente digita l'anno a mano, azzerando il campo. L'URL si aggiorna onBlur.
  const [dateFromInput, setDateFromInput] = useState(dateFrom);
  const [dateToInput, setDateToInput] = useState(dateTo);

  // Riallinea gli input date quando l'URL cambia dall'esterno (preset, X, back/forward).
  useEffect(() => { setDateFromInput(dateFrom); }, [dateFrom]);
  useEffect(() => { setDateToInput(dateTo); }, [dateTo]);

  const handleSort = (key: string) => {
    if (key === sortKey) {
      setSortDir(prev => (prev === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir(key === 'issueDate' ? 'desc' : 'asc');
    }
  };

  useEffect(() => {
    setIsLoading(true);
    setError(null);
    getDocuments(statusFilter !== 'all' ? { stato: statusFilter } : {})
      .then(setDocs)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, [statusFilter, reloadKey]);

  const updateFilter = (key: string, value: string | null) => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      if (value === null || value === '') next.delete(key);
      else next.set(key, value);
      return next;
    }, { replace: true });
  };

  const applyPreset = (preset: string) => {
    const oggi = new Date();
    let from = '', to = '';
    switch (preset) {
      case 'ieri': { const d = new Date(oggi); d.setDate(oggi.getDate() - 1); from = toLocalISO(d); to = toLocalISO(d); break; }
      case '3gg': { const d = new Date(oggi); d.setDate(oggi.getDate() - 3); from = toLocalISO(d); to = toLocalISO(oggi); break; }
      case '7gg': { const d = new Date(oggi); d.setDate(oggi.getDate() - 7); from = toLocalISO(d); to = toLocalISO(oggi); break; }
      case '14gg': { const d = new Date(oggi); d.setDate(oggi.getDate() - 14); from = toLocalISO(d); to = toLocalISO(oggi); break; }
      default: preset = '';
    }
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      if (preset) next.set('preset', preset); else next.delete('preset');
      if (from) next.set('dateFrom', from); else next.delete('dateFrom');
      if (to) next.set('dateTo', to); else next.delete('dateTo');
      return next;
    }, { replace: true });
  };

  const clearDateFilter = () => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      next.delete('dateFrom'); next.delete('dateTo'); next.delete('preset');
      return next;
    }, { replace: true });
  };

  // Legge le ricevute SDI da incoming/: aggiorna gli stati e ricarica la lista.
  const handleElaboraRisposte = async () => {
    setIsProcessingSdi(true);
    try {
      const r = await elaboraRisposteSdi();
      const parti = [`${r.accettati} accettati`, `${r.scartati} scartati`];
      if (r.metadati > 0) parti.push(`${r.metadati} metadati`);
      if (r.errori > 0) parti.push(`${r.errori} errori`);
      toast({ title: `Elaborati ${r.elaborati}`, description: parti.join(', ') });
      if (r.dettagli && r.dettagli.length > 0) setSdiDettagli(r.dettagli);
      setReloadKey(k => k + 1);
    } catch (err) {
      toast({
        title: 'Errore elaborazione risposte SDI',
        description: err instanceof Error ? err.message : 'Errore imprevisto',
        variant: 'destructive',
      });
    } finally {
      setIsProcessingSdi(false);
    }
  };

  const filtered = docs
    .filter(d =>
      (search === '' ||
        d.documentNumber.toLowerCase().includes(search.toLowerCase()) ||
        d.recipientName.toLowerCase().includes(search.toLowerCase()) ||
        (d.ownerName ?? '').toLowerCase().includes(search.toLowerCase()))
      && (ownerIdFilter == null || d.fkOwnerId === ownerIdFilter)
    )
    // Filtro data emissione: confronto lessicografico su ISO yyyy-MM-dd
    .filter(d => {
      if (!dateFrom && !dateTo) return true;
      const from = dateFrom || '0000-01-01';
      const to = dateTo || '9999-12-31';
      return d.issueDate >= from && d.issueDate <= to;
    })
    .filter(d => {
      if (!tipoFilter) return true;
      return d.documentType === tipoFilter;
    });

  const ownerFilterName = ownerIdFilter != null
    ? (docs.find(d => d.fkOwnerId === ownerIdFilter)?.ownerName ?? `owner #${ownerIdFilter}`)
    : null;

  const sorted = useMemo(() => {
    return [...filtered].sort((a, b) => {
      // Indicizzazione con keyof: DocumentListItem è una interface e quindi non ha
      // index signature implicita — il cast a Record<string, unknown> darebbe TS2352
      // sui type checker che applicano l'assegnabilità (es. servizio TS di IntelliJ).
      const valA = a[sortKey as keyof DocumentListItem];
      const valB = b[sortKey as keyof DocumentListItem];
      const dir = sortDir === 'asc' ? 1 : -1;
      if (valA == null) return 1;
      if (valB == null) return -1;
      if (typeof valA === 'number' && typeof valB === 'number') return (valA - valB) * dir;
      return String(valA).localeCompare(String(valB), 'it') * dir;
    });
  }, [filtered, sortKey, sortDir]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Documenti Fiscali</h1>
        <p className="text-sm text-muted-foreground">
          {isLoading ? 'Caricamento…' : `${filtered.length} documenti`}
        </p>
      </div>

      <Card>
        <CardContent className="p-4">
          <div className="flex flex-wrap gap-3">
            <div className="relative flex-1 min-w-[200px]">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input placeholder="Cerca numero, destinatario..." value={search} onChange={e => setSearch(e.target.value)} className="pl-9" />
            </div>
            <Select value={statusFilter} onValueChange={setStatusFilter}>
              <SelectTrigger className="w-[160px]"><Filter className="h-3.5 w-3.5 mr-2" /><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Tutti gli stati</SelectItem>
                <SelectItem value="draft">Bozza</SelectItem>
                <SelectItem value="ready">Pronto</SelectItem>
                <SelectItem value="sent_sdi">Inviato SDI</SelectItem>
                <SelectItem value="accepted">Accettato</SelectItem>
                <SelectItem value="rejected">Rifiutato</SelectItem>
              </SelectContent>
            </Select>
            {/* Filtro tipo documento: in memoria, persistito nell'URL (?tipo=fattura).
                Stesso stile pill dei preset date. "Tutti" include anche le note di credito. */}
            <div className="flex items-center gap-2">
              {[
                { key: '', label: 'Tutti' },
                { key: 'fattura', label: 'Fatture' },
                { key: 'ricevuta', label: 'Ricevute' },
              ].map(t => (
                <Button
                  key={t.key || 'tutti'}
                  variant={tipoFilter === t.key ? 'default' : 'outline'}
                  size="sm"
                  className="h-7 text-xs"
                  onClick={() => updateFilter('tipo', t.key)}
                >
                  {t.label}
                </Button>
              ))}
            </div>
            <Button
              variant="outline"
              className="gap-2"
              onClick={handleElaboraRisposte}
              disabled={isProcessingSdi}
              title="Legge le ricevute SDI ricevute e aggiorna lo stato dei documenti"
            >
              {isProcessingSdi
                ? <Loader2 className="h-4 w-4 animate-spin" />
                : <RefreshCw className="h-4 w-4" />}
              {isProcessingSdi ? 'Elaborazione…' : 'Verifica risposte SDI'}
            </Button>
          </div>

          {/* Seconda riga: filtro per data emissione */}
          <div className="flex flex-wrap items-center gap-3 mt-3 pt-3 border-t">
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">Dal</span>
              <Input
                type="date"
                value={dateFromInput}
                onChange={e => setDateFromInput(e.target.value)}
                onBlur={e => {
                  if (e.target.value !== dateFrom) {
                    // Una sola setSearchParams: due updateFilter consecutivi si annullerebbero
                    // (react-router passa allo updater il searchParams del render corrente).
                    setSearchParams(prev => {
                      const next = new URLSearchParams(prev);
                      if (e.target.value) next.set('dateFrom', e.target.value);
                      else next.delete('dateFrom');
                      next.delete('preset');
                      return next;
                    }, { replace: true });
                  }
                }}
                className="w-[150px]"
              />
              <span className="text-sm text-muted-foreground">Al</span>
              <Input
                type="date"
                value={dateToInput}
                onChange={e => setDateToInput(e.target.value)}
                onBlur={e => {
                  if (e.target.value !== dateTo) {
                    setSearchParams(prev => {
                      const next = new URLSearchParams(prev);
                      if (e.target.value) next.set('dateTo', e.target.value);
                      else next.delete('dateTo');
                      next.delete('preset');
                      return next;
                    }, { replace: true });
                  }
                }}
                className="w-[150px]"
              />
              {(dateFrom || dateTo) && (
                <Button variant="ghost" size="icon" className="h-8 w-8" title="Azzera filtro date" onClick={clearDateFilter}>
                  <X className="h-4 w-4" />
                </Button>
              )}
            </div>
            <div className="flex items-center gap-2">
              {[
                { key: 'ieri', label: 'Ieri' },
                { key: '3gg', label: '3gg' },
                { key: '7gg', label: '7gg' },
                { key: '14gg', label: '14gg' },
              ].map(p => (
                <Button
                  key={p.key}
                  variant={datePreset === p.key ? 'default' : 'outline'}
                  size="sm"
                  className="h-7 text-xs"
                  onClick={() => applyPreset(p.key)}
                >
                  {p.label}
                </Button>
              ))}
            </div>
          </div>
        </CardContent>
      </Card>

      {ownerIdFilter != null && (
        <div className="flex items-start gap-2 rounded-md border border-primary/20 bg-primary/5 p-3 text-sm">
          <Info className="h-4 w-4 mt-0.5 text-primary shrink-0" />
          <span className="flex-1 text-muted-foreground">
            Documenti filtrati per proprietario — <strong>{ownerFilterName}</strong>
          </span>
          <button
            type="button"
            className="text-muted-foreground hover:text-foreground"
            title="Rimuovi filtro proprietario"
            // Rimuove solo ownerId: gli altri filtri (date, preset) restano nell'URL.
            onClick={() => setSearchParams(prev => {
              const next = new URLSearchParams(prev);
              next.delete('ownerId');
              return next;
            }, { replace: true })}
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      <Card>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="flex items-center justify-center py-16 text-muted-foreground gap-2">
              <Loader2 className="h-5 w-5 animate-spin" />
              <span>Caricamento documenti…</span>
            </div>
          ) : error ? (
            <div className="flex items-center justify-center py-16 text-destructive gap-2">
              <AlertCircle className="h-5 w-5" />
              <span>{error}</span>
            </div>
          ) : filtered.length === 0 ? (
            <div className="flex items-center justify-center py-16 text-muted-foreground">
              Nessun documento
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <SortableTh label="Numero" colKey="documentNumber" sortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                  <SortableTh label="Tipo" colKey="documentType" sortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                  <SortableTh label="Destinatario" colKey="recipientName" sortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                  <SortableTh label="Proprietario" colKey="ownerName" sortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                  <SortableTh label="Immobile" colKey="propertyName" sortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                  <SortableTh label="Data" colKey="issueDate" sortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                  <SortableTh label="Totale €" colKey="totalAmount" sortKey={sortKey} sortDir={sortDir} onSort={handleSort} align="right" />
                  <SortableTh label="Stato SDI" colKey="statoDocumento" sortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                  <TableHead className="w-10"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {sorted.slice(0, 20).map(d => (
                  <TableRow key={d.id}>
                    <TableCell>
                      {/* Numero documento + id prenotazione collegata, cliccabile.
                          stopPropagation: la riga potrebbe diventare cliccabile in futuro. */}
                      <div className="flex flex-col gap-0.5">
                        <span className="font-mono text-sm">{d.documentNumber}</span>
                        {d.fkBookingId && d.externalBookingId && (
                          <button
                            type="button"
                            onClick={e => { e.stopPropagation(); navigate(`/bookings/${d.fkBookingId}`); }}
                            className="text-xs text-primary hover:underline text-left font-mono truncate max-w-[140px]"
                            title={d.externalBookingId}
                          >
                            {d.externalBookingId}
                          </button>
                        )}
                      </div>
                    </TableCell>
                    <TableCell><Badge variant="outline" className="text-xs">{d.documentType}</Badge></TableCell>
                    <TableCell className="text-sm font-medium">{d.recipientName}</TableCell>
                    <TableCell className="text-sm">
                      {d.ownerName && d.fkOwnerId ? (
                        <button
                          type="button"
                          className="text-primary hover:underline"
                          title="Vedi gli F24 di questo proprietario"
                          onClick={() => navigate(`/f24?ownerId=${d.fkOwnerId}`)}
                        >
                          {d.ownerName}
                        </button>
                      ) : (
                        '—'
                      )}
                    </TableCell>
                    <TableCell className="text-sm">{d.propertyName}</TableCell>
                    <TableCell className="text-sm">{d.issueDate}</TableCell>
                    <TableCell className="text-right font-medium">€{d.totalAmount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</TableCell>
                    <TableCell><Badge variant="outline" className={statusColors[d.statoDocumento]}>{d.statoDocumento}</Badge></TableCell>
                    <TableCell><Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => navigate(`/documents/${d.id}`)}><Eye className="h-3.5 w-3.5" /></Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Dettagli dell'elaborazione SDI: scarti, mancate consegne, file ignorati */}
      <Dialog open={sdiDettagli !== null} onOpenChange={open => { if (!open) setSdiDettagli(null); }}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>Esiti da verificare</DialogTitle>
            <DialogDescription>
              Risposte SDI che richiedono attenzione. Le consegne andate a buon fine non sono elencate.
            </DialogDescription>
          </DialogHeader>
          <ul className="space-y-2 text-sm max-h-[60vh] overflow-y-auto">
            {(sdiDettagli ?? []).map((d, i) => (
              <li key={i} className="flex items-start gap-2 rounded-md border border-destructive/20 bg-destructive/5 px-3 py-2">
                <AlertCircle className="h-4 w-4 mt-0.5 text-destructive shrink-0" />
                <span className="break-words">{d}</span>
              </li>
            ))}
          </ul>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default DocumentsList;

