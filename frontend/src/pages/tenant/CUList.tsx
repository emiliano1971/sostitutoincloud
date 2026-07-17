import { useState, useEffect, useCallback } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { FileText, Loader2, AlertCircle } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import { getCuList, generaCuBatch, updateCuStatus, type CuListItem } from '@/api/cuApi';

const statusColors: Record<string, string> = {
  draft: 'bg-muted text-muted-foreground',
  generated: 'bg-primary/10 text-primary',
  delivered: 'bg-warning/10 text-warning',
  sent: 'bg-success/10 text-success',
};

const STATI = ['draft', 'generated', 'delivered', 'sent'];

const fmtEuro = (v: number) => `€${(v ?? 0).toLocaleString('it-IT', { minimumFractionDigits: 2 })}`;

const CUList = () => {
  const { toast } = useToast();
  const currentYear = new Date().getFullYear();
  const [taxYear, setTaxYear] = useState<number>(currentYear - 1);
  const [cuList, setCuList] = useState<CuListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [generating, setGenerating] = useState(false);

  const reload = useCallback(() => {
    setIsLoading(true);
    setError(null);
    getCuList({ taxYear })
      .then(setCuList)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, [taxYear]);

  useEffect(reload, [reload]);

  const handleGeneraTutte = async () => {
    setGenerating(true);
    try {
      const res = await generaCuBatch(taxYear);
      toast({ title: 'Generazione CU completata', description: `Generate: ${res.generated} — Saltate: ${res.skipped}` });
      reload();
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    } finally {
      setGenerating(false);
    }
  };

  const handleStatusChange = async (id: number, stato: string) => {
    try {
      const updated = await updateCuStatus(id, stato);
      setCuList(prev => prev.map(cu => (cu.id === id ? { ...cu, stato: updated.stato } : cu)));
      toast({ title: 'Stato aggiornato', description: `CU #${id} → ${stato}` });
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    }
  };

  const anniDisponibili = Array.from({ length: 6 }, (_, i) => currentYear - i);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Certificazioni Uniche</h1>
          <p className="text-sm text-muted-foreground">CU annuali per proprietari</p>
        </div>
        <div className="flex items-center gap-2">
          <Select value={String(taxYear)} onValueChange={v => setTaxYear(Number(v))}>
            <SelectTrigger className="w-[120px]"><SelectValue /></SelectTrigger>
            <SelectContent>
              {anniDisponibili.map(a => <SelectItem key={a} value={String(a)}>{a}</SelectItem>)}
            </SelectContent>
          </Select>
          <Button size="sm" className="gap-2" onClick={handleGeneraTutte} disabled={generating}>
            {generating ? <Loader2 className="h-4 w-4 animate-spin" /> : <FileText className="h-4 w-4" />}
            Genera tutte
          </Button>
        </div>
      </div>
      <Card>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="flex items-center justify-center py-16 text-muted-foreground gap-2">
              <Loader2 className="h-5 w-5 animate-spin" />
              <span>Caricamento CU…</span>
            </div>
          ) : error ? (
            <div className="flex items-center justify-center py-16 text-destructive gap-2">
              <AlertCircle className="h-5 w-5" />
              <span>{error}</span>
            </div>
          ) : cuList.length === 0 ? (
            <div className="flex items-center justify-center py-16 text-muted-foreground">
              Nessuna CU per l'anno {taxYear}
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Proprietario</TableHead>
                  <TableHead>Anno Fiscale</TableHead>
                  <TableHead className="text-right">Compensi €</TableHead>
                  <TableHead className="text-right">Ritenute €</TableHead>
                  <TableHead>Stato</TableHead>
                  <TableHead>Generata il</TableHead>
                  <TableHead className="w-[160px]">Cambia stato</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {cuList.map(cu => (
                  <TableRow key={cu.id}>
                    <TableCell className="font-medium">{cu.ownerName}</TableCell>
                    <TableCell className="text-sm">{cu.taxYear}</TableCell>
                    <TableCell className="text-right">{fmtEuro(cu.totalCompensi)}</TableCell>
                    <TableCell className="text-right">{fmtEuro(cu.totalRitenute)}</TableCell>
                    <TableCell><Badge variant="outline" className={statusColors[cu.stato]}>{cu.stato}</Badge></TableCell>
                    <TableCell className="text-sm">{cu.generatedAt ?? '—'}</TableCell>
                    <TableCell>
                      <Select value={cu.stato} onValueChange={v => handleStatusChange(cu.id, v)}>
                        <SelectTrigger className="h-8 w-[140px]"><SelectValue /></SelectTrigger>
                        <SelectContent>
                          {STATI.map(s => <SelectItem key={s} value={s}>{s}</SelectItem>)}
                        </SelectContent>
                      </Select>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default CUList;
