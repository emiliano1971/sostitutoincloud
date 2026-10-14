import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Receipt, Send, Download, Loader2 } from 'lucide-react';
import { toast } from '@/hooks/use-toast';
import type { Booking, OwnerProfile, Property } from '@/types';
import { downloadDocumentPdf, type DocumentGenerateResponse } from '@/api/documentApi';
import type { FiscalDocumentSummary } from '@/api/bookingApi';

interface ReceiptOwnerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  booking: Booking;
  owner?: OwnerProfile;
  property?: Property;
  /** Sostituto d'imposta indicato nelle note (stessa struttura passata a InvoicePMDialog). */
  tenantData?: { legal_name: string; vat_number: string; tax_code: string; address: string };
  generatedDoc?: DocumentGenerateResponse | null;
  existingDoc?: FiscalDocumentSummary;
  isSaving?: boolean;
  onEmetti?: () => void;
  /** Canone oltre il quale si applica il bollo (tenant_settings.bollo_soglia). */
  sogliaBollo?: number;
  /** Importo del bollo (tenant_settings.bollo_importo). */
  importoBollo?: number;
}

const fmt = (v: number) => `€${Math.abs(v).toLocaleString('it-IT', { minimumFractionDigits: 2 })}`;

const statoDocLabels: Record<string, string> = {
  draft: 'Bozza',
  ready: 'Pronto',
  sent_sdi: 'Inviato SDI',
  accepted: 'Accettato',
  rejected: 'Rifiutato',
  error: 'Errore',
};

