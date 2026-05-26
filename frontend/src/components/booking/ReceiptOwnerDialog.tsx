import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Receipt, Printer, Send } from 'lucide-react';
import { toast } from '@/hooks/use-toast';
import type { Booking, OwnerProfile, Property } from '@/types';

interface ReceiptOwnerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  booking: Booking;
  owner?: OwnerProfile;
  property?: Property;
}

const fmt = (v: number) => `€${Math.abs(v).toLocaleString('it-IT', { minimumFractionDigits: 2 })}`;

const ReceiptOwnerDialog = ({ open, onOpenChange, booking, owner, property }: ReceiptOwnerDialogProps) => {
  const receiptNumber = `RIC-2025-${String(parseInt(booking.booking_id.replace('b', ''))).padStart(4, '0')}`;
  const receiptDate = new Date().toLocaleDateString('it-IT');

  // Ricevuta: canone di locazione (gross - commissione OTA) fuori campo IVA
  const canonLocazione = booking.gross_amount;
  const totaleRicevuta = canonLocazione;
  // Bollo €2,00 se importo > €77,47
  const bolloApplicabile = totaleRicevuta > 77.47;
  const importoBollo = bolloApplicabile ? 2.00 : 0;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild>
        <Button variant="outline" className="w-full sm:w-auto gap-2">
          <Receipt className="h-4 w-4" />
          Stampa Ricevuta Owner
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Receipt className="h-5 w-5" />
            Ricevuta Proprietario → Ospite
          </DialogTitle>
        </DialogHeader>

        <div className="border rounded-lg p-6 space-y-6 bg-background text-foreground text-sm" id="receipt-owner-preview">
          {/* Header proprietario */}
          <div className="flex justify-between items-start">
            <div>
              <h2 className="text-lg font-bold text-foreground">{booking.owner_name}</h2>
              {owner && (
                <>
                  <p className="text-xs text-muted-foreground mt-1">C.F.: {owner.tax_code}</p>
                  <p className="text-xs text-muted-foreground">Indirizzo registrato</p>
                </>
              )}
            </div>
            <div className="text-right">
              <Badge className="text-xs mb-2">RICEVUTA</Badge>
              <p className="font-mono text-xs font-bold">{receiptNumber}</p>
              <p className="text-xs text-muted-foreground mt-1">Data: {receiptDate}</p>
            </div>
          </div>

          <Separator />

          {/* Ospite e immobile */}
          <div className="grid grid-cols-2 gap-6">
            <div>
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1">Ospite (Conduttore)</p>
              <p className="font-medium">{booking.guest_name}</p>
              <p className="text-xs text-muted-foreground mt-1">Prenotazione: {booking.external_booking_id}</p>
              <p className="text-xs text-muted-foreground">Canale: {booking.channel_name}</p>
            </div>
            <div>
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1">Immobile</p>
              <p className="font-medium">{booking.property_name}</p>
              {property && <p className="text-xs text-muted-foreground mt-1">{property.address}, {property.city}</p>}
              <p className="text-xs text-muted-foreground">Cod. {property?.internal_code}</p>
            </div>
          </div>

          <Separator />

          {/* Dettaglio soggiorno */}
          <div>
            <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3">Dettaglio Soggiorno</p>
            <div className="border rounded-md overflow-hidden">
              <div className="grid grid-cols-12 gap-2 bg-muted/50 p-2 text-xs font-semibold text-muted-foreground">
                <div className="col-span-6">Descrizione</div>
                <div className="col-span-2 text-right">Qtà</div>
                <div className="col-span-2 text-right">Prezzo Unit.</div>
                <div className="col-span-2 text-right">Totale</div>
              </div>
              <div className="grid grid-cols-12 gap-2 p-2 text-xs border-t">
                <div className="col-span-6">
                  Canone di locazione turistica breve<br />
                  <span className="text-muted-foreground">Dal {booking.checkin_date} al {booking.checkout_date}</span>
                </div>
                <div className="col-span-2 text-right">{booking.nights} notti</div>
                <div className="col-span-2 text-right">{fmt(canonLocazione / booking.nights)}</div>
                <div className="col-span-2 text-right font-medium">{fmt(canonLocazione)}</div>
              </div>
            </div>
          </div>

          <Separator />

          {/* Totale */}
          <div className="flex justify-end">
            <div className="w-64 space-y-2">
              <div className="flex justify-between text-xs">
                <span className="text-muted-foreground">Canone di locazione</span>
                <span>{fmt(canonLocazione)}</span>
              </div>
              <div className="flex justify-between text-xs">
                <span className="text-muted-foreground">IVA</span>
                <span className="italic">Fuori campo IVA</span>
              </div>
              {bolloApplicabile && (
                <div className="flex justify-between text-xs">
                  <span className="text-muted-foreground">Imposta di bollo</span>
                  <span>{fmt(importoBollo)}</span>
                </div>
              )}
              <Separator />
              <div className="flex justify-between font-bold text-sm">
                <span>Totale Ricevuta</span>
                <span>{fmt(totaleRicevuta)}</span>
              </div>
            </div>
          </div>

          <Separator />

          {/* Note */}
          <div className="text-xs text-muted-foreground space-y-1">
            <p><strong>Operazione fuori campo IVA</strong> – Canone di locazione breve ai sensi dell'art. 4 D.L. 50/2017, soggetto a cedolare secca</p>
            {bolloApplicabile && <p><strong>Imposta di bollo:</strong> €2,00 assolta in modo virtuale (importo ricevuta &gt; €77,47)</p>}
            <p><strong>Locatore:</strong> {booking.owner_name} {owner ? `(C.F. ${owner.tax_code})` : ''}</p>
            <p><strong>Conduttore:</strong> {booking.guest_name}</p>
            <p><strong>Periodo di locazione:</strong> {booking.checkin_date} – {booking.checkout_date} ({booking.nights} notti)</p>
            <p><strong>Pagamento ricevuto tramite:</strong> {booking.channel_name}</p>
          </div>
        </div>

        <div className="flex gap-3 justify-end pt-2">
          <Button variant="outline" onClick={() => onOpenChange(false)}>Chiudi</Button>
          <Button className="gap-2" onClick={() => window.print()}>
            <Printer className="h-4 w-4" />
            Stampa / PDF
          </Button>
          <Button className="gap-2 bg-success hover:bg-success/90 text-white" onClick={() => { toast({ title: 'Ricevuta inviata', description: `Ricevuta ${receiptNumber} inviata al proprietario` }); onOpenChange(false); }}>
            <Send className="h-4 w-4" />
            Invia
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default ReceiptOwnerDialog;
