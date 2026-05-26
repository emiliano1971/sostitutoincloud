/* v2 */ import React, { createContext, useContext, useState, useCallback } from 'react';
import type { UserContext, UserRole } from '@/types';
import { mockUsers } from '@/data/mock-data';

interface AuthContextType {
  user: UserContext | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => boolean;
  logout: () => void;
  switchRole: (role: UserRole) => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserContext | null>(null);

  const login = useCallback((email: string, _password: string) => {
    if (_password !== 'atene') return false;
    const found = mockUsers.find(u => u.email === email);
    if (found) {
      setUser(found);
      return true;
    }
    setUser(mockUsers[1]);
    return true;
  }, []);

  const logout = useCallback(() => setUser(null), []);

  const switchRole = useCallback((role: UserRole) => {
    const found = mockUsers.find(u => u.role === role);
    if (found) setUser(found);
  }, []);

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, login, logout, switchRole }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
};
