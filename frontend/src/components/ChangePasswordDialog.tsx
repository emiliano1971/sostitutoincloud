import { useState } from 'react';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { AlertCircle, Loader2 } from 'lucide-react';
import { changePassword } from '@/api/authApi';
import { toast } from '@/hooks/use-toast';

const MIN_PASSWORD_LENGTH = 8;

interface ChangePasswordDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ChangePasswordDialog({ open, onOpenChange }: ChangePasswordDialogProps) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPwd, setConfirmPwd] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const reset = () => {
    setCurrentPassword(''); setNewPassword(''); setConfirmPwd(''); setError('');
  };

  const handleOpenChange = (next: boolean) => {
    if (!next) reset();
    onOpenChange(next);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      setError(`La nuova password deve essere di almeno ${MIN_PASSWORD_LENGTH} caratteri`);
      return;
    }
    if (newPassword !== confirmPwd) {
      setError('Le due nuove password non coincidono');
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
            Inserisci la password attuale e scegline una nuova di almeno {MIN_PASSWORD_LENGTH} caratteri.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="cp-current">Password corrente</Label>
            <Input
              id="cp-current"
              type="password"
              value={currentPassword}
              onChange={e => setCurrentPassword(e.target.value)}
              placeholder="••••••••"
              disabled={loading}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="cp-new">Nuova password</Label>
            <Input
              id="cp-new"
              type="password"
              value={newPassword}
              onChange={e => setNewPassword(e.target.value)}
              placeholder="••••••••"
              disabled={loading}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="cp-confirm">Conferma nuova password</Label>
            <Input
              id="cp-confirm"
              type="password"
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

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)} disabled={loading}>
              Annulla
            </Button>
            <Button type="submit" disabled={loading || !currentPassword}>
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
