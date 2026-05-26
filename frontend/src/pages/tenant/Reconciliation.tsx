import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Check, X, ArrowLeftRight } from 'lucide-react';

const mockMatches = [
  { id: 'm1', booking: { id: 'AIRBNB-20250301001', guest: 'John Smith', checkin: '2025-03-01', property: 'Appartamento Trastevere' }, alloggiati: { name: 'SMITH JOHN', arrival: '2025-03-01', doc: 'AB123456' }, score: 95 },
  { id: 'm2', booking: { id: 'BOOKING-20250302002', guest: 'Marie Dupont', checkin: '2025-03-02', property: 'Loft Monti' }, alloggiati: { name: 'DUPONT MARIE', arrival: '2025-03-02', doc: 'CD789012' }, score: 88 },
  { id: 'm3', booking: { id: 'VRBO-20250303003', guest: 'Hans Müller', checkin: '2025-03-03', property: 'Suite Vaticano' }, alloggiati: { name: 'MULLER HANS', arrival: '2025-03-03', doc: 'EF345678' }, score: 72 },
  { id: 'm4', booking: { id: 'AIRBNB-20250304004', guest: 'Yuki Tanaka', checkin: '2025-03-04', property: 'Casa Centro Storico' }, alloggiati: null, score: 0 },
];

const Reconciliation = () => (
  <div className="space-y-6">
    <div>
      <h1 className="text-2xl font-bold">Riconciliazione Booking / Alloggiati</h1>
      <p className="text-sm text-muted-foreground">Abbina prenotazioni ai dati ospite importati</p>
    </div>

    <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
      <Card><CardContent className="p-4 text-center"><p className="text-2xl font-bold text-success">2</p><p className="text-xs text-muted-foreground">Match automatici (&gt;85%)</p></CardContent></Card>
      <Card><CardContent className="p-4 text-center"><p className="text-2xl font-bold text-warning">1</p><p className="text-xs text-muted-foreground">Da verificare (50-85%)</p></CardContent></Card>
      <Card><CardContent className="p-4 text-center"><p className="text-2xl font-bold text-destructive">1</p><p className="text-xs text-muted-foreground">Senza match</p></CardContent></Card>
    </div>

    <div className="space-y-4">
      {mockMatches.map(match => (
        <Card key={match.id} className={match.score === 0 ? 'border-destructive/30' : match.score >= 85 ? 'border-success/30' : 'border-warning/30'}>
          <CardContent className="p-4">
            <div className="flex flex-col md:flex-row md:items-center gap-4">
              {/* Booking side */}
              <div className="flex-1 p-3 rounded-lg bg-muted/50">
                <p className="text-[10px] font-medium text-muted-foreground uppercase tracking-wider mb-1">Prenotazione</p>
                <p className="text-sm font-medium">{match.booking.guest}</p>
                <p className="text-xs text-muted-foreground">{match.booking.id}</p>
                <p className="text-xs text-muted-foreground">{match.booking.property} · {match.booking.checkin}</p>
              </div>

              {/* Score */}
              <div className="flex flex-col items-center gap-1 shrink-0">
                <ArrowLeftRight className="h-4 w-4 text-muted-foreground" />
                <Badge variant="outline" className={`text-xs ${
                  match.score >= 85 ? 'bg-success/10 text-success' : match.score >= 50 ? 'bg-warning/10 text-warning' : 'bg-destructive/10 text-destructive'
                }`}>
                  {match.score > 0 ? `${match.score}%` : 'N/A'}
                </Badge>
              </div>

              {/* Alloggiati side */}
              <div className="flex-1 p-3 rounded-lg bg-muted/50">
                {match.alloggiati ? (
                  <>
                    <p className="text-[10px] font-medium text-muted-foreground uppercase tracking-wider mb-1">Alloggiati</p>
                    <p className="text-sm font-medium">{match.alloggiati.name}</p>
                    <p className="text-xs text-muted-foreground">Arrivo: {match.alloggiati.arrival}</p>
                    <p className="text-xs text-muted-foreground">Doc: {match.alloggiati.doc}</p>
                  </>
                ) : (
                  <div className="text-center py-3">
                    <p className="text-sm text-destructive font-medium">Nessun match trovato</p>
                    <p className="text-xs text-muted-foreground">Importa dati Alloggiati</p>
                  </div>
                )}
              </div>

              {/* Actions */}
              <div className="flex gap-2 shrink-0">
                {match.alloggiati && (
                  <>
                    <Button size="sm" variant="outline" className="gap-1 text-success border-success/30"><Check className="h-3.5 w-3.5" /> Conferma</Button>
                    <Button size="sm" variant="outline" className="gap-1 text-destructive border-destructive/30"><X className="h-3.5 w-3.5" /> Rifiuta</Button>
                  </>
                )}
              </div>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  </div>
);

export default Reconciliation;
