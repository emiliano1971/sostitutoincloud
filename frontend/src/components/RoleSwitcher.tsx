import { useAuth } from '@/contexts/AuthContext';
import { Shield, Building2, User, Home } from 'lucide-react';
import type { UserRole } from '@/types';

/**
 * Etichette leggibili dei ruoli — sola visualizzazione.
 * Il cambio ruolo non esiste: il ruolo arriva dal token e cambiarlo lato client
 * non avrebbe alcun effetto sulle autorizzazioni del backend.
 */
const roleLabels: Record<UserRole, { label: string; icon: React.ReactNode }> = {
  super_admin:  { label: 'Super Admin',  icon: <Shield className="h-3.5 w-3.5" /> },
  tenant_admin: { label: 'Admin',        icon: <Building2 className="h-3.5 w-3.5" /> },
  pm_user:      { label: 'PM',           icon: <User className="h-3.5 w-3.5" /> },
  owner_user:   { label: 'Proprietario', icon: <Home className="h-3.5 w-3.5" /> },
};

/** Ruolo dell'utente loggato. Nome ed email stanno nel blocco sotto, in AppSidebar. */
export function RoleSwitcher() {
  const { user } = useAuth();
  if (!user) return null;

  const ruolo = roleLabels[user.role];

  return (
    <div className="flex items-center gap-2 px-2 text-xs text-sidebar-foreground">
      {ruolo.icon}
      <span className="truncate">{ruolo.label}</span>
    </div>
  );
}
