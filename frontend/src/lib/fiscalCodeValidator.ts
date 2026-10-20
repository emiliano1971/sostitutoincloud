// Validazione formale di codice fiscale e partita IVA, allineata a
// CodiceFiscalePivaValidator del backend (solo formato, non il carattere di controllo).

/** CF persona fisica: 16 caratteri alfanumerici. */
export const isCF = (v: string) => /^[A-Z0-9]{16}$/i.test(v.trim());

/** P.IVA: 11 cifre, senza prefisso IT. Società e ditte hanno CF numerico = P.IVA. */
export const isPIVA = (v: string) => /^\d{11}$/.test(v.trim());

/** CF a 16 caratteri oppure CF numerico a 11 cifre. */
export const isValidCFOrPIVA = (v: string) => isCF(v) || isPIVA(v);

export const normalizzaCF = (v: string) => v.trim().toUpperCase();
