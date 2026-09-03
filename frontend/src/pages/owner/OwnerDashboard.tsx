import { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { TrendingUp, CalendarDays, FileText, Receipt, Wallet, Coins, Loader2, AlertCircle } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { getOwnerDashboardSelf, getOwnerBookings, type OwnerDashboardDTO } from '@/api/ownerApi';
import type { BookingListItem } from '@/api/bookingApi';

const fmtEuro = (v?: number) =>
  `€${(v ?? 0).toLocaleString('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const OwnerDashboard = () => {
  const [dashboard, setDashboard] = useState<OwnerDashboardDTO | null>(null);
  const [recentBookings, setRecentBookings] = useState<BookingListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    // Entrambi gli endpoint ricavano il proprietario dal token: non serve più
    // scaricare tutte le prenotazioni del tenant e filtrarle per nome nel browser.
    Promise.all([getOwnerDashboardSelf(), getOwnerBookings()])
      .then(([dash, bookings]) => {
        setDashboard(dash);
        setRecentBookings(bookings.slice(0, 5));
      })
      .catch(err => setError(err instanceof Error ? err.message : 'Errore nel caricamento dei dati'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16 text-muted-foreground gap-2">
        <Loader2 className="h-5 w-5 animate-spin" />
        <span>Caricamento…</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center py-16 text-destructive gap-2">
        <AlertCircle className="h-5 w-5" />
        <span>{error}</span>
      </div>
    );
  }

  const kpis = [
    { label: 'Totale lordo', value: fmtEuro(dashboard?.ricaviTotali), icon: TrendingUp, color: 'text-primary' },
    { label: 'Netto maturato', value: fmtEuro(dashboard?.totalNet), icon: Wallet, color: 'text-success' },
    { label: 'Ritenute', value: fmtEuro(dashboard?.totalRitenute), icon: FileText, color: 'text-warning' },
    { label: 'Prenotazioni', value: dashboard?.prenotazioniCount ?? 0, icon: CalendarDays, color: 'text-primary' },
    { label: 'Liquidazioni', value: dashboard?.settlementsCount ?? 0, icon: Receipt, color: 'text-primary' },
    { label: 'Netto da pagare', value: fmtEuro(dashboard?.netDaPagare), icon: Coins, color: 'text-success' },
  ];

  const ownerRevenue = (dashboard?.ricaviMensili ?? []).map(d => ({
    month: d.mese,
    netto: d.ricaviOw,
    ritenute: d.ritenute,
  }));

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">I Miei Ricavi</h1>
        <p className="text-sm text-muted-foreground">Riepilogo della tua attività</p>
      </div>

      {/* KPI */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
        {kpis.map(kpi => (
          <Card key={kpi.label}>
            <CardContent className="p-4">
              <kpi.icon className={`h-5 w-5 ${kpi.color} mb-2`} />
              <p className="text-xl font-bold">{kpi.value}</p>
              <p className="text-[11px] text-muted-foreground">{kpi.label}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Chart */}
      <Card>
        <CardHeader><CardTitle className="text-base">Ricavi Mensili</CardTitle></CardHeader>
        <CardContent>
          <div className="h-[250px]">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={ownerRevenue}>
                <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
                <XAxis dataKey="month" tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 11 }} />
                <YAxis tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 11 }} tickFormatter={v => `€${(v/1000).toFixed(0)}k`} />
                <Tooltip formatter={(v: number) => `€${v.toLocaleString('it-IT')}`} />
                <Bar dataKey="netto" name="Netto" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
                <Bar dataKey="ritenute" name="Ritenute" fill="hsl(var(--warning))" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </CardContent>
      </Card>

      {/* Recent bookings */}
      <Card>
        <CardHeader><CardTitle className="text-base">Ultime Prenotazioni</CardTitle></CardHeader>
        <CardContent className="space-y-3 p-4">
          {recentBookings.map(b => (
            <div key={b.id} className="flex items-center justify-between p-3 rounded-lg bg-muted/50">
              <div>
                <p className="text-sm font-medium">{b.guestName}</p>
                <p className="text-xs text-muted-foreground">{b.propertyName} · {b.checkinDate}</p>
              </div>
              <div className="text-right">
                <p className="text-sm font-medium">{fmtEuro(b.ownerNetAmount)}</p>
                <Badge variant="outline" className="text-[10px]">{b.statoPrenotazione}</Badge>
              </div>
            </div>
          ))}
          {recentBookings.length === 0 && (
            <p className="text-sm text-muted-foreground text-center">Nessuna prenotazione</p>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default OwnerDashboard;
