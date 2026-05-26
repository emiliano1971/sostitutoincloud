import { useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { AlertCircle, Lock, UserCheck } from 'lucide-react';
import appLogoIcon from '@/assets/logo-icon.png';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import loginBg from '@/assets/login-bg.jpg';
import { toast } from '@/hooks/use-toast';
import { mockBookings } from '@/data/mock-data';

const GATE_PASSWORD = 'atena';

const Login = () => {
  const [gateUnlocked, setGateUnlocked] = useState(() => sessionStorage.getItem('gate_unlocked') === 'true');
  const [gatePassword, setGatePassword] = useState('');
  const [gateError, setGateError] = useState('');

  const [email, setEmail] = useState('admin@casavacanze.it');
  const [password, setPassword] = useState('atene');
  const [error, setError] = useState('');
  const { login } = useAuth();
  const navigate = useNavigate();

  // Guest access state
  const [guestCheckin, setGuestCheckin] = useState('');
  const [guestCheckout, setGuestCheckout] = useState('');
  const [guestTaxCode, setGuestTaxCode] = useState('');

  const handleGate = (e: React.FormEvent) => {
    e.preventDefault();
    if (gatePassword === GATE_PASSWORD) {
      sessionStorage.setItem('gate_unlocked', 'true');
      setGateUnlocked(true);
      setGateError('');
    } else {
      setGateError('Password di accesso non valida.');
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    const ok = login(email, password);
    if (ok) {
      toast({ title: 'Accesso effettuato', description: 'Benvenuto in Sostituto in Cloud' });
      const roleRoutes: Record<string, string> = {
        super_admin: '/admin',
        tenant_admin: '/dashboard',
        pm_user: '/dashboard',
        owner_user: '/owner',
      };
      const role = email === 'superadmin@sostitutoincloud.it' ? 'super_admin'
        : email === 'proprietario@email.it' ? 'owner_user'
        : email === 'pm@casavacanze.it' ? 'pm_user'
        : 'tenant_admin';
      navigate(roleRoutes[role] || '/dashboard');
    } else {
      setError('Credenziali non valide. Usa una delle email demo con password "atene".');
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

  if (!gateUnlocked) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4 relative" style={{ backgroundImage: `url(${loginBg})`, backgroundSize: 'cover', backgroundPosition: 'center' }}>
        <div className="absolute inset-0 bg-black/40 backdrop-blur-[2px]" />
        <div className="w-full max-w-sm space-y-6 relative z-10">
          <div className="flex flex-col items-center gap-2">
            <div className="h-14 w-14 rounded-2xl bg-primary flex items-center justify-center">
              <Lock className="h-8 w-8 text-primary-foreground" />
            </div>
            <h1 className="text-2xl font-bold text-white">Area Riservata</h1>
            <p className="text-sm text-white/70 text-center">Inserisci la password per accedere alla piattaforma</p>
          </div>

          <Card>
            <CardContent className="pt-6">
              <form onSubmit={handleGate} className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="gate-password">Password</Label>
                  <Input
                    id="gate-password"
                    type="password"
                    value={gatePassword}
                    onChange={e => setGatePassword(e.target.value)}
                    placeholder="Inserisci la password"
                    autoFocus
                  />
                </div>

                {gateError && (
                  <div className="flex items-center gap-2 text-destructive text-sm">
                    <AlertCircle className="h-4 w-4 shrink-0" />
                    <span>{gateError}</span>
                  </div>
                )}

                <Button type="submit" className="w-full">Entra</Button>
              </form>
            </CardContent>
          </Card>
        </div>
      </div>
    );
  }

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
                    <Input id="email" type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="email@esempio.it" />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="password">Password</Label>
                    <Input id="password" type="password" value={password} onChange={e => setPassword(e.target.value)} />
                  </div>

                  {error && (
                    <div className="flex items-center gap-2 text-destructive text-sm">
                      <AlertCircle className="h-4 w-4 shrink-0" />
                      <span>{error}</span>
                    </div>
                  )}

                  <Button type="submit" className="w-full">Accedi</Button>
                </form>

                {/* Demo accounts */}
                <div className="mt-6 pt-4 border-t">
                  <p className="text-xs text-muted-foreground mb-3 font-medium">Account demo disponibili:</p>
                  <div className="space-y-1.5">
                    {[
                      { email: 'superadmin@sostitutoincloud.it', role: 'Super Admin' },
                      { email: 'admin@casavacanze.it', role: 'Tenant Admin' },
                      { email: 'pm@casavacanze.it', role: 'PM User' },
                      { email: 'proprietario@email.it', role: 'Proprietario' },
                    ].map(acc => (
                      <button
                        key={acc.email}
                        type="button"
                        onClick={() => { setEmail(acc.email); setPassword('atene'); }}
                        className="w-full flex items-center justify-between p-2 rounded-md text-xs hover:bg-muted transition-colors"
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
                    <Input id="guest-cf" type="text" value={guestTaxCode} onChange={e => setGuestTaxCode(e.target.value.toUpperCase())} placeholder="Es. RSSMRA85A01H501Z" maxLength={16} className="uppercase" />
                  </div>
                  <Button type="submit" className="w-full gap-2">
                    <UserCheck className="h-4 w-4" />
                    Visualizza Documenti
                  </Button>
                </form>

                {/* Demo hint */}
                <div className="mt-6 pt-4 border-t">
                  <p className="text-xs text-muted-foreground mb-2 font-medium">Dati demo per provare:</p>
                  {(() => {
                    const demoBooking = mockBookings.find(b => b.guest_tax_code === 'SMTJHN85A01H501X');
                    if (!demoBooking) return null;
                    return (
                      <button
                        type="button"
                        onClick={() => {
                          setGuestCheckin(demoBooking.checkin_date);
                          setGuestCheckout(demoBooking.checkout_date);
                          setGuestTaxCode(demoBooking.guest_tax_code);
                        }}
                        className="w-full flex flex-col gap-1 p-2 rounded-md text-xs hover:bg-muted transition-colors text-left"
                      >
                        <div className="flex justify-between w-full">
                          <span className="text-muted-foreground">{demoBooking.guest_name}</span>
                          <span className="text-primary font-medium">CF: {demoBooking.guest_tax_code}</span>
                        </div>
                        <span className="text-muted-foreground">Check-in: {demoBooking.checkin_date} · Check-out: {demoBooking.checkout_date}</span>
                      </button>
                    );
                  })()}
                </div>
              </CardContent>
            </TabsContent>
          </Tabs>
        </Card>
      </div>
    </div>
  );
};

export default Login;
