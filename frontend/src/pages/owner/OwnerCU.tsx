import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Download, Award } from 'lucide-react';
import { mockCU } from '@/data/mock-data';

const OwnerCU = () => {
  const cus = mockCU.filter(cu => cu.owner_id === 'o1');

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Certificazioni Uniche</h1>
      <p className="text-sm text-muted-foreground">CU disponibili per download</p>

      <div className="space-y-3">
        {cus.map(cu => (
          <Card key={cu.cu_id}>
            <CardContent className="p-4">
              <div className="flex items-center justify-between">
                <div className="flex gap-3">
                  <div className="h-10 w-10 rounded-lg bg-primary/10 flex items-center justify-center shrink-0">
                    <Award className="h-5 w-5 text-primary" />
                  </div>
                  <div>
                    <p className="font-medium">CU {cu.tax_year}</p>
                    <p className="text-xs text-muted-foreground">Compensi: €{cu.total_compensi.toLocaleString('it-IT')}</p>
                    <p className="text-xs text-muted-foreground">Ritenute: €{cu.total_ritenute.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</p>
                    <Badge variant="outline" className="mt-1 text-[10px]">{cu.status}</Badge>
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
