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
