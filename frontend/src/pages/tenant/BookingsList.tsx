import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Search, Filter, Eye, Upload, AlertTriangle } from 'lucide-react';
import { mockBookings } from '@/data/mock-data';
import { useNavigate } from 'react-router-dom';

const statusLabels: Record<string, string> = {
  imported: 'Importata',
  enriched: 'Arricchita',
  ready: 'Pronta',
  doc_issued: 'Doc. Emesso',
  settled: 'Liquidata',
  cancelled: 'Annullata',
};

const statusColors: Record<string, string> = {
  imported: 'bg-muted text-muted-foreground',
  enriched: 'bg-primary/10 text-primary',
  ready: 'bg-success/10 text-success',
  doc_issued: 'bg-accent/10 text-accent-foreground',
  settled: 'bg-success/20 text-success',
  cancelled: 'bg-destructive/10 text-destructive',
};

const channelColors: Record<string, string> = {
  airbnb: 'bg-[#FF5A5F]/10 text-[#FF5A5F]',
  booking: 'bg-[#003580]/10 text-[#003580]',
  vrbo: 'bg-[#3B5998]/10 text-[#3B5998]',
};

const BookingsList = () => {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('da_completare');
  const [channelFilter, setChannelFilter] = useState<string>('all');

  const today = new Date().toISOString().slice(0, 10);

  const filtered = mockBookings
    .filter(b => b.tenant_id === 't1')
    .filter(b => {
      if (statusFilter === 'da_completare') {
        return b.checkout_date <= today && !['doc_issued', 'settled'].includes(b.booking_status) && b.booking_status !== 'cancelled';
      }
      return statusFilter === 'all' || b.booking_status === statusFilter;
    })
    .filter(b => channelFilter === 'all' || b.channel_name === channelFilter)
    .filter(b =>
      search === '' ||
      b.guest_name.toLowerCase().includes(search.toLowerCase()) ||
      b.property_name.toLowerCase().includes(search.toLowerCase()) ||
      b.external_booking_id.toLowerCase().includes(search.toLowerCase())
    );

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Prenotazioni</h1>
          <p className="text-sm text-muted-foreground">{filtered.length} prenotazioni trovate</p>
        </div>
        <Button size="sm" className="gap-2" onClick={() => navigate('/import/bookings')}>
          <Upload className="h-4 w-4" /> Import
        </Button>
      </div>

      {/* Filters */}
      <Card>
        <CardContent className="p-4">
          <div className="flex flex-wrap gap-3">
            <div className="relative flex-1 min-w-[200px]">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input placeholder="Cerca ospite, immobile, ID..." value={search} onChange={e => setSearch(e.target.value)} className="pl-9" />
            </div>
            <Select value={statusFilter} onValueChange={setStatusFilter}>
              <SelectTrigger className="w-[180px]">
                <Filter className="h-3.5 w-3.5 mr-2" />
                <SelectValue placeholder="Stato" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="da_completare">Da completare</SelectItem>
                <SelectItem value="all">Tutti gli stati</SelectItem>
                <SelectItem value="imported">Importata</SelectItem>
                <SelectItem value="enriched">Arricchita</SelectItem>
                <SelectItem value="ready">Pronta</SelectItem>
                <SelectItem value="doc_issued">Doc. Emesso</SelectItem>
                <SelectItem value="settled">Liquidata</SelectItem>
              </SelectContent>
            </Select>
            <Select value={channelFilter} onValueChange={setChannelFilter}>
              <SelectTrigger className="w-[140px]">
                <SelectValue placeholder="Canale" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Tutti i canali</SelectItem>
                <SelectItem value="airbnb">Airbnb</SelectItem>
                <SelectItem value="booking">Booking</SelectItem>
                <SelectItem value="vrbo">Vrbo</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </CardContent>
      </Card>

      {/* Table */}
      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>ID / Canale</TableHead>
                <TableHead>Ospite</TableHead>
                <TableHead>Immobile</TableHead>
                <TableHead>Check-in</TableHead>
                <TableHead>Check-out</TableHead>
                <TableHead className="text-right">Notti</TableHead>
                <TableHead className="text-right">Lordo €</TableHead>
                <TableHead>Stato</TableHead>
                <TableHead className="w-10"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.slice(0, 20).map(b => {
                const checkoutDate = new Date(b.checkout_date);
                const todayDate = new Date(today);
                const daysSinceCheckout = Math.floor((todayDate.getTime() - checkoutDate.getTime()) / (1000 * 60 * 60 * 24));
                const isOverdue = daysSinceCheckout > 0 && !['doc_issued', 'settled'].includes(b.booking_status) && b.booking_status !== 'cancelled';
                const isPenalty = daysSinceCheckout > 12 && isOverdue;

                return (
                <TableRow key={b.booking_id} className={`cursor-pointer ${isPenalty ? 'bg-destructive/8 hover:bg-destructive/12' : isOverdue ? 'bg-warning/6 hover:bg-warning/10' : ''}`} onClick={() => navigate(`/bookings/${b.booking_id}`)}>
                  <TableCell>
                    <div>
                      <p className="text-xs font-mono text-muted-foreground">{b.external_booking_id}</p>
                      <Badge variant="outline" className={`text-[10px] mt-0.5 ${channelColors[b.channel_name] || ''}`}>
                        {b.channel_name}
                      </Badge>
                    </div>
                  </TableCell>
                  <TableCell className="font-medium text-sm">{b.guest_name}</TableCell>
                  <TableCell className="text-sm">{b.property_name}</TableCell>
                  <TableCell className="text-sm">{b.checkin_date}</TableCell>
                  <TableCell className="text-sm">{b.checkout_date}</TableCell>
                  <TableCell className="text-right text-sm">{b.nights}</TableCell>
                  <TableCell className="text-right text-sm font-medium">€{b.gross_amount.toLocaleString('it-IT')}</TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1.5">
                      {isPenalty && <AlertTriangle className="h-3.5 w-3.5 text-destructive flex-shrink-0" />}
                      <Badge variant="outline" className={
                        isPenalty ? 'bg-destructive/15 text-destructive border-destructive/30' :
                        isOverdue ? statusColors[b.booking_status] :
                        statusColors[b.booking_status]
                      }>
                        {isPenalty ? `${daysSinceCheckout}gg - PENALE` : isOverdue ? `${daysSinceCheckout}gg - Scaduta` : (statusLabels[b.booking_status] || b.booking_status)}
                      </Badge>
                    </div>
                  </TableCell>
                  <TableCell>
                    <Button variant="ghost" size="icon" className="h-7 w-7">
                      <Eye className="h-3.5 w-3.5" />
                    </Button>
                  </TableCell>
                </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
};

export default BookingsList;
