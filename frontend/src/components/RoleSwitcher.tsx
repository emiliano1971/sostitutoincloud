import { useAuth } from '@/contexts/AuthContext';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Shield, Building2, User, Home } from 'lucide-react';
import type { UserRole } from '@/types';

const roles: { value: UserRole; label: string; icon: React.ReactNode }[] = [
  { value: 'super_admin', label: 'Super Admin', icon: <Shield className="h-3.5 w-3.5" /> },
  { value: 'tenant_admin', label: 'Tenant Admin', icon: <Building2 className="h-3.5 w-3.5" /> },
  { value: 'pm_user', label: 'PM User', icon: <User className="h-3.5 w-3.5" /> },
  { value: 'owner_user', label: 'Proprietario', icon: <Home className="h-3.5 w-3.5" /> },
];

export function RoleSwitcher() {
  const { user, switchRole } = useAuth();
  if (!user) return null;

  return (
    <Select value={user.role} onValueChange={(v) => switchRole(v as UserRole)}>
      <SelectTrigger className="w-[170px] h-8 text-xs bg-sidebar-accent border-sidebar-border text-sidebar-foreground">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {roles.map(r => (
          <SelectItem key={r.value} value={r.value}>
            <span className="flex items-center gap-2">{r.icon} {r.label}</span>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
