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

// Sfondi per gruppo di colonne (chiari in light, tenui in dark)
const COST_BG = 'bg-[#fff5f5] dark:bg-red-950/20';       // voci di costo
const FISCAL_BG = 'bg-[#fffaf0] dark:bg-amber-950/20';   // deduzioni fiscali
const NET_BG = 'bg-[#f0fff4] dark:bg-green-950/20';      // netto

const fmtEuro = (v: number) => `€${(v ?? 0).toLocaleString('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

// Voce di costo/deduzione: negativa in rosso se > 0, altrimenti €0,00 neutro
const fmtCost = (v: number) => ((v ?? 0) > 0 ? `-${fmtEuro(v)}` : fmtEuro(0));

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

  // Netto per riga = canone − ritenuta (il bollo è informativo e NON incide sui totali).
  // Il totale riconcilia con settlement.netAmount (card "Netto da pagare").
  const nettoRiga = (b: { ownerNetAmount?: number; withholdingAmount?: number }) =>
    (b.ownerNetAmount ?? 0) - (b.withholdingAmount ?? 0);

  const sumLordo = bookings.reduce((acc, b) => acc + (b.grossAmount ?? 0), 0);
  const sumOta = bookings.reduce((acc, b) => acc + (b.otaCommissionAmount ?? 0), 0);
  const sumCleaning = bookings.reduce((acc, b) => acc + (b.cleaningAmount ?? 0), 0);
  const sumPm = bookings.reduce((acc, b) => acc + (b.pmFeeAmount ?? 0), 0);
  const sumIva = bookings.reduce((acc, b) => acc + (b.ivaAmount ?? 0), 0);
  const sumCanone = bookings.reduce((acc, b) => acc + (b.ownerNetAmount ?? 0), 0);
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

      {/* Tabella prenotazioni — dal lordo al netto */}
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
                  <TableHead className={`text-right ${COST_BG}`}>Comm. OTA €</TableHead>
                  <TableHead className={`text-right ${COST_BG}`}>Pulizie €</TableHead>
                  <TableHead className={`text-right ${COST_BG}`}>Provv. PM €</TableHead>
                  <TableHead className={`text-right ${COST_BG}`}>di cui IVA €</TableHead>
                  <TableHead className="text-right">Canone €</TableHead>
                  <TableHead className={`text-right ${FISCAL_BG}`}>Bollo €</TableHead>
                  <TableHead className={`text-right ${FISCAL_BG}`}>Ritenuta €</TableHead>
                  <TableHead className={`text-right ${NET_BG}`}>Netto €</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {bookings.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={15} className="text-center py-10 text-muted-foreground">
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
                      <TableCell className={`text-right text-destructive ${COST_BG}`}>{fmtCost(b.otaCommissionAmount)}</TableCell>
                      <TableCell className={`text-right text-destructive ${COST_BG}`}>{fmtCost(b.cleaningAmount)}</TableCell>
                      <TableCell className={`text-right text-destructive ${COST_BG}`}>{fmtCost(b.pmFeeAmount)}</TableCell>
                      <TableCell className={`text-right text-muted-foreground text-xs ${COST_BG}`}>{fmtEuro(b.ivaAmount)}</TableCell>
                      <TableCell className="text-right font-semibold">{fmtEuro(b.ownerNetAmount)}</TableCell>
                      {/* Bollo: informativo, non incide sul netto */}
                      <TableCell className={`text-right text-muted-foreground ${FISCAL_BG}`}>{fmtEuro((b.bolloCents ?? 0) / 100)}</TableCell>
                      <TableCell className={`text-right text-destructive ${FISCAL_BG}`}>{fmtCost(b.withholdingAmount)}</TableCell>
                      <TableCell className={`text-right font-semibold text-success ${NET_BG}`}>{fmtEuro(nettoRiga(b))}</TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
              {bookings.length > 0 && (
                <tfoot>
                  <TableRow className="bg-muted/50 font-bold hover:bg-muted/50">
                    <TableCell colSpan={6}>TOTALE</TableCell>
                    <TableCell className="text-right">{fmtEuro(sumLordo)}</TableCell>
                    <TableCell className={`text-right text-destructive ${COST_BG}`}>{fmtCost(sumOta)}</TableCell>
                    <TableCell className={`text-right text-destructive ${COST_BG}`}>{fmtCost(sumCleaning)}</TableCell>
                    <TableCell className={`text-right text-destructive ${COST_BG}`}>{fmtCost(sumPm)}</TableCell>
                    <TableCell className={`text-right text-muted-foreground text-xs ${COST_BG}`}>{fmtEuro(sumIva)}</TableCell>
                    <TableCell className="text-right">{fmtEuro(sumCanone)}</TableCell>
                    <TableCell className={`text-right text-muted-foreground ${FISCAL_BG}`}>{fmtEuro(sumBollo)}</TableCell>
                    <TableCell className={`text-right text-destructive ${FISCAL_BG}`}>{fmtCost(sumRitenuta)}</TableCell>
                    <TableCell className={`text-right text-success ${NET_BG}`}>{fmtEuro(sumNetto)}</TableCell>
                  </TableRow>
                </tfoot>
              )}
            </Table>
          </div>
          <p className="px-4 py-2 text-xs text-muted-foreground">
            Il bollo è mostrato a titolo informativo e non è dedotto dal netto pagato al proprietario.
          </p>
        </CardContent>
      </Card>

      {/* Riepilogo card */}
      <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Lordo totale</p>
            <p className="text-xl font-bold mt-1">{fmtEuro(sumLordo)}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Comm. OTA</p>
            <p className="text-xl font-bold mt-1 text-destructive">{fmtCost(sumOta)}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Pulizie</p>
            <p className="text-xl font-bold mt-1 text-destructive">{fmtCost(sumCleaning)}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Provv. PM (+ IVA)</p>
            <p className="text-xl font-bold mt-1 text-destructive">{fmtCost(sumPm)}</p>
            <p className="text-xs text-muted-foreground mt-0.5">di cui IVA {fmtEuro(sumIva)}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Bollo totale</p>
            <p className="text-xl font-bold mt-1 text-muted-foreground">{fmtEuro(sumBollo)}</p>
            <p className="text-xs text-muted-foreground mt-0.5">informativo</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Ritenute totali</p>
            <p className="text-xl font-bold mt-1 text-destructive">{fmtCost(settlement.withholdingAmount)}</p>
          </CardContent>
        </Card>
      </div>

      {/* Netto da pagare — evidenza */}
      <Card className={NET_BG}>
        <CardContent className="p-6 text-center">
          <p className="text-sm text-muted-foreground">Netto da pagare</p>
          <p className="text-3xl font-bold mt-1 text-success">{fmtEuro(settlement.netAmount)}</p>
        </CardContent>
      </Card>
    </div>
  );
};

export default SettlementDetail;
