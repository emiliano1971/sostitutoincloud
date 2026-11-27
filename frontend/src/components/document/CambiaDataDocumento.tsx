import { useEffect, useState } from 'react';
import { AlertTriangle, CalendarClock, Info, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import {
  CODICI_AVVISO_SCADENZA, cambiaDataDocumento, getCambioDataControllo,
  type CambioDataControllo, type DocumentDetail,
} from '@/api/documentApi';

const MOTIVO_MIN = 5;
const MOTIVO_MAX = 500;
/** Attesa dopo l'ultima modifica della data prima di richiamare il controllo. */
const DEBOUNCE_MS = 400;

const fmtData = (iso?: string) => (iso ? new Date(`${iso}T00:00:00`).toLocaleDateString('it-IT') : '—');

interface Props {
  documentId: number;
  /** Dettaglio riletto dopo il cambio (il chiamante lo mostra e avvisa). */
  onCambiata: (doc: DocumentDetail, dataPrecedente: string) => void;
  onErrore: (messaggio: string) => void;
}

/**
 * Pulsante "Cambia data" + dialog per fattura PM / nota di credito mai inviata e per la
 * ricevuta (con la sua riga di ritenuta: in un altro mese esce da F24 ready e rendiconto, con
 * avvisi; scadenze F24 superate con una seconda conferma esplicita). Il controllo
 * senza data si carica al montaggio: se non consentito il pulsante resta disabilitato con i
 * blocchi nel tooltip. Nel dialog ogni cambio della data rilancia il controllo (con debounce)
 * per intervallo, blocchi e avvisi della data scelta.
 */
const CambiaDataDocumento = ({ documentId, onCambiata, onErrore }: Props) => {
  const [base, setBase] = useState<CambioDataControllo | null>(null);
  const [erroreBase, setErroreBase] = useState<string | null>(null);
  const [aperto, setAperto] = useState(false);
  const [data, setData] = useState('');
  const [controlloData, setControlloData] = useState<CambioDataControllo | null>(null);
  const [isControllando, setIsControllando] = useState(false);
  const [motivo, setMotivo] = useState('');
  const [confermaAvvisi, setConfermaAvvisi] = useState(false);
  const [confermaScadenza, setConfermaScadenza] = useState(false);
  const [isSalvando, setIsSalvando] = useState(false);

  useEffect(() => {
    let attivo = true;
    setBase(null);
    setErroreBase(null);
    getCambioDataControllo(documentId)
      .then(c => { if (attivo) setBase(c); })
      .catch(err => { if (attivo) setErroreBase(err instanceof Error ? err.message : 'Controllo non disponibile'); });
    return () => { attivo = false; };
  }, [documentId]);

  // Controllo della data scelta, con debounce
  useEffect(() => {
    if (!aperto || !data) { setControlloData(null); return; }
    let attivo = true;
    setIsControllando(true);
    const t = setTimeout(() => {
      getCambioDataControllo(documentId, data)
        .then(c => { if (attivo) { setControlloData(c); setConfermaAvvisi(false); setConfermaScadenza(false); } })
        .catch(err => { if (attivo) setControlloData({ consentito: false, blocchi: [err instanceof Error ? err.message : 'Controllo non riuscito'], avvisi: [], dataAttuale: base?.dataAttuale ?? '' }); })
        .finally(() => { if (attivo) setIsControllando(false); });
    }, DEBOUNCE_MS);
    return () => { attivo = false; clearTimeout(t); };
  }, [aperto, data, documentId, base?.dataAttuale]);

  const consentito = base?.consentito === true;
  const motiviBlocco = erroreBase ? [erroreBase] : base?.blocchi ?? [];
  const motivoPulito = motivo.trim();
  const motivoValido = motivoPulito.length >= MOTIVO_MIN && motivoPulito.length <= MOTIVO_MAX;
  const tuttiAvvisi = controlloData?.avvisi ?? [];
  const avvisiScadenza = tuttiAvvisi.filter(a => CODICI_AVVISO_SCADENZA.includes(a.codice));
  const avvisi = tuttiAvvisi.filter(a => !CODICI_AVVISO_SCADENZA.includes(a.codice));
  const serveConfermaScadenza = controlloData?.richiedeConfermaScadenza === true || avvisiScadenza.length > 0;
  const dataValida = controlloData?.consentito === true && !isControllando;
  const puoConfermare = dataValida && motivoValido && (avvisi.length === 0 || confermaAvvisi)
    && (!serveConfermaScadenza || confermaScadenza) && !isSalvando;

  const apri = () => {
    // Campo vuoto: la data attuale è già mostrata nella descrizione e darebbe subito il
    // blocco "La data è già quella indicata"
    setData('');
    setMotivo('');
    setControlloData(null);
    setConfermaAvvisi(false);
    setConfermaScadenza(false);
    setAperto(true);
  };

  const conferma = async () => {
    if (!puoConfermare || !base) return;
    setIsSalvando(true);
    try {
      const aggiornato = await cambiaDataDocumento(documentId, data, motivoPulito, serveConfermaScadenza && confermaScadenza);
      setAperto(false);
      onCambiata(aggiornato, base.dataAttuale);
    } catch (err) {
      onErrore(err instanceof Error ? err.message : 'Cambio data non riuscito');
    } finally {
      setIsSalvando(false);
    }
  };

  const pulsante = (
    <Button variant="outline" className="gap-2" onClick={apri} disabled={!consentito}>
      {base || erroreBase ? <CalendarClock className="h-4 w-4" /> : <Loader2 className="h-4 w-4 animate-spin" />}
      Cambia data
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

      <AlertDialog open={aperto} onOpenChange={o => { if (!o && !isSalvando) setAperto(false); }}>
        <AlertDialogContent className="max-w-lg">
          <AlertDialogHeader>
            <AlertDialogTitle>Cambia data del documento</AlertDialogTitle>
            <AlertDialogDescription>
              Data attuale: <strong>{fmtData(base?.dataAttuale)}</strong>. Il numero resta lo stesso: la nuova data
              deve stare nello stesso anno, tra il documento precedente e il successivo della serie.
            </AlertDialogDescription>
          </AlertDialogHeader>

          <div className="space-y-4 text-sm">
            <div className="space-y-1">
              <Label htmlFor="nuova-data-documento">Nuova data</Label>
              <Input
                id="nuova-data-documento"
                type="date"
                value={data}
                min={base?.dataMinima}
                max={base?.dataMassima}
                onChange={e => setData(e.target.value)}
                disabled={isSalvando}
              />
              <p className="flex items-center gap-1 text-xs text-muted-foreground">
                Ammessa dal {fmtData(base?.dataMinima)} al {fmtData(base?.dataMassima)}.
                {base?.limitatoAlMese && (
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <span tabIndex={0} className="inline-flex items-center gap-0.5 text-warning cursor-help">
                        <Info className="h-3.5 w-3.5" /> Solo nel mese attuale
                      </span>
                    </TooltipTrigger>
                    <TooltipContent className="max-w-sm text-xs">{base.motivoLimiteMese}</TooltipContent>
                  </Tooltip>
                )}
              </p>
            </div>

            {isControllando && (
              <p className="flex items-center gap-1 text-xs text-muted-foreground">
                <Loader2 className="h-3 w-3 animate-spin" /> Controllo della data…
              </p>
            )}
            {!isControllando && controlloData && controlloData.blocchi.length > 0 && (
              <ul className="rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2 list-disc pl-6 space-y-1 text-xs text-destructive">
                {controlloData.blocchi.map(b => <li key={b}>{b}</li>)}
              </ul>
            )}
            {!isControllando && avvisi.length > 0 && (
              <div className="rounded-md border border-warning/30 bg-warning/5 px-3 py-2 space-y-2">
                <p className="flex items-center gap-1 font-medium text-warning">
                  <AlertTriangle className="h-4 w-4" /> Da sapere prima di confermare
                </p>
                <ul className="list-disc pl-5 space-y-1 text-xs">
                  {avvisi.map((a, i) => <li key={`${a.codice}-${i}`}>{a.messaggio}</li>)}
                </ul>
                <label className="flex items-center gap-2 text-xs">
                  <input
                    type="checkbox"
                    checked={confermaAvvisi}
                    onChange={e => setConfermaAvvisi(e.target.checked)}
                    disabled={isSalvando}
                  />
                  Ho letto gli avvisi e confermo
                </label>
              </div>
            )}
            {!isControllando && avvisiScadenza.length > 0 && (
              <div className="rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 space-y-2">
                <p className="flex items-center gap-1 font-medium text-destructive">
                  <AlertTriangle className="h-4 w-4" /> Scadenza F24 superata
                </p>
                <ul className="list-disc pl-5 space-y-1 text-xs text-destructive">
                  {avvisiScadenza.map((a, i) => <li key={`${a.codice}-${i}`}>{a.messaggio}</li>)}
                </ul>
                <label className="flex items-center gap-2 text-xs font-medium">
                  <input
                    type="checkbox"
                    checked={confermaScadenza}
                    onChange={e => setConfermaScadenza(e.target.checked)}
                    disabled={isSalvando}
                  />
                  Confermo il cambio nonostante la scadenza superata
                </label>
              </div>
            )}

            <div className="space-y-1">
              <Label htmlFor="motivo-cambio-data">Motivo (obbligatorio)</Label>
              <Textarea
                id="motivo-cambio-data"
                value={motivo}
                maxLength={MOTIVO_MAX}
                onChange={e => setMotivo(e.target.value)}
                placeholder="Es. data inserita per errore all'emissione"
                disabled={isSalvando}
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
            <AlertDialogCancel disabled={isSalvando}>Chiudi</AlertDialogCancel>
            <AlertDialogAction onClick={e => { e.preventDefault(); void conferma(); }} disabled={!puoConfermare}>
              {isSalvando ? 'Salvataggio…' : 'Cambia data'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
};

export default CambiaDataDocumento;
