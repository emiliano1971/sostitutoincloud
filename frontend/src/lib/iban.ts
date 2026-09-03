/**
 * Validazione IBAN con algoritmo MOD-97 (ISO 7064, standard ISO 13616).
 *
 * Il campo IBAN è opzionale in tutto il gestionale: una stringa vuota è quindi
 * considerata valida — è il chiamante a decidere se il campo è obbligatorio.
 */
export function validateIban(iban: string): boolean {
  if (!iban) return true; // opzionale

  // Rimuovi spazi e converti in maiuscolo
  const cleaned = iban.replace(/\s/g, '').toUpperCase();

  // Lunghezza: 15 (Norvegia) — 34 caratteri
  if (cleaned.length < 15 || cleaned.length > 34) return false;

  // Deve iniziare con 2 lettere (paese) + 2 cifre (check digit)
  if (!/^[A-Z]{2}[0-9]{2}/.test(cleaned)) return false;

  // Solo caratteri alfanumerici: il controllo non è ridondante rispetto a quello
  // sopra, che guarda i primi 4 caratteri. Senza di esso un IBAN incollato con
  // separatori (es. IT60-X054-…) arriverebbe a BigInt() con caratteri non
  // numerici e lancerebbe SyntaxError invece di restituire false.
  if (!/^[A-Z0-9]+$/.test(cleaned)) return false;

  // Sposta i primi 4 caratteri in fondo
  const rearranged = cleaned.slice(4) + cleaned.slice(0, 4);

  // Sostituisci le lettere con numeri (A=10, B=11, …, Z=35)
  const numeric = rearranged
    .split('')
    .map(c => {
      const code = c.charCodeAt(0);
      return code >= 65 && code <= 90 ? (code - 55).toString() : c;
    })
    .join('');

  // MOD 97: BigInt necessario, il numero eccede Number.MAX_SAFE_INTEGER
  return BigInt(numeric) % 97n === 1n;
}
