import { useMemo, useState } from 'react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Loader2 } from 'lucide-react';
import { toast } from '@/hooks/use-toast';
import {
  emettiNdc,
  type BookingDetail,
  type BookingSplitRiga,
  type FiscalDocumentSummary,
  type NotaCredito,
} from '@/api/bookingApi';

interface NdcDialogProps {
  booking: BookingDetail;
  /** Fattura PM da stornare. */
  fattura: FiscalDocumentSummary;
  open: boolean;
  onClose: () => void;
  onSuccess: (ndc: NotaCredito) => void;
}

const formatAmount = (v: number) => `€${v.toLocaleString('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

/**
 * Nota di credito sempre TOTALE sulla fattura PM: le voci della fattura (righe split in
 * fattura PM con importo > 0) sono mostrate tutte selezionate e non modificabili.
 * Il backend ricava le righe dalla fattura: il client invia solo l'id della fattura.
 */
const NdcDialog = ({ booking, fattura, open, onClose, onSuccess }: NdcDialogProps) => {
  const righeFattura: BookingSplitRiga[] = useMemo(
    () => (booking.righeSplit ?? []).filter(r => r.includeInFatturaPm && r.importo > 0),
    [booking.righeSplit],
  );
  const [saving, setSaving] = useState(false);
  const totaleFattura = fattura.importoTotale ?? righeFattura.reduce((acc, r) => acc + r.importo, 0);
  // Stessa regola del backend: il TD04 va allo SDI solo se la fattura è stata trasmessa
  const fatturaInviata = ['sent_sdi', 'accepted'].includes(fattura.statoDocumento);

  const handleEmetti = async () => {
    setSaving(true);
    try {
      const ndc = await emettiNdc({ fkFiscalDocumentId: fattura.id });
      toast({ title: 'Nota di credito emessa', description: ndc.documentNumber });
      onSuccess(ndc);
    } catch (e) {
      toast({
        title: 'Errore emissione nota di credito',
        description: e instanceof Error ? e.message : String(e),
        variant: 'destructive',
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={v => { if (!v) onClose(); }}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Emetti Nota di Credito</DialogTitle>
          <DialogDescription>Storno totale di {fattura.documentNumber}</DialogDescription>
        </DialogHeader>

        <div className="space-y-1">
          {righeFattura.length === 0 && (
            <p className="text-sm text-muted-foreground py-2">Storno dell'intero importo della fattura.</p>
          )}
          {righeFattura.map(r => (
            <div key={r.id} className="flex items-center gap-3 py-2 border-b opacity-75">
              <Checkbox checked={true} disabled={true} />
              <span className="flex-1 text-sm min-w-0 truncate" title={r.descrizione}>{r.descrizione}</span>
              <span className="text-sm font-medium whitespace-nowrap">-{formatAmount(r.importo)}</span>
            </div>
          ))}
          <div className="flex justify-between font-medium pt-2 border-t">
            <span>Totale storno</span>
            <span className="text-destructive">-{formatAmount(totaleFattura)}</span>
          </div>
          <p className="text-xs text-muted-foreground pt-2">
            La prenotazione diventa "Stornata" e la ricevuta owner viene annullata: per riemettere
            i documenti usa poi "Copia prenotazione".
          </p>
          {!fatturaInviata && (
            <p className="text-xs text-muted-foreground mt-2">
              ⓘ La fattura non è stata inviata allo SDI: la nota di credito non verrà inviata automaticamente.
            </p>
          )}
          {booking.ritenutaVersata && (
            <p className="text-xs text-amber-600 mt-2">
              ⚠ La ritenuta è già stata versata. Verrà registrato un credito d'imposta compensabile nel prossimo F24.
            </p>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={saving}>Annulla</Button>
          <Button onClick={handleEmetti} disabled={saving} className="gap-2">
            {saving && <Loader2 className="h-4 w-4 animate-spin" />}
            Emetti NDC
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default NdcDialog;
