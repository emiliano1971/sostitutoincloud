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
import { Eye, CheckCircle2, Loader2, AlertCircle, Plus, Info, X, RefreshCw, Filter } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { useToast } from '@/hooks/use-toast';
import {
  getF24List, generaF24, getF24Detail, marcaF24Pagato, ricalcolaF24,
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

  // Filtro per stato in memoria, applicato dopo il caricamento e prima della render.
  const filtered = useMemo(
    () => (statoFilter === 'tutti' ? f24List : f24List.filter(f => f.stato === statoFilter)),
    [f24List, statoFilter],
  );

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
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>
              Dettaglio F24 {dettaglio && fmtPeriodo(dettaglio.periodoMese, dettaglio.periodoAnno)}
            </DialogTitle>
            <DialogDescription>
              {dettaglio && `${dettaglio.numeroRitenute} ritenute — ${fmtEuro(dettaglio.totaleRitenute)}`}
            </DialogDescription>
          </DialogHeader>
          {dettaglio && <RitenuteTable ritenute={dettaglio.ritenute} />}
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

const RitenuteTable = ({ ritenute }: { ritenute: F24GenerazioneResult['ritenute'] }) => (
  <div className="max-h-80 overflow-x-auto overflow-y-auto">
    <Table className="min-w-[640px]">
      <TableHeader>
        <TableRow>
          <TableHead className="whitespace-nowrap">Proprietario</TableHead>
          <TableHead className="whitespace-nowrap">Prenotazione</TableHead>
          <TableHead className="whitespace-nowrap">Documento</TableHead>
          <TableHead className="text-right whitespace-nowrap">Canone €</TableHead>
          <TableHead className="text-right whitespace-nowrap">Aliq. %</TableHead>
          <TableHead className="text-right whitespace-nowrap">Ritenuta €</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {ritenute.map(r => (
          <TableRow key={r.id}>
            <TableCell>{r.ownerName ?? '—'}</TableCell>
            <TableCell className="font-mono text-xs">{r.bookingExternalId ?? '—'}</TableCell>
            <TableCell className="font-mono text-xs">{r.documentNumber ?? '—'}</TableCell>
            <TableCell className="text-right">{fmtEuro(r.canoneLocazione)}</TableCell>
            <TableCell className="text-right">{r.aliquotaRitenuta}</TableCell>
            <TableCell className="text-right font-medium">{fmtEuro(r.ritenutaAmount)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  </div>
);

export default F24List;
