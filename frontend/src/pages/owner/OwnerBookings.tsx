import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { mockBookings } from '@/data/mock-data';

const OwnerBookings = () => {
  const bookings = mockBookings.filter(b => b.owner_name === 'Anna Moretti');

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Prenotazioni</h1>
      <p className="text-sm text-muted-foreground">{bookings.length} prenotazioni</p>

      <div className="space-y-3">
        {bookings.map(b => (
          <Card key={b.booking_id}>
            <CardContent className="p-4">
              <div className="flex items-start justify-between">
                <div>
                  <p className="font-medium text-sm">{b.guest_name}</p>
                  <p className="text-xs text-muted-foreground mt-0.5">{b.property_name}</p>
                  <p className="text-xs text-muted-foreground">{b.checkin_date} → {b.checkout_date} · {b.nights} notti</p>
                  <Badge variant="outline" className="mt-2 text-[10px]">{b.channel_name}</Badge>
                </div>
                <div className="text-right">
                  <p className="font-bold text-sm">€{b.owner_net_amount.toLocaleString('it-IT')}</p>
                  <p className="text-[10px] text-muted-foreground">netto</p>
                  <Badge variant="outline" className="mt-1 text-[10px]">{b.booking_status}</Badge>
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
};

export default OwnerBookings;
