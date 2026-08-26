import { useState, useEffect, useCallback } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Checkbox } from '@/components/ui/checkbox';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Search, Filter, Eye, Upload, AlertTriangle, Trash2, X, ChevronDown } from 'lucide-react';
import { getBookings, deleteBooking, type BookingListItem } from '@/api/bookingApi';
import { getSettings, type TenantSettingsDTO } from '@/api/settingsApi';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useLookup } from '@/contexts/LookupContext';
import { useToast } from '@/hooks/use-toast';

const statusColors: Record<string, string> = {
  imported: 'bg-muted text-muted-foreground',                                        // grigio
  enriched: 'bg-blue-100 text-blue-700 dark:bg-blue-950/30 dark:text-blue-300',      // blu
  ready: 'bg-sky-100 text-sky-700 dark:bg-sky-950/30 dark:text-sky-300',             // azzurro
  doc_issued: 'bg-success/10 text-success',                                          // verde
  settled: 'bg-emerald-600/20 text-emerald-800 dark:text-emerald-300',               // verde scuro
  cancelled: 'bg-destructive/10 text-destructive',                                   // rosso
};

// Label brevi per il badge stato (fallback alla descrizione lookup se codice ignoto)
const statusLabels: Record<string, string> = {
  imported: 'Importata',
  enriched: 'Arricchita',
  ready: 'Pronta',
  doc_issued: 'Doc. emesso',
  settled: 'Liquidata',
  cancelled: 'Annullata',
};

// Stati singoli selezionabili nel dropdown multi-check (ordine di visualizzazione)
const STATO_OPTIONS: { codice: string; label: string }[] = [
  { codice: 'imported', label: 'Importata' },
  { codice: 'enriched', label: 'Arricchita' },
  { codice: 'ready', label: 'Pronta' },
  { codice: 'doc_issued', label: 'Doc. emesso' },
  { codice: 'settled', label: 'Liquidata' },
  { codice: 'cancelled', label: 'Annullata' },
];

const channelColors: Record<string, string> = {
  airbnb: 'bg-[#FF5A5F]/10 text-[#FF5A5F]',
  booking: 'bg-[#003580]/10 text-[#003580]',
  vrbo: 'bg-[#3B5998]/10 text-[#3B5998]',
};

const isFinalStatus = (status: string) =>
  ['doc_issued', 'settled', 'cancelled'].includes(status);

