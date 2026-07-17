import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { FileText, Printer, Send } from 'lucide-react';
import { toast } from '@/hooks/use-toast';
import type { Booking, OwnerProfile, Property } from '@/types';
import { aggiornaStatoDocumento, type DocumentGenerateResponse } from '@/api/documentApi';
import type { FiscalDocumentSummary } from '@/api/bookingApi';

interface InvoicePMDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  booking: Booking;
  owner?: OwnerProfile;
  property?: Property;
  tenantData: { legal_name: string; vat_number: string; tax_code: string; address: string; pec: string };
  generatedDoc?: DocumentGenerateResponse | null;
  existingDoc?: FiscalDocumentSummary;
  isSaving?: boolean;
  onEmetti?: () => void;
  onSent?: () => void;
}

const fmt = (v: number) => `€${Math.abs(v).toLocaleString('it-IT', { minimumFractionDigits: 2 })}`;

const statoDocLabels: Record<string, string> = {
  draft: 'Bozza',
  ready: 'Pronto',
  sent_sdi: 'Inviato SDI',
  accepted: 'Accettato',
  rejected: 'Rifiutato',
};

const InvoicePMDialog = ({ open, onOpenChange, booking, owner, property, tenantData, generatedDoc, existingDoc, isSaving, onEmetti, onSent }: InvoicePMDialogProps) => {
  const handleInvia = async (documentId: number) => {
    try {
      await aggiornaStatoDocumento(documentId, 'sent_sdi');
      toast({ title: 'Fattura inviata', description: `Fattura ${invoiceNumber} inviata allo SDI` });
      onOpenChange(false);
      onSent?.();
    } catch (err) {
      toast({
        title: 'Errore invio',
        description: err instanceof Error ? err.message : 'Errore imprevisto',
      });
    }
  };

  const invoiceNumber = existingDoc?.documentNumber
    ?? generatedDoc?.documentNumber
    ?? `FT-${new Date().getFullYear()}-${String(booking.booking_id).padStart(4, '0')}`;
  const invoiceDateSource = existingDoc?.dataEmissione ?? generatedDoc?.dataEmissione;
  const invoiceDate = invoiceDateSource
    ? new Date(invoiceDateSource).toLocaleDateString('it-IT')
    : new Date().toLocaleDateString('it-IT');

  // Scenario A: i valori OTA / pulizie / provvigione PM sono LORDI (IVA inclusa).
  // L'IVA va SCORPORATA dal lordo, non aggiunta sopra.
  // RF01 ordinario → divisore 1.22; RF19 forfettario → nessuno scorporo (divisore 1, IVA 0).
  const regimeFiscalePm = (tenantData as { regimeFiscalePm?: string })?.regimeFiscalePm ?? 'RF01';
  const divisore = regimeFiscalePm === 'RF19' ? 1 : 1.22;

  const otaLordo = booking.ota_commission_amount ?? 0;
  const otaImponibile = Math.round(otaLordo / divisore * 100) / 100;
  const otaIva = Math.round((otaLordo - otaImponibile) * 100) / 100;

  const cleaningLordo = booking.cleaning_amount ?? 0;
  const cleaningImponibile = Math.round(cleaningLordo / divisore * 100) / 100;
  const cleaningIva = Math.round((cleaningLordo - cleaningImponibile) * 100) / 100;

  const pmLordo = booking.pm_fee_amount ?? 0;
  const pmImponibile = Math.round(pmLordo / divisore * 100) / 100;
  const pmIva = Math.round((pmLordo - pmImponibile) * 100) / 100;

  // Totali: dal documento generato (DB, già corretti) se disponibile, altrimenti calcolati.
  const totaleImponibile = generatedDoc
    ? generatedDoc.imponibile
    : Math.round((otaImponibile + cleaningImponibile + pmImponibile) * 100) / 100;
  const totaleIva = generatedDoc
    ? generatedDoc.iva
    : Math.round((otaIva + cleaningIva + pmIva) * 100) / 100;
  const totaleFattura = generatedDoc
    ? generatedDoc.importoTotale
    : otaLordo + cleaningLordo + pmLordo;

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
                <div className="col-span-2 text-right">Imponibile</div>
                <div className="col-span-2 text-right">IVA 22%</div>
                <div className="col-span-2 text-right">Totale</div>
              </div>
              <div className="grid grid-cols-12 gap-2 p-2 text-xs border-t">
                <div className="col-span-6">
                  Riaddebito commissione OTA<br />
                  <span className="text-muted-foreground">Canale: {booking.channel_name}</span>
                </div>
                <div className="col-span-2 text-right">{fmt(otaImponibile)}</div>
                <div className="col-span-2 text-right">{fmt(otaIva)}</div>
                <div className="col-span-2 text-right">{fmt(Math.round((otaImponibile + otaIva) * 100) / 100)}</div>
              </div>
              <div className="grid grid-cols-12 gap-2 p-2 text-xs border-t">
                <div className="col-span-6">Riaddebito pulizia finale</div>
                <div className="col-span-2 text-right">{fmt(cleaningImponibile)}</div>
                <div className="col-span-2 text-right">{fmt(cleaningIva)}</div>
                <div className="col-span-2 text-right">{fmt(Math.round((cleaningImponibile + cleaningIva) * 100) / 100)}</div>
              </div>
              <div className="grid grid-cols-12 gap-2 p-2 text-xs border-t">
                <div className="col-span-6">
                  Provvigione gestione immobiliare<br />
                  <span className="text-muted-foreground">Periodo: {booking.checkin_date} → {booking.checkout_date}</span>
                </div>
                <div className="col-span-2 text-right">{fmt(pmImponibile)}</div>
                <div className="col-span-2 text-right">{fmt(pmIva)}</div>
                <div className="col-span-2 text-right">{fmt(Math.round((pmImponibile + pmIva) * 100) / 100)}</div>
              </div>
            </div>
          </div>

          <Separator />

          <div className="flex justify-end">
            <div className="w-64 space-y-2">
              <div className="flex justify-between text-xs">
                <span className="text-muted-foreground">Imponibile</span>
                <span>{fmt(totaleImponibile)}</span>
              </div>
              <div className="flex justify-between text-xs">
                <span className="text-muted-foreground">IVA (22%)</span>
                <span>{fmt(totaleIva)}</span>
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
            <>
              <Button className="gap-2" disabled>
                <FileText className="h-4 w-4" />
                Già emesso il {invoiceDate}
              </Button>
              {existingDoc.statoDocumento === 'ready' && (
                <Button className="gap-2 bg-success hover:bg-success/90 text-white" onClick={() => handleInvia(existingDoc.id)}>
                  <Send className="h-4 w-4" />
                  Invia
                </Button>
              )}
            </>
          ) : !generatedDoc ? (
            <Button className="gap-2" onClick={onEmetti} disabled={isSaving}>
              <FileText className="h-4 w-4" />
              {isSaving ? 'Emissione…' : 'Emetti Documento'}
            </Button>
          ) : (
            <>
              <Button className="gap-2" onClick={() => window.print()}>
                <Printer className="h-4 w-4" />
                Stampa / PDF
              </Button>
              <Button className="gap-2 bg-success hover:bg-success/90 text-white" onClick={() => handleInvia(generatedDoc.documentId)}>
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

export default InvoicePMDialog;
