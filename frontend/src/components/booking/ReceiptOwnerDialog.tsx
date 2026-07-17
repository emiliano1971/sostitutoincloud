import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Receipt, Printer, Send } from 'lucide-react';
import { toast } from '@/hooks/use-toast';
import type { Booking, OwnerProfile, Property } from '@/types';
import type { DocumentGenerateResponse } from '@/api/documentApi';
import type { FiscalDocumentSummary } from '@/api/bookingApi';

interface ReceiptOwnerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  booking: Booking;
  owner?: OwnerProfile;
  property?: Property;
  generatedDoc?: DocumentGenerateResponse | null;
  existingDoc?: FiscalDocumentSummary;
  isSaving?: boolean;
  onEmetti?: () => void;
}

const fmt = (v: number) => `€${Math.abs(v).toLocaleString('it-IT', { minimumFractionDigits: 2 })}`;

const statoDocLabels: Record<string, string> = {
  draft: 'Bozza',
  ready: 'Pronto',
  sent_sdi: 'Inviato SDI',
  accepted: 'Accettato',
  rejected: 'Rifiutato',
};

const ReceiptOwnerDialog = ({ open, onOpenChange, booking, owner, property, generatedDoc, existingDoc, isSaving, onEmetti }: ReceiptOwnerDialogProps) => {
  const receiptNumber = existingDoc?.documentNumber
    ?? generatedDoc?.documentNumber
    ?? `RIC-${new Date().getFullYear()}-${String(booking.booking_id).padStart(4, '0')}`;
  const receiptDateSource = existingDoc?.dataEmissione ?? generatedDoc?.dataEmissione;
  const receiptDate = receiptDateSource
    ? new Date(receiptDateSource).toLocaleDateString('it-IT')
    : new Date().toLocaleDateString('it-IT');

  // Ricevuta: canone di locazione fuori campo IVA.
  // Di norma emessa DOPO la fattura PM: canone = lordo ospite - totale fattura PM.
  // Se la fattura PM non esiste ancora: fallback su owner_net_amount.
  const fatturaPM = booking.documenti?.find(d => d.tipoDocumento === 'fattura');
  const canone = fatturaPM
    ? booking.gross_amount - fatturaPM.importoTotale
    : (booking.owner_net_amount ?? 0);
  // Bollo €2,00 se canone > €77,47
  const bolloApplicabile = canone > 77.47;
  const importoBollo = bolloApplicabile ? 2.00 : 0;
  const totaleRicevuta = canone + importoBollo;
  const ritenuta = canone * 0.21;

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
              {booking.guest_tax_code && <p className="text-xs text-muted-foreground mt-1">C.F.: {booking.guest_tax_code}</p>}
              <p className="text-xs text-muted-foreground">Prenotazione: {booking.external_booking_id}</p>
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
                <div className="col-span-2 text-right">{fmt(canone / booking.nights)}</div>
                <div className="col-span-2 text-right font-medium">{fmt(canone)}</div>
              </div>
            </div>
          </div>

          <Separator />

          {/* Totale */}
          <div className="flex justify-end">
            <div className="w-64 space-y-2">
              <div className="flex justify-between text-xs gap-3">
                <span className="text-muted-foreground">Canone di locazione (fuori campo IVA art. 4 D.L. 50/2017 – cedolare secca)</span>
                <span className="whitespace-nowrap">{fmt(canone)}</span>
              </div>
              {bolloApplicabile && (
                <div className="flex justify-between text-xs">
                  <span className="text-muted-foreground">Marca da bollo</span>
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
            <p>Imposta assolta in forma di cedolare secca ai sensi dell'art. 3 D.Lgs. 23/2011</p>
            {bolloApplicabile && <p><strong>Imposta di bollo:</strong> €2,00 assolta in modo virtuale (canone &gt; €77,47)</p>}
            <p><strong>Ritenuta d'acconto 21%:</strong> {fmt(ritenuta)} (trattenuta dal sostituto d'imposta)</p>
            <p><strong>Locatore:</strong> {booking.owner_name} {owner ? `(C.F. ${owner.tax_code})` : ''}</p>
            <p><strong>Conduttore:</strong> {booking.guest_name}</p>
            <p><strong>Periodo di locazione:</strong> {booking.checkin_date} – {booking.checkout_date} ({booking.nights} notti)</p>
            <p><strong>Rif. contratto:</strong> prenotazione {booking.external_booking_id}</p>
          </div>
        </div>

        {existingDoc ? (
          <div className="rounded-md bg-success/10 text-success text-xs px-3 py-2 flex items-center gap-2">
            <span>Documento già emesso — numero <strong>{existingDoc.documentNumber}</strong></span>
            <Badge variant="outline" className="ml-auto text-xs">{statoDocLabels[existingDoc.statoDocumento] ?? existingDoc.statoDocumento}</Badge>
          </div>
        ) : generatedDoc && (
          <div className="rounded-md bg-success/10 text-success text-xs px-3 py-2">
            Documento emesso — numero <strong>{generatedDoc.documentNumber}</strong> (stato: {generatedDoc.statoDocumento})
          </div>
        )}

        <div className="flex gap-3 justify-end pt-2">
          <Button variant="outline" onClick={() => onOpenChange(false)}>Chiudi</Button>
          {existingDoc ? (
            <Button className="gap-2" disabled>
              <Receipt className="h-4 w-4" />
              Già emesso il {receiptDate}
            </Button>
          ) : !generatedDoc ? (
            <Button className="gap-2" onClick={onEmetti} disabled={isSaving}>
              <Receipt className="h-4 w-4" />
              {isSaving ? 'Emissione…' : 'Emetti Documento'}
            </Button>
          ) : (
            <>
              <Button className="gap-2" onClick={() => window.print()}>
                <Printer className="h-4 w-4" />
                Stampa / PDF
              </Button>
              <Button className="gap-2 bg-success hover:bg-success/90 text-white" onClick={() => { toast({ title: 'Ricevuta inviata', description: `Ricevuta ${receiptNumber} inviata al proprietario` }); onOpenChange(false); }}>
                <Send className="h-4 w-4" />
                Invia
              </Button>
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default ReceiptOwnerDialog;
