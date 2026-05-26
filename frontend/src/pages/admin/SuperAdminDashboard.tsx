import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Building2, Users, Home, CalendarDays } from 'lucide-react';
import { mockTenants, mockAuditLog } from '@/data/mock-data';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

const statusColor: Record<string, string> = {
  active: 'bg-success/10 text-success border-success/20',
  draft: 'bg-muted text-muted-foreground border-muted',
  suspended: 'bg-warning/10 text-warning border-warning/20',
  closed: 'bg-destructive/10 text-destructive border-destructive/20',
};

const SuperAdminDashboard = () => {
  const totalProps = mockTenants.reduce((s, t) => s + t.properties_count, 0);
  const totalOwners = mockTenants.reduce((s, t) => s + t.owners_count, 0);
  const totalBookings = mockTenants.reduce((s, t) => s + t.bookings_count, 0);

  const kpis = [
    { label: 'Tenant Attivi', value: mockTenants.filter(t => t.tenant_status === 'active').length, total: mockTenants.length, icon: Building2 },
    { label: 'Proprietari', value: totalOwners, icon: Users },
    { label: 'Immobili', value: totalProps, icon: Home },
    { label: 'Prenotazioni', value: totalBookings, icon: CalendarDays },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Dashboard Piattaforma</h1>
        <p className="text-sm text-muted-foreground">Panoramica generale della piattaforma</p>
      </div>

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
              {mockTenants.map(t => (
                <TableRow key={t.tenant_id}>
                  <TableCell>
                    <div>
                      <p className="font-medium text-sm">{t.display_name}</p>
                      <p className="text-xs text-muted-foreground">{t.legal_name}</p>
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline" className={statusColor[t.tenant_status]}>
                      {t.tenant_status}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">{t.properties_count}</TableCell>
                  <TableCell className="text-right">{t.bookings_count}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">{t.created_at}</TableCell>
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
            {mockAuditLog.filter(l => !l.tenant_id).slice(0, 5).map(log => (
              <div key={log.log_id} className="flex items-start gap-3 p-3 rounded-md bg-muted/50">
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium">{log.details}</p>
                  <p className="text-xs text-muted-foreground mt-0.5">{log.user_email} · {new Date(log.created_at).toLocaleString('it-IT')}</p>
                </div>
                <Badge variant="outline" className="text-[10px] shrink-0">{log.action}</Badge>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default SuperAdminDashboard;
