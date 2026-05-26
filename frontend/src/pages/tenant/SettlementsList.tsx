import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Eye } from 'lucide-react';
import { mockSettlements } from '@/data/mock-data';

const statusColors: Record<string, string> = {
  pending: 'bg-muted text-muted-foreground',
  calculated: 'bg-primary/10 text-primary',
  approved: 'bg-warning/10 text-warning',
  paid: 'bg-success/10 text-success',
};

const SettlementsList = () => (
  <div className="space-y-6">
    <div>
      <h1 className="text-2xl font-bold">Liquidazioni</h1>
      <p className="text-sm text-muted-foreground">Pagamenti ai proprietari</p>
    </div>
    <Card>
      <CardContent className="p-0">
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
            {mockSettlements.map(s => (
              <TableRow key={s.settlement_id}>
                <TableCell className="font-medium">{s.owner_name}</TableCell>
                <TableCell className="text-sm">{s.period}</TableCell>
                <TableCell className="text-right">{s.bookings_count}</TableCell>
                <TableCell className="text-right">€{s.total_amount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</TableCell>
                <TableCell className="text-right text-destructive">-€{s.withholding_amount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</TableCell>
                <TableCell className="text-right font-medium">€{s.net_amount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</TableCell>
                <TableCell><Badge variant="outline" className={statusColors[s.status]}>{s.status}</Badge></TableCell>
                <TableCell><Button variant="ghost" size="icon" className="h-7 w-7"><Eye className="h-3.5 w-3.5" /></Button></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  </div>
);

export default SettlementsList;
