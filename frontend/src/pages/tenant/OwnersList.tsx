import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Search, Plus, Eye } from 'lucide-react';
import { mockOwners } from '@/data/mock-data';
import { useNavigate } from 'react-router-dom';

const OwnersList = () => {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const owners = mockOwners.filter(o => o.tenant_id === 't1')
    .filter(o => search === '' || `${o.first_name} ${o.last_name}`.toLowerCase().includes(search.toLowerCase()) || o.tax_code.toLowerCase().includes(search.toLowerCase()));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Proprietari</h1>
          <p className="text-sm text-muted-foreground">{owners.length} proprietari</p>
        </div>
        <Button size="sm" className="gap-2"><Plus className="h-4 w-4" /> Nuovo Proprietario</Button>
      </div>

      <Card>
        <CardContent className="p-4">
          <div className="relative max-w-sm">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input placeholder="Cerca per nome o CF..." value={search} onChange={e => setSearch(e.target.value)} className="pl-9" />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Nome</TableHead>
                <TableHead>Tipo</TableHead>
                <TableHead>Codice Fiscale</TableHead>
                <TableHead>Regime</TableHead>
                <TableHead className="text-right">Immobili</TableHead>
                <TableHead>Stato</TableHead>
                <TableHead className="w-10"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {owners.map(o => (
                <TableRow key={o.owner_id} className="cursor-pointer" onClick={() => navigate(`/owners/${o.owner_id}`)}>
                  <TableCell className="font-medium">{o.first_name} {o.last_name}</TableCell>
                  <TableCell><Badge variant="outline" className="text-xs">{o.owner_type.replace('_', ' ')}</Badge></TableCell>
                  <TableCell className="font-mono text-xs">{o.tax_code}</TableCell>
                  <TableCell className="text-sm">{o.fiscal_regime.replace('_', ' ')}</TableCell>
                  <TableCell className="text-right">{o.properties_count}</TableCell>
                  <TableCell><Badge variant={o.status === 'active' ? 'default' : 'secondary'}>{o.status}</Badge></TableCell>
                  <TableCell><Button variant="ghost" size="icon" className="h-7 w-7"><Eye className="h-3.5 w-3.5" /></Button></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
};

export default OwnersList;
