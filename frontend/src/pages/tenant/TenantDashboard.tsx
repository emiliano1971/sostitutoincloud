import { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  CalendarDays, FileText, Receipt, AlertTriangle, TrendingUp,
  CreditCard, Upload, ArrowRight, AlertCircle, Info, Loader2,
} from 'lucide-react';
import { getDashboard, type DashboardDTO } from '@/api/dashboardApi';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { useNavigate } from 'react-router-dom';

const TenantDashboard = () => {
  const navigate = useNavigate();
  const [dashboard, setDashboard] = useState<DashboardDTO | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getDashboard()
      .then(setDashboard)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, []);

  const fmt = (v: number) => `€${v.toLocaleString('it-IT', { minimumFractionDigits: 0 })}`;

  const kpis = dashboard ? [
    { label: 'Prenotazioni da Completare', value: dashboard.bookingsDaCompletare, icon: CalendarDays, color: 'text-primary' },
    { label: 'Ricavi Mese Corrente', value: fmt(dashboard.ricaviMeseCorrente), icon: TrendingUp, color: 'text-success' },
    { label: 'Documenti da Emettere', value: dashboard.documentiPending, icon: FileText, color: 'text-warning' },
    { label: 'F24 da Generare', value: dashboard.f24DaGenerare, icon: CreditCard, color: 'text-destructive' },
  ] : [];

  const alerts = dashboard ? [
    ...(dashboard.bookingsInPenale > 0 ? [{
      id: 'penale',
      type: 'error' as const,
      message: `${dashboard.bookingsInPenale} prenotazioni in penale (checkout scaduto da oltre 12 giorni)`,
      action: 'Vedi prenotazioni',
      url: '/bookings',
    }] : []),
    ...(dashboard.bookingsDaCompletare > 0 ? [{
      id: 'daCompletare',
      type: 'warning' as const,
      message: `${dashboard.bookingsDaCompletare} prenotazioni con checkout passato da completare`,
      action: 'Vedi prenotazioni',
      url: '/bookings',
    }] : []),
    ...(dashboard.f24DaGenerare > 0 ? [{
      id: 'f24',
      type: 'info' as const,
      message: `${dashboard.f24DaGenerare} F24 da generare`,
      action: 'Vedi F24',
      url: '/f24',
    }] : []),
  ] : [];

  const alertIcons = { warning: AlertTriangle, error: AlertCircle, info: Info };
  const alertColors = {
    warning: 'bg-warning/10 border-warning/20 text-warning',
    error: 'bg-destructive/10 border-destructive/20 text-destructive',
    info: 'bg-primary/10 border-primary/20 text-primary',
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Dashboard</h1>
          <p className="text-sm text-muted-foreground">Riepilogo attività</p>
        </div>
        <Button size="sm" className="gap-2" onClick={() => navigate('/import/bookings')}>
          <Upload className="h-4 w-4" /> Import
        </Button>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-20 gap-2 text-muted-foreground">
          <Loader2 className="h-5 w-5 animate-spin" />
          <span>Caricamento dashboard…</span>
        </div>
      ) : error ? (
        <div className="flex items-center justify-center py-20 gap-2 text-destructive">
          <AlertCircle className="h-5 w-5" />
          <span>{error}</span>
        </div>
      ) : (
        <>
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
          {alerts.length > 0 && (
            <div className="space-y-2">
              {alerts.map(alert => {
                const Icon = alertIcons[alert.type];
                return (
                  <div key={alert.id} className={`flex items-center gap-3 p-3 rounded-lg border ${alertColors[alert.type]}`}>
                    <Icon className="h-4 w-4 shrink-0" />
                    <span className="text-sm flex-1">{alert.message}</span>
                    {alert.action && (
                      <Button variant="ghost" size="sm" className="text-xs gap-1 shrink-0" onClick={() => navigate(alert.url)}>
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
                  <BarChart data={dashboard?.ricaviUltimi12Mesi ?? []}>
                    <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
                    <XAxis dataKey="mese" className="text-xs" tick={{ fill: 'hsl(var(--muted-foreground))' }} />
                    <YAxis className="text-xs" tick={{ fill: 'hsl(var(--muted-foreground))' }} tickFormatter={v => `€${(v / 1000).toFixed(0)}k`} />
                    <Tooltip formatter={(v: number) => `€${v.toLocaleString('it-IT')}`} />
                    <Legend />
                    <Bar dataKey="ricaviPm" name="Ricavi PM" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
                    <Bar dataKey="ricaviOw" name="Ricavi OW" fill="hsl(var(--success))" radius={[4, 4, 0, 0]} />
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
              <Card key={action.label} className="hover:border-primary/30 transition-colors cursor-pointer" onClick={() => navigate(action.url)}>
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
        </>
      )}
    </div>
  );
};

export default TenantDashboard;
