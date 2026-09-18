import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import PasswordInput from '@/components/PasswordInput';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { AlertCircle, KeyRound, Loader2, LogOut } from 'lucide-react';
import appLogoIcon from '@/assets/logo-icon.png';
import loginBg from '@/assets/login-bg.jpg';
import { forceChangePassword } from '@/api/authApi';
import { useAuth } from '@/contexts/AuthContext';
import { getConfig } from '@/config/AppConfig';
import { toast } from '@/hooks/use-toast';
import { MIN_PASSWORD_LENGTH } from '@/lib/passwordUtils';
import type { UserRole } from '@/types';

// Stesse rotte iniziali di App.tsx: dopo il cambio si entra nella home del proprio ruolo.
const HOME_BY_ROLE: Record<UserRole, string> = {
  super_admin: '/admin',
  tenant_admin: '/dashboard',
  pm_user: '/dashboard',
  owner_user: '/owner',
};

/**
 * Cambio password obbligatorio al primo accesso. Pagina a tutto schermo e non dialog:
 * finché il flag è attivo il backend respinge ogni altra API, quindi non c'è nulla
 * sotto con cui interagire.
 */
const ChangePassword = () => {
  const navigate = useNavigate();
  const { user, refreshUser, logout } = useAuth();

  const [newPassword, setNewPassword] = useState('');
  const [confirmPwd, setConfirmPwd] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // In locale i dati sono fittizi e la regola intralcerebbe solo lo sviluppo,
  // come già per la validazione IBAN in OwnerDetail. Il vincolo vero resta comunque
  // quello del backend, che applica le stesse regole.
  const isLocal = getConfig().environment === 'local';

  const validate = (): string => {
    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      return `La password deve essere di almeno ${MIN_PASSWORD_LENGTH} caratteri`;
    }
    if (!isLocal && !(/[A-Z]/.test(newPassword) && /[a-z]/.test(newPassword) && /\d/.test(newPassword))) {
      return 'La password deve contenere almeno una maiuscola, una minuscola e una cifra';
    }
    if (newPassword !== confirmPwd) {
      return 'Le due password non coincidono';
    }
    return '';
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const errore = validate();
    setError(errore);
    if (errore) return;

    setLoading(true);
    try {
      await forceChangePassword(newPassword);
      // Rilegge /auth/me: il flag torna false e ProtectedRoute smette di reindirizzare qui.
      const aggiornato = await refreshUser();
      toast({ title: 'Password aggiornata', description: 'Da ora accedi con la nuova password.' });
      navigate(HOME_BY_ROLE[aggiornato?.role ?? user?.role ?? 'tenant_admin'] ?? '/dashboard', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Impossibile aggiornare la password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      className="min-h-screen flex items-center justify-center p-4 pb-24 relative"
      style={{ backgroundImage: `url(${loginBg})`, backgroundSize: 'cover', backgroundPosition: 'center' }}
    >
      <div className="absolute inset-0 bg-black/40 backdrop-blur-[2px]" />
      <div className="w-full max-w-md space-y-8 relative z-10">
        <div className="flex flex-col items-center gap-2">
          <img src={appLogoIcon} alt="Sostituto in Cloud" className="h-40 object-contain" />
          <h1 className="text-2xl font-bold text-white">Sostituto in Cloud</h1>
          <p className="text-sm text-white/70">PMS Fiscale Multi-Tenant</p>
        </div>

        <Card>
          <CardHeader className="pt-4 pb-2">
            <CardTitle className="flex items-center gap-2 text-lg">
              <KeyRound className="h-4 w-4" /> Cambio password obbligatorio
            </CardTitle>
            <CardDescription>Per continuare devi impostare una nuova password.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="new-password">Nuova password</Label>
                <PasswordInput
                  id="new-password"
                  value={newPassword}
                  onChange={e => setNewPassword(e.target.value)}
                  placeholder="••••••••"
                  disabled={loading}
                  autoFocus
                />
                <p className="text-[11px] text-muted-foreground">
                  Almeno {MIN_PASSWORD_LENGTH} caratteri
                  {!isLocal && ', con una maiuscola, una minuscola e una cifra'}.
                </p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="confirm-password">Conferma nuova password</Label>
                <PasswordInput
                  id="confirm-password"
                  value={confirmPwd}
                  onChange={e => setConfirmPwd(e.target.value)}
                  placeholder="••••••••"
                  disabled={loading}
                />
              </div>

              {error && (
                <div className="flex items-center gap-2 text-destructive text-sm">
                  <AlertCircle className="h-4 w-4 shrink-0" />
                  <span>{error}</span>
                </div>
              )}

              <Button type="submit" className="w-full" disabled={loading}>
                {loading ? (
                  <><Loader2 className="h-4 w-4 mr-2 animate-spin" />Aggiornamento…</>
                ) : (
                  'Imposta password e continua'
                )}
              </Button>
              <button
                type="button"
                onClick={() => { logout(); navigate('/login', { replace: true }); }}
                className="flex items-center justify-center gap-1 w-full text-xs text-muted-foreground hover:text-foreground"
              >
                <LogOut className="h-3 w-3" />Esci
              </button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default ChangePassword;
