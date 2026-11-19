import { useEffect, useState } from 'react';
import { AlertTriangle, OctagonAlert, Undo2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import {
  annullaInvioManuale, getAnnulloInvioManualeControllo,
  type AnnulloInvioManualeControllo, type DocumentDetail,
} from '@/api/documentApi';

const MOTIVO_MIN = 5;
const MOTIVO_MAX = 500;

interface Props {
  documentId: number;
  isNotaCredito: boolean;
  /** Dettaglio aggiornato dopo l'annullo (il chiamante lo mostra e avvisa). */
  onAnnullato: (doc: DocumentDetail) => void;
  onErrore: (messaggio: string) => void;
}

/**
 * Pulsante "Annulla invio manuale" + dialog. Il controllo si carica al montaggio e il pulsante
 * compare solo se l'annullo è consentito (sent_sdi senza progressivo né file, fattura senza
 * nota di credito attiva). Il documento torna "pronto".
 */
const AnnullaInvioManuale = ({ documentId, isNotaCredito, onAnnullato, onErrore }: Props) => {
  const [controllo, setControllo] = useState<AnnulloInvioManualeControllo | null>(null);
  const [aperto, setAperto] = useState(false);
  const [motivo, setMotivo] = useState('');
  const [isAnnullando, setIsAnnullando] = useState(false);

  useEffect(() => {
    let attivo = true;
    setControllo(null);
    getAnnulloInvioManualeControllo(documentId)
      .then(c => { if (attivo) setControllo(c); })
      .catch(() => { if (attivo) setControllo(null); });
    return () => { attivo = false; };
  }, [documentId]);

  if (!controllo?.consentito) return null;

  const motivoPulito = motivo.trim();
  const motivoValido = motivoPulito.length >= MOTIVO_MIN && motivoPulito.length <= MOTIVO_MAX;
  const ndcRiaperta = controllo.avvisi.find(a => a.codice === 'NDC_RIAPERTA');
  const altriAvvisi = controllo.avvisi.filter(a => a.codice !== 'NDC_RIAPERTA' && a.codice !== 'RIAPERTURA');

  const conferma = async () => {
    if (!motivoValido) return;
    setIsAnnullando(true);
    try {
      const aggiornato = await annullaInvioManuale(documentId, motivoPulito);
      setAperto(false);
      onAnnullato(aggiornato);
    } catch (err) {
      setAperto(false);
      onErrore(err instanceof Error ? err.message : 'Annullo non riuscito');
      getAnnulloInvioManualeControllo(documentId).then(setControllo).catch(() => setControllo(null));
    } finally {
      setIsAnnullando(false);
    }
  };

  return (
    <>
      <Button variant="outline" size="sm" className="gap-1 self-center" onClick={() => { setMotivo(''); setAperto(true); }}>
        <Undo2 className="h-4 w-4" />
        Annulla invio manuale
      </Button>

      <AlertDialog open={aperto} onOpenChange={o => { if (!o && !isAnnullando) setAperto(false); }}>
        <AlertDialogContent className="max-w-lg">
          <AlertDialogHeader>
            <AlertDialogTitle>Annullare l'invio manuale?</AlertDialogTitle>
            <AlertDialogDescription>
              {isNotaCredito ? 'La nota di credito' : 'La fattura'} è stata marcata come inviata allo SDI
              {controllo.inviatoManualmenteAt
                ? ` il ${new Date(controllo.inviatoManualmenteAt).toLocaleString('it-IT', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })}`
                : ''}{' '}
              senza un file generato dall'applicazione. Annullando torna "pronta": si potrà inviare di nuovo.
            </AlertDialogDescription>
          </AlertDialogHeader>

          <div className="space-y-3 text-sm">
            <div className="rounded-md border-2 border-destructive/50 bg-destructive/10 px-3 py-2 text-destructive">
              <p className="flex items-center gap-1 font-semibold">
                <OctagonAlert className="h-4 w-4 shrink-0" /> Attenzione
              </p>
              <p className="mt-1">
                Se l'hai trasmessa davvero fuori dall'applicazione (anche dopo "Scarica e marca come inviati")
                NON tornare indietro: {isNotaCredito ? 'lasciala inviata.' : 'emetti una nota di credito.'}
              </p>
            </div>

            {ndcRiaperta && (
              <div className="rounded-md border-2 border-warning/50 bg-warning/10 px-3 py-2 text-warning">
                <p className="flex items-center gap-1 font-semibold">
                  <AlertTriangle className="h-4 w-4 shrink-0" /> Nota di credito riaperta
                </p>
                <p className="mt-1">{ndcRiaperta.messaggio}</p>
              </div>
            )}

            {altriAvvisi.length > 0 && (
              <ul className="list-disc pl-5 space-y-1 text-xs text-muted-foreground">
                {altriAvvisi.map((a, i) => <li key={`${a.codice}-${i}`}>{a.messaggio}</li>)}
              </ul>
            )}

            <div className="space-y-1">
              <Label htmlFor="motivo-annullo-invio">Motivo (obbligatorio)</Label>
              <Textarea
                id="motivo-annullo-invio"
                value={motivo}
                maxLength={MOTIVO_MAX}
                onChange={e => setMotivo(e.target.value)}
                placeholder="Es. marcata per errore, il file non è stato caricato sul portale"
                disabled={isAnnullando}
              />
              <div className="flex justify-between text-xs text-muted-foreground">
                <span>Almeno {MOTIVO_MIN} caratteri: viene registrato nel log delle attività.</span>
                <span className={motivoPulito.length > 0 && !motivoValido ? 'text-destructive' : undefined}>
                  {motivoPulito.length}/{MOTIVO_MAX}
                </span>
              </div>
            </div>
          </div>

          <AlertDialogFooter>
            <AlertDialogCancel disabled={isAnnullando}>Chiudi</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={e => { e.preventDefault(); void conferma(); }}
              disabled={!motivoValido || isAnnullando}
            >
              {isAnnullando ? 'Annullo…' : 'Annulla invio manuale'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
};

export default AnnullaInvioManuale;
