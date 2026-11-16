/** Oggetto di ripiego dell'email documenti, se il tenant non ne ha configurato uno. */
export const EMAIL_OGGETTO_DEFAULT = 'Documenti fiscali soggiorno';

/** Oggetto iniziale dell'email documenti: impostazione del tenant, altrimenti il default. */
export const oggettoEmailIniziale = (s?: { emailOggettoPredefinito?: string | null } | null): string =>
  s?.emailOggettoPredefinito?.trim() || EMAIL_OGGETTO_DEFAULT;

/** Data e ora dell'ultimo invio email di un documento, es. "16/11/2026 12:30". */
export const dataInvioEmail = (at: string): string =>
  new Date(at).toLocaleString('it-IT', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });

/** "Inviata a {destinatari} il {data e ora}": invio accettato dal server SMTP, non la consegna. */
export const testoEmailInviata = (at: string, a?: string | null): string =>
  `Inviata a ${a ?? '—'} il ${dataInvioEmail(at)}`;
