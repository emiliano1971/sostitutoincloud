import { useState, useEffect, useMemo } from 'react';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Eye, CheckCircle2, Loader2, AlertCircle, Plus, Info, X, RefreshCw, Filter, Download, Printer } from 'lucide-react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useToast } from '@/hooks/use-toast';
import {
  getF24List, generaF24, getF24Detail, marcaF24Pagato, ricalcolaF24, downloadF24Pdf,
  type F24Record, type F24GenerazioneResult,
} from '@/api/f24Api';

const statusColors: Record<string, string> = {
  draft: 'bg-muted text-muted-foreground',
  ready: 'bg-primary/10 text-primary',
  sent: 'bg-warning/10 text-warning',
  paid: 'bg-success/10 text-success',
  error: 'bg-destructive/10 text-destructive',
};

const statusLabels: Record<string, string> = {
  draft: 'Bozza',
  ready: 'Pronto',
  sent: 'Inviato',
  paid: 'Pagato',
  error: 'Errore',
};

const MESI = [
  'Gennaio', 'Febbraio', 'Marzo', 'Aprile', 'Maggio', 'Giugno',
  'Luglio', 'Agosto', 'Settembre', 'Ottobre', 'Novembre', 'Dicembre',
];

const fmtEuro = (v: number) => `€${v.toLocaleString('it-IT', { minimumFractionDigits: 2 })}`;
const fmtPeriodo = (mese: number, anno: number) => `${String(mese).padStart(2, '0')}/${anno}`;

