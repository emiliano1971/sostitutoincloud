import { post } from '@/lib/apiClient';

export interface MessageResponse {
  message: string;
}

/**
 * Richiesta di reset password. Risponde sempre 200 con lo stesso messaggio,
 * anche se l'email non è registrata: non è possibile dedurre quali email esistono.
 */
export async function requestPasswordReset(email: string): Promise<MessageResponse> {
  return post<MessageResponse>('/public/password-reset/request', { email });
}

/** Conferma il reset con il token ricevuto per email. 400 se token non valido o scaduto. */
export async function confirmPasswordReset(token: string, newPassword: string): Promise<MessageResponse> {
  return post<MessageResponse>('/public/password-reset/confirm', { token, newPassword });
}

/** Cambio password per l'utente autenticato. 400 se la password corrente non è corretta. */
export async function changePassword(currentPassword: string, newPassword: string): Promise<MessageResponse> {
  return post<MessageResponse>('/auth/change-password', { currentPassword, newPassword });
}

/**
 * Cambio password al primo accesso: non serve quella corrente perché l'utente non
 * conosce la temporanea assegnata dall'amministratore. 400 se l'utente non ha il
 * flag must_change_password attivo o se la nuova password non rispetta i requisiti.
 */
export async function forceChangePassword(newPassword: string): Promise<MessageResponse> {
  return post<MessageResponse>('/auth/force-change-password', { newPassword });
}
