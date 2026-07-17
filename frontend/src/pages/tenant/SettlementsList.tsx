import { useState, useEffect, useMemo } from 'react';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { Loader2, AlertCircle, Plus, Filter, CheckCircle2, ThumbsUp } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import {
  getSettlements, calcolaSettlements, updateSettlementStatus,
  type SettlementListItem, type SettlementCalcolaResult,
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

const SettlementsList = () => {
  const { toast } = useToast();
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

  const reload = () => {
    setIsLoading(true);
    getSettlements()
      .then(setSettlements)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
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
                  <TableHead className="text-right">Lordo €</TableHead>
                  <TableHead className="text-right">Ritenuta €</TableHead>
                  <TableHead className="text-right">Netto €</TableHead>
                  <TableHead>Stato</TableHead>
                  <TableHead className="w-24">Azioni</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filtered.map(s => (
                  <TableRow key={s.id}>
                    <TableCell className="font-medium">{s.ownerName}</TableCell>
                    <TableCell className="text-sm">{s.period}</TableCell>
                    <TableCell className="text-right">{s.bookingsCount}</TableCell>
                    <TableCell className="text-right">{fmtEuro(s.totalAmount)}</TableCell>
                    <TableCell className="text-right text-destructive">-{fmtEuro(s.withholdingAmount)}</TableCell>
                    <TableCell className="text-right font-medium">{fmtEuro(s.netAmount)}</TableCell>
                    <TableCell><Badge variant="outline" className={statusColors[s.stato]}>{statusLabels[s.stato] || s.stato}</Badge></TableCell>
                    <TableCell>
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
    </div>
  );
};

export default SettlementsList;