const BookingsList = () => {
  const navigate = useNavigate();
  const { toast } = useToast();
  const { lookups, getLabelByCodice } = useLookup();

  const getStatusLabel = (codice: string) =>
    statusLabels[codice] ?? getLabelByCodice(lookups?.statiPrenotazione ?? [], codice);
  const [searchParams, setSearchParams] = useSearchParams();
  // Tutti i filtri sono derivati dall'URL (persistono a refresh e back/forward).
  const search = searchParams.get('q') ?? '';
  const channelFilter = searchParams.get('channel') ?? 'all';
  const statiSelezionati = new Set<string>(searchParams.get('stati')?.split(',').filter(Boolean) ?? []);
  const dateFrom = searchParams.get('dateFrom') ?? '';
  const dateTo = searchParams.get('dateTo') ?? '';
  const datePreset = searchParams.get('preset') ?? '';
  const [qInput, setQInput] = useState(searchParams.get('q') ?? '');
  // Input date locali: scrivere l'URL a ogni keystroke rimonterebbe il valore
  // mentre l'utente digita l'anno a mano, azzerando il campo. L'URL si aggiorna onBlur.
  const [dateFromInput, setDateFromInput] = useState(dateFrom);
  const [dateToInput, setDateToInput] = useState(dateTo);
  const [allBookings, setAllBookings] = useState<BookingListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [settings, setSettings] = useState<TenantSettingsDTO | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());

  // Aggiorna un singolo parametro URL (replace:true → non riempie la history).
  const updateFilter = useCallback((key: string, value: string | null) => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      if (value === null || value === '') next.delete(key);
      else next.set(key, value);
      return next;
    }, { replace: true });
  }, [setSearchParams]);

  // Ricerca testuale: input locale fluido + debounce 400ms verso l'URL.
  useEffect(() => {
    const t = setTimeout(() => updateFilter('q', qInput || null), 400);
    return () => clearTimeout(t);
  }, [qInput, updateFilter]);

  // Riallinea l'input al valore URL (refresh / back-forward).
  useEffect(() => { setQInput(search); }, [search]);

  // Riallinea gli input date quando l'URL cambia dall'esterno (preset, X, back/forward).
  useEffect(() => { setDateFromInput(dateFrom); }, [dateFrom]);
  useEffect(() => { setDateToInput(dateTo); }, [dateTo]);

  const today = new Date().toISOString().slice(0, 10);

  useEffect(() => {
    getSettings().then(setSettings).catch(() => { /* soglia di fallback se i settings non caricano */ });
  }, []);

  const penaltyThreshold = settings?.documentWindowDays ?? 12;

  const loadBookings = useCallback(() => {
    setLoading(true);
    setError(null);
    const params: Parameters<typeof getBookings>[0] = {};
    if (channelFilter !== 'all') params.channel = channelFilter;
    // Lo stato è filtrato client-side (multi-check): carichiamo tutti gli stati.
    return getBookings(params)
      .then(data => setAllBookings(data))
      .catch(err => setError(err.message ?? 'Errore nel caricamento'))
      .finally(() => setLoading(false));
  }, [channelFilter]);

  useEffect(() => { loadBookings(); }, [loadBookings]);

  const filtered = allBookings
    .filter(b =>
      search === '' ||
      b.guestName.toLowerCase().includes(search.toLowerCase()) ||
      b.propertyName.toLowerCase().includes(search.toLowerCase()) ||
      b.externalBookingId.toLowerCase().includes(search.toLowerCase()) ||
      (b.ownerName ?? '').toLowerCase().includes(search.toLowerCase())
    )
    // Filtro date check-in/check-out: include il booking se il periodo si sovrappone al range
    .filter(b => {
      if (!dateFrom && !dateTo) return true;
      const from = dateFrom || '0000-01-01';
      const to = dateTo || '9999-12-31';
      return b.checkinDate <= to && b.checkoutDate >= from;
    })
    // Filtro stato multi-check ("da_completare" è una combinazione, gestito a parte)
    .filter(b => {
      if (statiSelezionati.size === 0) return true;
      if (statiSelezionati.has('da_completare')) {
        const oggi = new Date().toISOString().split('T')[0];
        return b.checkoutDate <= oggi && !['doc_issued', 'settled', 'cancelled'].includes(b.statoPrenotazione);
      }
      return statiSelezionati.has(b.statoPrenotazione);
    });

  const visible = filtered;
  const allSelected = visible.length > 0 && visible.every(b => selectedIds.has(b.id));

  const toggleId = (id: number) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const toggleAll = () => {
    setSelectedIds(allSelected ? new Set() : new Set(visible.map(b => b.id)));
  };

  const applyPreset = (preset: string) => {
    const oggi = new Date();
    const fmt = (d: Date) => d.toISOString().split('T')[0];
    let from = '', to = '';
    switch (preset) {
      case 'ieri': { const d = new Date(oggi); d.setDate(oggi.getDate() - 1); from = fmt(d); to = fmt(d); break; }
      case '3gg': { const d = new Date(oggi); d.setDate(oggi.getDate() - 3); from = fmt(d); to = fmt(oggi); break; }
      case '7gg': { const d = new Date(oggi); d.setDate(oggi.getDate() - 7); from = fmt(d); to = fmt(oggi); break; }
      case '14gg': { const d = new Date(oggi); d.setDate(oggi.getDate() - 14); from = fmt(d); to = fmt(oggi); break; }
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

  // Toggle stato nel multi-check. "da_completare" è esclusivo con gli stati singoli.
  const toggleStato = (codice: string) => {
    let arr: string[];
    if (codice === 'da_completare') {
      arr = statiSelezionati.has('da_completare') ? [] : ['da_completare']; // esclusivo
    } else {
      const next = new Set(statiSelezionati);
      next.delete('da_completare'); // stati singoli non si combinano con "da completare"
      if (next.has(codice)) next.delete(codice); else next.add(codice);
      arr = [...next];
    }
    updateFilter('stati', arr.length ? arr.join(',') : null);
  };

  const statoTriggerLabel = statiSelezionati.size === 0
    ? 'Tutti gli stati'
    : statiSelezionati.size === 1
      ? (statiSelezionati.has('da_completare') ? 'Da completare' : getStatusLabel([...statiSelezionati][0]))
      : `${statiSelezionati.size} stati`;

  const handleDeleteSelected = async () => {
    const ids = Array.from(selectedIds);
    const msg = `Eliminare ${ids.length} booking e tutti i dati collegati (documenti, ritenute, liquidazioni)? Operazione irreversibile.`;
    if (!window.confirm(msg)) return;
    let ok = 0;
    for (const id of ids) {
      try {
        await deleteBooking(id);
        ok++;
      } catch (err) {
        toast({ title: 'Errore eliminazione', description: `Booking #${id}: ${(err as Error).message}`, variant: 'destructive' });
      }
    }
    if (ok > 0) toast({ title: 'Eliminazione completata', description: `${ok} booking eliminati` });
    setSelectedIds(new Set());
    await loadBookings();
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Prenotazioni</h1>
          <p className="text-sm text-muted-foreground">{filtered.length} prenotazioni trovate</p>
        </div>
        <div className="flex items-center gap-2">
          {searchParams.toString() !== '' && (
            <Button size="sm" variant="outline" className="gap-2"
              onClick={() => { setSearchParams(new URLSearchParams(), { replace: true }); setQInput(''); }}>
              <X className="h-4 w-4" /> Reset filtri
            </Button>
          )}
          <Button size="sm" className="gap-2" onClick={() => navigate('/import/bookings')}>
            <Upload className="h-4 w-4" /> Import
          </Button>
        </div>
      </div>

      {/* Filters */}
      <Card>
        <CardContent className="p-4">
          <div className="flex flex-wrap gap-3">
            <div className="relative flex-1 min-w-[200px]">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input placeholder="Cerca ospite, immobile, ID..." value={qInput} onChange={e => setQInput(e.target.value)} className="pl-9" />
            </div>
            <Popover>
              <PopoverTrigger asChild>
                <Button variant="outline" className="w-[200px] justify-between font-normal">
                  <span className="flex items-center gap-2 truncate">
                    <Filter className="h-3.5 w-3.5 shrink-0" />
                    {statoTriggerLabel}
                  </span>
                  <ChevronDown className="h-4 w-4 opacity-50 shrink-0" />
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-[240px] p-2" align="start">
                <div
                  role="button"
                  tabIndex={0}
                  className="flex w-full items-center gap-2 rounded px-2 py-1.5 text-sm hover:bg-accent cursor-pointer"
                  onClick={() => toggleStato('da_completare')}
                  onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggleStato('da_completare'); } }}
                >
                  <Checkbox checked={statiSelezionati.has('da_completare')} className="pointer-events-none" />
                  <span className="flex-1 text-left">Da completare</span>
                  <span className="h-2.5 w-2.5 rounded-full bg-muted-foreground" />
                </div>
                <div className="my-1 h-px bg-border" />
                {STATO_OPTIONS.map(opt => (
                  <div
                    key={opt.codice}
                    role="button"
                    tabIndex={0}
                    className="flex w-full items-center gap-2 rounded px-2 py-1.5 text-sm hover:bg-accent cursor-pointer"
                    onClick={() => toggleStato(opt.codice)}
                    onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggleStato(opt.codice); } }}
                  >
                    <Checkbox checked={statiSelezionati.has(opt.codice)} className="pointer-events-none" />
                    <span className="flex-1 text-left">{opt.label}</span>
                    <Badge variant="outline" className={`${statusColors[opt.codice]} text-[10px] px-1.5`}>&nbsp;</Badge>
                  </div>
                ))}
                {statiSelezionati.size > 0 && (
                  <>
                    <div className="my-1 h-px bg-border" />
                    <button
                      type="button"
                      className="w-full rounded px-2 py-1.5 text-sm text-muted-foreground hover:bg-accent text-left"
                      onClick={() => updateFilter('stati', null)}
                    >
                      Deseleziona tutto
                    </button>
                  </>
                )}
              </PopoverContent>
            </Popover>
            <Select value={channelFilter} onValueChange={(value) => updateFilter('channel', value === 'all' ? null : value)}>
              <SelectTrigger className="w-[140px]">
                <SelectValue placeholder="Canale" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Tutti i canali</SelectItem>
                {lookups?.canaliOta.filter(c => c.attivo).map(c => (
                  <SelectItem key={c.codice} value={c.codice}>{c.descrizione}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* Seconda riga: filtro per data check-in/check-out */}
          <div className="flex flex-wrap items-center gap-3 mt-3 pt-3 border-t">
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">Dal</span>
              <Input
                type="date"
                value={dateFromInput}
                onChange={e => setDateFromInput(e.target.value)}
                onBlur={e => {
                  if (e.target.value !== dateFrom) {
                    updateFilter('dateFrom', e.target.value || null);
                    updateFilter('preset', null);
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
                    updateFilter('dateTo', e.target.value || null);
                    updateFilter('preset', null);
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

          {/* Badge degli stati selezionati con X per rimozione singola */}
          {statiSelezionati.size > 0 && (
            <div className="flex flex-wrap items-center gap-2 mt-3 pt-3 border-t">
              {[...statiSelezionati].map(codice => {
                const label = codice === 'da_completare' ? 'Da completare' : getStatusLabel(codice);
                const cls = codice === 'da_completare' ? 'bg-muted text-muted-foreground' : statusColors[codice];
                return (
                  <Badge key={codice} variant="outline" className={`${cls} gap-1`}>
                    {label}
                    <button type="button" onClick={() => toggleStato(codice)} className="ml-0.5 hover:opacity-70">
                      <X className="h-3 w-3" />
                    </button>
                  </Badge>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Barra azioni selezione (solo local/test: la delete backend è @Profile) */}
      {selectedIds.size > 0 && (
        <div className="flex items-center gap-3 rounded-md border border-destructive/20 bg-destructive/5 p-3">
          <span className="text-sm font-medium">{selectedIds.size} selezionati</span>
          <Button variant="destructive" size="sm" className="gap-2" onClick={handleDeleteSelected}>
            <Trash2 className="h-4 w-4" /> Elimina selezionati
          </Button>
          <Button variant="ghost" size="sm" onClick={() => setSelectedIds(new Set())}>
            Annulla selezione
          </Button>
        </div>
      )}

      {/* Table */}
      <Card>
        <CardContent className="p-0">
          {loading && (
            <div className="p-6 text-center text-sm text-muted-foreground">Caricamento...</div>
          )}
          {error && (
            <div className="p-6 text-center text-sm text-destructive">{error}</div>
          )}
          {!loading && !error && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-10">
                    <Checkbox checked={allSelected} onCheckedChange={toggleAll} aria-label="Seleziona tutti" />
                  </TableHead>
                  <TableHead>ID / Canale</TableHead>
                  <TableHead>Ospite</TableHead>
                  <TableHead>Immobile</TableHead>
                  <TableHead>Proprietario</TableHead>
                  <TableHead>Check-in</TableHead>
                  <TableHead>Check-out</TableHead>
                  <TableHead className="text-right">Notti</TableHead>
                  <TableHead className="text-right">Lordo €</TableHead>
                  <TableHead>Stato</TableHead>
                  <TableHead className="w-10"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {visible.map(b => {
                  const checkoutDate = new Date(b.checkoutDate);
                  const todayDate = new Date(today);
                  const daysSinceCheckout = Math.floor((todayDate.getTime() - checkoutDate.getTime()) / (1000 * 60 * 60 * 24));
                  const isOverdue = daysSinceCheckout > 0 && !isFinalStatus(b.statoPrenotazione);
                  const isPenalty = daysSinceCheckout > penaltyThreshold && isOverdue;
                  const channelKey = b.channelName.toLowerCase();

                  return (
                    <TableRow key={b.id} className={`cursor-pointer ${isPenalty ? 'bg-destructive/8 hover:bg-destructive/12' : isOverdue ? 'bg-warning/6 hover:bg-warning/10' : ''}`} onClick={() => navigate(`/bookings/${b.id}`)}>
                      <TableCell onClick={(e) => e.stopPropagation()}>
                        <Checkbox
                          checked={selectedIds.has(b.id)}
                          onCheckedChange={() => toggleId(b.id)}
                          aria-label={`Seleziona booking ${b.id}`}
                        />
                      </TableCell>
                      <TableCell>
                        <div>
                          <p className="text-xs font-mono text-muted-foreground">{b.externalBookingId}</p>
                          <Badge variant="outline" className={`text-[10px] mt-0.5 ${channelColors[channelKey] || ''}`}>
                            {b.channelName}
                          </Badge>
                        </div>
                      </TableCell>
                      <TableCell className="font-medium text-sm">{b.guestName}</TableCell>
                      <TableCell className="text-sm">{b.propertyName}</TableCell>
                      <TableCell className="text-sm">
                        {b.ownerName && b.fkOwnerId ? (
                          <button
                            type="button"
                            className="text-primary hover:underline"
                            title="Vedi i documenti di questo proprietario"
                            onClick={(e) => { e.stopPropagation(); navigate(`/documents?ownerId=${b.fkOwnerId}`); }}
                          >
                            {b.ownerName}
                          </button>
                        ) : (
                          '—'
                        )}
                      </TableCell>
                      <TableCell className="text-sm">{b.checkinDate}</TableCell>
                      <TableCell className="text-sm">{b.checkoutDate}</TableCell>
                      <TableCell className="text-right text-sm">{b.nights}</TableCell>
                      <TableCell className="text-right text-sm font-medium">€{b.grossAmount.toLocaleString('it-IT')}</TableCell>
                      <TableCell>
                        <div className="flex items-center gap-1.5">
                          {isPenalty && <AlertTriangle className="h-3.5 w-3.5 text-destructive flex-shrink-0" />}
                          <Badge variant="outline" className={
                            isPenalty ? 'bg-destructive/15 text-destructive border-destructive/30' :
                            isOverdue ? statusColors[b.statoPrenotazione] :
                            statusColors[b.statoPrenotazione]
                          }>
                            {isPenalty ? `${daysSinceCheckout}gg - PENALE` : isOverdue ? `${daysSinceCheckout}gg - Scaduta` : getStatusLabel(b.statoPrenotazione)}
                          </Badge>
                        </div>
                      </TableCell>
                      <TableCell>
                        <Button variant="ghost" size="icon" className="h-7 w-7">
                          <Eye className="h-3.5 w-3.5" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default BookingsList;
