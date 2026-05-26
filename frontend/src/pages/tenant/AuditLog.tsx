import { useState } from 'react';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Card, CardContent } from '@/components/ui/card';
import { Search, Filter } from 'lucide-react';
import { mockAuditLog } from '@/data/mock-data';

const AuditLog = () => {
  const [search, setSearch] = useState('');
  const [actionFilter, setActionFilter] = useState('all');

  const logs = mockAuditLog
    .filter(l => actionFilter === 'all' || l.action.startsWith(actionFilter))
    .filter(l => search === '' || l.details.toLowerCase().includes(search.toLowerCase()) || l.user_email.toLowerCase().includes(search.toLowerCase()));

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
            <Select value={actionFilter} onValueChange={setActionFilter}>
              <SelectTrigger className="w-[160px]"><Filter className="h-3.5 w-3.5 mr-2" /><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Tutte le azioni</SelectItem>
                <SelectItem value="booking">Prenotazioni</SelectItem>
                <SelectItem value="document">Documenti</SelectItem>
                <SelectItem value="settlement">Liquidazioni</SelectItem>
                <SelectItem value="tenant">Tenant</SelectItem>
                <SelectItem value="owner">Proprietari</SelectItem>
                <SelectItem value="f24">F24</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </CardContent>
      </Card>

      <div className="space-y-2">
        {logs.map(log => (
          <Card key={log.log_id}>
            <CardContent className="p-4">
              <div className="flex items-start gap-3">
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium">{log.details}</p>
                  <div className="flex flex-wrap items-center gap-2 mt-1.5">
                    <span className="text-xs text-muted-foreground">{log.user_email}</span>
                    <span className="text-xs text-muted-foreground">·</span>
                    <span className="text-xs text-muted-foreground">{new Date(log.created_at).toLocaleString('it-IT')}</span>
                    <span className="text-xs text-muted-foreground">·</span>
                    <span className="text-xs font-mono text-muted-foreground">{log.ip_address}</span>
                  </div>
                </div>
                <div className="flex flex-col items-end gap-1 shrink-0">
                  <Badge variant="outline" className="text-[10px]">{log.action}</Badge>
                  <Badge variant="secondary" className="text-[10px]">{log.entity_type}</Badge>
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
};

export default AuditLog;
