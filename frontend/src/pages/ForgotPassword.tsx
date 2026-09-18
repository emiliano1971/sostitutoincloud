import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { ArrowLeft, Loader2, MailCheck } from 'lucide-react';
import appLogoIcon from '@/assets/logo-icon.png';
import loginBg from '@/assets/login-bg.jpg';
import { requestPasswordReset } from '@/api/authApi';

const ForgotPassword = () => {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      await requestPasswordReset(email);
    } catch {
      // Anche in caso di errore mostriamo lo stesso messaggio: non deve essere
      // possibile dedurre dall'esito se l'email è registrata.
    } finally {
      setLoading(false);
      setSent(true);
    }
  };

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
            <CardTitle className="text-lg">Password dimenticata</CardTitle>
            <CardDescription>
              Inserisci la tua email: ti invieremo un link per impostare una nuova password.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {sent ? (
              <div className="space-y-4">
                <div className="flex items-start gap-2 rounded-md border bg-muted/40 p-3">
                  <MailCheck className="h-4 w-4 mt-0.5 shrink-0 text-primary" />
                  <p className="text-sm text-muted-foreground">
                    Se l'email è registrata riceverai le istruzioni per il reset.
                  </p>
                </div>
                <Button asChild variant="outline" className="w-full gap-2">
                  <Link to="/login"><ArrowLeft className="h-4 w-4" />Torna al login</Link>
                </Button>
              </div>
            ) : (
              <form onSubmit={handleSubmit} className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="email">Email</Label>
                  <Input
                    id="email"
                    type="email"
                    value={email}
                    onChange={e => setEmail(e.target.value)}
                    placeholder="email@esempio.it"
                    disabled={loading}
                    required
                  />
                </div>
                <Button type="submit" className="w-full" disabled={loading || !email}>
                  {loading ? (
                    <><Loader2 className="h-4 w-4 mr-2 animate-spin" />Invio in corso…</>
                  ) : (
                    'Invia istruzioni'
                  )}
                </Button>
                <Link to="/login" className="flex items-center justify-center gap-1 text-xs text-muted-foreground hover:text-foreground">
                  <ArrowLeft className="h-3 w-3" />Torna al login
                </Link>
              </form>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default ForgotPassword;
