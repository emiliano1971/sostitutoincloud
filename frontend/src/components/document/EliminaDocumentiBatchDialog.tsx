import { useEffect, useRef, useState } from 'react';
import { AlertTriangle, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { useToast } from '@/hooks/use-toast';
import {
  ELIMINAZIONE_BATCH_MAX, eliminaDocumentiBatch, getEliminazioneBatchControllo,
  type EliminazioneBatch, type UnitaEliminazione,
} from '@/api/documentApi';

const MOTIVO_MIN = 5;
const MOTIVO_MAX = 500;

const labelTipo: Record<string, string> = {
  fattura: 'Fattura PM', ricevuta: 'Ricevuta owner', nota_credito: 'Nota di credito',
};

interface Props {
  open: boolean;
  /** Documenti selezionati nella lista (pagina corrente). */
  documenti: { id: number; documentNumber: string; documentType: string }[];
  onClose: () => void;
  /** Dopo un'esecuzione, alla chiusura del riepilogo: il chiamante azzera la selezione e ricarica. */
  onCompletato: () => void;
}

/**
 * Eliminazione fisica in blocco di fatture PM e ricevute, a ritroso per serie, con le stesse regole
 * dell'eliminazione singola. All'apertura chiama il controllo (simulazione) e mostra cosa verrà
 * eliminato, bloccato o non tentato; dopo la conferma mostra il riepilogo reale.
 * Le note di credito sono escluse dalla selezione.
 */
const EliminaDocumentiBatchDialog = ({ open, documenti, onClose, onCompletato }: Props) => {
  const { toast } = useToast();
  const [controllo, setControllo] = useState<EliminazioneBatch | null>(null);
  const [erroreControllo, setErroreControllo] = useState<string | null>(null);
  const [esito, setEsito] = useState<EliminazioneBatch | null>(null);
  const [motivo, setMotivo] = useState('');
  const [isEliminando, setIsEliminando] = useState(false);
  // Guardia sincrona contro il doppio click: lo stato React si aggiorna solo al render successivo
  const eliminazioneInCorso = useRef(false);

  const idonei = documenti.filter(d => d.documentType === 'fattura' || d.documentType === 'ricevuta');
  const ndcEscluse = documenti.filter(d => d.documentType === 'nota_credito').length;
  const oltreLimite = idonei.length > ELIMINAZIONE_BATCH_MAX;
  const ids = idonei.map(d => d.id);
  const idsKey = ids.join(',');

  const caricaControllo = () => {
    setControllo(null);
    setErroreControllo(null);
    if (ids.length === 0 || oltreLimite) return;
    getEliminazioneBatchControllo(ids)
      .then(setControllo)
      .catch(err => setErroreControllo(err instanceof Error ? err.message : 'Controllo non disponibile'));
  };

  useEffect(() => {
    if (!open) return;
    setEsito(null);
    setMotivo('');
    caricaControllo();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- la selezione è in idsKey
  }, [open, idsKey]);

  const motivoPulito = motivo.trim();
  const motivoValido = motivoPulito.length >= MOTIVO_MIN && motivoPulito.length <= MOTIVO_MAX;
  const puoConfermare = !!controllo?.consentito && motivoValido && !isEliminando && !esito;

  const conferma = async () => {
    if (!puoConfermare || eliminazioneInCorso.current) return;
    eliminazioneInCorso.current = true;
    setIsEliminando(true);
    try {
      setEsito(await eliminaDocumentiBatch(ids, motivoPulito));
    } catch (err) {
      toast({
        title: 'Eliminazione non eseguita',
        description: err instanceof Error ? err.message : 'Errore imprevisto',
        variant: 'destructive',
      });
      // La situazione può essere cambiata: ricarico il controllo
      caricaControllo();
    } finally {
      eliminazioneInCorso.current = false;
      setIsEliminando(false);
    }
  };

  const chiudi = () => {
    if (isEliminando) return;
    if (esito) onCompletato(); else onClose();
  };

  const dati = esito ?? controllo;

  return (
    <Dialog open={open} onOpenChange={o => { if (!o) chiudi(); }}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{esito ? 'Eliminazione in blocco completata' : 'Elimina documenti selezionati'}</DialogTitle>
          <DialogDescription>
            {esito
              ? `Lotto ${esito.lotto ?? '—'}: riepilogo dell'operazione.`
              : 'Fatture PM e ricevute vengono eliminate a ritroso, dalla più recente di ogni serie, con le stesse regole dell\'eliminazione singola. Al primo documento bloccato la sua serie si ferma.'}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 text-sm">
          {!esito && ndcEscluse > 0 && (
            <p className="rounded-md border px-3 py-2 text-xs text-muted-foreground">
              {ndcEscluse === 1 ? '1 nota di credito esclusa' : `${ndcEscluse} note di credito escluse`}:
              le note di credito non si eliminano in blocco, si annullano dal loro dettaglio.
            </p>
          )}

          {!esito && oltreLimite && (
            <p className="rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2 text-destructive">
              Selezionati {idonei.length} documenti: il massimo per un'eliminazione in blocco è {ELIMINAZIONE_BATCH_MAX}.
            </p>
          )}

          {!esito && !oltreLimite && idonei.length === 0 && (
            <p className="text-muted-foreground">Nessuna fattura o ricevuta tra i documenti selezionati.</p>
          )}

          {!esito && erroreControllo && (
            <p className="rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2 text-destructive">
              {erroreControllo}
            </p>
          )}

          {!dati && !erroreControllo && !oltreLimite && idonei.length > 0 && (
            <div className="flex items-center gap-2 text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Controllo in corso…
            </div>
          )}

          {dati && (
            <>
              <Elenco
                titolo={esito ? 'Eliminati' : 'Verranno eliminati'}
                vuoto={esito ? 'Nessun documento eliminato' : 'Nessun documento eliminabile'}
                unita={dati.eliminati}
                mostraAvvisi={!esito}
              />
              <Elenco titolo="Bloccati" unita={dati.bloccati} tono="bloccato" />
              <Elenco titolo="Non tentati" unita={dati.nonTentati} tono="nonTentato" />
            </>
          )}

          {!esito && (
            <>
              <ul className="list-disc pl-5 space-y-1 text-xs text-muted-foreground">
                <li>
                  Gli avvisi su F24 e rendiconto sono calcolati sullo stato attuale, documento per documento:
                  non tengono conto delle altre eliminazioni dello stesso lotto. All'esecuzione ogni documento
                  viene ricontrollato.
                </li>
                <li>
                  Massimo {ELIMINAZIONE_BATCH_MAX} documenti selezionati per volta: le ricevute incluse
                  automaticamente con la loro fattura non contano nel limite.
                </li>
              </ul>

              <div className="space-y-1">
                <Label htmlFor="motivo-eliminazione-batch">Motivo (obbligatorio, uguale per tutti i documenti)</Label>
                <Textarea
                  id="motivo-eliminazione-batch"
                  value={motivo}
                  maxLength={MOTIVO_MAX}
                  onChange={e => setMotivo(e.target.value)}
                  placeholder="Es. documenti emessi per errore sul mese sbagliato"
                  disabled={isEliminando}
                />
                <div className="flex justify-between text-xs text-muted-foreground">
                  <span>Almeno {MOTIVO_MIN} caratteri: viene registrato nel log delle attività.</span>
                  <span className={motivoPulito.length > 0 && !motivoValido ? 'text-destructive' : undefined}>
                    {motivoPulito.length}/{MOTIVO_MAX}
                  </span>
                </div>
              </div>

              <p className="flex items-center gap-1 font-medium text-destructive">
                <AlertTriangle className="h-4 w-4" />
                Operazione irreversibile: i numeri liberati verranno riassegnati.
              </p>
            </>
          )}
        </div>

        <DialogFooter>
          {esito ? (
            <Button onClick={chiudi}>Chiudi</Button>
          ) : (
            <>
              <Button variant="outline" onClick={chiudi} disabled={isEliminando}>Annulla</Button>
              <Button variant="destructive" onClick={() => void conferma()} disabled={!puoConfermare}>
                {isEliminando ? 'Eliminazione…' : 'Elimina definitivamente'}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

interface ElencoProps {
  titolo: string;
  unita: UnitaEliminazione[];
  vuoto?: string;
  tono?: 'bloccato' | 'nonTentato';
  mostraAvvisi?: boolean;
}

/** Una delle tre liste: documenti dell'unità, poi blocchi, motivo o avvisi. */
const Elenco = ({ titolo, unita, vuoto, tono, mostraAvvisi }: ElencoProps) => {
  if (unita.length === 0 && !vuoto) return null;
  const testo = tono === 'bloccato' ? 'text-destructive' : tono === 'nonTentato' ? 'text-muted-foreground' : '';
  return (
    <div className="space-y-1">
      <p className={`font-medium ${testo}`}>{titolo} ({unita.length})</p>
      {unita.length === 0 ? (
        <p className="text-xs text-muted-foreground">{vuoto}</p>
      ) : (
        <ul className="rounded-md border divide-y">
          {unita.map((u, i) => (
            <li key={`${u.documenti[0]?.id ?? i}-${i}`} className="px-3 py-2 space-y-1">
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                {u.documenti.map(d => (
                  <span key={d.id}>
                    <span className="font-mono">{d.numero ?? `id ${d.id}`}</span>
                    {d.tipo && <span className="text-xs text-muted-foreground"> {labelTipo[d.tipo] ?? d.tipo}</span>}
                  </span>
                ))}
                {u.ricevutaInclusa && (
                  <span className="text-xs text-warning">ricevuta inclusa con la fattura</span>
                )}
              </div>
              {u.blocchi.length > 0 && (
                <ul className="list-disc pl-5 text-xs text-destructive">
                  {u.blocchi.map(b => <li key={b}>{b}</li>)}
                </ul>
              )}
              {u.motivo && <p className="text-xs text-muted-foreground">{u.motivo}</p>}
              {mostraAvvisi && u.avvisi.length > 0 && (
                <ul className="list-disc pl-5 text-xs text-warning">
                  {u.avvisi.map((a, j) => <li key={`${a.codice}-${j}`}>{a.messaggio}</li>)}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

export default EliminaDocumentiBatchDialog;
