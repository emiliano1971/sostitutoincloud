import { useState, useEffect } from 'react';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Eye, Loader2, AlertCircle } from 'lucide-react';
import { getSettlements, type SettlementListItem } from '@/api/settlementApi';

const statusColors: Record<string, string> = {
  pending: 'bg-muted text-muted-foreground',
  calculated: 'bg-primary/10 text-primary',
  approved: 'bg-warning/10 text-warning',
  paid: 'bg-success/10 text-success',
};

const SettlementsList = () => {
  const [settlements, setSettlements] = useState<SettlementListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getSettlements()
      .then(setSettlements)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Liquidazioni</h1>
        <p className="text-sm text-muted-foreground">Pagamenti ai proprietari</p>
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
          ) : settlements.length === 0 ? (
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
                  <TableHead className="w-10"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {settlements.map(s => (
                  <TableRow key={s.id}>
                    <TableCell className="font-medium">{s.ownerName}</TableCell>
                    <TableCell className="text-sm">{s.period}</TableCell>
                    <TableCell className="text-right">{s.bookingsCount}</TableCell>
                    <TableCell className="text-right">€{s.totalAmount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</TableCell>
                    <TableCell className="text-right text-destructive">-€{s.withholdingAmount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</TableCell>
                    <TableCell className="text-right font-medium">€{s.netAmount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</TableCell>
                    <TableCell><Badge variant="outline" className={statusColors[s.stato]}>{s.stato}</Badge></TableCell>
                    <TableCell><Button variant="ghost" size="icon" className="h-7 w-7"><Eye className="h-3.5 w-3.5" /></Button></TableCell>
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

export default SettlementsList;
