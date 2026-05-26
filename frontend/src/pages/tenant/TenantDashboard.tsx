import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  CalendarDays, FileText, Receipt, AlertTriangle, TrendingUp,
  CreditCard, Upload, ArrowRight, AlertCircle, Info,
} from 'lucide-react';
import { mockBookings, mockDocuments, mockAlerts, mockRevenueData, mockF24 } from '@/data/mock-data';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { useNavigate } from 'react-router-dom';

const alertIcons = { warning: AlertTriangle, error: AlertCircle, info: Info };

const TenantDashboard = () => {
  const navigate = useNavigate();
  const t1Bookings = mockBookings.filter(b => b.tenant_id === 't1');
  const thisMonth = t1Bookings.filter(b => b.checkin_date.startsWith('2025-03'));
  const pendingDocs = mockDocuments.filter(d => d.status === 'draft' || d.status === 'ready').length;
  const totalRevenue = thisMonth.reduce((s, b) => s + b.gross_amount, 0);
  const pendingF24 = mockF24.filter(f => f.status === 'draft').length;

  const kpis = [
    { label: 'Prenotazioni Mese', value: thisMonth.length, icon: CalendarDays, color: 'text-primary' },
    { label: 'Ricavi Mese', value: `€${totalRevenue.toLocaleString('it-IT')}`, icon: TrendingUp, color: 'text-success' },
    { label: 'Documenti da Emettere', value: pendingDocs, icon: FileText, color: 'text-warning' },
    { label: 'F24 da Generare', value: pendingF24, icon: CreditCard, color: 'text-destructive' },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Dashboard</h1>
          <p className="text-sm text-muted-foreground">Riepilogo attività — Marzo 2025</p>
        </div>
        <Button size="sm" className="gap-2" onClick={() => navigate('/import/bookings')}>
          <Upload className="h-4 w-4" /> Import
        </Button>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {kpis.map(kpi => (
          <Card key={kpi.label}>
            <CardContent className="p-4">
              <div className="flex items-center justify-between mb-2">
                <kpi.icon className={`h-5 w-5 ${kpi.color}`} />
              </div>
              <p className="text-2xl font-bold text-foreground">{kpi.value}</p>
              <p className="text-xs text-muted-foreground">{kpi.label}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Alerts */}
      {mockAlerts.length > 0 && (
        <div className="space-y-2">
          {mockAlerts.map(alert => {
            const Icon = alertIcons[alert.type];
            const colors = {
              warning: 'bg-warning/10 border-warning/20 text-warning',
              error: 'bg-destructive/10 border-destructive/20 text-destructive',
              info: 'bg-primary/10 border-primary/20 text-primary',
            };
            return (
              <div key={alert.id} className={`flex items-center gap-3 p-3 rounded-lg border ${colors[alert.type]}`}>
                <Icon className="h-4 w-4 shrink-0" />
                <span className="text-sm flex-1">{alert.message}</span>
                {alert.action && (
                  <Button variant="ghost" size="sm" className="text-xs gap-1 shrink-0">
                    {alert.action} <ArrowRight className="h-3 w-3" />
                  </Button>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Revenue Chart */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Ricavi ultimi 12 mesi</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="h-[300px]">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={mockRevenueData}>
                <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
                <XAxis dataKey="month" className="text-xs" tick={{ fill: 'hsl(var(--muted-foreground))' }} />
                <YAxis className="text-xs" tick={{ fill: 'hsl(var(--muted-foreground))' }} tickFormatter={v => `€${(v/1000).toFixed(0)}k`} />
                <Tooltip formatter={(v: number) => `€${v.toLocaleString('it-IT')}`} />
                <Legend />
                <Bar dataKey="ricavi_pm" name="Ricavi PM" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
                <Bar dataKey="ricavi_ow" name="Ricavi OW" fill="hsl(var(--success))" radius={[4, 4, 0, 0]} />
                <Bar dataKey="commissioni" name="Commissioni" fill="hsl(var(--warning))" radius={[4, 4, 0, 0]} />
                <Bar dataKey="ritenute" name="Ritenute" fill="hsl(var(--destructive))" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </CardContent>
      </Card>

      {/* Quick actions */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {[
          { label: 'Import Prenotazioni', desc: 'Carica file CSV/XLSX dal channel manager', icon: Upload, url: '/import/bookings' },
          { label: 'Emetti Documenti', desc: 'Genera fatture e ricevute per checkout recenti', icon: FileText, url: '/documents' },
          { label: 'Liquidazioni', desc: 'Gestisci pagamenti ai proprietari', icon: Receipt, url: '/settlements' },
        ].map(action => (
          <Card key={action.label} className="hover:border-primary/30 transition-colors cursor-pointer">
            <CardContent className="p-4 flex items-start gap-3">
              <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center shrink-0">
                <action.icon className="h-4 w-4 text-primary" />
              </div>
              <div>
                <p className="text-sm font-medium">{action.label}</p>
                <p className="text-xs text-muted-foreground">{action.desc}</p>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
};

export default TenantDashboard;
