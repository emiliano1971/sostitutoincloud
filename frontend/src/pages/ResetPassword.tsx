import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import PasswordInput from '@/components/PasswordInput';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { AlertCircle, ArrowLeft, Loader2 } from 'lucide-react';
import appLogoIcon from '@/assets/logo-icon.png';
import loginBg from '@/assets/login-bg.jpg';
import { confirmPasswordReset } from '@/api/authApi';
import { toast } from '@/hooks/use-toast';
import { MIN_PASSWORD_LENGTH } from '@/lib/passwordUtils';

const ResetPassword = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token') ?? '';

  const [newPassword, setNewPassword] = useState('');
  const [confirmPwd, setConfirmPwd] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // Senza token la pagina non ha senso: si torna al login.
  useEffect(() => {
    if (!token) navigate('/login', { replace: true });
  }, [token, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      setError(`La password deve essere di almeno ${MIN_PASSWORD_LENGTH} caratteri`);
      return;
    }
    if (newPassword !== confirmPwd) {
      setError('Le due password non coincidono');
      return;
    }

    setLoading(true);
    try {
      await confirmPasswordReset(token, newPassword);
      toast({ title: 'Password aggiornata', description: 'Effettua il login con la nuova password' });
      navigate('/login', { replace: true });
    } catch (err) {
      const msg = err instanceof Error ? err.message : '';
      setError(msg || 'Token non valido o scaduto — richiedere un nuovo reset');
    } finally {
      setLoading(false);
    }
  };

  if (!token) return null;

  return (
    <div className="min-h-screen flex items-center justify-center p-4 pb-24 relative" style={{ backgroundImage: `url(${loginBg})`, backgroundSize: 'cover', backgroundPosition: 'center' }}>
      <div className="absolute inset-0 bg-black/40 backdrop-blur-[2px]" />
      <div className="w-full max-w-md space-y-8 relative z-10">
        <div className="flex flex-col items-center gap-2">
          <img src={appLogoIcon} alt="Sostituto in Cloud" className="h-40 object-contain" />
          <h1 className="text-2xl font-bold text-white">Sostituto in Cloud</h1>
          <p className="text-sm text-white/70">PMS Fiscale Multi-Tenant</p>
        </div>

        <Card>
          <CardHeader className="pt-4 pb-2">
            <CardTitle className="text-lg">Nuova password</CardTitle>
            <CardDescription>Imposta una nuova password per il tuo account.</CardDescription>
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
                />
                <p className="text-[11px] text-muted-foreground">Almeno {MIN_PASSWORD_LENGTH} caratteri.</p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="confirm-password">Conferma password</Label>
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
                  'Imposta password'
                )}
              </Button>
              <Link to="/login" className="flex items-center justify-center gap-1 text-xs text-muted-foreground hover:text-foreground">
                <ArrowLeft className="h-3 w-3" />Torna al login
              </Link>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default ResetPassword;
