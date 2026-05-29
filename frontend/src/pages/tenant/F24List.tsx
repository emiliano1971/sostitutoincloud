import { useState, useEffect } from 'react';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Eye, Loader2, AlertCircle } from 'lucide-react';
import { getF24List, type F24ListItem } from '@/api/f24Api';
import type { F24Record } from '@/types';
import F24PreviewDialog from '@/components/f24/F24PreviewDialog';

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

function toF24Record(item: F24ListItem): F24Record {
  return {
    f24_id: String(item.id),
    tenant_id: 't1',
    period: item.period,
    tax_code: item.codiceTributo,
    total_amount: item.totalAmount,
    withholdings_count: item.withholdingsCount,
    status: item.stato as F24Record['status'],
    deadline_date: item.deadlineDate,
    payment_date: item.paymentDate,
    created_at: item.createdAt,
  };
}

const F24List = () => {
  const [f24List, setF24List] = useState<F24ListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedF24, setSelectedF24] = useState<F24Record | null>(null);

  useEffect(() => {
    getF24List()
      .then(setF24List)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">F24 Mensili</h1>
        <p className="text-sm text-muted-foreground">Gestione versamenti ritenute con codice tributo 1919</p>
      </div>
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
          ) : f24List.length === 0 ? (
            <div className="flex items-center justify-center py-16 text-muted-foreground">
              Nessun F24
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Periodo</TableHead>
                  <TableHead>Codice Tributo</TableHead>
                  <TableHead className="text-right">Ritenute</TableHead>
                  <TableHead className="text-right">Importo €</TableHead>
                  <TableHead>Scadenza</TableHead>
                  <TableHead>Stato</TableHead>
                  <TableHead className="w-20"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {f24List.map(f => (
                  <TableRow key={f.id}>
                    <TableCell className="font-medium">{f.period}</TableCell>
                    <TableCell className="font-mono text-sm">{f.codiceTributo}</TableCell>
                    <TableCell className="text-right">{f.withholdingsCount}</TableCell>
                    <TableCell className="text-right font-medium">€{f.totalAmount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</TableCell>
                    <TableCell className="text-sm">{f.deadlineDate}</TableCell>
                    <TableCell><Badge variant="outline" className={statusColors[f.stato]}>{statusLabels[f.stato] || f.stato}</Badge></TableCell>
                    <TableCell>
                      <div className="flex gap-1">
                        <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => setSelectedF24(toF24Record(f))}>
                          <Eye className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <F24PreviewDialog f24={selectedF24} open={!!selectedF24} onOpenChange={(open) => !open && setSelectedF24(null)} />
    </div>
  );
};

export default F24List;
