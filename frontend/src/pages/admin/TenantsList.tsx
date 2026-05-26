import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Input } from '@/components/ui/input';
import { Search, Plus, Eye, Pause, Play } from 'lucide-react';
import { mockTenants } from '@/data/mock-data';
import { useState } from 'react';

const statusColor: Record<string, string> = {
  active: 'bg-success/10 text-success border-success/20',
  draft: 'bg-muted text-muted-foreground',
  suspended: 'bg-warning/10 text-warning border-warning/20',
  closed: 'bg-destructive/10 text-destructive border-destructive/20',
};

const TenantsList = () => {
  const [search, setSearch] = useState('');
  const tenants = mockTenants.filter(t =>
    search === '' || t.display_name.toLowerCase().includes(search.toLowerCase()) || t.legal_name.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Gestione Tenant</h1>
          <p className="text-sm text-muted-foreground">{tenants.length} tenant registrati</p>
        </div>
        <Button size="sm" className="gap-2"><Plus className="h-4 w-4" /> Nuovo Tenant</Button>
      </div>

      <Card>
        <CardContent className="p-4">
          <div className="relative max-w-sm">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input placeholder="Cerca tenant..." value={search} onChange={e => setSearch(e.target.value)} className="pl-9" />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Tenant</TableHead>
                <TableHead>P.IVA</TableHead>
                <TableHead>Stato</TableHead>
                <TableHead className="text-right">Immobili</TableHead>
                <TableHead className="text-right">Proprietari</TableHead>
                <TableHead className="text-right">Prenotazioni</TableHead>
                <TableHead>Creato il</TableHead>
                <TableHead className="w-20"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {tenants.map(t => (
                <TableRow key={t.tenant_id}>
                  <TableCell>
                    <p className="font-medium">{t.display_name}</p>
                    <p className="text-xs text-muted-foreground">{t.legal_name}</p>
                  </TableCell>
                  <TableCell className="font-mono text-xs">{t.vat_number}</TableCell>
                  <TableCell><Badge variant="outline" className={statusColor[t.tenant_status]}>{t.tenant_status}</Badge></TableCell>
                  <TableCell className="text-right">{t.properties_count}</TableCell>
                  <TableCell className="text-right">{t.owners_count}</TableCell>
                  <TableCell className="text-right">{t.bookings_count}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">{t.created_at}</TableCell>
                  <TableCell>
                    <div className="flex gap-1">
                      <Button variant="ghost" size="icon" className="h-7 w-7"><Eye className="h-3.5 w-3.5" /></Button>
                      <Button variant="ghost" size="icon" className="h-7 w-7">
                        {t.tenant_status === 'active' ? <Pause className="h-3.5 w-3.5" /> : <Play className="h-3.5 w-3.5" />}
                      </Button>
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
};

export default TenantsList;
