import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { mockSettlements } from '@/data/mock-data';
import { Receipt } from 'lucide-react';

const statusColors: Record<string, string> = {
  pending: 'bg-muted text-muted-foreground',
  calculated: 'bg-primary/10 text-primary',
  approved: 'bg-warning/10 text-warning',
  paid: 'bg-success/10 text-success',
};

const OwnerSettlements = () => {
  const settlements = mockSettlements.filter(s => s.owner_id === 'o1');

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Liquidazioni</h1>
      <p className="text-sm text-muted-foreground">{settlements.length} liquidazioni</p>

      <div className="space-y-3">
        {settlements.map(s => (
          <Card key={s.settlement_id}>
            <CardContent className="p-4">
              <div className="flex items-start justify-between">
                <div className="flex gap-3">
                  <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center shrink-0">
                    <Receipt className="h-4 w-4 text-primary" />
                  </div>
                  <div>
                    <p className="font-medium text-sm">Periodo {s.period}</p>
                    <p className="text-xs text-muted-foreground">{s.bookings_count} prenotazioni</p>
                    {s.payment_date && <p className="text-xs text-muted-foreground">Pagato il {s.payment_date}</p>}
                    <Badge variant="outline" className={`mt-1.5 text-[10px] ${statusColors[s.status]}`}>{s.status}</Badge>
                  </div>
                </div>
                <div className="text-right">
                  <p className="font-bold text-sm">€{s.net_amount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</p>
                  <p className="text-[10px] text-muted-foreground">netto</p>
                  <p className="text-[10px] text-destructive mt-0.5">-€{s.withholding_amount.toLocaleString('it-IT', { minimumFractionDigits: 2 })} rit.</p>
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
};

export default OwnerSettlements;
