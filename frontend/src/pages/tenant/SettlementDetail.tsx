import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ArrowLeft, Loader2, AlertCircle } from 'lucide-react';
import { getSettlementById, type SettlementDetail as SettlementDetailType } from '@/api/settlementApi';

const statusColors: Record<string, string> = {
  pending: 'bg-muted text-muted-foreground',
  calculated: 'bg-primary/10 text-primary',
  approved: 'bg-warning/10 text-warning',
  paid: 'bg-success/10 text-success',
};

const statusLabels: Record<string, string> = {
  pending: 'In attesa',
  calculated: 'Calcolato',
  approved: 'Approvato',
  paid: 'Pagato',
};

const fmtEuro = (v: number) => `€${(v ?? 0).toLocaleString('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const fmtDate = (s?: string) => (s ? new Date(s).toLocaleDateString('it-IT') : '—');

const notti = (checkin: string, checkout: string): number => {
  if (!checkin || !checkout) return 0;
  const ms = new Date(checkout).getTime() - new Date(checkin).getTime();
  return Math.max(0, Math.round(ms / (1000 * 60 * 60 * 24)));
};

const SettlementDetail = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [settlement, setSettlement] = useState<SettlementDetailType | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    setIsLoading(true);
    getSettlementById(Number(id))
      .then(setSettlement)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, [id]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-16 text-muted-foreground gap-2">
        <Loader2 className="h-5 w-5 animate-spin" />
        <span>Caricamento liquidazione…</span>
      </div>
    );
  }

  if (error || !settlement) {
    return (
      <div className="space-y-4">
        <Button variant="ghost" onClick={() => navigate('/settlements')}>
          <ArrowLeft className="h-4 w-4 mr-2" /> Torna alle liquidazioni
        </Button>
        <div className="flex items-center justify-center py-16 text-destructive gap-2">
          <AlertCircle className="h-5 w-5" />
          <span>{error ?? 'Liquidazione non trovata'}</span>
        </div>
      </div>
    );
  }

  const bookings = settlement.bookings ?? [];
  // Netto per riga = quanto realmente pagato al proprietario = ownerNetAmount − ritenuta.
  // Così i totali della tabella riconciliano con settlement.netAmount (card "Netto da pagare").
  const nettoRiga = (b: { ownerNetAmount?: number; withholdingAmount?: number }) =>
    (b.ownerNetAmount ?? 0) - (b.withholdingAmount ?? 0);
  const sumLordo = bookings.reduce((acc, b) => acc + (b.grossAmount ?? 0), 0);
  const sumBolloCents = bookings.reduce((acc, b) => acc + (b.bolloCents ?? 0), 0);
  const sumRitenuta = bookings.reduce((acc, b) => acc + (b.withholdingAmount ?? 0), 0);
  const sumNetto = bookings.reduce((acc, b) => acc + nettoRiga(b), 0);
  const sumBollo = sumBolloCents / 100;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="space-y-3">
        <Button variant="ghost" className="h-8 px-2 -ml-2" onClick={() => navigate('/settlements')}>
          <ArrowLeft className="h-4 w-4 mr-2" /> Torna alle liquidazioni
        </Button>
        <div className="flex items-center justify-between flex-wrap gap-3">
          <div>
            <h1 className="text-2xl font-bold">Liquidazione — {settlement.ownerName}</h1>
            <p className="text-sm text-muted-foreground flex items-center gap-2">
              Periodo: {settlement.period}
              <span className="text-muted-foreground/50">|</span>
              Stato:
              <Badge variant="outline" className={statusColors[settlement.stato]}>
                {statusLabels[settlement.stato] || settlement.stato}
              </Badge>
            </p>
          </div>
        </div>
      </div>

      {/* Tabella prenotazioni */}
      <Card>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-10">#</TableHead>
                  <TableHead>ID Prenotazione</TableHead>
                  <TableHead>Immobile</TableHead>
                  <TableHead>Check-in</TableHead>
                  <TableHead>Check-out</TableHead>
                  <TableHead className="text-right">Notti</TableHead>
                  <TableHead className="text-right">Lordo €</TableHead>
                  <TableHead className="text-right">Bollo €</TableHead>
                  <TableHead className="text-right">Ritenuta €</TableHead>
                  <TableHead className="text-right">Netto €</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {bookings.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={10} className="text-center py-10 text-muted-foreground">
                      Nessuna prenotazione collegata
                    </TableCell>
                  </TableRow>
                ) : (
                  bookings.map((b, i) => (
                    <TableRow
                      key={b.bookingId}
                      className="cursor-pointer"
                      onClick={() => navigate(`/bookings/${b.bookingId}`)}
                    >
                      <TableCell className="text-muted-foreground">{i + 1}</TableCell>
                      <TableCell className="font-mono text-xs">{b.externalBookingId}</TableCell>
                      <TableCell>{b.propertyName}</TableCell>
                      <TableCell className="text-sm">{fmtDate(b.checkinDate)}</TableCell>
                      <TableCell className="text-sm">{fmtDate(b.checkoutDate)}</TableCell>
                      <TableCell className="text-right">{notti(b.checkinDate, b.checkoutDate)}</TableCell>
                      <TableCell className="text-right">{fmtEuro(b.grossAmount)}</TableCell>
                      <TableCell className="text-right">{fmtEuro((b.bolloCents ?? 0) / 100)}</TableCell>
                      <TableCell className="text-right text-destructive">-{fmtEuro(b.withholdingAmount)}</TableCell>
                      <TableCell className="text-right font-semibold">{fmtEuro(nettoRiga(b))}</TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
              {bookings.length > 0 && (
                <tfoot>
                  <TableRow className="bg-muted/50 font-bold hover:bg-muted/50">
                    <TableCell colSpan={6}>TOTALE</TableCell>
                    <TableCell className="text-right">{fmtEuro(sumLordo)}</TableCell>
                    <TableCell className="text-right">{fmtEuro(sumBollo)}</TableCell>
                    <TableCell className="text-right text-destructive">-{fmtEuro(sumRitenuta)}</TableCell>
                    <TableCell className="text-right">{fmtEuro(sumNetto)}</TableCell>
                  </TableRow>
                </tfoot>
              )}
            </Table>
          </div>
        </CardContent>
      </Card>

      {/* Riepilogo card */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Lordo totale</p>
            <p className="text-xl font-bold mt-1">{fmtEuro(sumLordo)}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Bollo totale</p>
            <p className="text-xl font-bold mt-1">{fmtEuro(sumBollo)}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Ritenute totali</p>
            <p className="text-xl font-bold mt-1 text-destructive">-{fmtEuro(settlement.withholdingAmount)}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Netto da pagare</p>
            <p className="text-xl font-bold mt-1 text-success">{fmtEuro(settlement.netAmount)}</p>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default SettlementDetail;
