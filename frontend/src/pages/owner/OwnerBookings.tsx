import { useEffect, useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Loader2, AlertCircle } from 'lucide-react';
import { getOwnerBookings } from '@/api/ownerApi';
import type { BookingListItem } from '@/api/bookingApi';
import { labelStatoPrenotazione } from '@/lib/statiLabels';

const fmtEuro = (v?: number) =>
  `€${(v ?? 0).toLocaleString('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const fmtDate = (s?: string) => (s ? new Date(s).toLocaleDateString('it-IT') : '—');

const OwnerBookings = () => {
  const [bookings, setBookings] = useState<BookingListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    // Nessun filtro lato client: /api/owner/bookings restituisce già solo le
    // prenotazioni del proprietario autenticato.
    getOwnerBookings()
      .then(setBookings)
      .catch(err => setError(err instanceof Error ? err.message : 'Errore nel caricamento delle prenotazioni'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16 text-muted-foreground gap-2">
        <Loader2 className="h-5 w-5 animate-spin" />
        <span>Caricamento prenotazioni…</span>
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

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-bold">Prenotazioni</h1>
        <p className="text-sm text-muted-foreground">{bookings.length} prenotazioni</p>
      </div>

      <Card>
        <CardContent className="p-0">
          {bookings.length === 0 ? (
            <div className="py-12 text-center text-muted-foreground">Nessuna prenotazione</div>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>ID</TableHead>
                    <TableHead>Immobile</TableHead>
                    <TableHead>Check-in</TableHead>
                    <TableHead>Check-out</TableHead>
                    <TableHead className="text-right">Notti</TableHead>
                    <TableHead className="text-right">Lordo</TableHead>
                    <TableHead className="text-right">Netto</TableHead>
                    <TableHead>Stato</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {bookings.map(b => (
                    <TableRow key={b.id}>
                      <TableCell className="font-mono text-xs">{b.externalBookingId}</TableCell>
                      <TableCell className="text-sm">{b.propertyName}</TableCell>
                      <TableCell className="text-sm whitespace-nowrap">{fmtDate(b.checkinDate)}</TableCell>
                      <TableCell className="text-sm whitespace-nowrap">{fmtDate(b.checkoutDate)}</TableCell>
                      <TableCell className="text-right">{b.nights}</TableCell>
                      <TableCell className="text-right">{fmtEuro(b.grossAmount)}</TableCell>
                      <TableCell className="text-right font-medium">{fmtEuro(b.ownerNetAmount)}</TableCell>
                      <TableCell>
                        <Badge variant="outline" className="text-xs">{labelStatoPrenotazione(b.statoPrenotazione)}</Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default OwnerBookings;
