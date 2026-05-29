import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { Search, Plus, Eye, Globe } from 'lucide-react';
import { getProperties, type PropertyListItem } from '@/api/propertyApi';

const PropertiesList = () => {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [properties, setProperties] = useState<PropertyListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    getProperties()
      .then(data => setProperties(data))
      .catch(err => setError(err.message ?? 'Errore nel caricamento'))
      .finally(() => setLoading(false));
  }, []);

  const filtered = properties.filter(p =>
    search === '' ||
    p.displayName.toLowerCase().includes(search.toLowerCase()) ||
    p.internalCode.toLowerCase().includes(search.toLowerCase()) ||
    (p.cinCode ?? '').toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Immobili</h1>
          <p className="text-sm text-muted-foreground">{filtered.length} immobili</p>
        </div>
        <Button size="sm" className="gap-2" onClick={() => navigate('/properties/new')}><Plus className="h-4 w-4" /> Nuovo Immobile</Button>
      </div>

      <Card>
        <CardContent className="p-4">
          <div className="relative max-w-sm">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input placeholder="Cerca per nome, codice o CIN..." value={search} onChange={e => setSearch(e.target.value)} className="pl-9" />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          {loading && (
            <div className="p-6 text-center text-sm text-muted-foreground">Caricamento...</div>
          )}
          {error && (
            <div className="p-6 text-center text-sm text-destructive">{error}</div>
          )}
          {!loading && !error && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Codice</TableHead>
                  <TableHead>Nome</TableHead>
                  <TableHead>CIN</TableHead>
                  <TableHead>Città</TableHead>
                  <TableHead>Tipo</TableHead>
                  <TableHead>OTA</TableHead>
                  <TableHead className="text-right">Prenotazioni</TableHead>
                  <TableHead>Stato</TableHead>
                  <TableHead className="w-10"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filtered.map(p => (
                  <TableRow key={p.id} className="cursor-pointer" onClick={() => navigate(`/properties/${p.id}`)}>
                    <TableCell className="font-mono text-xs">{p.internalCode}</TableCell>
                    <TableCell className="font-medium">{p.displayName}</TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">{p.cinCode ?? '—'}</TableCell>
                    <TableCell className="text-sm">{p.city}</TableCell>
                    <TableCell><Badge variant="outline" className="text-xs">{p.propertyType}</Badge></TableCell>
                    <TableCell>
                      {p.otaCodes.length > 0 ? (
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <span className="flex items-center gap-1 text-xs text-muted-foreground cursor-help">
                              <Globe className="h-3 w-3" /> {p.otaCodes.length}
                            </span>
                          </TooltipTrigger>
                          <TooltipContent>
                            {p.otaCodes.map(ota => (
                              <div key={ota.canaleCodiceName} className="text-xs">{ota.canaleCodiceName}: {ota.externalId}</div>
                            ))}
                          </TooltipContent>
                        </Tooltip>
                      ) : (
                        <span className="text-xs text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    <TableCell className="text-right">{p.bookingsCount}</TableCell>
                    <TableCell><Badge variant={p.attivo ? 'default' : 'secondary'}>{p.attivo ? 'Attivo' : 'Inattivo'}</Badge></TableCell>
                    <TableCell><Button variant="ghost" size="icon" className="h-7 w-7"><Eye className="h-3.5 w-3.5" /></Button></TableCell>
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

export default PropertiesList;
