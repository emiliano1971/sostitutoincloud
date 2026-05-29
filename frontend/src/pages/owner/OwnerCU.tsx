import { useEffect, useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Download, Award } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import { getCuList, type CuListItem } from '@/api/cuApi';

const OwnerCU = () => {
  const { user } = useAuth();
  const ownerId = user?.owner_id ? parseInt(user.owner_id) : undefined;
  const [cus, setCus] = useState<CuListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!ownerId) return;
    getCuList({ ownerId })
      .then(setCus)
      .catch(() => setError('Errore nel caricamento delle CU'))
      .finally(() => setLoading(false));
  }, [ownerId]);

  if (loading) return <div className="p-6 text-muted-foreground">Caricamento...</div>;
  if (error) return <div className="p-6 text-destructive">{error}</div>;

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Certificazioni Uniche</h1>
      <p className="text-sm text-muted-foreground">CU disponibili per download</p>

      <div className="space-y-3">
        {cus.map(cu => (
          <Card key={cu.id}>
            <CardContent className="p-4">
              <div className="flex items-center justify-between">
                <div className="flex gap-3">
                  <div className="h-10 w-10 rounded-lg bg-primary/10 flex items-center justify-center shrink-0">
                    <Award className="h-5 w-5 text-primary" />
                  </div>
                  <div>
                    <p className="font-medium">CU {cu.taxYear}</p>
                    <p className="text-xs text-muted-foreground">Compensi: €{cu.totalCompensi.toLocaleString('it-IT')}</p>
                    <p className="text-xs text-muted-foreground">Ritenute: €{cu.totalRitenute.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</p>
                    <Badge variant="outline" className="mt-1 text-[10px]">{cu.stato}</Badge>
                  </div>
                </div>
                <Button variant="outline" size="sm" className="gap-1.5">
                  <Download className="h-4 w-4" /> Scarica
                </Button>
              </div>
            </CardContent>
          </Card>
        ))}
        {cus.length === 0 && (
          <Card><CardContent className="p-8 text-center text-muted-foreground">Nessuna CU disponibile</CardContent></Card>
        )}
      </div>
    </div>
  );
};

export default OwnerCU;
