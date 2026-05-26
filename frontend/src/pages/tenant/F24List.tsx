import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Eye, Download } from 'lucide-react';
import { mockF24 } from '@/data/mock-data';
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

const F24List = () => {
  const [selectedF24, setSelectedF24] = useState<F24Record | null>(null);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">F24 Mensili</h1>
        <p className="text-sm text-muted-foreground">Gestione versamenti ritenute con codice tributo 1919</p>
      </div>
      <Card>
        <CardContent className="p-0">
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
              {mockF24.map(f => (
                <TableRow key={f.f24_id}>
                  <TableCell className="font-medium">{f.period}</TableCell>
                  <TableCell className="font-mono text-sm">{f.tax_code}</TableCell>
                  <TableCell className="text-right">{f.withholdings_count}</TableCell>
                  <TableCell className="text-right font-medium">€{f.total_amount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</TableCell>
                  <TableCell className="text-sm">{f.deadline_date}</TableCell>
                  <TableCell><Badge variant="outline" className={statusColors[f.status]}>{statusLabels[f.status] || f.status}</Badge></TableCell>
                  <TableCell>
                    <div className="flex gap-1">
                      <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => setSelectedF24(f)}>
                        <Eye className="h-3.5 w-3.5" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <F24PreviewDialog f24={selectedF24} open={!!selectedF24} onOpenChange={(open) => !open && setSelectedF24(null)} />
    </div>
  );
};

export default F24List;
