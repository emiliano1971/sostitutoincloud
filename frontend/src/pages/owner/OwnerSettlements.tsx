import { useEffect, useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Receipt } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import { getSettlements, type SettlementListItem } from '@/api/settlementApi';

const statusColors: Record<string, string> = {
  pending: 'bg-muted text-muted-foreground',
  calculated: 'bg-primary/10 text-primary',
  approved: 'bg-warning/10 text-warning',
  paid: 'bg-success/10 text-success',
};

const OwnerSettlements = () => {
  const { user } = useAuth();
  const ownerId = user?.owner_id ? parseInt(user.owner_id) : undefined;
  const [settlements, setSettlements] = useState<SettlementListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!ownerId) return;
    getSettlements({ ownerId })
      .then(setSettlements)
      .catch(() => setError('Errore nel caricamento delle liquidazioni'))
      .finally(() => setLoading(false));
  }, [ownerId]);

  if (loading) return <div className="p-6 text-muted-foreground">Caricamento...</div>;
  if (error) return <div className="p-6 text-destructive">{error}</div>;

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Liquidazioni</h1>
      <p className="text-sm text-muted-foreground">{settlements.length} liquidazioni</p>

      <div className="space-y-3">
        {settlements.map(s => (
          <Card key={s.id}>
            <CardContent className="p-4">
              <div className="flex items-start justify-between">
                <div className="flex gap-3">
                  <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center shrink-0">
                    <Receipt className="h-4 w-4 text-primary" />
                  </div>
                  <div>
                    <p className="font-medium text-sm">Periodo {s.period}</p>
                    <p className="text-xs text-muted-foreground">{s.bookingsCount} prenotazioni</p>
                    {s.paymentDate && <p className="text-xs text-muted-foreground">Pagato il {s.paymentDate}</p>}
                    <Badge variant="outline" className={`mt-1.5 text-[10px] ${statusColors[s.stato] ?? ''}`}>{s.stato}</Badge>
                  </div>
                </div>
                <div className="text-right">
                  <p className="font-bold text-sm">€{s.netAmount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</p>
                  <p className="text-[10px] text-muted-foreground">netto</p>
                  <p className="text-[10px] text-destructive mt-0.5">-€{s.withholdingAmount.toLocaleString('it-IT', { minimumFractionDigits: 2 })} rit.</p>
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
        {settlements.length === 0 && (
          <Card><CardContent className="p-8 text-center text-muted-foreground">Nessuna liquidazione</CardContent></Card>
        )}
      </div>
    </div>
  );
};

export default OwnerSettlements;
