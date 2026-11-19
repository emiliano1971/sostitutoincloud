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
  type EliminazioneControllo, type EliminazioneEsito, type ScenarioEliminazione,
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
 * Pulsante "Elimina documento" + dialog di conferma. Il controllo (scenari, blocchi, avvisi,
 * documenti coinvolti) si carica al montaggio: se nessuno scenario è consentito il pulsante resta
 * visibile ma disabilitato, con i blocchi nel tooltip. Dalla fattura con una ricevuta sulla
 * prenotazione si sceglie se eliminare anche la ricevuta (spuntato di default) o solo la fattura.
 * Eliminazione fisica: il numero torna libero.
 */
const EliminaDocumento = ({ documentId, onEliminato, onErrore }: Props) => {
  const [controllo, setControllo] = useState<EliminazioneControllo | null>(null);
  const [erroreControllo, setErroreControllo] = useState<string | null>(null);
  const [aperto, setAperto] = useState(false);
  const [motivo, setMotivo] = useState('');
  const [conRicevuta, setConRicevuta] = useState(true);
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

  const soloFattura = controllo?.scenarioSoloFattura;
  const scenarioRicevuta = controllo?.scenarioConRicevuta;
  const ricevutaConsentita = scenarioRicevuta?.consentito === true;
  const usaRicevuta = !!scenarioRicevuta && conRicevuta && ricevutaConsentita;
  // Scenario scelto: per la fattura uno dei due, per la ricevuta i campi in cima alla risposta
  const scenario: ScenarioEliminazione | null = controllo
    ? (soloFattura ? (usaRicevuta ? scenarioRicevuta! : soloFattura) : controllo)
    : null;
  const consentitoAlmenoUno = controllo
    ? (soloFattura ? soloFattura.consentito || ricevutaConsentita : controllo.consentito)
    : false;
  const motiviBlocco = erroreControllo ? [erroreControllo] : (soloFattura?.blocchi ?? controllo?.blocchi ?? []);
  // Blocchi della sola ricevuta: quelli dello scenario completo che non valgono per la sola fattura
  const blocchiRicevuta = scenarioRicevuta
    ? scenarioRicevuta.blocchi.filter(b => !(soloFattura?.blocchi ?? []).includes(b))
    : [];
  const numeroRicevuta = scenarioRicevuta?.documenti.find(d => d.tipo === 'ricevuta')?.numero;

  const motivoPulito = motivo.trim();
  const motivoValido = motivoPulito.length >= MOTIVO_MIN && motivoPulito.length <= MOTIVO_MAX;
  const puoConfermare = !!scenario?.consentito && motivoValido && !isEliminando;

  const apri = () => {
    setMotivo('');
    // Spuntata di default se l'eliminazione con ricevuta è consentita
    setConRicevuta(ricevutaConsentita);
    setAperto(true);
  };

  const conferma = async () => {
    if (!puoConfermare) return;
    setIsEliminando(true);
    try {
      // Sempre esplicito: per la ricevuta il backend lo ignora
      const esito = await eliminaDocumento(documentId, motivoPulito, soloFattura ? usaRicevuta : true);
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
      disabled={!consentitoAlmenoUno || isEliminando}
    >
      {controllo || erroreControllo ? <Trash2 className="h-4 w-4" /> : <Loader2 className="h-4 w-4 animate-spin" />}
      Elimina documento
    </Button>
  );

  const casella = (
    <label className={`flex items-start gap-2 text-sm ${ricevutaConsentita ? '' : 'text-muted-foreground'}`}>
      <input
        type="checkbox"
        className="mt-0.5"
        checked={usaRicevuta}
        onChange={e => setConRicevuta(e.target.checked)}
        disabled={!ricevutaConsentita || isEliminando}
      />
      <span>Elimina anche la ricevuta collegata{numeroRicevuta ? ` (${numeroRicevuta})` : ''}</span>
    </label>
  );

  return (
    <>
      {consentitoAlmenoUno ? pulsante : (
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
            {scenarioRicevuta && (
              <div className="space-y-1">
                {ricevutaConsentita ? casella : (
                  <Tooltip>
                    <TooltipTrigger asChild><div tabIndex={0}>{casella}</div></TooltipTrigger>
                    <TooltipContent className="max-w-sm">
                      <ul className="list-disc pl-4 space-y-1 text-xs">
                        {blocchiRicevuta.map(b => <li key={b}>{b}</li>)}
                      </ul>
                    </TooltipContent>
                  </Tooltip>
                )}
                {!ricevutaConsentita && (
                  <p className="text-xs text-warning">La ricevuta non si può eliminare: verrà cancellata solo la fattura.</p>
                )}
              </div>
            )}

            <ul className="rounded-md border divide-y">
              {(scenario?.documenti ?? []).map(d => (
                <li key={d.id} className="flex justify-between px-3 py-2">
                  <span className="font-mono">{d.numero}</span>
                  <span className="text-muted-foreground">{labelTipo[d.tipo] ?? d.tipo}</span>
                </li>
              ))}
            </ul>

            {(scenario?.blocchi.length ?? 0) > 0 && (
              <ul className="rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2 list-disc pl-6 space-y-1 text-xs text-destructive">
                {scenario!.blocchi.map(b => <li key={b}>{b}</li>)}
              </ul>
            )}

            {(scenario?.avvisi.length ?? 0) > 0 && (
              <div className="rounded-md border border-warning/30 bg-warning/5 px-3 py-2 space-y-1">
                <p className="flex items-center gap-1 font-medium text-warning">
                  <AlertTriangle className="h-4 w-4" /> Da sapere prima di confermare
                </p>
                <ul className="list-disc pl-5 space-y-1 text-xs">
                  {scenario!.avvisi.map((a, i) => <li key={`${a.codice}-${i}`}>{a.messaggio}</li>)}
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
              <div className="flex justify-between text-xs text-muted-foreground">
                <span>Almeno {MOTIVO_MIN} caratteri: viene registrato nel log delle attività.</span>
                <span className={motivoPulito.length > 0 && !motivoValido ? 'text-destructive' : undefined}>
                  {motivoPulito.length}/{MOTIVO_MAX}
                </span>
              </div>
            </div>
          </div>

          <AlertDialogFooter>
            <AlertDialogCancel disabled={isEliminando}>Annulla</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={e => { e.preventDefault(); void conferma(); }}
              disabled={!puoConfermare}
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
