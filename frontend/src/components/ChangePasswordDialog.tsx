import { useState } from 'react';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import PasswordInput from '@/components/PasswordInput';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { AlertCircle, Check, Loader2, X } from 'lucide-react';
import { changePassword } from '@/api/authApi';
import { getConfig } from '@/config/AppConfig';
import { toast } from '@/hooks/use-toast';
import { MIN_PASSWORD_LENGTH, checkPassword } from '@/lib/passwordUtils';

interface ChangePasswordDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/** Riga del banner requisiti: grigia a campo vuoto, poi verde se soddisfatta e rossa se no. */
function Requisito({ ok, vuoto, testo }: { ok: boolean; vuoto: boolean; testo: string }) {
  const colore = vuoto ? 'text-muted-foreground' : ok ? 'text-green-600' : 'text-destructive';
  return (
    <li className={`flex items-center gap-2 ${colore}`}>
      {!vuoto && ok
        ? <Check className="h-3.5 w-3.5 shrink-0" aria-hidden />
        : <X className="h-3.5 w-3.5 shrink-0" aria-hidden />}
      <span>{testo}</span>
    </li>
  );
}

export function ChangePasswordDialog({ open, onOpenChange }: ChangePasswordDialogProps) {
  // getConfig() va letta a render e non a livello di modulo: main.tsx importa App.tsx
  // staticamente, quindi i moduli sono valutati prima che loadConfig() risolva e a
  // livello di modulo getConfig() lancerebbe 'Config non ancora caricata'.
  const isLocal = getConfig().environment === 'local';

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPwd, setConfirmPwd] = useState('');
  const [newPwdFocused, setNewPwdFocused] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const strength = checkPassword(newPassword, isLocal);
  const passwordsMatch = newPassword === confirmPwd;
  const campoVuoto = newPassword.length === 0;
  // A campi entrambi vuoti il confronto sarebbe vero: il length > 0 evita di mostrare
  // l'avviso prima ancora che l'utente digiti.
  const stessaPassword = newPassword.length > 0 && newPassword === currentPassword;
  // Il banner compare solo fuori dal locale, e solo con campo attivo o già compilato.
  const mostraRequisiti = !isLocal && (newPwdFocused || !campoVuoto);

  // Il pulsante è disabilitato in questo caso, quindi handleSubmit non scatterebbe mai:
  // l'avviso va derivato dallo stato o resterebbe invisibile.
  const messaggioErrore = error
    || (stessaPassword ? 'La nuova password non può essere uguale a quella attuale' : '');

  const reset = () => {
    setCurrentPassword(''); setNewPassword(''); setConfirmPwd('');
    setNewPwdFocused(false); setError('');
  };

  const handleOpenChange = (next: boolean) => {
    if (!next) reset();
    onOpenChange(next);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!strength.isValid) {
      setError(isLocal
        ? `La nuova password deve essere di almeno ${MIN_PASSWORD_LENGTH} caratteri`
        : 'La nuova password non rispetta i requisiti indicati');
      return;
    }
    if (!passwordsMatch) {
      setError('Le due nuove password non coincidono');
      return;
    }
    if (newPassword === currentPassword) {
      setError('La nuova password non può essere uguale a quella attuale');
      return;
    }

    setLoading(true);
    try {
      await changePassword(currentPassword, newPassword);
      toast({ title: 'Password aggiornata' });
      reset();
      onOpenChange(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Errore durante il cambio password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[420px]">
        <DialogHeader>
          <DialogTitle>Cambia password</DialogTitle>
          <DialogDescription>
            {isLocal
              ? `Inserisci la password attuale e scegline una nuova di almeno ${MIN_PASSWORD_LENGTH} caratteri.`
              : 'Inserisci la password attuale e scegline una nuova che rispetti i requisiti indicati.'}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="cp-current">Password corrente</Label>
            <PasswordInput
              id="cp-current"
              value={currentPassword}
              onChange={e => setCurrentPassword(e.target.value)}
              placeholder="••••••••"
              disabled={loading}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="cp-new">Nuova password</Label>
            <PasswordInput
              id="cp-new"
              value={newPassword}
              onChange={e => setNewPassword(e.target.value)}
              onFocus={() => setNewPwdFocused(true)}
              onBlur={() => setNewPwdFocused(false)}
              placeholder="••••••••"
              disabled={loading}
            />

            {mostraRequisiti && (
              <div className="rounded-md border bg-muted/40 p-3 text-xs">
                <p className="mb-2 font-medium text-foreground">La password deve contenere:</p>
                <ul className="space-y-1">
                  <Requisito vuoto={campoVuoto} ok={strength.hasMinLength}
                             testo={`Almeno ${MIN_PASSWORD_LENGTH} caratteri`} />
                  <Requisito vuoto={campoVuoto} ok={strength.hasUppercase}
                             testo="Una lettera maiuscola" />
                  <Requisito vuoto={campoVuoto} ok={strength.hasLowercase}
                             testo="Una lettera minuscola" />
                  <Requisito vuoto={campoVuoto} ok={strength.hasNumber}
                             testo="Un numero" />
                  <Requisito vuoto={campoVuoto} ok={strength.hasSpecial}
                             testo="Un carattere speciale" />
                </ul>
              </div>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="cp-confirm">Conferma nuova password</Label>
            <PasswordInput
              id="cp-confirm"
              value={confirmPwd}
              onChange={e => setConfirmPwd(e.target.value)}
              placeholder="••••••••"
              disabled={loading}
            />
          </div>

          {messaggioErrore && (
            <div className="flex items-center gap-2 text-destructive text-sm">
              <AlertCircle className="h-4 w-4 shrink-0" />
              <span>{messaggioErrore}</span>
            </div>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)} disabled={loading}>
              Annulla
            </Button>
            <Button
              type="submit"
              disabled={loading || !currentPassword || !strength.isValid || !passwordsMatch || stessaPassword}
            >
              {loading ? (
                <><Loader2 className="h-4 w-4 mr-2 animate-spin" />Aggiornamento…</>
              ) : (
                'Aggiorna password'
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
