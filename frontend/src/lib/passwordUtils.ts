/**
 * Regole di robustezza della password, condivise da tutti i form che ne impostano una.
 * Rispecchiano PasswordResetService.validateNewPassword() lato backend, che resta il
 * vincolo vero: qui si evita solo un giro di rete per un errore prevedibile.
 *
 * NB non vale per la creazione di un utente: lì la password è temporanea e
 * must_change_password forza il cambio al primo accesso, quindi basta la lunghezza.
 */
export const MIN_PASSWORD_LENGTH = 8;

const SPECIAL_CHARS = /[!@#$%^&*()_+\-=[\]{}|;:,.<>?]/;

export interface PasswordStrength {
  hasMinLength: boolean;
  hasUppercase: boolean;
  hasLowercase: boolean;
  hasNumber: boolean;
  hasSpecial: boolean;
  isValid: boolean;
}

/**
 * In locale vale solo la lunghezza minima, così le password di test restano semplici;
 * negli altri ambienti servono tutti i requisiti.
 */
export const checkPassword = (pwd: string, isLocal: boolean): PasswordStrength => {
  const hasMinLength = pwd.length >= MIN_PASSWORD_LENGTH;
  const hasUppercase = /[A-Z]/.test(pwd);
  const hasLowercase = /[a-z]/.test(pwd);
  const hasNumber = /[0-9]/.test(pwd);
  const hasSpecial = SPECIAL_CHARS.test(pwd);
  return {
    hasMinLength,
    hasUppercase,
    hasLowercase,
    hasNumber,
    hasSpecial,
    isValid: hasMinLength
      && (isLocal || (hasUppercase && hasLowercase && hasNumber && hasSpecial)),
  };
};
