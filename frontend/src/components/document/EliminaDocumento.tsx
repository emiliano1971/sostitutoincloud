import { useEffect, useState } from 'react';
import { AlertTriangle, Loader2, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import {
  eliminaDocumento, getEliminazioneControllo,
  type EliminazioneControllo, type EliminazioneEsito,
} from '@/api/documentApi';

const MOTIVO_MIN = 5;
const MOTIVO_MAX = 500;

const labelTipo: Record<string, string> = { fattura: 'Fattura PM', ricevuta: 'Ricevuta owner' };

interface Props {
  documentId: number;
  /** Chiamata dopo l'eliminazione riuscita (toast e ritorno alla lista sono del chiamante). */
  onEliminato: (esito: EliminazioneEsito) => void;
  onErrore: (messaggio: string) => void;
}

/**
 * Pulsante "Elimina documento" + dialog di conferma. Il controllo (blocchi, avvisi, documenti
 * coinvolti) si carica al montaggio: se l'eliminazione non è consentita il pulsante resta
 * visibile ma disabilitato, con i blocchi nel tooltip. Eliminazione fisica: il numero torna libero.
 */
const EliminaDocumento = ({ documentId, onEliminato, onErrore }: Props) => {
  const [controllo, setControllo] = useState<EliminazioneControllo | null>(null);
  const [erroreControllo, setErroreControllo] = useState<string | null>(null);
  const [aperto, setAperto] = useState(false);
  const [motivo, setMotivo] = useState('');
  const [isEliminando, setIsEliminando] = useState(false);

  useEffect(() => {
    let attivo = true;
    setControllo(null);
    setErroreControllo(null);
    getEliminazioneControllo(documentId)
      .then(c => { if (attivo) setControllo(c); })
      .catch(err => { if (attivo) setErroreControllo(err instanceof Error ? err.message : 'Controllo non disponibile'); });
    return () => { attivo = false; };
  }, [documentId]);

  const motivoPulito = motivo.trim();
  const motivoValido = motivoPulito.length >= MOTIVO_MIN && motivoPulito.length <= MOTIVO_MAX;
  const consentito = controllo?.consentito === true;
  const motiviBlocco = erroreControllo ? [erroreControllo] : controllo?.blocchi ?? [];

  const apri = () => { setMotivo(''); setAperto(true); };

  const conferma = async () => {
    if (!motivoValido) return;
    setIsEliminando(true);
    try {
      const esito = await eliminaDocumento(documentId, motivoPulito);
      setAperto(false);
      onEliminato(esito);
    } catch (err) {
      onErrore(err instanceof Error ? err.message : 'Eliminazione non riuscita');
      // La situazione può essere cambiata: ricarico il controllo
      getEliminazioneControllo(documentId).then(setControllo).catch(() => undefined);
      setAperto(false);
    } finally {
      setIsEliminando(false);
    }
  };

  const pulsante = (
    <Button
      variant="outline"
      className="gap-2 text-destructive"
      onClick={apri}
      disabled={!consentito || isEliminando}
    >
      {controllo || erroreControllo ? <Trash2 className="h-4 w-4" /> : <Loader2 className="h-4 w-4 animate-spin" />}
      Elimina documento
    </Button>
  );

  return (
    <>
      {consentito ? pulsante : (
        <Tooltip>
          {/* span: un pulsante disabilitato non riceve gli eventi del mouse */}
          <TooltipTrigger asChild><span tabIndex={0}>{pulsante}</span></TooltipTrigger>
          <TooltipContent className="max-w-sm">
            {motiviBlocco.length > 0 ? (
              <ul className="list-disc pl-4 space-y-1 text-xs">
                {motiviBlocco.map(b => <li key={b}>{b}</li>)}
              </ul>
            ) : <span className="text-xs">Controllo in corso…</span>}
          </TooltipContent>
        </Tooltip>
      )}

      <AlertDialog open={aperto} onOpenChange={o => { if (!o && !isEliminando) setAperto(false); }}>
        <AlertDialogContent className="max-w-lg">
          <AlertDialogHeader>
            <AlertDialogTitle>Eliminare definitivamente?</AlertDialogTitle>
            <AlertDialogDescription>
              I documenti seguenti verranno eliminati in modo definitivo e il loro numero tornerà libero:
              la prossima emissione lo riutilizzerà, senza buchi nella numerazione.
            </AlertDialogDescription>
          </AlertDialogHeader>

          <div className="space-y-4 text-sm">
            <ul className="rounded-md border divide-y">
              {(controllo?.documenti ?? []).map(d => (
                <li key={d.id} className="flex justify-between px-3 py-2">
                  <span className="font-mono">{d.numero}</span>
                  <span className="text-muted-foreground">{labelTipo[d.tipo] ?? d.tipo}</span>
                </li>
              ))}
            </ul>

            {(controllo?.avvisi.length ?? 0) > 0 && (
              <div className="rounded-md border border-warning/30 bg-warning/5 px-3 py-2 space-y-1">
                <p className="flex items-center gap-1 font-medium text-warning">
                  <AlertTriangle className="h-4 w-4" /> Da sapere prima di confermare
                </p>
                <ul className="list-disc pl-5 space-y-1 text-xs">
                  {controllo!.avvisi.map((a, i) => <li key={`${a.codice}-${i}`}>{a.messaggio}</li>)}
                </ul>
              </div>
            )}

            <div className="space-y-1">
              <Label htmlFor="motivo-eliminazione">Motivo (obbligatorio)</Label>
              <Textarea
                id="motivo-eliminazione"
                value={motivo}
                maxLength={MOTIVO_MAX}
                onChange={e => setMotivo(e.target.value)}
                placeholder="Es. emessa per errore sulla prenotazione sbagliata"
                disabled={isEliminando}
              />
              <p className="text-xs text-muted-foreground">
                Da {MOTIVO_MIN} a {MOTIVO_MAX} caratteri: viene registrato nel log delle attività.
              </p>
            </div>
          </div>

          <AlertDialogFooter>
            <AlertDialogCancel disabled={isEliminando}>Annulla</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={e => { e.preventDefault(); void conferma(); }}
              disabled={!motivoValido || isEliminando}
            >
              {isEliminando ? 'Eliminazione…' : 'Elimina definitivamente'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
};

export default EliminaDocumento;
