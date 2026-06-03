import { useState, useEffect } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Search, Plus, Eye, Loader2, AlertCircle } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { getOwners, type OwnerListItem } from '@/api/ownerApi';

const OwnersList = () => {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [owners, setOwners] = useState<OwnerListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getOwners()
      .then(setOwners)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, []);

  const filtered = owners.filter(o =>
    search === '' ||
    `${o.firstName} ${o.lastName}`.toLowerCase().includes(search.toLowerCase()) ||
    o.taxCode.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Proprietari</h1>
          <p className="text-sm text-muted-foreground">
            {isLoading ? 'Caricamento…' : `${filtered.length} proprietari`}
          </p>
        </div>
        <Button size="sm" className="gap-2" onClick={() => navigate('/owners/new')}><Plus className="h-4 w-4" /> Nuovo Proprietario</Button>
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
          {isLoading ? (
            <div className="flex items-center justify-center py-16 text-muted-foreground gap-2">
              <Loader2 className="h-5 w-5 animate-spin" />
              <span>Caricamento proprietari…</span>
            </div>
          ) : error ? (
            <div className="flex items-center justify-center py-16 text-destructive gap-2">
              <AlertCircle className="h-5 w-5" />
              <span>{error}</span>
            </div>
          ) : filtered.length === 0 ? (
            <div className="flex items-center justify-center py-16 text-muted-foreground">
              Nessun proprietario
            </div>
          ) : (
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
                {filtered.map(o => (
                  <TableRow key={o.id} className="cursor-pointer" onClick={() => navigate(`/owners/${o.id}`)}>
                    <TableCell className="font-medium">
                      {o.legalName ?? `${o.firstName} ${o.lastName}`}
                    </TableCell>
                    <TableCell>
                      <Badge variant="outline" className="text-xs">{o.ownerType.replace('_', ' ')}</Badge>
                    </TableCell>
                    <TableCell className="font-mono text-xs">{o.taxCode}</TableCell>
                    <TableCell className="text-sm">{o.fiscalRegime.replace('_', ' ')}</TableCell>
                    <TableCell className="text-right">{o.propertiesCount}</TableCell>
                    <TableCell>
                      <Badge variant={o.attivo ? 'default' : 'secondary'}>
                        {o.attivo ? 'attivo' : 'inattivo'}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Button variant="ghost" size="icon" className="h-7 w-7">
                        <Eye className="h-3.5 w-3.5" />
                      </Button>
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

export default OwnersList;
