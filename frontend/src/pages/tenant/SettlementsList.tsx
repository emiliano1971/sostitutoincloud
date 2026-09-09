import { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { Loader2, AlertCircle, Plus, Filter, CheckCircle2, ThumbsUp, Info, AlertTriangle } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import {
  getSettlements, calcolaSettlements, updateSettlementStatus,
  type SettlementListItem, type SettlementCalcolaResult,
  getBookingsDaLiquidare, type BookingDaLiquidare,
} from '@/api/settlementApi';

const statusColors: Record<string, string> = {
  pending: 'bg-muted text-muted-foreground',
  calculated: 'bg-primary/10 text-primary',
  approved: 'bg-warning/10 text-warning',
  paid: 'bg-success/10 text-success',
};

const statusLabels: Record<string, string> = {
  pending: 'In attesa',
  calculated: 'Calcolato',
  approved: 'Approvato',
  paid: 'Pagato',
};

const MESI = [
  'Gennaio', 'Febbraio', 'Marzo', 'Aprile', 'Maggio', 'Giugno',
  'Luglio', 'Agosto', 'Settembre', 'Ottobre', 'Novembre', 'Dicembre',
];

const fmtEuro = (v: number) => `€${v.toLocaleString('it-IT', { minimumFractionDigits: 2 })}`;

const fmtData = (v: string) => (v ? new Date(v).toLocaleDateString('it-IT') : '—');

/**
 * Intestazione di colonna con icona informativa e tooltip esplicativo.
 * `inline-flex` con `justify-end` per restare allineata a destra come le celle importi.
 */
const HeaderConTooltip = ({ label, testo }: { label: string; testo: string }) => (
  <Tooltip>
    <TooltipTrigger asChild>
      <span className="inline-flex items-center justify-end gap-1 cursor-help">
        {label}
        <Info className="h-3 w-3 text-muted-foreground" aria-label={testo} />
      </span>
    </TooltipTrigger>
    <TooltipContent className="max-w-xs text-xs">{testo}</TooltipContent>
  </Tooltip>
);

const SettlementsList = () => {
  const { toast } = useToast();
  const navigate = useNavigate();
  const [settlements, setSettlements] = useState<SettlementListItem[]>([]);
  const [statoFilter, setStatoFilter] = useState<string>('tutti');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Dialog calcola
  const [calcolaOpen, setCalcolaOpen] = useState(false);
  const [mese, setMese] = useState<number>(new Date().getMonth() + 1);
  const [anno, setAnno] = useState<number>(new Date().getFullYear());
  const [calcolando, setCalcolando] = useState(false);
  const [risultato, setRisultato] = useState<SettlementCalcolaResult | null>(null);

  // Prenotazioni con documenti emessi ancora fuori dalle liquidazioni.
  const [daLiquidare, setDaLiquidare] = useState<BookingDaLiquidare[]>([]);
  const [showModal, setShowModal] = useState(false);

  const reload = () => {
    setIsLoading(true);
    getSettlements()
      .then(setSettlements)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
    // L'avviso è informativo: se la lista non arriva non blocca la pagina.
    getBookingsDaLiquidare()
      .then(setDaLiquidare)
      .catch(() => setDaLiquidare([]));
  };

  useEffect(reload, []);

  const filtered = useMemo(
    () => (statoFilter === 'tutti' ? settlements : settlements.filter(s => s.stato === statoFilter)),
    [settlements, statoFilter],
  );

  const handleCalcola = async () => {
    setCalcolando(true);
    try {
      const result = await calcolaSettlements({ mese, anno });
      setRisultato(result);
      toast({
        title: 'Liquidazioni calcolate',
        description: `${result.generated} nuovi, ${result.updated} aggiornati, ${result.skipped} saltati`,
      });
      reload();
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    } finally {
      setCalcolando(false);
    }
  };

  const handleUpdateStatus = async (id: number, stato: string) => {
    try {
      await updateSettlementStatus(id, stato);
      toast({ title: stato === 'approved' ? 'Liquidazione approvata' : 'Liquidazione segnata come pagata' });
      reload();
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    }
  };

  const closeCalcola = (open: boolean) => {
    setCalcolaOpen(open);
    if (!open) setRisultato(null);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Liquidazioni</h1>
          <p className="text-sm text-muted-foreground">
            {isLoading ? 'Caricamento…' : `${filtered.length} liquidazioni`} — pagamenti ai proprietari
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Select value={statoFilter} onValueChange={setStatoFilter}>
            <SelectTrigger className="w-[160px]"><Filter className="h-3.5 w-3.5 mr-2" /><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="tutti">Tutti gli stati</SelectItem>
              <SelectItem value="pending">In attesa</SelectItem>
              <SelectItem value="calculated">Calcolato</SelectItem>
              <SelectItem value="approved">Approvato</SelectItem>
              <SelectItem value="paid">Pagato</SelectItem>
            </SelectContent>
          </Select>
          <Button onClick={() => setCalcolaOpen(true)}>
            <Plus className="h-4 w-4 mr-2" />
            Calcola liquidazioni
          </Button>
        </div>
      </div>

      {/* Prenotazioni con ricevuta emessa ma senza liquidazione: si aggiorna a ogni reload(),
          quindi anche dopo "Calcola liquidazioni". */}
      {daLiquidare.length > 0 && (
        <div className="flex items-start gap-3 rounded-md border border-orange-300 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          <AlertTriangle className="h-4 w-4 mt-0.5 shrink-0 text-orange-500" />
          <span>
            <strong>{daLiquidare.length}</strong>{' '}
            {daLiquidare.length === 1 ? 'prenotazione' : 'prenotazioni'} con ricevuta emessa non ancora{' '}
            {daLiquidare.length === 1 ? 'liquidata' : 'liquidate'}.{' '}
            <Button
              variant="link"
              className="h-auto p-0 text-sm text-amber-900 underline"
              onClick={() => setShowModal(true)}
            >
              Vedi dettaglio
            </Button>
            . Usa "Calcola liquidazioni" per
            {daLiquidare.length === 1 ? ' includerla' : ' includerle'} nel prossimo settlement.
          </span>
        </div>
      )}

      <Card>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="flex items-center justify-center py-16 text-muted-foreground gap-2">
              <Loader2 className="h-5 w-5 animate-spin" />
              <span>Caricamento liquidazioni…</span>
            </div>
          ) : error ? (
            <div className="flex items-center justify-center py-16 text-destructive gap-2">
              <AlertCircle className="h-5 w-5" />
              <span>{error}</span>
            </div>
          ) : filtered.length === 0 ? (
            <div className="flex items-center justify-center py-16 text-muted-foreground">
              Nessuna liquidazione
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Proprietario</TableHead>
                  <TableHead>Periodo</TableHead>
                  <TableHead className="text-right">Prenotazioni</TableHead>
                  {/* Le colonne importi usano nomi interni: il tooltip mappa ciascuna
                      sul rigo corrispondente della CU, per evitare fraintendimenti
                      con la nozione di "lordo" dell'Agenzia delle Entrate. */}
                  <TableHead className="text-right">
                    <HeaderConTooltip
                      label="Lordo €"
                      testo="Canone di locazione netto (corrisponde all'Imponibile - rigo 8 della CU). Il lordo AdE (rigo 4) include anche la ritenuta."
                    />
                  </TableHead>
                  <TableHead className="text-right">
                    <HeaderConTooltip
                      label="Ritenuta €"
                      testo="Ritenuta a titolo d'acconto operata dal sostituto d'imposta (rigo 9 della CU)."
                    />
                  </TableHead>
                  <TableHead className="text-right">
                    <HeaderConTooltip
                      label="Netto €"
                      testo="Importo netto da pagare al proprietario (Imponibile - Ritenuta)."
                    />
                  </TableHead>
                  <TableHead>Stato</TableHead>
                  <TableHead className="w-24">Azioni</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filtered.map(s => (
                  <TableRow key={s.id} className="cursor-pointer" onClick={() => navigate(`/settlements/${s.id}`)}>
                    <TableCell className="font-medium">{s.ownerName}</TableCell>
                    <TableCell className="text-sm">{s.period}</TableCell>
                    <TableCell className="text-right">{s.bookingsCount}</TableCell>
                    <TableCell className="text-right">{fmtEuro(s.totalAmount)}</TableCell>
                    <TableCell className="text-right text-destructive">-{fmtEuro(s.withholdingAmount)}</TableCell>
                    <TableCell className="text-right font-medium">{fmtEuro(s.netAmount)}</TableCell>
                    <TableCell><Badge variant="outline" className={statusColors[s.stato]}>{statusLabels[s.stato] || s.stato}</Badge></TableCell>
                    <TableCell onClick={e => e.stopPropagation()}>
                      <div className="flex gap-1">
                        {s.stato === 'calculated' && (
                          <Button variant="ghost" size="icon" className="h-7 w-7 text-warning" title="Approva" onClick={() => handleUpdateStatus(s.id, 'approved')}>
                            <ThumbsUp className="h-3.5 w-3.5" />
                          </Button>
                        )}
                        {s.stato === 'approved' && (
                          <Button variant="ghost" size="icon" className="h-7 w-7 text-success" title="Segna pagato" onClick={() => handleUpdateStatus(s.id, 'paid')}>
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

      {/* Dialog: Calcola liquidazioni */}
      <Dialog open={calcolaOpen} onOpenChange={closeCalcola}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Calcola liquidazioni</DialogTitle>
            <DialogDescription>Aggrega le ritenute del periodo selezionato per ogni proprietario.</DialogDescription>
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
            <div className="rounded-md bg-muted/50 p-3 text-sm space-y-1">
              <div className="flex justify-between"><span className="text-muted-foreground">Nuovi</span><span className="font-medium">{risultato.generated}</span></div>
              <div className="flex justify-between"><span className="text-muted-foreground">Aggiornati</span><span className="font-medium">{risultato.updated}</span></div>
              <div className="flex justify-between"><span className="text-muted-foreground">Saltati</span><span className="font-medium">{risultato.skipped}</span></div>
            </div>
          )}

          <DialogFooter>
            {!risultato ? (
              <>
                <Button variant="outline" onClick={() => closeCalcola(false)}>Annulla</Button>
                <Button onClick={handleCalcola} disabled={calcolando}>
                  {calcolando && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                  Calcola
                </Button>
              </>
            ) : (
              <Button onClick={() => closeCalcola(false)}>Chiudi</Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Dialog: dettaglio prenotazioni da liquidare */}
      <Dialog open={showModal} onOpenChange={setShowModal}>
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>Prenotazioni da liquidare</DialogTitle>
            <DialogDescription>
              Prenotazioni con ricevuta emessa non ancora incluse in una liquidazione. La
              colonna Periodo indica la competenza della ritenuta: se precedente al mese che
              stai liquidando, la prenotazione rientra come arretrato.
            </DialogDescription>
          </DialogHeader>

          <div className="max-h-[60vh] overflow-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>ID</TableHead>
                  <TableHead>Owner</TableHead>
                  <TableHead>Immobile</TableHead>
                  <TableHead>Check-in</TableHead>
                  <TableHead>Periodo</TableHead>
                  <TableHead className="text-right">Canone €</TableHead>
                  <TableHead className="text-right">Netto €</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {daLiquidare.map(b => (
                  <TableRow key={b.bookingId}>
                    <TableCell>
                      <Button
                        variant="link"
                        className="h-auto p-0 font-medium"
                        onClick={() => {
                          setShowModal(false);
                          navigate(`/bookings/${b.bookingId}`);
                        }}
                      >
                        {b.externalBookingId || b.bookingId}
                      </Button>
                    </TableCell>
                    <TableCell>{b.ownerName}</TableCell>
                    <TableCell>{b.propertyName}</TableCell>
                    <TableCell className="text-sm">{fmtData(b.checkinDate)}</TableCell>
                    <TableCell className="text-sm">{b.periodoLedger}</TableCell>
                    <TableCell className="text-right">{fmtEuro(b.canoneLocazione)}</TableCell>
                    <TableCell className="text-right font-medium">{fmtEuro(b.nettoProprietario)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          <DialogFooter>
            <Button onClick={() => setShowModal(false)}>Chiudi</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default SettlementsList;