const ReceiptOwnerDialog = ({ open, onOpenChange, booking, owner, property, tenantData, generatedDoc, existingDoc, isSaving, onEmetti,
  sogliaBollo = 77.47, importoBollo: importoBolloTenant = 2.00 }: ReceiptOwnerDialogProps) => {
  const [isDownloading, setIsDownloading] = useState(false);
  const receiptNumber = existingDoc?.documentNumber
    ?? generatedDoc?.documentNumber
    ?? `RIC-${new Date().getFullYear()}-${String(booking.booking_id).padStart(4, '0')}`;
  const receiptDateSource = existingDoc?.dataEmissione ?? generatedDoc?.dataEmissione;
  const receiptDate = receiptDateSource
    ? new Date(receiptDateSource).toLocaleDateString('it-IT')
    : new Date().toLocaleDateString('it-IT');

  const round2 = (v: number) => Math.round(v * 100) / 100;

  // Ricevuta emessa: importi del documento salvato, gli stessi del PDF del server.
  // Subito dopo l'emissione, finché il booking non è ricaricato, vale la risposta di generate.
  // Il bollo è solo informativo: non entra nel totale (totale = canone di locazione).
  let canone: number;
  let importoBollo: number;
  let totaleRicevuta: number;
  const emessa = !!existingDoc || !!generatedDoc;
  if (existingDoc) {
    canone = existingDoc.canoneLocazione ?? existingDoc.importoTotale;
    importoBollo = existingDoc.bolloAmount ?? 0;
    totaleRicevuta = existingDoc.importoTotale;
  } else if (generatedDoc) {
    canone = generatedDoc.importoTotale;
    importoBollo = generatedDoc.importoBollo ?? 0;
    totaleRicevuta = generatedDoc.importoTotale;
  } else {
    // Anteprima preliminare (ricevuta non emessa). Di norma emessa DOPO la fattura PM:
    // canone = lordo ospite - totale fattura PM - tassa di soggiorno inclusa nel lordo.
    // Se la fattura PM non esiste ancora: fallback su owner_net_amount (già al netto della tassa).
    const fatturaPM = booking.documenti?.find(d => d.tipoDocumento === 'fattura');
    const tassaInclusa = booking.tourist_tax_included_in_gross ? (booking.tourist_tax_amount ?? 0) : 0;
    canone = round2(fatturaPM
      ? booking.gross_amount - fatturaPM.importoTotale - tassaInclusa
      : (booking.owner_net_amount ?? 0));
    // Bollo dalle impostazioni del tenant se canone > soglia (solo informativo)
    importoBollo = canone > sogliaBollo ? importoBolloTenant : 0;
    totaleRicevuta = canone;
  }
  const bolloApplicabile = importoBollo > 0;

  // Semantica bottoni: se QUESTA ricevuta esiste già solo stampa/download, altrimenti
  // emissione (azione irreversibile).
  // NB: si guarda existingDoc e non booking_status === 'doc_issued'. Lo stato del booking
  // diventa 'doc_issued' con un solo documento qualsiasi (BookingService.resolveStatoId),
  // quindi con la sola fattura emessa la ricevuta non sarebbe più emettibile da qui.
  const isDocIssued = !!existingDoc;

  // Il PDF server-side richiede l'id del documento fiscale già emesso.
  const docId = existingDoc?.id ?? generatedDoc?.documentId;
  const handleDownloadPdf = async () => {
    if (!docId) return;
    setIsDownloading(true);
    try {
      await downloadDocumentPdf(docId, receiptNumber);
    } catch (err) {
      toast({
        title: 'Errore download PDF',
        description: err instanceof Error ? err.message : 'Errore imprevisto',
        variant: 'destructive',
      });
    } finally {
      setIsDownloading(false);
    }
  };

  // "Stampa" = PDF generato dal server sulla ricevuta emessa: la vecchia stampa HTML
  // ricalcolava gli importi nel browser e non coincideva con il documento.
  const handlePrint = () => {
    if (!docId) {
      toast({
        title: 'Ricevuta non emessa',
        description: 'Emetti prima la ricevuta per poter scaricare il PDF',
        variant: 'destructive',
      });
      return;
    }
    void handleDownloadPdf();
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {/* Aperto dalle card documento di BookingDetail (prop open) */}
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Receipt className="h-5 w-5" />
            Ricevuta Proprietario → Ospite
          </DialogTitle>
        </DialogHeader>

        <div className="border rounded-lg p-6 space-y-6 bg-background text-foreground text-sm" id="print-document-content">
          {/* Emittente: proprietario — stessa struttura del PDF ricevuta-owner.html */}
          <div className="flex justify-between items-start">
            <div>
              <h2 className="text-lg font-bold text-foreground">{booking.owner_cognome_nome ?? booking.owner_name}</h2>
              <p className="text-xs text-muted-foreground mt-1">C.F.: {owner?.tax_code || '—'}</p>
            </div>
            <div className="text-right">
              <Badge className="text-xs mb-2">RICEVUTA</Badge>
              <p className="font-mono text-xs font-bold">{receiptNumber}</p>
              <p className="text-xs text-muted-foreground mt-1">Data: {receiptDate}</p>
            </div>
          </div>

          <Separator />

          {/* Destinatario (ospite), immobile */}
          <div className="grid grid-cols-2 gap-6">
            <div>
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1">Destinatario</p>
              <p className="font-medium">{booking.guest_name}</p>
              <p className="text-xs text-muted-foreground">C.F.: {booking.guest_tax_code || '—'}</p>
            </div>
            <div>
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1">Immobile</p>
              <p className="font-medium">{booking.property_name}</p>
              {property?.city && <p className="text-xs text-muted-foreground mt-1">{property.city}</p>}
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
                <span className="text-muted-foreground">Canone di locazione</span>
                <span className="whitespace-nowrap">{fmt(canone)}</span>
              </div>
              {bolloApplicabile && (
                <div className="flex justify-between text-xs">
                  <span className="text-muted-foreground">Marca da bollo (informativa, non sommata)</span>
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
            <p><strong>Operazione fuori campo IVA</strong> – Canone di locazione breve ai sensi dell'art. 4 D.L. 50/2017</p>
            {bolloApplicabile && <p><strong>Imposta di bollo:</strong> {fmt(importoBollo)} assolta in modo virtuale (canone &gt; {fmt(sogliaBollo)})</p>}
            {!emessa && <p className="italic">Anteprima preliminare: gli importi definitivi sono quelli della ricevuta emessa.</p>}
            <p><strong>Sostituto d'imposta:</strong> {tenantData?.legal_name || '—'} — P.IVA {tenantData?.vat_number || '—'}</p>
          </div>
        </div>

        {existingDoc ? (
          <div className="rounded-md bg-success/10 text-success text-xs px-3 py-2 flex items-center gap-2">
            <span>Documento già emesso — numero <strong>{existingDoc.documentNumber}</strong></span>
            <Badge variant="outline" className="ml-auto text-xs">{statoDocLabels[existingDoc.statoDocumento] ?? existingDoc.statoDocumento}</Badge>
          </div>
        ) : generatedDoc && (
          <div className="rounded-md bg-success/10 text-success text-xs px-3 py-2">
            Documento emesso — numero <strong>{generatedDoc.documentNumber}</strong> (stato: {statoDocLabels[generatedDoc.statoDocumento] ?? generatedDoc.statoDocumento})
          </div>
        )}

        <div className="flex gap-3 justify-end pt-2">
          <Button variant="outline" onClick={() => onOpenChange(false)}>Chiudi</Button>
          {isDocIssued ? (
            // Documenti già emessi: azione ripetibile, solo download del PDF del server.
            <Button className="gap-2" onClick={handlePrint} disabled={isDownloading}>
              {isDownloading
                ? <Loader2 className="h-4 w-4 animate-spin" />
                : <Download className="h-4 w-4" />}
              Scarica PDF
            </Button>
          ) : existingDoc ? (
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
              <Button className="gap-2" onClick={handlePrint} disabled={isDownloading}>
                {isDownloading
                  ? <Loader2 className="h-4 w-4 animate-spin" />
                  : <Download className="h-4 w-4" />}
                Scarica PDF
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
