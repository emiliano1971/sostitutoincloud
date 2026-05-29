import { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { TrendingUp, CalendarDays, FileText, Receipt, Download } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useAuth } from '@/contexts/AuthContext';
import { getOwnerDashboard, type OwnerDashboardDTO } from '@/api/ownerApi';
import { getBookings, type BookingListItem } from '@/api/bookingApi';
import { getCuList, type CuListItem } from '@/api/cuApi';

const OwnerDashboard = () => {
  const { user } = useAuth();
  const ownerId = user?.owner_id ? parseInt(user.owner_id) : undefined;

  const [dashboard, setDashboard] = useState<OwnerDashboardDTO | null>(null);
  const [recentBookings, setRecentBookings] = useState<BookingListItem[]>([]);
  const [cuList, setCuList] = useState<CuListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!ownerId) return;
    setLoading(true);
    Promise.all([
      getOwnerDashboard(ownerId),
      getBookings(),
      getCuList({ ownerId }),
    ])
      .then(([dash, bookings, cus]) => {
        setDashboard(dash);
        const ownerFullName = `${user?.first_name ?? ''} ${user?.last_name ?? ''}`.trim();
        setRecentBookings(
          bookings.filter(b => b.ownerName === ownerFullName).slice(0, 5)
        );
        setCuList(cus);
      })
      .catch(() => setError('Errore nel caricamento dei dati'))
      .finally(() => setLoading(false));
  }, [ownerId]);

  if (loading) return <div className="p-6 text-muted-foreground">Caricamento...</div>;
  if (error) return <div className="p-6 text-destructive">{error}</div>;

  const kpis = [
    { label: 'Ricavi Totali', value: `€${(dashboard?.ricaviTotali ?? 0).toLocaleString('it-IT', { minimumFractionDigits: 0 })}`, icon: TrendingUp, color: 'text-primary' },
    { label: 'Prenotazioni', value: dashboard?.prenotazioniCount ?? 0, icon: CalendarDays, color: 'text-success' },
    { label: 'Ritenute', value: `€${(dashboard?.totalRitenute ?? 0).toLocaleString('it-IT', { minimumFractionDigits: 0 })}`, icon: FileText, color: 'text-warning' },
    { label: 'Liquidato', value: `€${(dashboard?.totalLiquidato ?? 0).toLocaleString('it-IT', { minimumFractionDigits: 0 })}`, icon: Receipt, color: 'text-success' },
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

      {/* KPIs - mobile cards */}
      <div className="grid grid-cols-2 gap-3">
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
                <p className="text-sm font-medium">€{b.ownerNetAmount.toLocaleString('it-IT')}</p>
                <Badge variant="outline" className="text-[10px]">{b.statoPrenotazione}</Badge>
              </div>
            </div>
          ))}
          {recentBookings.length === 0 && (
            <p className="text-sm text-muted-foreground text-center">Nessuna prenotazione</p>
          )}
        </CardContent>
      </Card>

      {/* CU */}
      {cuList.length > 0 && (
        <Card>
          <CardHeader><CardTitle className="text-base">Certificazioni Uniche</CardTitle></CardHeader>
          <CardContent className="space-y-2">
            {cuList.map(cu => (
              <div key={cu.id} className="flex items-center justify-between p-3 rounded-lg bg-muted/50">
                <div>
                  <p className="text-sm font-medium">CU {cu.taxYear}</p>
                  <p className="text-xs text-muted-foreground">Compensi: €{cu.totalCompensi.toLocaleString('it-IT')} · Ritenute: €{cu.totalRitenute.toLocaleString('it-IT')}</p>
                </div>
                <Button variant="outline" size="sm" className="gap-1"><Download className="h-3.5 w-3.5" /> PDF</Button>
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
};

export default OwnerDashboard;
