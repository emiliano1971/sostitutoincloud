import { useEffect, useState } from 'react';
import { getSettings } from '@/api/settingsApi';

/** Testo del tooltip sulle azioni SDI quando la trasmissione è disabilitata. */
export const MSG_SDI_DISABILITATO = 'Invio SDI disabilitato nelle impostazioni';

/**
 * Flag tenant_settings.sdiAbilitato per abilitare/disabilitare i pulsanti di trasmissione SDI.
 * null mentre carica. Se la lettura fallisce vale true: la regola vera è nel backend, che
 * risponde comunque 422 con l'invio disabilitato.
 */
export function useSdiAbilitato(): boolean | null {
  const [abilitato, setAbilitato] = useState<boolean | null>(null);
  useEffect(() => {
    let attivo = true;
    getSettings()
      .then(s => { if (attivo) setAbilitato(s.sdiAbilitato === true); })
      .catch(() => { if (attivo) setAbilitato(true); });
    return () => { attivo = false; };
  }, []);
  return abilitato;
}
