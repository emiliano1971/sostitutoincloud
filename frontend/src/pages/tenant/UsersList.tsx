import { useState, useEffect } from 'react';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Plus, Trash2, Loader2, AlertCircle } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import { getUsers, createUser, updateUserStatus, deleteUser, type UtenteListItem } from '@/api/userApi';
import { getOwners, type OwnerListItem } from '@/api/ownerApi';

const emptyForm = { firstName: '', lastName: '', email: '', password: '', ruolo: 'pm_user', fkOwnerId: '' };

const scopeLabel = (u: UtenteListItem): string => {
  switch (u.ruolo) {
    case 'tenant_admin': return 'Amministratore';
    case 'pm_user':      return 'Accesso completo';
    case 'owner_user':   return u.ownerName ?? '—';
    default:             return '—';
  }
};

const UsersList = () => {
  const { toast } = useToast();
  const [users, setUsers] = useState<UtenteListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [inviteOpen, setInviteOpen] = useState(false);
  const [owners, setOwners] = useState<OwnerListItem[]>([]);
  const [form, setForm] = useState(emptyForm);
  const [isSaving, setIsSaving] = useState(false);

  const [toDelete, setToDelete] = useState<UtenteListItem | null>(null);

  const load = () => {
    setIsLoading(true);
    getUsers()
      .then(setUsers)
      .then(() => setError(null))
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  };

  useEffect(() => { load(); }, []);

  const openInvite = () => {
    setForm(emptyForm);
    setInviteOpen(true);
    getOwners(true).then(setOwners).catch(() => {});
  };

  const update = (field: keyof typeof emptyForm, value: string) =>
    setForm(prev => ({ ...prev, [field]: value }));

  const handleInvite = async () => {
    if (!form.firstName || !form.lastName || !form.email || !form.password) {
      toast({ title: 'Errore', description: 'Compila nome, cognome, email e password.', variant: 'destructive' });
      return;
    }
    if (form.password.length < 8) {
      toast({ title: 'Errore', description: 'La password deve avere almeno 8 caratteri.', variant: 'destructive' });
      return;
    }
    if (form.ruolo === 'owner_user' && !form.fkOwnerId) {
      toast({ title: 'Errore', description: 'Seleziona il proprietario.', variant: 'destructive' });
      return;
    }
    setIsSaving(true);
    try {
      await createUser({
        firstName: form.firstName,
        lastName: form.lastName,
        email: form.email,
        password: form.password,
        ruolo: form.ruolo,
        fkOwnerId: form.ruolo === 'owner_user' ? Number(form.fkOwnerId) : undefined,
      });
      toast({ title: 'Utente invitato', description: `${form.firstName} ${form.lastName} è stato creato.` });
      setInviteOpen(false);
      load();
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    } finally {
      setIsSaving(false);
    }
  };

  const handleToggleStatus = async (u: UtenteListItem) => {
    try {
      await updateUserStatus(u.id, !u.attivo);
      load();
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    }
  };

  const handleDelete = async () => {
    if (!toDelete) return;
    try {
      await deleteUser(toDelete.id);
      toast({ title: 'Utente eliminato', description: `${toDelete.firstName} ${toDelete.lastName} è stato rimosso.` });
      setToDelete(null);
      load();
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
      setToDelete(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Utenti e Scope</h1>
          <p className="text-sm text-muted-foreground">Gestisci gli accessi al tenant</p>
        </div>
        <Button size="sm" className="gap-2" onClick={openInvite}><Plus className="h-4 w-4" /> Invita Utente</Button>
      </div>
      <Card>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="flex items-center justify-center py-16 text-muted-foreground gap-2">
              <Loader2 className="h-5 w-5 animate-spin" />
              <span>Caricamento utenti…</span>
            </div>
          ) : error ? (
            <div className="flex items-center justify-center py-16 text-destructive gap-2">
              <AlertCircle className="h-5 w-5" />
              <span>{error}</span>
            </div>
          ) : users.length === 0 ? (
            <div className="flex items-center justify-center py-16 text-muted-foreground">
              Nessun utente
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Nome</TableHead>
                  <TableHead>Email</TableHead>
                  <TableHead>Ruolo</TableHead>
                  <TableHead>Scope</TableHead>
                  <TableHead>Stato</TableHead>
                  <TableHead className="w-10"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {users.map(u => {
                  const isAdmin = u.ruolo === 'tenant_admin';
                  return (
                    <TableRow key={u.id}>
                      <TableCell className="font-medium">{u.firstName} {u.lastName}</TableCell>
                      <TableCell className="text-sm text-muted-foreground">{u.email}</TableCell>
                      <TableCell><Badge variant="outline" className="text-xs">{u.ruolo}</Badge></TableCell>
                      <TableCell className="text-sm">{scopeLabel(u)}</TableCell>
                      <TableCell>
                        <Badge
                          variant={u.attivo ? 'default' : 'secondary'}
                          className={isAdmin ? 'text-xs' : 'text-xs cursor-pointer'}
                          onClick={isAdmin ? undefined : () => handleToggleStatus(u)}
                        >
                          {u.attivo ? 'attivo' : 'inattivo'}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        {!isAdmin && (
                          <Button
                            variant="ghost" size="icon" className="h-7 w-7 text-destructive"
                            onClick={() => setToDelete(u)}
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Dialog Invita Utente */}
      <Dialog open={inviteOpen} onOpenChange={setInviteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Invita Utente</DialogTitle>
            <DialogDescription>Crea un nuovo accesso al tenant.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label htmlFor="firstName">Nome</Label>
                <Input id="firstName" value={form.firstName} onChange={e => update('firstName', e.target.value)} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="lastName">Cognome</Label>
                <Input id="lastName" value={form.lastName} onChange={e => update('lastName', e.target.value)} />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" value={form.email} onChange={e => update('email', e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="password">Password</Label>
              <Input id="password" type="password" value={form.password} onChange={e => update('password', e.target.value)} />
              <p className="text-xs text-muted-foreground">Minimo 8 caratteri.</p>
            </div>
            <div className="space-y-1.5">
              <Label>Ruolo</Label>
              <Select value={form.ruolo} onValueChange={v => update('ruolo', v)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="pm_user">PM User</SelectItem>
                  <SelectItem value="owner_user">Owner User</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {form.ruolo === 'owner_user' && (
              <div className="space-y-1.5">
                <Label>Proprietario</Label>
                <Select value={form.fkOwnerId} onValueChange={v => update('fkOwnerId', v)}>
                  <SelectTrigger><SelectValue placeholder="Seleziona proprietario" /></SelectTrigger>
                  <SelectContent>
                    {owners.map(o => (
                      <SelectItem key={o.id} value={String(o.id)}>
                        {o.legalName ?? `${o.firstName} ${o.lastName}`}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setInviteOpen(false)} disabled={isSaving}>Annulla</Button>
            <Button onClick={handleInvite} disabled={isSaving} className="gap-2">
              {isSaving && <Loader2 className="h-4 w-4 animate-spin" />}
              Invita
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Conferma eliminazione */}
      <AlertDialog open={!!toDelete} onOpenChange={open => { if (!open) setToDelete(null); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Eliminare l'utente?</AlertDialogTitle>
            <AlertDialogDescription>
              {toDelete && `${toDelete.firstName} ${toDelete.lastName} (${toDelete.email}) verrà rimosso definitivamente.`}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Annulla</AlertDialogCancel>
            <AlertDialogAction onClick={handleDelete} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
              Elimina
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
};

export default UsersList;
