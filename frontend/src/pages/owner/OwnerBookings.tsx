import { useEffect, useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { useAuth } from '@/contexts/AuthContext';
import { getBookings, type BookingListItem } from '@/api/bookingApi';

const OwnerBookings = () => {
  const { user } = useAuth();
  const [bookings, setBookings] = useState<BookingListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getBookings()
      .then(all => {
        const ownerFullName = `${user?.first_name ?? ''} ${user?.last_name ?? ''}`.trim();
        setBookings(all.filter(b => b.ownerName === ownerFullName));
      })
      .catch(() => setError('Errore nel caricamento delle prenotazioni'))
      .finally(() => setLoading(false));
  }, [user]);

  if (loading) return <div className="p-6 text-muted-foreground">Caricamento...</div>;
  if (error) return <div className="p-6 text-destructive">{error}</div>;

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Prenotazioni</h1>
      <p className="text-sm text-muted-foreground">{bookings.length} prenotazioni</p>

      <div className="space-y-3">
        {bookings.map(b => (
          <Card key={b.id}>
            <CardContent className="p-4">
              <div className="flex items-start justify-between">
                <div>
                  <p className="font-medium text-sm">{b.guestName}</p>
                  <p className="text-xs text-muted-foreground mt-0.5">{b.propertyName}</p>
                  <p className="text-xs text-muted-foreground">{b.checkinDate} → {b.checkoutDate} · {b.nights} notti</p>
                  <Badge variant="outline" className="mt-2 text-[10px]">{b.channelName}</Badge>
                </div>
                <div className="text-right">
                  <p className="font-bold text-sm">€{b.ownerNetAmount.toLocaleString('it-IT')}</p>
                  <p className="text-[10px] text-muted-foreground">netto</p>
                  <Badge variant="outline" className="mt-1 text-[10px]">{b.statoPrenotazione}</Badge>
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
        {bookings.length === 0 && (
          <Card><CardContent className="p-8 text-center text-muted-foreground">Nessuna prenotazione</CardContent></Card>
        )}
      </div>
    </div>
  );
};

export default OwnerBookings;
