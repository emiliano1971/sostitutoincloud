import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Input } from '@/components/ui/input';
import { Search, Plus, Eye, Pause, Play, Loader2, AlertCircle } from 'lucide-react';
import { getTenants, updateTenantStatus, type TenantListItem } from '@/api/tenantApi';

const statusColor: Record<string, string> = {
  active:    'bg-success/10 text-success border-success/20',
  draft:     'bg-muted text-muted-foreground',
  suspended: 'bg-warning/10 text-warning border-warning/20',
  closed:    'bg-destructive/10 text-destructive border-destructive/20',
};

const TenantsList = () => {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [tenants, setTenants] = useState<TenantListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [togglingId, setTogglingId] = useState<number | null>(null);

  useEffect(() => {
    getTenants()
      .then(setTenants)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, []);

  const filtered = tenants.filter(t =>
    search === '' ||
    t.displayName.toLowerCase().includes(search.toLowerCase()) ||
    t.legalName.toLowerCase().includes(search.toLowerCase())
  );

  const handleToggleStatus = async (t: TenantListItem) => {
    const nuovoStato = t.stato === 'active' ? 'suspended' : 'active';
    setTogglingId(t.id);
    try {
      const updated = await updateTenantStatus(t.id, nuovoStato);
      setTenants(prev => prev.map(x => x.id === t.id ? { ...x, stato: updated.stato } : x));
    } catch {
      // errore silente — UI non cambia
    } finally {
      setTogglingId(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Gestione Tenant</h1>
          <p className="text-sm text-muted-foreground">
            {isLoading ? 'Caricamento…' : `${filtered.length} tenant registrati`}
          </p>
        </div>
        <Button size="sm" className="gap-2" onClick={() => navigate('/admin/tenants/new')}>
          <Plus className="h-4 w-4" /> Nuovo Tenant
        </Button>
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
          {isLoading ? (
            <div className="flex items-center justify-center py-16 gap-2 text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin" /> Caricamento tenant…
            </div>
          ) : error ? (
            <div className="flex items-center justify-center py-16 gap-2 text-destructive">
              <AlertCircle className="h-5 w-5" /> {error}
            </div>
          ) : (
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
                {filtered.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={8} className="text-center text-muted-foreground py-8">
                      Nessun tenant trovato
                    </TableCell>
                  </TableRow>
                ) : filtered.map(t => (
                  <TableRow key={t.id}>
                    <TableCell>
                      <p className="font-medium">{t.displayName}</p>
                      <p className="text-xs text-muted-foreground">{t.legalName}</p>
                    </TableCell>
                    <TableCell className="font-mono text-xs">{t.vatNumber ?? '—'}</TableCell>
                    <TableCell>
                      <Badge variant="outline" className={statusColor[t.stato] ?? ''}>{t.stato}</Badge>
                    </TableCell>
                    <TableCell className="text-right">{t.propertiesCount}</TableCell>
                    <TableCell className="text-right">{t.ownersCount}</TableCell>
                    <TableCell className="text-right">{t.bookingsCount}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {t.createdAt ? new Date(t.createdAt).toLocaleDateString('it-IT') : '—'}
                    </TableCell>
                    <TableCell>
                      <div className="flex gap-1">
                        <Button variant="ghost" size="icon" className="h-7 w-7"
                          onClick={() => navigate(`/admin/tenants/${t.id}`)}>
                          <Eye className="h-3.5 w-3.5" />
                        </Button>
                        <Button variant="ghost" size="icon" className="h-7 w-7"
                          disabled={togglingId === t.id || t.stato === 'draft' || t.stato === 'closed'}
                          onClick={() => handleToggleStatus(t)}>
                          {togglingId === t.id
                            ? <Loader2 className="h-3.5 w-3.5 animate-spin" />
                            : t.stato === 'active'
                              ? <Pause className="h-3.5 w-3.5" />
                              : <Play className="h-3.5 w-3.5" />}
                        </Button>
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

export default TenantsList;
