import { useState, useEffect, useRef } from 'react';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Search, Filter, Eye, Loader2, AlertCircle, ChevronsUpDown, ChevronUp, ChevronDown, Info, X, RefreshCw, Ban, BarChart2, FileDown } from 'lucide-react';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { getDocuments, downloadDocumentPdf, elaboraRisposteSdi, getStatoFiscaleRicevuta, type DocumentListItem, type StatoFiscaleRicevuta } from '@/api/documentApi';
import { annullaNdc } from '@/api/bookingApi';
import Paginazione from '@/components/Paginazione';
import { useToast } from '@/hooks/use-toast';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { labelStatoDocumento, labelTipoDocumento, labelStatoCu } from '@/lib/statiLabels';

// Pannello "Stato fiscale": etichette e colori dei codici di stato restituiti dal backend
const f24StatoLabels: Record<string, string> = {
  draft: 'Bozza', ready: 'Pronto', sent: 'Inviato', paid: '✓ Pagato', error: 'Errore',
};
const f24StatoColors: Record<string, string> = {
  paid: 'bg-green-100 text-green-800 dark:bg-green-950/30 dark:text-green-300',
  ready: 'bg-amber-100 text-amber-800 dark:bg-amber-950/30 dark:text-amber-300',
  sent: 'bg-blue-100 text-blue-800 dark:bg-blue-950/30 dark:text-blue-300',
};
const liquidazioneColors: Record<string, string> = {
  paid: 'bg-green-100 text-green-800 dark:bg-green-950/30 dark:text-green-300',
  approved: 'bg-blue-100 text-blue-800 dark:bg-blue-950/30 dark:text-blue-300',
  calculated: 'bg-amber-100 text-amber-800 dark:bg-amber-950/30 dark:text-amber-300',
};
const cuColors: Record<string, string> = {
  sent: 'bg-green-100 text-green-800 dark:bg-green-950/30 dark:text-green-300',
  delivered: 'bg-green-100 text-green-800 dark:bg-green-950/30 dark:text-green-300',
  generated: 'bg-amber-100 text-amber-800 dark:bg-amber-950/30 dark:text-amber-300',
};
const fmtImporto = (v?: number | null) =>
  `€${Math.abs(v ?? 0).toLocaleString('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

/** Filtri rapidi sullo stato fiscale delle ricevute (parametro filtroFiscale del backend). */
const FILTRI_FISCALI = [
  { key: '', label: 'Tutti' },
  { key: 'da_liquidare', label: 'Da liquidare' },
  { key: 'f24_non_pagato', label: 'F24 non pagato' },
  { key: 'senza_cu', label: 'Senza CU' },
];

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
  annullata: 'bg-muted text-muted-foreground line-through',
};

// Nota di credito: badge tipo con colore distintivo
// Nota di credito in rosso (storno); il badge "Stornata" della fattura resta verde
const NDC_BADGE = 'text-destructive border-destructive';
/** NDC annullabile finché non è presa in carico dallo SDI (stessa regola di NdcService). */
const ndcAnnullabile = (d: DocumentListItem) =>
  d.documentType === 'nota_credito' && !['sent_sdi', 'accepted', 'annullata'].includes(d.statoDocumento);

// Stato liquidazione — stesse etichette e colori di BookingDetail
const settlementLabels: Record<string, string> = {
  pending: 'In attesa',
  calculated: 'Calcolata',
  approved: 'Approvata',
  paid: 'Pagata',
};

const settlementBadgeColors: Record<string, string> = {
  pending: 'bg-muted text-muted-foreground',
  calculated: 'bg-blue-100 text-blue-700 dark:bg-blue-950/30 dark:text-blue-300',
  approved: 'bg-warning/10 text-warning',
  paid: 'bg-success/10 text-success',
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
  // Paginazione lato server (dimensione pagina dalle impostazioni del tenant)
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  // Ricerca inviata al backend con un breve ritardo: niente richiesta a ogni tasto
  const [searchQuery, setSearchQuery] = useState('');
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
  // Filtro stato liquidazione: parametro 'liquidazione' del backend,
  // persistito nell'URL (?liquidazione=paid). 'none' = documenti non ancora liquidati.
  const settlementFilter = searchParams.get('liquidazione') ?? '';
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

  // Filtro rapido sulle ricevute (backend, ?fiscale=… nell'URL): con un filtro attivo la
  // lista contiene solo ricevute owner non annullate. Visibile con tipo 'Tutti' o 'Ricevute'.
  const filtroFiscale = searchParams.get('fiscale') ?? '';
  const filtriFiscaliVisibili = tipoFilter === '' || tipoFilter === 'ricevuta';
  const filtroFiscaleAttivo = filtriFiscaliVisibili ? filtroFiscale : '';

  useEffect(() => {
    const t = setTimeout(() => setSearchQuery(search.trim()), 300);
    return () => clearTimeout(t);
  }, [search]);

  // Filtri, ordinamento e paginazione sono tutti lato server. Al cambio di un filtro (o
  // dell'ordinamento) si riparte da pagina 0: se si è su un'altra pagina si azzera e il
  // caricamento parte dal render successivo, senza una richiesta inutile sulla pagina vecchia.
  const filterKey = JSON.stringify([statusFilter, tipoFilter, searchQuery, filtroFiscaleAttivo,
    dateFrom, dateTo, ownerIdFilter, settlementFilter, sortKey, sortDir]);
  const prevFilterKey = useRef(filterKey);
  useEffect(() => {
    if (prevFilterKey.current !== filterKey) {
      prevFilterKey.current = filterKey;
      if (page !== 0) { setPage(0); return; }
    }
    let annullato = false;   // risposta superata da una richiesta più recente
    setIsLoading(true);
    setError(null);
    getDocuments({
      stato: statusFilter !== 'all' ? statusFilter : undefined,
      tipo: tipoFilter || undefined,
      search: searchQuery || undefined,
      filtroFiscale: filtroFiscaleAttivo || undefined,
      dataFrom: dateFrom || undefined,
      dataTo: dateTo || undefined,
      ownerId: ownerIdFilter ?? undefined,
      liquidazione: settlementFilter || undefined,
      sort: sortKey,
      dir: sortDir,
      page,
      size: 0,   // dimensione pagina del tenant
    })
      .then(r => {
        if (annullato) return;
        setDocs(r.content);
        setTotalPages(r.totalPages);
        setTotalElements(r.totalElements);
      })
      .catch(err => { if (!annullato) setError(err.message); })
      .finally(() => { if (!annullato) setIsLoading(false); });
    return () => { annullato = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- i filtri sono tutti in filterKey
  }, [filterKey, page, reloadKey]);

  // Pannello laterale "Stato fiscale" della ricevuta
  const [statoFiscaleDocId, setStatoFiscaleDocId] = useState<number | null>(null);
  const [statoFiscale, setStatoFiscale] = useState<StatoFiscaleRicevuta | null>(null);
  const [loadingStatoFiscale, setLoadingStatoFiscale] = useState(false);

  const handleApriStatoFiscale = async (docId: number) => {
    setStatoFiscaleDocId(docId);
    setStatoFiscale(null);
    setLoadingStatoFiscale(true);
    try {
      setStatoFiscale(await getStatoFiscaleRicevuta(docId));
    } catch (e) {
      toast({ title: 'Errore', description: e instanceof Error ? e.message : String(e), variant: 'destructive' });
      setStatoFiscaleDocId(null);
    } finally {
      setLoadingStatoFiscale(false);
    }
  };

  const handleAnnullaNdc = async (d: DocumentListItem) => {
    if (!window.confirm(`Annullare la nota di credito ${d.documentNumber}?`)) return;
    try {
      await annullaNdc(d.id);
      toast({ title: 'Nota di credito annullata', description: d.documentNumber });
      setReloadKey(k => k + 1);
    } catch (e) {
      toast({ title: 'Annullamento non riuscito', description: e instanceof Error ? e.message : String(e), variant: 'destructive' });
    }
  };

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

  const ownerFilterName = ownerIdFilter != null
    ? (docs.find(d => d.fkOwnerId === ownerIdFilter)?.ownerName ?? `owner #${ownerIdFilter}`)
    : null;


  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Documenti Fiscali</h1>
        <p className="text-sm text-muted-foreground">
          {isLoading ? 'Caricamento…' : `${totalElements} documenti`}
        </p>
        {/* Nota informativa: la conservazione sostitutiva non è gestita dall'applicazione */}
        <p className="text-xs text-muted-foreground mt-2">
          L'archiviazione sostitutiva delle fatture inviate va attivata nel proprio cassetto
          fiscale dell'Agenzia delle Entrate.
        </p>
      </div>

      <Card>
        <CardContent className="p-4">
          <div className="flex flex-wrap gap-3">
            <div className="relative flex-1 min-w-[200px]">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input placeholder="Cerca numero, destinatario, prenotazione..." value={search} onChange={e => setSearch(e.target.value)} className="pl-9" />
            </div>
            <Select value={statusFilter} onValueChange={setStatusFilter}>
              <SelectTrigger className="w-[160px]"><Filter className="h-3.5 w-3.5 mr-2" /><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Stato SDI</SelectItem>
                <SelectItem value="draft">Bozza</SelectItem>
                <SelectItem value="ready">Pronto</SelectItem>
                <SelectItem value="sent_sdi">Inviato SDI</SelectItem>
                <SelectItem value="accepted">Accettato</SelectItem>
                <SelectItem value="rejected">Rifiutato</SelectItem>
              </SelectContent>
            </Select>
            {/* Filtro stato liquidazione: lato server, persistito nell'URL (?liquidazione=paid).
                Il Select non accetta value="" — 'all' fa da valore neutro e non finisce nell'URL. */}
            <Select
              value={settlementFilter || 'all'}
              onValueChange={v => updateFilter('liquidazione', v === 'all' ? '' : v)}
            >
              {/* Tooltip sul trigger: Select (Root) non è un elemento DOM, asChild va sul trigger */}
              <Tooltip>
                <TooltipTrigger asChild>
                  <SelectTrigger className="w-[160px]"><Filter className="h-3.5 w-3.5 mr-2" /><SelectValue placeholder="Liquidazione" /></SelectTrigger>
                </TooltipTrigger>
                <TooltipContent>Filtra le prenotazioni in base allo stato della liquidazione al proprietario</TooltipContent>
              </Tooltip>
              <SelectContent>
                <SelectItem value="all">Liquidazione</SelectItem>
                <SelectItem value="pending">In attesa</SelectItem>
                <SelectItem value="calculated">Calcolata</SelectItem>
                <SelectItem value="approved">Approvata</SelectItem>
                <SelectItem value="paid">Pagata</SelectItem>
                <SelectItem value="none">Non liquidata</SelectItem>
              </SelectContent>
            </Select>
            {/* Filtro tipo documento: lato server, persistito nell'URL (?tipo=fattura).
                Stesso stile pill dei preset date. "Tutti" include anche le note di credito. */}
            <div className="flex items-center gap-2">
              {[
                { key: '', label: 'Tutti' },
                { key: 'fattura', label: 'Fatture' },
                { key: 'ricevuta', label: 'Ricevute' },
                { key: 'nota_credito', label: 'Note di credito' },
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

      {filtriFiscaliVisibili && (
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs text-muted-foreground mr-1">Ricevute:</span>
          {FILTRI_FISCALI.map(f => (
            <Button
              key={f.key || 'tutti'}
              variant={filtroFiscale === f.key ? 'default' : 'outline'}
              size="sm"
              className="h-7 text-xs"
              onClick={() => updateFilter('fiscale', f.key)}
            >
              {f.label}
            </Button>
          ))}
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
          ) : docs.length === 0 ? (
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
                  <SortableTh label="Liquidazione" colKey="settlementStato" sortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                  <TableHead className="w-10"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {docs.map(d => (
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
                        {/* Collegamenti fattura ↔ nota di credito */}
                        {d.ndcNumber && d.ndcId && (
                          <button
                            type="button"
                            onClick={e => { e.stopPropagation(); navigate(`/documents/${d.ndcId}`); }}
                            className="text-xs text-green-600 hover:text-green-700 hover:underline text-left block"
                          >
                            NDC: {d.ndcNumber}
                          </button>
                        )}
                        {d.fatturaCollegataNumber && d.fatturaCollegataId && (
                          <button
                            type="button"
                            onClick={e => { e.stopPropagation(); navigate(`/documents/${d.fatturaCollegataId}`); }}
                            className="text-xs text-muted-foreground hover:text-foreground hover:underline text-left block"
                          >
                            Storno di: {d.fatturaCollegataNumber}
                          </button>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>
                      {d.documentType === 'nota_credito'
                        ? <Badge variant="outline" className={`text-xs ${NDC_BADGE}`}>NDC</Badge>
                        : <Badge variant="outline" className="text-xs">{labelTipoDocumento(d.documentType)}</Badge>}
                      {d.stornata && (
                        <Badge variant="outline" className="text-green-600 border-green-600 text-xs ml-1">
                          Stornata
                        </Badge>
                      )}
                    </TableCell>
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
                    <TableCell className="text-right font-medium">
                      {/* NDC: totale negativo, segno prima del simbolo (-€116,00) */}
                      {d.totalAmount < 0 ? '-' : ''}€{Math.abs(d.totalAmount).toLocaleString('it-IT', { minimumFractionDigits: 2 })}
                    </TableCell>
                    <TableCell><Badge variant="outline" className={statusColors[d.statoDocumento]}>{labelStatoDocumento(d.statoDocumento)}</Badge></TableCell>
                    <TableCell>
                      {d.settlementId ? (
                        <button
                          type="button"
                          title="Vedi la liquidazione"
                          onClick={e => { e.stopPropagation(); navigate(`/settlements/${d.settlementId}`); }}
                        >
                          <Badge className={settlementBadgeColors[d.settlementStato ?? 'pending'] ?? settlementBadgeColors.pending}>
                            {settlementLabels[d.settlementStato ?? 'pending'] ?? d.settlementStato}
                          </Badge>
                        </button>
                      ) : (
                        <span className="text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center">
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Button variant="ghost" size="icon" className="h-7 w-7" aria-label="Dettaglio"
                                    onClick={() => navigate(`/documents/${d.id}`)}>
                              <Eye className="h-3.5 w-3.5" />
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>Dettaglio</TooltipContent>
                        </Tooltip>
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Button variant="ghost" size="icon" className="h-7 w-7 text-muted-foreground hover:text-foreground"
                                    aria-label="Scarica PDF"
                                    onClick={e => {
                                      e.stopPropagation();
                                      downloadDocumentPdf(d.id, d.documentNumber).catch(err => toast({
                                        title: 'Errore download PDF',
                                        description: err instanceof Error ? err.message : String(err),
                                        variant: 'destructive',
                                      }));
                                    }}>
                              <FileDown className="h-3.5 w-3.5" />
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>Scarica PDF</TooltipContent>
                        </Tooltip>
                        {d.documentType === 'ricevuta' && (
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <Button variant="ghost" size="icon" className="h-7 w-7 text-muted-foreground hover:text-foreground"
                                      aria-label="Stato fiscale"
                                      onClick={e => { e.stopPropagation(); handleApriStatoFiscale(d.id); }}>
                                <BarChart2 className="h-3.5 w-3.5" />
                              </Button>
                            </TooltipTrigger>
                            <TooltipContent>Stato fiscale</TooltipContent>
                          </Tooltip>
                        )}
                        {ndcAnnullabile(d) && (
                          <Button variant="ghost" size="icon" className="h-7 w-7 text-destructive" title="Annulla NDC"
                                  onClick={e => { e.stopPropagation(); handleAnnullaNdc(d); }}>
                            <Ban className="h-3.5 w-3.5" />
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
          {!isLoading && !error && (
            <Paginazione page={page} totalPages={totalPages} totalElements={totalElements}
              etichetta="documenti" onPageChange={setPage} />
          )}
        </CardContent>
      </Card>

      {/* Pannello laterale: stato fiscale della ricevuta (ritenuta/F24, liquidazione, CU) */}
      <Sheet
        open={statoFiscaleDocId !== null}
        onOpenChange={open => {
          if (!open) {
            setStatoFiscaleDocId(null);
            setStatoFiscale(null);
          }
        }}
      >
        <SheetContent side="right" className="w-80">
          <SheetHeader>
            <SheetTitle>Stato Fiscale</SheetTitle>
            <SheetDescription>
              {statoFiscale ? `${statoFiscale.documentNumber} — ${statoFiscale.proprietarioNome ?? ''}` : ' '}
            </SheetDescription>
          </SheetHeader>

          {loadingStatoFiscale && (
            <div className="flex items-center justify-center gap-2 text-muted-foreground py-8">
              <Loader2 className="h-4 w-4 animate-spin" /> Caricamento...
            </div>
          )}

          {statoFiscale && (
            <div className="space-y-4 mt-4">
              {statoFiscale.statoDocumento === 'annullata' && (
                <p className="text-xs rounded-md border border-amber-300/50 bg-amber-50 dark:bg-amber-950/20 px-2 py-1.5 text-amber-800 dark:text-amber-300">
                  Ricevuta annullata da nota di credito: esclusa da liquidazioni e CU.
                </p>
              )}

              <div className="space-y-1 text-sm border-b pb-3">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Canone</span>
                  <span>{fmtImporto(statoFiscale.canoneLocazione)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">
                    Ritenuta{statoFiscale.aliquotaRitenuta != null ? ` ${Number(statoFiscale.aliquotaRitenuta).toLocaleString('it-IT')}%` : ''}
                  </span>
                  <span className="text-destructive">-{fmtImporto(statoFiscale.ritenutaAmount)}</span>
                </div>
              </div>

              <div className="space-y-1">
                <p className="text-xs font-medium uppercase text-muted-foreground">F24</p>
                <div className="flex items-center gap-2">
                  {statoFiscale.f24Stato ? (
                    <Badge variant="outline" className={f24StatoColors[statoFiscale.f24Stato] ?? ''}>
                      {f24StatoLabels[statoFiscale.f24Stato] ?? statoFiscale.f24Stato}
                    </Badge>
                  ) : (
                    <Badge variant="outline">
                      {statoFiscale.ritenutaStato === 'stornata' ? 'Ritenuta stornata' : 'Da versare'}
                    </Badge>
                  )}
                  {statoFiscale.f24Periodo && (
                    <span className="text-xs text-muted-foreground">{statoFiscale.f24Periodo}</span>
                  )}
                </div>
                {statoFiscale.f24Id && (
                  <button
                    onClick={() => navigate(`/f24?anno=${statoFiscale.f24Anno}&mese=${statoFiscale.f24Mese}`)}
                    className="text-xs text-primary hover:underline"
                  >
                    Vai all'F24 →
                  </button>
                )}
              </div>

              <div className="space-y-1">
                <p className="text-xs font-medium uppercase text-muted-foreground">Liquidazione</p>
                <div className="flex items-center gap-2">
                  {statoFiscale.liquidazioneStato ? (
                    <Badge variant="outline" className={liquidazioneColors[statoFiscale.liquidazioneStato] ?? ''}>
                      {settlementLabels[statoFiscale.liquidazioneStato] ?? statoFiscale.liquidazioneStato}
                    </Badge>
                  ) : (
                    <Badge variant="outline">Da liquidare</Badge>
                  )}
                  {statoFiscale.liquidazionePeriodo && (
                    <span className="text-xs text-muted-foreground">{statoFiscale.liquidazionePeriodo}</span>
                  )}
                </div>
                {statoFiscale.liquidazioneId && (
                  <button
                    onClick={() => navigate(`/settlements/${statoFiscale.liquidazioneId}`)}
                    className="text-xs text-primary hover:underline"
                  >
                    Vai alla liquidazione →
                  </button>
                )}
              </div>

              <div className="space-y-1">
                <p className="text-xs font-medium uppercase text-muted-foreground">Certificazione Unica</p>
                <div className="flex items-center gap-2">
                  {statoFiscale.cuStato ? (
                    <Badge variant="outline" className={cuColors[statoFiscale.cuStato] ?? ''}>
                      {labelStatoCu(statoFiscale.cuStato)}
                    </Badge>
                  ) : (
                    <Badge variant="outline">Non generata</Badge>
                  )}
                  {statoFiscale.cuAnno && (
                    <span className="text-xs text-muted-foreground">Anno {statoFiscale.cuAnno}</span>
                  )}
                </div>
                {statoFiscale.cuId && (
                  <button onClick={() => navigate('/cu')} className="text-xs text-primary hover:underline">
                    Vai alla CU →
                  </button>
                )}
              </div>
            </div>
          )}
        </SheetContent>
      </Sheet>

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

