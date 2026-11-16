import { useEffect, useState } from 'react';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription,
  AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { AlertCircle } from 'lucide-react';
import {
  getEmissioneControllo, type AvvisoEmissione, type DocumentGenerateRequest, type EmissioneControllo,
} from '@/api/documentApi';

/** Data locale yyyy-MM-dd (toISOString darebbe la data UTC). */
const oggiISO = (): string => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

const dataIt = (iso?: string) => (iso ? new Date(`${iso}T00:00:00`).toLocaleDateString('it-IT') : '');

interface Opzioni {
  /** Dialog aperto e documento ancora da emettere: solo allora si interroga il backend. */
  attivo: boolean;
  bookingId: number;
  tipoDocumento: DocumentGenerateRequest['tipoDocumento'];
  /** Emissione vera: un errore lanciato (es. 422 della data) viene mostrato sotto il campo. */
  onEmetti: (dataEmissione: string, avvisi: AvvisoEmissione[]) => Promise<void>;
}

/**
 * Data di emissione manuale di fattura PM / ricevuta owner.
 * Il campo parte da oggi, con min = data dell'ultimo documento dello stesso tipo e anno e
 * max = oggi (dal controllo del backend). Al click su "Emetti" la data viene ricontrollata:
 * errore → mostrato sotto il campo; avvisi (F24, CU, anno precedente, SDI tardivo) →
 * conferma esplicita prima di emettere.
 */
export function useEmissioneConData({ attivo, bookingId, tipoDocumento, onEmetti }: Opzioni) {
  const [data, setData] = useState(oggiISO);
  const [controllo, setControllo] = useState<EmissioneControllo | null>(null);
  const [errore, setErrore] = useState<string | null>(null);
  const [avvisiDaConfermare, setAvvisiDaConfermare] = useState<AvvisoEmissione[] | null>(null);
  const [verificando, setVerificando] = useState(false);

  // A ogni apertura si riparte da oggi
  useEffect(() => {
    if (attivo) {
      setData(oggiISO());
      setErrore(null);
    }
  }, [attivo]);

  // Intervallo ammesso ed errore della data scelta, aggiornati a ogni cambio
  useEffect(() => {
    if (!attivo || !data) return;
    let annullato = false;
    getEmissioneControllo(bookingId, tipoDocumento, data)
      .then(c => { if (!annullato) { setControllo(c); setErrore(c.erroreData ?? null); } })
      .catch(() => { /* il controllo si ripete comunque al click su Emetti */ });
    return () => { annullato = true; };
  }, [attivo, bookingId, tipoDocumento, data]);

  const esegui = async (avvisi: AvvisoEmissione[]) => {
    setAvvisiDaConfermare(null);
    setErrore(null);
    try {
      await onEmetti(data, avvisi);
    } catch (e) {
      setErrore(e instanceof Error ? e.message : 'Errore imprevisto');
    }
  };

  const emetti = async () => {
    setVerificando(true);
    try {
      const c = await getEmissioneControllo(bookingId, tipoDocumento, data);
      setControllo(c);
      if (c.erroreData) {
        setErrore(c.erroreData);
        return;
      }
      if (c.avvisi?.length) {
        setAvvisiDaConfermare(c.avvisi);
        return;
      }
      await esegui([]);
    } catch (e) {
      setErrore(e instanceof Error ? e.message : 'Errore imprevisto');
    } finally {
      setVerificando(false);
    }
  };

  const campoData = (
    <div className="space-y-1">
      <Label htmlFor={`data-emissione-${tipoDocumento}`} className="text-xs">Data di emissione</Label>
      <Input
        id={`data-emissione-${tipoDocumento}`}
        type="date"
        value={data}
        min={controllo?.dataMinima}
        max={controllo?.dataMassima ?? oggiISO()}
        onChange={e => setData(e.target.value)}
        className="w-44 text-sm"
      />
      {controllo?.ultimoDocumentoNumero && (
        <p className="text-xs text-muted-foreground">
          Ultimo: {controllo.ultimoDocumentoNumero} del {dataIt(controllo.ultimoDocumentoData)}
        </p>
      )}
      {errore && (
        <p className="flex items-center gap-1 text-xs text-destructive">
          <AlertCircle className="h-3.5 w-3.5 shrink-0" /> {errore}
        </p>
      )}
    </div>
  );

  const conferma = (
    <AlertDialog open={avvisiDaConfermare !== null} onOpenChange={o => { if (!o) setAvvisiDaConfermare(null); }}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Confermi l'emissione con data {dataIt(data)}?</AlertDialogTitle>
          <AlertDialogDescription asChild>
            <ul className="list-disc space-y-2 pl-4 text-sm">
              {avvisiDaConfermare?.map(a => <li key={`${a.codice}-${a.f24Id ?? ''}`}>{a.messaggio}</li>)}
            </ul>
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Annulla</AlertDialogCancel>
          <AlertDialogAction onClick={() => esegui(avvisiDaConfermare ?? [])}>Emetti comunque</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );

  // Solo l'errore della data blocca il pulsante: altri errori (es. CF mancante) restano ritentabili
  return { campoData, conferma, emetti, verificando, dataNonValida: !data || !!controllo?.erroreData };
}