const F24List = () => {
  const { toast } = useToast();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  // Arrivo da un documento fiscale: l'owner è passato come query param.
  // I modelli F24 aggregano le ritenute di TUTTI i proprietari del periodo,
  // quindi non sono filtrabili per singolo owner: mostriamo solo un avviso
  // contestuale e rimandiamo al dettaglio per vedere le ritenute del proprietario.
  const ownerIdParam = searchParams.get('ownerId');
  const [f24List, setF24List] = useState<F24Record[]>([]);
  const [statoFilter, setStatoFilter] = useState<string>('tutti');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filtro periodo persistito nell'URL. L'F24 ha periodo_mese + periodo_anno,
  // non una data singola: quindi due select invece dei preset a giorni.
  const annoFilter = searchParams.get('anno') ?? '';
  const meseFilter = searchParams.get('mese') ?? '';

  // Filtri in memoria, applicati dopo il caricamento e prima della render.
  const filtered = useMemo(
    () => (statoFilter === 'tutti' ? f24List : f24List.filter(f => f.stato === statoFilter))
      .filter(f => {
        if (!annoFilter && !meseFilter) return true;
        const annoOk = !annoFilter || String(f.periodoAnno) === annoFilter;
        const meseOk = !meseFilter || String(f.periodoMese) === meseFilter;
        return annoOk && meseOk;
      }),
    [f24List, statoFilter, annoFilter, meseFilter],
  );

  // Solo gli anni effettivamente presenti nei dati, dal più recente.
  const anniDisponibili = useMemo(
    () => [...new Set(f24List.map(f => f.periodoAnno))].sort((a, b) => b - a),
    [f24List],
  );

  const updateFilter = (key: string, value: string | null) => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      if (value === null || value === '') next.delete(key);
      else next.set(key, value);
      return next;
    }, { replace: true });
  };

  const clearPeriodoFilter = () => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      next.delete('anno'); next.delete('mese');
      return next;
    }, { replace: true });
  };

  // Dialog genera
  const [generaOpen, setGeneraOpen] = useState(false);
  const [mese, setMese] = useState<number>(new Date().getMonth() + 1);
  const [anno, setAnno] = useState<number>(new Date().getFullYear());
  const [generating, setGenerating] = useState(false);
  const [risultato, setRisultato] = useState<F24GenerazioneResult | null>(null);

  // Dialog dettaglio
  const [dettaglio, setDettaglio] = useState<F24GenerazioneResult | null>(null);
  const [dettaglioOpen, setDettaglioOpen] = useState(false);

  // Conferma pagamento
  const [pagatoTarget, setPagatoTarget] = useState<F24Record | null>(null);

  // Download PDF
  const [downloadingPdf, setDownloadingPdf] = useState<number | null>(null);

  const handleDownloadPdf = async (id: number) => {
    setDownloadingPdf(id);
    try {
      await downloadF24Pdf(id);
    } catch (err) {
      toast({ title: 'Errore generazione PDF', description: (err as Error).message, variant: 'destructive' });
    } finally {
      setDownloadingPdf(null);
    }
  };

  const reload = () => {
    setIsLoading(true);
    getF24List()
      .then(setF24List)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  };

  useEffect(reload, []);

  const handleGenera = async () => {
    setGenerating(true);
    try {
      const result = await generaF24(anno, mese);
      setRisultato(result);
      toast({ title: 'F24 generato', description: `Periodo ${fmtPeriodo(mese, anno)} — ${fmtEuro(result.totaleRitenute)}` });
      reload();
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    } finally {
      setGenerating(false);
    }
  };

  const openDettaglio = async (id: number) => {
    try {
      const d = await getF24Detail(id);
      setDettaglio(d);
      setDettaglioOpen(true);
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    }
  };

  const handlePagato = async () => {
    if (!pagatoTarget) return;
    try {
      await marcaF24Pagato(pagatoTarget.id);
      toast({ title: 'F24 segnato come pagato' });
      reload();
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    } finally {
      setPagatoTarget(null);
    }
  };

  const handleRicalcola = async (id: number) => {
    try {
      await ricalcolaF24(id);
      toast({ title: 'F24 aggiornato' });
      reload();
    } catch (err) {
      // 400 (nessuna ritenuta nuova) e 422 (già pagato) arrivano con il messaggio del backend
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    }
  };

  const closeGenera = (open: boolean) => {
    setGeneraOpen(open);
    if (!open) setRisultato(null);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Modelli F24</h1>
          <p className="text-sm text-muted-foreground">
            {isLoading ? 'Caricamento…' : `${filtered.length} modelli F24`} — codice tributo 1919
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Select value={statoFilter} onValueChange={setStatoFilter}>
            <SelectTrigger className="w-[160px]"><Filter className="h-3.5 w-3.5 mr-2" /><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="tutti">Tutti gli stati</SelectItem>
              <SelectItem value="draft">Bozza</SelectItem>
              <SelectItem value="ready">Pronto</SelectItem>
              <SelectItem value="sent">Inviato</SelectItem>
              <SelectItem value="paid">Pagato</SelectItem>
              <SelectItem value="error">Errore</SelectItem>
            </SelectContent>
          </Select>

          {/* Filtro periodo: anni ricavati dai dati, mesi 1-12.
              'tutti' è la sentinella per "nessun filtro": Radix non ammette value="". */}
          <Select
            value={annoFilter || 'tutti'}
            onValueChange={v => updateFilter('anno', v === 'tutti' ? null : v)}
          >
            <SelectTrigger className="w-[140px]"><SelectValue placeholder="Anno" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="tutti">Tutti gli anni</SelectItem>
              {anniDisponibili.map(a => (
                <SelectItem key={a} value={String(a)}>{a}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value={meseFilter || 'tutti'}
            onValueChange={v => updateFilter('mese', v === 'tutti' ? null : v)}
          >
            <SelectTrigger className="w-[150px]"><SelectValue placeholder="Mese" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="tutti">Tutti i mesi</SelectItem>
              {MESI.map((nome, i) => (
                <SelectItem key={i + 1} value={String(i + 1)}>{nome}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          {(annoFilter || meseFilter) && (
            <Button variant="ghost" size="icon" className="h-8 w-8" title="Azzera filtro periodo" onClick={clearPeriodoFilter}>
              <X className="h-4 w-4" />
            </Button>
          )}

          <Button onClick={() => setGeneraOpen(true)}>
            <Plus className="h-4 w-4 mr-2" />
            Genera F24
          </Button>
        </div>
      </div>

      {ownerIdParam && (
        <div className="flex items-start gap-2 rounded-md border border-primary/20 bg-primary/5 p-3 text-sm">
          <Info className="h-4 w-4 mt-0.5 text-primary shrink-0" />
          <span className="flex-1 text-muted-foreground">
            Sei arrivato dal documento di un proprietario. I modelli F24 aggregano le ritenute
            di tutti i proprietari del periodo: apri il <strong>dettaglio</strong> di un F24
            per individuare le ritenute del proprietario selezionato.
          </span>
          <button
            type="button"
            className="text-muted-foreground hover:text-foreground"
            title="Chiudi avviso"
            onClick={() => {
              searchParams.delete('ownerId');
              setSearchParams(searchParams, { replace: true });
            }}
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
              <span>Caricamento F24…</span>
            </div>
          ) : error ? (
            <div className="flex items-center justify-center py-16 text-destructive gap-2">
              <AlertCircle className="h-5 w-5" />
              <span>{error}</span>
            </div>
          ) : filtered.length === 0 ? (
            <div className="flex items-center justify-center py-16 text-muted-foreground">
              Nessun F24
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Periodo</TableHead>
                  <TableHead>Codice Tributo</TableHead>
                  <TableHead className="text-right">Importo €</TableHead>
                  <TableHead className="text-right">N° Ritenute</TableHead>
                  <TableHead>Scadenza</TableHead>
                  <TableHead>Stato</TableHead>
                  <TableHead>Pagato il</TableHead>
                  <TableHead className="w-24">Azioni</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filtered.map(f => (
                  <TableRow key={f.id}>
                    <TableCell className="font-medium">{fmtPeriodo(f.periodoMese, f.periodoAnno)}</TableCell>
                    <TableCell className="font-mono text-sm">{f.codiceTributo}</TableCell>
                    <TableCell className="text-right font-medium">{fmtEuro(f.totalAmount)}</TableCell>
                    <TableCell className="text-right">{f.withholdingsCount}</TableCell>
                    <TableCell className="text-sm">{f.deadlineDate}</TableCell>
                    <TableCell><Badge variant="outline" className={statusColors[f.stato]}>{statusLabels[f.stato] || f.stato}</Badge></TableCell>
                    <TableCell className="text-sm">{f.paymentDate ?? '—'}</TableCell>
                    <TableCell>
                      <div className="flex gap-1">
                        <Button variant="ghost" size="icon" className="h-7 w-7" title="Dettaglio" onClick={() => openDettaglio(f.id)}>
                          <Eye className="h-3.5 w-3.5" />
                        </Button>
                        {f.stato !== 'draft' && (
                          <Button variant="ghost" size="icon" className="h-7 w-7" title="Scarica PDF"
                                  disabled={downloadingPdf === f.id} onClick={() => handleDownloadPdf(f.id)}>
                            {downloadingPdf === f.id
                              ? <Loader2 className="h-3.5 w-3.5 animate-spin" />
                              : <Download className="h-3.5 w-3.5" />}
                          </Button>
                        )}
                        {f.stato !== 'paid' && (
                          <Button variant="ghost" size="icon" className="h-7 w-7" title="Aggiungi ritenute non incluse" onClick={() => handleRicalcola(f.id)}>
                            <RefreshCw className="h-3.5 w-3.5" />
                          </Button>
                        )}
                        {(f.stato === 'ready' || f.stato === 'sent') && (
                          <Button variant="ghost" size="icon" className="h-7 w-7 text-success" title="Marca pagato" onClick={() => setPagatoTarget(f)}>
                            <CheckCircle2 className="h-3.5 w-3.5" />
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Dialog: Genera F24 */}
      <Dialog open={generaOpen} onOpenChange={closeGenera}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>Genera F24</DialogTitle>
            <DialogDescription>Aggrega le ritenute da versare del periodo selezionato.</DialogDescription>
          </DialogHeader>

          {!risultato ? (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label>Mese</Label>
                <Select value={String(mese)} onValueChange={v => setMese(Number(v))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {MESI.map((nome, i) => (
                      <SelectItem key={i + 1} value={String(i + 1)}>{nome}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>Anno</Label>
                <Input type="number" value={anno} onChange={e => setAnno(Number(e.target.value))} />
              </div>
            </div>
          ) : (
            <div className="space-y-3">
              <div className="rounded-md bg-muted/50 p-3 text-sm">
                <div className="flex justify-between"><span className="text-muted-foreground">Periodo</span><span className="font-medium">{fmtPeriodo(risultato.periodoMese, risultato.periodoAnno)}</span></div>
                <div className="flex justify-between"><span className="text-muted-foreground">Totale ritenute</span><span className="font-medium">{fmtEuro(risultato.totaleRitenute)}</span></div>
                <div className="flex justify-between"><span className="text-muted-foreground">N° ritenute</span><span className="font-medium">{risultato.numeroRitenute}</span></div>
                <div className="flex justify-between"><span className="text-muted-foreground">Scadenza</span><span className="font-medium">{risultato.scadenza}</span></div>
              </div>
              <RitenuteTable ritenute={risultato.ritenute} />
            </div>
          )}

          <DialogFooter>
            {!risultato ? (
              <>
                <Button variant="outline" onClick={() => closeGenera(false)}>Annulla</Button>
                <Button onClick={handleGenera} disabled={generating}>
                  {generating && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                  Genera
                </Button>
              </>
            ) : (
              <Button onClick={() => closeGenera(false)}>Chiudi</Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Dialog: dettaglio ritenute collegate */}
      <Dialog open={dettaglioOpen} onOpenChange={setDettaglioOpen}>
        <DialogContent className="max-w-5xl">
          <DialogHeader>
            <DialogTitle>
              Dettaglio F24 {dettaglio && fmtPeriodo(dettaglio.periodoMese, dettaglio.periodoAnno)}
            </DialogTitle>
            <DialogDescription>
              {dettaglio && `${dettaglio.numeroRitenute} ritenute — ${fmtEuro(dettaglio.totaleRitenute)}`}
            </DialogDescription>
          </DialogHeader>
          {dettaglio && (
            <RitenuteTable
              ritenute={dettaglio.ritenute}
              periodoF24={{ mese: dettaglio.periodoMese, anno: dettaglio.periodoAnno }}
              onApriBooking={(bookingId) => {
                setDettaglioOpen(false);
                navigate(`/bookings/${bookingId}`);
              }}
            />
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setDettaglioOpen(false)}>Chiudi</Button>
            <Button onClick={() => dettaglio && stampaDettaglioF24(dettaglio)} className="gap-2">
              <Printer className="h-4 w-4" /> Stampa dettaglio
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Conferma marca pagato (operazione irreversibile) */}
      <AlertDialog open={!!pagatoTarget} onOpenChange={(open) => !open && setPagatoTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Conferma pagamento F24</AlertDialogTitle>
            <AlertDialogDescription>
              {pagatoTarget && `Stai per segnare come pagato il modello F24 del periodo ${fmtPeriodo(pagatoTarget.periodoMese, pagatoTarget.periodoAnno)} per un importo di ${fmtEuro(pagatoTarget.totalAmount)}. Questa operazione non può essere annullata.`}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Annulla</AlertDialogCancel>
            <AlertDialogAction onClick={handlePagato} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
              Confermo il pagamento
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
};

/** Ritenuta di un periodo diverso da quello dell'F24: è un arretrato recuperato. */
const isArretrato = (
  r: F24GenerazioneResult['ritenute'][number],
  periodoF24: { mese: number; anno: number },
) => r.periodoMese != null && r.periodoAnno != null
  && (r.periodoMese !== periodoF24.mese || r.periodoAnno !== periodoF24.anno);

const RitenuteTable = ({
  ritenute, periodoF24, onApriBooking,
}: {
  ritenute: F24GenerazioneResult['ritenute'];
  periodoF24: { mese: number; anno: number };
  onApriBooking: (bookingId: number) => void;
}) => (
  <div className="max-h-96 overflow-x-auto overflow-y-auto">
    <Table className="min-w-[1000px]">
      <TableHeader>
        <TableRow>
          <TableHead className="whitespace-nowrap">Prenotazione</TableHead>
          <TableHead className="whitespace-nowrap">Ospite</TableHead>
          <TableHead className="whitespace-nowrap">Immobile</TableHead>
          <TableHead className="whitespace-nowrap">Proprietario</TableHead>
          <TableHead className="whitespace-nowrap">Check-in</TableHead>
          <TableHead className="whitespace-nowrap">Check-out</TableHead>
          <TableHead className="whitespace-nowrap">Periodo</TableHead>
          <TableHead className="text-right whitespace-nowrap">Canone €</TableHead>
          <TableHead className="text-right whitespace-nowrap">Aliq. %</TableHead>
          <TableHead className="text-right whitespace-nowrap">Ritenuta €</TableHead>
          <TableHead className="whitespace-nowrap">Documento</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {ritenute.map(r => (
          <TableRow key={r.id}>
            <TableCell className="font-mono text-xs">
              {r.bookingId ? (
                <button
                  type="button"
                  className="text-primary hover:underline"
                  title="Apri la prenotazione"
                  onClick={() => onApriBooking(r.bookingId!)}
                >
                  {r.bookingExternalId ?? `#${r.bookingId}`}
                </button>
              ) : (r.bookingExternalId ?? '—')}
            </TableCell>
            <TableCell className="text-sm">{r.guestName ?? '—'}</TableCell>
            <TableCell className="text-sm">{r.propertyName ?? '—'}</TableCell>
            <TableCell className="text-sm">{r.ownerName ?? '—'}</TableCell>
            <TableCell className="text-sm">{r.checkinDate ?? '—'}</TableCell>
            <TableCell className="text-sm">{r.checkoutDate ?? '—'}</TableCell>
            <TableCell className="whitespace-nowrap text-sm">
              {r.periodoMese != null && r.periodoAnno != null
                ? fmtPeriodo(r.periodoMese, r.periodoAnno) : '—'}
              {isArretrato(r, periodoF24) && (
                <Badge className="ml-1.5 bg-warning/10 text-warning">arretrato</Badge>
              )}
            </TableCell>
            <TableCell className="text-right">{fmtEuro(r.canoneLocazione)}</TableCell>
            <TableCell className="text-right">{r.aliquotaRitenuta}</TableCell>
            <TableCell className="text-right font-medium">{fmtEuro(r.ritenutaAmount)}</TableCell>
            <TableCell className="font-mono text-xs">{r.documentNumber ?? '—'}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  </div>
);

/**
 * Stampa del dettaglio in una finestra dedicata: il contenuto del dialog vive in un
 * portal di Radix, quindi nasconderlo via @media print sulla pagina non funziona.
 * Stesso approccio già usato per l'anteprima del modello F24.
 */
const stampaDettaglioF24 = (d: F24GenerazioneResult) => {
  const periodo = fmtPeriodo(d.periodoMese, d.periodoAnno);
  const esc = (v: unknown) => String(v ?? '—')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const righe = d.ritenute.map(r => `
    <tr>
      <td class="mono">${esc(r.bookingExternalId)}</td>
      <td>${esc(r.guestName)}</td>
      <td>${esc(r.propertyName)}</td>
      <td>${esc(r.ownerName)}</td>
      <td>${esc(r.checkinDate)}</td>
      <td>${esc(r.checkoutDate)}</td>
      <td>${r.periodoMese != null && r.periodoAnno != null ? fmtPeriodo(r.periodoMese, r.periodoAnno) : '—'}${
        isArretrato(r, { mese: d.periodoMese, anno: d.periodoAnno }) ? ' <span class="arr">arretrato</span>' : ''}</td>
      <td class="num">${esc(fmtEuro(r.canoneLocazione))}</td>
      <td class="num">${esc(r.aliquotaRitenuta)}</td>
      <td class="num bold">${esc(fmtEuro(r.ritenutaAmount))}</td>
      <td class="mono">${esc(r.documentNumber)}</td>
    </tr>`).join('');

  const win = window.open('', '_blank');
  if (!win) return;
  win.document.write(`<html><head><title>Dettaglio F24 ${periodo}</title><style>
    * { box-sizing: border-box; }
    body { font-family: system-ui, sans-serif; margin: 24px; color: #1a1a1a; }
    h1 { font-size: 16px; margin: 0 0 4px; }
    .sub { font-size: 12px; color: #555; margin-bottom: 16px; }
    table { width: 100%; border-collapse: collapse; font-size: 10px; }
    th { background: #f0f2f5; border: 1px solid #b0bec5; padding: 5px 6px; text-align: left;
         font-size: 9px; text-transform: uppercase; color: #546e7a; }
    td { border: 1px solid #b0bec5; padding: 5px 6px; }
    .mono { font-family: 'Courier New', monospace; }
    .num { text-align: right; }
    .bold { font-weight: bold; }
    .arr { background: #fef3c7; color: #92400e; padding: 1px 5px; border-radius: 3px; font-size: 9px; }
    tfoot td { background: #e8edf3; font-weight: bold; }
    @media print { body { margin: 0; } }
  </style></head><body>
    <h1>Dettaglio F24 — periodo ${periodo}</h1>
    <div class="sub">${d.numeroRitenute} ritenute — totale ${fmtEuro(d.totaleRitenute)} — stato ${esc(d.stato)}</div>
    <table>
      <thead><tr>
        <th>Prenotazione</th><th>Ospite</th><th>Immobile</th><th>Proprietario</th>
        <th>Check-in</th><th>Check-out</th><th>Periodo</th>
        <th class="num">Canone €</th><th class="num">Aliq. %</th><th class="num">Ritenuta €</th><th>Documento</th>
      </tr></thead>
      <tbody>${righe}</tbody>
      <tfoot><tr><td colspan="9">TOTALE</td><td class="num">${fmtEuro(d.totaleRitenute)}</td><td></td></tr></tfoot>
    </table>
  </body></html>`);
  win.document.close();
  win.focus();
  win.print();
};

export default F24List;
