import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import type { UserContext, UserRole } from '@/types';
import { get, post, setToken, getToken, clearToken } from '@/lib/apiClient';

interface UserMeResponse {
  id: number;
  email: string;
  ruolo: string;
  fkTenantId: number;
  fkOwnerId?: number;
  firstName?: string;
  lastName?: string;
  attivo: boolean;
  mustChangePassword?: boolean;
}

interface LoginResponse {
  token: string;
  user: UserMeResponse;
  mustChangePassword?: boolean;
}

interface AuthContextType {
  user: UserContext | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  /** Restituisce l'utente autenticato: serve al chiamante per il redirect per ruolo,
   *  che altrimenti dovrebbe attendere il re-render con lo stato aggiornato. */
  login: (email: string, password: string) => Promise<UserContext>;
  logout: () => void;
  /** Ricarica l'utente da /auth/me. Serve dopo il cambio password forzato per
   *  azzerare mustChangePassword senza costringere a un nuovo login. */
  refreshUser: () => Promise<UserContext | null>;
}

const AuthContext = createContext<AuthContextType | null>(null);

function mapToUserContext(me: UserMeResponse): UserContext {
  return {
    user_id: String(me.id),
    email: me.email,
    first_name: me.firstName ?? '',
    last_name: me.lastName ?? '',
    role: me.ruolo as UserRole,
    tenant_id: me.fkTenantId ? String(me.fkTenantId) : undefined,
    owner_id: me.fkOwnerId ? String(me.fkOwnerId) : undefined,
    mustChangePassword: me.mustChangePassword === true,
  };
}

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserContext | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // On mount: if there's a stored token, verify it and restore session
  useEffect(() => {
    const token = getToken();
    if (!token) {
      setIsLoading(false);
      return;
    }
    get<UserMeResponse>('/auth/me')
      .then(me => setUser(mapToUserContext(me)))
      .catch(() => clearToken())
      .finally(() => setIsLoading(false));
  }, []);

  const login = useCallback(async (email: string, password: string): Promise<UserContext> => {
    setIsLoading(true);
    try {
      const response = await post<LoginResponse>('/public/login', { email, password });
      setToken(response.token);
      // Il flag arriva sia a livello di risposta sia dentro user: basta che uno dei due
      // sia true. Lo stato viene salvato, ma la navigazione la decide il chiamante
      // (Login) e comunque ProtectedRoute impedisce di uscire da /change-password.
      const utente = {
        ...mapToUserContext(response.user),
        mustChangePassword: response.mustChangePassword === true || response.user.mustChangePassword === true,
      };
      setUser(utente);
      return utente;
    } catch (err) {
      clearToken();
      // Propaga il messaggio reale dal server
      const message = err instanceof Error ? err.message : 'Errore durante il login';
      throw new Error(message);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const logout = useCallback(async () => {
    // Il JWT è stateless: la chiamata serve solo a registrare l'uscita nell'audit.
    // Un errore non deve impedire il logout lato client, quindi viene ignorato.
    await post('/auth/logout', {}).catch(() => {});
    clearToken();
    setUser(null);
  }, []);

  const refreshUser = useCallback(async (): Promise<UserContext | null> => {
    try {
      const me = await get<UserMeResponse>('/auth/me');
      const utente = mapToUserContext(me);
      setUser(utente);
      return utente;
    } catch {
      return null;
    }
  }, []);

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, isLoading, login, logout, refreshUser }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
};
