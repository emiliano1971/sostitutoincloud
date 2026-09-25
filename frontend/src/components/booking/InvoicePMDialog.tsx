import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { FileText, Printer, Send, Download, Loader2, AlertTriangle } from 'lucide-react';
import { toast } from '@/hooks/use-toast';
import type { Booking, OwnerProfile, Property } from '@/types';
import { aggiornaStatoDocumento, downloadDocumentPdf, type DocumentGenerateResponse } from '@/api/documentApi';
import type { BookingSplitRiga, FiscalDocumentSummary } from '@/api/bookingApi';

interface InvoicePMDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  booking: Booking;
  owner?: OwnerProfile;
  property?: Property;
  tenantData: {
    legal_name: string; vat_number: string; tax_code: string; address: string; pec: string;
    /** Regime fiscale del PM: 'RF19' forfettario = senza IVA, altrimenti IVA 22% scorporata. */
    regimeFiscalePm?: string;
  };
  /**
   * Righe booking_split_economico che entrano nella fattura PM (include_in_fattura_pm e
   * importo > 0), voci extra comprese. Vuote = booking pre-migrazione 018: campi flat.
   */
  righeFattura?: BookingSplitRiga[];
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
  error: 'Errore',
};

const InvoicePMDialog = ({ open, onOpenChange, booking, owner, property, tenantData, righeFattura, generatedDoc, existingDoc, isSaving, onEmetti, onSent }: InvoicePMDialogProps) => {
  const [isDownloading, setIsDownloading] = useState(false);

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

  // Scenario A: i valori dei servizi sono LORDI (IVA inclusa): l'IVA va SCORPORATA dal lordo,
  // non aggiunta sopra. Aliquota: quella del documento se la fattura è già emessa (è il dato
  // reale), altrimenti dal regime del PM — RF19 forfettario 0% (nessuno scorporo), RF01 22%.
  // Mai quella delle righe split, che vale sempre 22 anche in RF19 (come PDF e XML SDI).
  const regimeFiscalePm = tenantData.regimeFiscalePm ?? 'RF01';
  const aliquotaIva = existingDoc?.aliquotaIva != null
    ? Number(existingDoc.aliquotaIva)
    : regimeFiscalePm === 'RF19' ? 0 : 22;
  const forfettario = aliquotaIva === 0;
  const divisore = 1 + aliquotaIva / 100;
  const aliquotaLabel = `${aliquotaIva.toLocaleString('it-IT')}%`;

  const scorpora = (lordo: number) => {
    const imponibile = Math.round(lordo / divisore * 100) / 100;
    return { lordo, imponibile, iva: Math.round((lordo - imponibile) * 100) / 100 };
  };

  // Righe: le stesse che il server metterà in fattura (booking_split_economico, voci extra
  // comprese, righe a zero escluse — come DocumentGenerationService, PDF e XML SDI).
  // Senza righe split (booking pre-migrazione 018) si ripiega sui campi flat, con le
  // etichette storiche di PDF / XML per quel caso.
  const righe: { key: string; descrizione: string; nota?: string; lordo: number; imponibile: number; iva: number }[] =
    (righeFattura?.length ?? 0) > 0
      ? righeFattura!.map(r => ({
          key: String(r.id),
          descrizione: r.descrizione,
          nota: r.tipoVoce === 'commissione_ota'
            ? `Canale: ${booking.channel_name}`
            : r.tipoVoce === 'commissione_pm'
              ? `Periodo: ${booking.checkin_date} → ${booking.checkout_date}`
              : undefined,
          ...scorpora(r.importo),
        }))
      : [
          { key: 'ota', descrizione: 'Riaddebito commissione OTA', nota: `Canale: ${booking.channel_name}`,
            ...scorpora(booking.ota_commission_amount ?? 0) },
          { key: 'pulizie', descrizione: 'Riaddebito pulizia finale', ...scorpora(booking.cleaning_amount ?? 0) },
          { key: 'pm', descrizione: 'Provvigione gestione immobiliare',
            nota: `Periodo: ${booking.checkin_date} → ${booking.checkout_date}`, ...scorpora(booking.pm_fee_amount ?? 0) },
        ].filter(r => r.lordo > 0);

  const somma = (f: (r: typeof righe[number]) => number) =>
    Math.round(righe.reduce((s, r) => s + f(r), 0) * 100) / 100;

  // Servizi PM tutti a zero: non c'è nulla da fatturare e il server rifiuta l'emissione
  // (DocumentGenerationService). Si avvisa prima, spiegando dove guardare.
  const servizioTotale = somma(r => r.lordo);

  // Totali: dal documento generato (DB, già corretti) se disponibile, altrimenti calcolati.
  // Il server scorpora l'IVA una volta sola sul totale (lordo / 1.22), non riga per riga:
  // stesso calcolo qui, così il totale imponibile dell'anteprima coincide al centesimo.
  const imponibileCalcolato = Math.round(servizioTotale / divisore * 100) / 100;
  // Residuo dello scorporo riga per riga sull'ultima riga, come FiscalDocumentService
  // (allineaResiduoScorporo): la somma delle righe coincide con l'imponibile del documento.
  const residuo = Math.round((imponibileCalcolato - somma(r => r.imponibile)) * 100) / 100;
  if (residuo !== 0 && righe.length > 0) {
    const ultima = righe[righe.length - 1];
    ultima.imponibile = Math.round((ultima.imponibile + residuo) * 100) / 100;
    ultima.iva = Math.round((ultima.lordo - ultima.imponibile) * 100) / 100;
  }
  const totaleFattura = generatedDoc ? generatedDoc.importoTotale : servizioTotale;
  const totaleImponibile = generatedDoc ? generatedDoc.imponibile : imponibileCalcolato;
  const totaleIva = generatedDoc
    ? generatedDoc.iva
    : Math.round((servizioTotale - totaleImponibile) * 100) / 100;

  // Semantica bottoni: se QUESTA fattura esiste già solo stampa/download, altrimenti
  // emissione (azione irreversibile).
  // NB: si guarda existingDoc e non booking_status === 'doc_issued'. Lo stato del booking
  // diventa 'doc_issued' con un solo documento qualsiasi (BookingService.resolveStatoId),
  // quindi con la sola ricevuta emessa la fattura non sarebbe più emettibile da qui.
  const isDocIssued = !!existingDoc;

  // Il PDF server-side richiede l'id del documento fiscale già emesso.
  const docId = existingDoc?.id ?? generatedDoc?.documentId;
  const handleDownloadPdf = async () => {
    if (!docId) return;
    setIsDownloading(true);
    try {
      await downloadDocumentPdf(docId, invoiceNumber);
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

  // Stampa in una finestra dedicata: window.print() sul dialog Radix (portal) stampa l'intera app.
  const handlePrint = () => {
    const content = document.getElementById('print-document-content')?.innerHTML;
    if (!content) return;
    const styles = Array.from(document.styleSheets)
      .map(s => {
        try { return Array.from(s.cssRules).map(r => r.cssText).join('\n'); }
        catch { return ''; }
      }).join('\n');
    const printWindow = window.open('', '_blank', 'width=800,height=600');
    if (!printWindow) return;
    printWindow.document.write(`
      <!DOCTYPE html>
      <html>
        <head>
          <meta charset="utf-8">
          <title>Documento</title>
          <style>${styles}</style>
          <style>
            body { font-family: Arial, sans-serif; font-size: 12px; margin: 20px; color: #000; }
            * { box-sizing: border-box; }
          </style>
        </head>
        <body>${content}</body>
      </html>
    `);
    printWindow.document.close();
    printWindow.focus();
    printWindow.print();
    printWindow.close();
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild>
        {isDocIssued ? (
          <Button variant="outline" className="w-full sm:w-auto gap-2">
            <Download className="h-4 w-4" />
            Scarica Fattura PM
          </Button>
        ) : (
          <Button className="w-full sm:w-auto gap-2">
            <FileText className="h-4 w-4" />
            Emetti Fattura PM
          </Button>
        )}
      </DialogTrigger>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <FileText className="h-5 w-5" />
            Anteprima Fattura P.M.
          </DialogTitle>
        </DialogHeader>

        {/* Fuori da print-document-content: è un avviso operativo, non parte del documento */}
        {servizioTotale === 0 && !existingDoc && !generatedDoc && (
          <div className="flex items-start gap-2 rounded-md border border-yellow-200 bg-yellow-50 px-3 py-2 text-xs text-yellow-900">
            <AlertTriangle className="h-4 w-4 mt-0.5 shrink-0 text-yellow-600" />
            <span>
              I servizi PM risultano tutti a zero (commissione OTA, pulizie e provvigione).
              Verificare le regole contratto dell'immobile o la commissione OTA nel file di
              import. L'emissione sarà bloccata dal server.
            </span>
          </div>
        )}

        <div className="border rounded-lg p-6 space-y-6 bg-background text-foreground text-sm" id="print-document-content">
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
              <p className="text-xs text-muted-foreground">CF: {booking.guest_tax_code ?? '—'}</p>
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
                <div className="col-span-2 text-right">IVA {aliquotaLabel}</div>
                <div className="col-span-2 text-right">Totale</div>
              </div>
              {righe.map(r => (
                <div key={r.key} className="grid grid-cols-12 gap-2 p-2 text-xs border-t">
                  <div className="col-span-6">
                    {r.descrizione}
                    {r.nota && (<><br /><span className="text-muted-foreground">{r.nota}</span></>)}
                  </div>
                  <div className="col-span-2 text-right">{fmt(r.imponibile)}</div>
                  <div className="col-span-2 text-right">{fmt(r.iva)}</div>
                  <div className="col-span-2 text-right">{fmt(r.lordo)}</div>
                </div>
              ))}
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
                <span className="text-muted-foreground">IVA ({aliquotaLabel})</span>
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
            {forfettario ? (
              <p><strong>Regime fiscale:</strong> Regime forfettario (RF19) – operazione senza applicazione dell'IVA</p>
            ) : (
              <>
                <p><strong>Regime fiscale:</strong> Regime ordinario – IVA {aliquotaLabel} ai sensi del DPR 633/72</p>
                <p className="text-[10px] italic">⚠️ Il PM riaddebita commissione OTA e pulizie con IVA {aliquotaLabel} anche se ricevute in reverse charge (Scenario A)</p>
              </>
            )}
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
            Documento emesso — numero <strong>{generatedDoc.documentNumber}</strong> (stato: {statoDocLabels[generatedDoc.statoDocumento] ?? generatedDoc.statoDocumento})
          </div>
        )}

        <div className="flex gap-3 justify-end pt-2">
          <Button variant="outline" onClick={() => onOpenChange(false)}>Chiudi</Button>
          {isDocIssued ? (
            // Documenti già emessi: azione ripetibile, solo stampa/download.
            <>
              <Button className="gap-2" onClick={handlePrint}>
                <Printer className="h-4 w-4" />
                Stampa
              </Button>
              {docId && (
                <Button variant="outline" className="gap-2" onClick={handleDownloadPdf} disabled={isDownloading}>
                  {isDownloading
                    ? <Loader2 className="h-4 w-4 animate-spin" />
                    : <Download className="h-4 w-4" />}
                  Scarica PDF
                </Button>
              )}
            </>
          ) : existingDoc ? (
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
              <Button className="gap-2" onClick={handlePrint}>
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
