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
}

interface LoginResponse {
  token: string;
  user: UserMeResponse;
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
    owner_id: me.fkOwnerId ? String(me.fkOwnerId) : undefined,
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

  const login = useCallback(async (email: string, password: string): Promise<void> => {
    setIsLoading(true);
    try {
      const response = await post<LoginResponse>('/public/login', { email, password });
      setToken(response.token);
      setUser(mapToUserContext(response.user));
    } catch (err) {
      clearToken();
      // Propaga il messaggio reale dal server
      const message = err instanceof Error ? err.message : 'Errore durante il login';
      throw new Error(message);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const logout = useCallback(() => {
    clearToken();
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
