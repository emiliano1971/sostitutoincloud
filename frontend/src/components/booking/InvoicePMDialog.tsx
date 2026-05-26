import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { FileText, Printer, Send } from 'lucide-react';
import { toast } from '@/hooks/use-toast';
import type { Booking, OwnerProfile, Property } from '@/types';

interface InvoicePMDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  booking: Booking;
  owner?: OwnerProfile;
  property?: Property;
  tenantData: { legal_name: string; vat_number: string; tax_code: string; address: string; pec: string };
}

const fmt = (v: number) => `€${Math.abs(v).toLocaleString('it-IT', { minimumFractionDigits: 2 })}`;

const InvoicePMDialog = ({ open, onOpenChange, booking, owner, property, tenantData }: InvoicePMDialogProps) => {
  const invoiceNumber = `FT-2025-${String(parseInt(booking.booking_id.replace('b', ''))).padStart(4, '0')}`;
  const invoiceDate = new Date().toLocaleDateString('it-IT');

  // Scenario A: imponibile = riaddebito commissione OTA + riaddebito pulizie + provvigione PM
  const riaddebitoOta = booking.ota_commission_amount;
  const riaddebitoPulizie = booking.cleaning_amount;
  const provvigionePm = booking.pm_fee_amount;
  const imponibile = riaddebitoOta + riaddebitoPulizie + provvigionePm;
  const vatRate = 0.22;
  const vatAmount = Math.round(imponibile * vatRate * 100) / 100;
  const totaleFattura = Math.round((imponibile + vatAmount) * 100) / 100;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild>
        <Button className="w-full sm:w-auto gap-2">
          <FileText className="h-4 w-4" />
          Stampa Fattura P.M.
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <FileText className="h-5 w-5" />
            Anteprima Fattura P.M.
          </DialogTitle>
        </DialogHeader>

        <div className="border rounded-lg p-6 space-y-6 bg-background text-foreground text-sm" id="invoice-preview">
          <div className="flex justify-between items-start">
            <div>
              <h2 className="text-lg font-bold text-foreground">{tenantData.legal_name}</h2>
              <p className="text-xs text-muted-foreground mt-1">P.IVA: {tenantData.vat_number}</p>
              <p className="text-xs text-muted-foreground">C.F.: {tenantData.tax_code}</p>
              <p className="text-xs text-muted-foreground">{tenantData.address}</p>
              <p className="text-xs text-muted-foreground">PEC: {tenantData.pec}</p>
            </div>
            <div className="text-right">
              <Badge className="text-xs mb-2">FATTURA</Badge>
              <p className="font-mono text-xs font-bold">{invoiceNumber}</p>
              <p className="text-xs text-muted-foreground mt-1">Data: {invoiceDate}</p>
            </div>
          </div>

          <Separator />

          <div className="grid grid-cols-2 gap-6">
            <div>
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1">Destinatario</p>
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

          <div>
            <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3">Dettaglio Prestazione</p>
            <div className="border rounded-md overflow-hidden">
              <div className="grid grid-cols-12 gap-2 bg-muted/50 p-2 text-xs font-semibold text-muted-foreground">
                <div className="col-span-6">Descrizione</div>
                <div className="col-span-3 text-right">Imponibile</div>
                <div className="col-span-3 text-right">IVA 22%</div>
              </div>
              <div className="grid grid-cols-12 gap-2 p-2 text-xs border-t">
                <div className="col-span-6">
                  Riaddebito commissione OTA<br />
                  <span className="text-muted-foreground">Canale: {booking.channel_name}</span>
                </div>
                <div className="col-span-3 text-right">{fmt(riaddebitoOta)}</div>
                <div className="col-span-3 text-right">{fmt(Math.round(riaddebitoOta * vatRate * 100) / 100)}</div>
              </div>
              <div className="grid grid-cols-12 gap-2 p-2 text-xs border-t">
                <div className="col-span-6">Riaddebito pulizia finale</div>
                <div className="col-span-3 text-right">{fmt(riaddebitoPulizie)}</div>
                <div className="col-span-3 text-right">{fmt(Math.round(riaddebitoPulizie * vatRate * 100) / 100)}</div>
              </div>
              <div className="grid grid-cols-12 gap-2 p-2 text-xs border-t">
                <div className="col-span-6">
                  Provvigione gestione immobiliare<br />
                  <span className="text-muted-foreground">Periodo: {booking.checkin_date} → {booking.checkout_date}</span>
                </div>
                <div className="col-span-3 text-right">{fmt(provvigionePm)}</div>
                <div className="col-span-3 text-right">{fmt(Math.round(provvigionePm * vatRate * 100) / 100)}</div>
              </div>
            </div>
          </div>

          <Separator />

          <div className="flex justify-end">
            <div className="w-64 space-y-2">
              <div className="flex justify-between text-xs">
                <span className="text-muted-foreground">Imponibile</span>
                <span>{fmt(imponibile)}</span>
              </div>
              <div className="flex justify-between text-xs">
                <span className="text-muted-foreground">IVA (22%)</span>
                <span>{fmt(vatAmount)}</span>
              </div>
              <Separator />
              <div className="flex justify-between font-bold text-sm">
                <span>Totale Fattura</span>
                <span>{fmt(totaleFattura)}</span>
              </div>
            </div>
          </div>

          <Separator />

          <div className="text-xs text-muted-foreground space-y-1">
            <p><strong>Regime fiscale:</strong> Regime ordinario – IVA 22% ai sensi del DPR 633/72</p>
            <p className="text-[10px] italic">⚠️ Il PM riaddebita commissione OTA e pulizie con IVA 22% anche se ricevute in reverse charge (Scenario A)</p>
            <p><strong>Ritenuta d'acconto 21%:</strong> {fmt(booking.withholding_amount)} (trattenuta dal sostituto d'imposta)</p>
            <p><strong>Proprietario:</strong> {booking.owner_name} {owner ? `(C.F. ${owner.tax_code})` : ''}</p>
            <p><strong>IBAN proprietario:</strong> {owner?.iban || 'N/D'}</p>
            <p><strong>Modalità di pagamento:</strong> Bonifico bancario entro 30 giorni</p>
          </div>
        </div>

        <div className="flex gap-3 justify-end pt-2">
          <Button variant="outline" onClick={() => onOpenChange(false)}>Chiudi</Button>
          <Button className="gap-2" onClick={() => window.print()}>
            <Printer className="h-4 w-4" />
            Stampa / PDF
          </Button>
          <Button className="gap-2 bg-success hover:bg-success/90 text-white" onClick={() => { toast({ title: 'Fattura inviata', description: `Fattura ${invoiceNumber} inviata allo SDI` }); onOpenChange(false); }}>
            <Send className="h-4 w-4" />
            Invia
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default InvoicePMDialog;
