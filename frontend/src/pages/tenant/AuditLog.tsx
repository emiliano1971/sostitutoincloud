import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Card, CardContent } from '@/components/ui/card';
import { Search, Filter, Loader2, AlertCircle } from 'lucide-react';
import { getAuditLog, type AuditLogItem } from '@/api/auditApi';

const ENTITA_FILTER = [
  { value: 'Booking', label: 'Prenotazioni' },
  { value: 'FiscalDocument', label: 'Documenti' },
  { value: 'OwnerProfile', label: 'Proprietari' },
  { value: 'Property', label: 'Immobili' },
  { value: 'Tenant', label: 'Tenant' },
  { value: 'Utente', label: 'Utenti' },
  { value: 'CanaleOta', label: 'OTA' },
  { value: 'RegolaTassaSoggiorno', label: 'Tassa Soggiorno' },
];

const AuditLog = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const entityFilter = searchParams.get('entity') ?? 'all';
  const [search, setSearch] = useState('');
  const [logs, setLogs] = useState<AuditLogItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const setEntityFilter = (value: string) => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      if (value === 'all') next.delete('entity');
      else next.set('entity', value);
      return next;
    });
  };

  useEffect(() => {
    setIsLoading(true);
    setError(null);
    getAuditLog({
      q: search || undefined,
      entity: entityFilter !== 'all' ? entityFilter : undefined,
    })
      .then(setLogs)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, [search, entityFilter]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Audit Log</h1>
        <p className="text-sm text-muted-foreground">Registro attività del sistema</p>
      </div>

      <Card>
        <CardContent className="p-4">
          <div className="flex flex-wrap gap-3">
            <div className="relative flex-1 min-w-[200px]">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input placeholder="Cerca nei log..." value={search} onChange={e => setSearch(e.target.value)} className="pl-9" />
            </div>
            <Select value={entityFilter} onValueChange={setEntityFilter}>
              <SelectTrigger className="w-[180px]"><Filter className="h-3.5 w-3.5 mr-2" /><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Tutte le entità</SelectItem>
                {ENTITA_FILTER.map(e => (
                  <SelectItem key={e.value} value={e.value}>{e.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </CardContent>
      </Card>

      {isLoading ? (
        <div className="flex items-center justify-center py-16 text-muted-foreground gap-2">
          <Loader2 className="h-5 w-5 animate-spin" />
          <span>Caricamento log…</span>
        </div>
      ) : error ? (
        <div className="flex items-center justify-center py-16 text-destructive gap-2">
          <AlertCircle className="h-5 w-5" />
          <span>{error}</span>
        </div>
      ) : logs.length === 0 ? (
        <div className="flex items-center justify-center py-16 text-muted-foreground">
          Nessun log trovato
        </div>
      ) : (
        <div className="space-y-2">
          {logs.map(log => (
            <Card key={log.id}>
              <CardContent className="p-4">
                <div className="flex items-start gap-3">
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium">{log.details}</p>
                    <div className="flex flex-wrap items-center gap-2 mt-1.5">
                      <span className="text-xs text-muted-foreground">{log.userEmail}</span>
                      <span className="text-xs text-muted-foreground">·</span>
                      <span className="text-xs text-muted-foreground">{new Date(log.createdAt).toLocaleString('it-IT')}</span>
                      <span className="text-xs text-muted-foreground">·</span>
                      <span className="text-xs font-mono text-muted-foreground">{log.ipAddress}</span>
                    </div>
                  </div>
                  <div className="flex flex-col items-end gap-1 shrink-0">
                    <Badge variant="outline" className="text-[10px]">{log.action}</Badge>
                    <Badge variant="secondary" className="text-[10px]">{log.entityType}</Badge>
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
};

export default AuditLog;
