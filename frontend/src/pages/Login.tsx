import { useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { AlertCircle, Loader2, UserCheck } from 'lucide-react';
import appLogoIcon from '@/assets/logo-icon.png';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import loginBg from '@/assets/login-bg.jpg';
import { toast } from '@/hooks/use-toast';

const Login = () => {
  const [email, setEmail] = useState('admin@casavacanze.it');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const { login, isLoading } = useAuth();
  const navigate = useNavigate();

  // Guest access state
  const [guestCheckin, setGuestCheckin] = useState('');
  const [guestCheckout, setGuestCheckout] = useState('');
  const [guestTaxCode, setGuestTaxCode] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      await login(email, password);
      toast({ title: 'Accesso effettuato', description: 'Benvenuto in Sostituto in Cloud' });
      const roleRoutes: Record<string, string> = {
        super_admin: '/admin',
        tenant_admin: '/dashboard',
        pm_user: '/dashboard',
        owner_user: '/owner',
      };
      // Role is set after login — read from auth context via navigate
      // Use a small workaround: re-read the role from response is not needed
      // since AuthProvider sets user, App.tsx handles redirect on next render.
      // But we need to navigate immediately, so we use role from email heuristic
      // that will be overridden by the real user.role once re-render occurs.
      navigate(
        email === 'proprietario@email.it' ? '/owner' : '/dashboard',
        { replace: true }
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Errore durante il login');
    }
  };

  const handleGuestAccess = (e: React.FormEvent) => {
    e.preventDefault();
    if (!guestCheckin || !guestCheckout || !guestTaxCode) {
      toast({ title: 'Dati mancanti', description: 'Compila tutti i campi per accedere ai documenti.', variant: 'destructive' });
      return;
    }
    navigate(`/guest/documents?checkin=${guestCheckin}&checkout=${guestCheckout}&cf=${encodeURIComponent(guestTaxCode)}`);
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 relative" style={{ backgroundImage: `url(${loginBg})`, backgroundSize: 'cover', backgroundPosition: 'center' }}>
      <div className="absolute inset-0 bg-black/40 backdrop-blur-[2px]" />
      <div className="w-full max-w-md space-y-8 relative z-10">
        {/* Logo */}
        <div className="flex flex-col items-center gap-2">
          <img src={appLogoIcon} alt="Sostituto in Cloud" className="h-40 object-contain" />
          <h1 className="text-2xl font-bold text-white">Sostituto in Cloud</h1>
          <p className="text-sm text-white/70">PMS Fiscale Multi-Tenant</p>
        </div>

        <Card>
          <Tabs defaultValue="operatore">
            <TabsList className="w-full grid grid-cols-2">
              <TabsTrigger value="operatore">Operatore</TabsTrigger>
              <TabsTrigger value="ospite" className="gap-1.5"><UserCheck className="h-3.5 w-3.5" />Ospite</TabsTrigger>
            </TabsList>

            {/* Tab Operatore */}
            <TabsContent value="operatore">
              <CardHeader className="pt-4 pb-2">
                <CardTitle className="text-lg">Accedi</CardTitle>
                <CardDescription>Inserisci le tue credenziali per accedere</CardDescription>
              </CardHeader>
              <CardContent>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="space-y-2">
                    <Label htmlFor="email">Email</Label>
                    <Input
                      id="email"
                      type="email"
                      value={email}
                      onChange={e => setEmail(e.target.value)}
                      placeholder="email@esempio.it"
                      disabled={isLoading}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="password">Password</Label>
                    <Input
                      id="password"
                      type="password"
                      value={password}
                      onChange={e => setPassword(e.target.value)}
                      placeholder="••••••••"
                      disabled={isLoading}
                    />
                  </div>

                  {error && (
                    <div className="flex items-center gap-2 text-destructive text-sm">
                      <AlertCircle className="h-4 w-4 shrink-0" />
                      <span>{error}</span>
                    </div>
                  )}

                  <Button type="submit" className="w-full" disabled={isLoading}>
                    {isLoading ? (
                      <><Loader2 className="h-4 w-4 mr-2 animate-spin" />Accesso in corso…</>
                    ) : (
                      'Accedi'
                    )}
                  </Button>
                </form>

                {/* Account disponibili */}
                <div className="mt-6 pt-4 border-t">
                  <p className="text-xs text-muted-foreground mb-3 font-medium">Account disponibili:</p>
                  <div className="space-y-1.5">
                    {[
                      { email: 'admin@casavacanze.it', role: 'Tenant Admin' },
                      { email: 'proprietario@email.it', role: 'Proprietario' },
                    ].map(acc => (
                      <button
                        key={acc.email}
                        type="button"
                        onClick={() => { setEmail(acc.email); setPassword('atena'); }}
                        className="w-full flex items-center justify-between p-2 rounded-md text-xs hover:bg-muted transition-colors"
                        disabled={isLoading}
                      >
                        <span className="text-muted-foreground font-mono">{acc.email}</span>
                        <span className="text-primary font-medium">{acc.role}</span>
                      </button>
                    ))}
                  </div>
                </div>
              </CardContent>
            </TabsContent>

            {/* Tab Ospite */}
            <TabsContent value="ospite">
              <CardHeader className="pt-4 pb-2">
                <CardTitle className="text-lg">Accesso Ospite</CardTitle>
                <CardDescription>Consulta fattura e ricevuta del tuo soggiorno</CardDescription>
              </CardHeader>
              <CardContent>
                <form onSubmit={handleGuestAccess} className="space-y-4">
                  <div className="space-y-2">
                    <Label htmlFor="guest-checkin">Data Check-in</Label>
                    <Input id="guest-checkin" type="date" value={guestCheckin} onChange={e => setGuestCheckin(e.target.value)} />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="guest-checkout">Data Check-out</Label>
                    <Input id="guest-checkout" type="date" value={guestCheckout} onChange={e => setGuestCheckout(e.target.value)} />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="guest-cf">Codice Fiscale o Partita IVA</Label>
                    <Input
                      id="guest-cf"
                      type="text"
                      value={guestTaxCode}
                      onChange={e => setGuestTaxCode(e.target.value.toUpperCase())}
                      placeholder="Es. RSSMRA85A01H501Z"
                      maxLength={16}
                      className="uppercase"
                    />
                  </div>
                  <Button type="submit" className="w-full gap-2">
                    <UserCheck className="h-4 w-4" />
                    Visualizza Documenti
                  </Button>
                </form>
              </CardContent>
            </TabsContent>
          </Tabs>
        </Card>
      </div>
    </div>
  );
};

export default Login;
