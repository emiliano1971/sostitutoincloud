import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import type { UserContext, UserRole } from '@/types';
import { get, setCredentials, clearCredentials } from '@/lib/apiClient';

interface UserMeResponse {
  id: number;
  email: string;
  ruolo: string;
  fkTenantId: number;
  firstName?: string;
  lastName?: string;
  attivo: boolean;
}

interface AuthContextType {
  user: UserContext | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  switchRole: (role: UserRole) => void;
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
  };
}

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserContext | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  // On mount: if sessionStorage has email (stale from previous session) → clear it
  useEffect(() => {
    const savedEmail = sessionStorage.getItem('auth_email');
    if (savedEmail) {
      // Basic Auth doesn't persist credentials — force re-login on refresh
      sessionStorage.removeItem('auth_email');
    }
  }, []);

  const login = useCallback(async (email: string, password: string): Promise<void> => {
    setIsLoading(true);
    try {
      setCredentials(email, password);
      const me = await get<UserMeResponse>('/auth/me');
      setUser(mapToUserContext(me));
      sessionStorage.setItem('auth_email', email);
    } catch (err) {
      clearCredentials();
      const message = err instanceof Error && err.message === 'UNAUTHORIZED'
        ? 'Credenziali non valide'
        : 'Errore di connessione al server';
      throw new Error(message);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const logout = useCallback(() => {
    clearCredentials();
    sessionStorage.removeItem('auth_email');
    setUser(null);
  }, []);

  // No-op: role switching not supported with real auth
  const switchRole = useCallback((_role: UserRole) => {}, []);

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, isLoading, login, logout, switchRole }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
};
