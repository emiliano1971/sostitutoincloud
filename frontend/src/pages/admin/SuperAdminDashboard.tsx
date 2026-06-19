import { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Building2, Users, Home, CalendarDays, Loader2, AlertCircle } from 'lucide-react';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { getSuperAdminDashboard, type SuperAdminDashboard } from '@/api/tenantApi';
import { getAuditLog, type AuditLogItem } from '@/api/auditApi';

const statusColor: Record<string, string> = {
  active: 'bg-success/10 text-success border-success/20',
  draft: 'bg-muted text-muted-foreground border-muted',
  suspended: 'bg-warning/10 text-warning border-warning/20',
  closed: 'bg-destructive/10 text-destructive border-destructive/20',
};

const SuperAdminDashboard = () => {
  const [data, setData] = useState<SuperAdminDashboard | null>(null);
  const [logs, setLogs] = useState<AuditLogItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getSuperAdminDashboard()
      .then(setData)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
    // Log di piattaforma: per ora senza filtro tenant — primi 5
    getAuditLog().then(items => setLogs(items.slice(0, 5))).catch(() => {});
  }, []);

  const kpis = data ? [
    { label: 'Tenant Attivi', value: `${data.tenantAttivi} / ${data.totalTenant}`, icon: Building2 },
    { label: 'Proprietari', value: data.totalProprietari, icon: Users },
    { label: 'Immobili', value: data.totalImmobili, icon: Home },
    { label: 'Prenotazioni', value: data.totalPrenotazioni, icon: CalendarDays },
  ] : [];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Dashboard Piattaforma</h1>
        <p className="text-sm text-muted-foreground">Panoramica generale della piattaforma</p>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-16 gap-2 text-muted-foreground">
          <Loader2 className="h-5 w-5 animate-spin" /> Caricamento dashboard…
        </div>
      ) : error || !data ? (
        <div className="flex items-center justify-center py-16 gap-2 text-destructive">
          <AlertCircle className="h-5 w-5" /> {error ?? 'Dati non disponibili'}
        </div>
      ) : (
        <>
          {/* KPIs */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {kpis.map(kpi => (
              <Card key={kpi.label}>
                <CardContent className="p-4 flex items-center gap-4">
                  <div className="h-10 w-10 rounded-lg bg-primary/10 flex items-center justify-center shrink-0">
                    <kpi.icon className="h-5 w-5 text-primary" />
                  </div>
                  <div>
                    <p className="text-2xl font-bold text-foreground">{kpi.value}</p>
                    <p className="text-xs text-muted-foreground">{kpi.label}</p>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>

          {/* Tenants */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Ultimi Tenant</CardTitle>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Tenant</TableHead>
                    <TableHead>Stato</TableHead>
                    <TableHead className="text-right">Immobili</TableHead>
                    <TableHead className="text-right">Prenotazioni</TableHead>
                    <TableHead>Creato il</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.ultimiTenant.map(t => (
                    <TableRow key={t.id}>
                      <TableCell>
                        <div>
                          <p className="font-medium text-sm">{t.displayName}</p>
                          <p className="text-xs text-muted-foreground">{t.legalName}</p>
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className={statusColor[t.stato] ?? ''}>
                          {t.stato}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">{t.propertiesCount}</TableCell>
                      <TableCell className="text-right">{t.bookingsCount}</TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {t.createdAt ? new Date(t.createdAt).toLocaleDateString('it-IT') : '—'}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          {/* Recent Audit */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Ultimi Log di Piattaforma</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-3">
                {logs.map(log => (
                  <div key={log.id} className="flex items-start gap-3 p-3 rounded-md bg-muted/50">
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium">{log.details}</p>
                      <p className="text-xs text-muted-foreground mt-0.5">{log.userEmail} · {new Date(log.createdAt).toLocaleString('it-IT')}</p>
                    </div>
                    <Badge variant="outline" className="text-[10px] shrink-0">{log.action}</Badge>
                  </div>
                ))}
                {logs.length === 0 && (
                  <p className="text-sm text-muted-foreground">Nessun log disponibile</p>
                )}
              </div>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
};

export default SuperAdminDashboard;
