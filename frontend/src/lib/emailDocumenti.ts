/** Oggetto di ripiego dell'email documenti, se il tenant non ne ha configurato uno. */
export const EMAIL_OGGETTO_DEFAULT = 'Documenti fiscali soggiorno';

/** Oggetto iniziale dell'email documenti: impostazione del tenant, altrimenti il default. */
export const oggettoEmailIniziale = (s?: { emailOggettoPredefinito?: string | null } | null): string =>
  s?.emailOggettoPredefinito?.trim() || EMAIL_OGGETTO_DEFAULT;
