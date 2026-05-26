import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { TrendingUp, CalendarDays, FileText, Receipt, Download } from 'lucide-react';
import { mockBookings, mockRevenueData, mockSettlements, mockCU } from '@/data/mock-data';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

const OwnerDashboard = () => {
  const ownerBookings = mockBookings.filter(b => b.owner_name === 'Anna Moretti');
  const totalRevenue = ownerBookings.reduce((s, b) => s + b.owner_net_amount, 0);
  const totalWithholding = ownerBookings.reduce((s, b) => s + b.withholding_amount, 0);
  const ownerSettlements = mockSettlements.filter(s => s.owner_id === 'o1');
  const paidAmount = ownerSettlements.filter(s => s.status === 'paid').reduce((sum, s) => sum + s.net_amount, 0);

  const kpis = [
    { label: 'Ricavi Totali', value: `€${totalRevenue.toLocaleString('it-IT', { minimumFractionDigits: 0 })}`, icon: TrendingUp, color: 'text-primary' },
    { label: 'Prenotazioni', value: ownerBookings.length, icon: CalendarDays, color: 'text-success' },
    { label: 'Ritenute', value: `€${totalWithholding.toLocaleString('it-IT', { minimumFractionDigits: 0 })}`, icon: FileText, color: 'text-warning' },
    { label: 'Liquidato', value: `€${paidAmount.toLocaleString('it-IT', { minimumFractionDigits: 0 })}`, icon: Receipt, color: 'text-success' },
  ];

  // Simplified owner revenue data
  const ownerRevenue = mockRevenueData.map(d => ({
    month: d.month,
    netto: Math.round((d.ricavi_pm + d.ricavi_ow) * 0.45),
    ritenute: Math.round((d.ricavi_pm + d.ricavi_ow) * 0.45 * 0.21),
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
          {ownerBookings.slice(0, 5).map(b => (
            <div key={b.booking_id} className="flex items-center justify-between p-3 rounded-lg bg-muted/50">
              <div>
                <p className="text-sm font-medium">{b.guest_name}</p>
                <p className="text-xs text-muted-foreground">{b.property_name} · {b.checkin_date}</p>
              </div>
              <div className="text-right">
                <p className="text-sm font-medium">€{b.owner_net_amount.toLocaleString('it-IT')}</p>
                <Badge variant="outline" className="text-[10px]">{b.booking_status}</Badge>
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      {/* CU */}
      {mockCU.filter(cu => cu.owner_id === 'o1').length > 0 && (
        <Card>
          <CardHeader><CardTitle className="text-base">Certificazioni Uniche</CardTitle></CardHeader>
          <CardContent className="space-y-2">
            {mockCU.filter(cu => cu.owner_id === 'o1').map(cu => (
              <div key={cu.cu_id} className="flex items-center justify-between p-3 rounded-lg bg-muted/50">
                <div>
                  <p className="text-sm font-medium">CU {cu.tax_year}</p>
                  <p className="text-xs text-muted-foreground">Compensi: €{cu.total_compensi.toLocaleString('it-IT')} · Ritenute: €{cu.total_ritenute.toLocaleString('it-IT')}</p>
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
