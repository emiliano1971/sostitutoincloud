import { useEffect, useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Loader2, AlertCircle } from 'lucide-react';
import { getOwnerSettlements } from '@/api/ownerApi';
import type { SettlementListItem } from '@/api/settlementApi';

const statusColors: Record<string, string> = {
  pending: 'bg-muted text-muted-foreground',
  calculated: 'bg-primary/10 text-primary',
  approved: 'bg-warning/10 text-warning',
  paid: 'bg-success/10 text-success',
};

const statusLabels: Record<string, string> = {
  pending: 'In attesa',
  calculated: 'Calcolata',
  approved: 'Approvata',
  paid: 'Pagata',
};

const fmtEuro = (v?: number) =>
  `€${(v ?? 0).toLocaleString('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const OwnerSettlements = () => {
  const [settlements, setSettlements] = useState<SettlementListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    // /api/owner/settlements ricava il proprietario dal token: nessun ownerId dal client.
    getOwnerSettlements()
      .then(setSettlements)
      .catch(err => setError(err instanceof Error ? err.message : 'Errore nel caricamento delle liquidazioni'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16 text-muted-foreground gap-2">
        <Loader2 className="h-5 w-5 animate-spin" />
        <span>Caricamento liquidazioni…</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center py-16 text-destructive gap-2">
        <AlertCircle className="h-5 w-5" />
        <span>{error}</span>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-bold">Liquidazioni</h1>
        <p className="text-sm text-muted-foreground">{settlements.length} liquidazioni</p>
      </div>

      <Card>
        <CardContent className="p-0">
          {settlements.length === 0 ? (
            <div className="py-12 text-center text-muted-foreground">Nessuna liquidazione</div>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Periodo</TableHead>
                    <TableHead className="text-right">N. Prenotazioni</TableHead>
                    <TableHead className="text-right">Lordo</TableHead>
                    <TableHead className="text-right">Ritenuta</TableHead>
                    <TableHead className="text-right">Netto</TableHead>
                    <TableHead>Stato</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {settlements.map(s => (
                    <TableRow key={s.id}>
                      <TableCell className="font-medium whitespace-nowrap">{s.period}</TableCell>
                      <TableCell className="text-right">{s.bookingsCount}</TableCell>
                      {/* totalAmount è il canone del periodo, base della ritenuta */}
                      <TableCell className="text-right">{fmtEuro(s.totalAmount)}</TableCell>
                      <TableCell className="text-right text-destructive">
                        {(s.withholdingAmount ?? 0) > 0 ? `-${fmtEuro(s.withholdingAmount)}` : fmtEuro(0)}
                      </TableCell>
                      <TableCell className="text-right font-semibold">{fmtEuro(s.netAmount)}</TableCell>
                      <TableCell>
                        <Badge variant="outline" className={`text-xs ${statusColors[s.stato] ?? ''}`}>
                          {statusLabels[s.stato] ?? s.stato}
                        </Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default OwnerSettlements;
