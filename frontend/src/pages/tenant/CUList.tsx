import { useState, useEffect } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Download, Eye, FileText, Loader2, AlertCircle } from 'lucide-react';
import { getCuList, type CuListItem } from '@/api/cuApi';

const statusColors: Record<string, string> = {
  draft: 'bg-muted text-muted-foreground',
  generated: 'bg-primary/10 text-primary',
  sent: 'bg-success/10 text-success',
  delivered: 'bg-success/20 text-success',
};

const CUList = () => {
  const [cuList, setCuList] = useState<CuListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getCuList()
      .then(setCuList)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, []);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Certificazioni Uniche</h1>
          <p className="text-sm text-muted-foreground">CU annuali per proprietari</p>
        </div>
        <Button size="sm" className="gap-2"><FileText className="h-4 w-4" /> Genera CU</Button>
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
              Nessuna CU
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
                  <TableHead className="w-20"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {cuList.map(cu => (
                  <TableRow key={cu.id}>
                    <TableCell className="font-medium">{cu.ownerName}</TableCell>
                    <TableCell className="text-sm">{cu.taxYear}</TableCell>
                    <TableCell className="text-right">€{cu.totalCompensi.toLocaleString('it-IT')}</TableCell>
                    <TableCell className="text-right">€{cu.totalRitenute.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</TableCell>
                    <TableCell><Badge variant="outline" className={statusColors[cu.stato]}>{cu.stato}</Badge></TableCell>
                    <TableCell>
                      <div className="flex gap-1">
                        <Button variant="ghost" size="icon" className="h-7 w-7"><Eye className="h-3.5 w-3.5" /></Button>
                        <Button variant="ghost" size="icon" className="h-7 w-7"><Download className="h-3.5 w-3.5" /></Button>
                      </div>
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
