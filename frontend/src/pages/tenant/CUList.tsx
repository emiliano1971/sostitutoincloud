import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Download, Eye, FileText } from 'lucide-react';
import { mockCU } from '@/data/mock-data';

const statusColors: Record<string, string> = {
  draft: 'bg-muted text-muted-foreground',
  generated: 'bg-primary/10 text-primary',
  sent: 'bg-success/10 text-success',
  delivered: 'bg-success/20 text-success',
};

const CUList = () => (
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
            {mockCU.map(cu => (
              <TableRow key={cu.cu_id}>
                <TableCell className="font-medium">{cu.owner_name}</TableCell>
                <TableCell className="text-sm">{cu.tax_year}</TableCell>
                <TableCell className="text-right">€{cu.total_compensi.toLocaleString('it-IT')}</TableCell>
                <TableCell className="text-right">€{cu.total_ritenute.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</TableCell>
                <TableCell><Badge variant="outline" className={statusColors[cu.status]}>{cu.status}</Badge></TableCell>
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
      </CardContent>
    </Card>
  </div>
);

export default CUList;
