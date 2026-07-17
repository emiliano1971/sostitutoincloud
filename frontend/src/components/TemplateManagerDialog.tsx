import { useEffect, useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Pencil, Trash2, Check, X, Loader2 } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import {
  getImportTemplates, deleteImportTemplate, saveImportTemplate,
  type ImportTemplate,
} from '@/api/importApi';

interface TemplateManagerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Invocata dopo ogni modifica (rinomina/elimina) per aggiornare la lista nel wizard. */
  onChanged?: () => void;
}

const TemplateManagerDialog = ({ open, onOpenChange, onChanged }: TemplateManagerDialogProps) => {
  const { toast } = useToast();
  const [templates, setTemplates] = useState<ImportTemplate[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editName, setEditName] = useState('');
  const [savingId, setSavingId] = useState<number | null>(null);

  const load = async () => {
    setIsLoading(true);
    try {
      setTemplates(await getImportTemplates());
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (open) load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const startRename = (t: ImportTemplate) => {
    setEditingId(t.id);
    setEditName(t.nome);
  };

  const cancelRename = () => {
    setEditingId(null);
    setEditName('');
  };

  const confirmRename = async (t: ImportTemplate) => {
    const nome = editName.trim();
    if (!nome || nome === t.nome) { cancelRename(); return; }
    setSavingId(t.id);
    try {
      // PUT con stesso mapping, nuovo nome
      await saveImportTemplate({
        id: t.id,
        nome,
        descrizione: t.descrizione,
        headerRow: t.headerRow,
        bookingMapping: t.bookingMapping,
        guestMapping: t.guestMapping,
      });
      toast({ title: 'Template rinominato' });
      cancelRename();
      await load();
      onChanged?.();
    } catch (err) {
      toast({ title: 'Errore rinomina', description: (err as Error).message, variant: 'destructive' });
    } finally {
      setSavingId(null);
    }
  };

  const handleDelete = async (t: ImportTemplate) => {
    if (!window.confirm(`Eliminare il template "${t.nome}"?`)) return;
    setSavingId(t.id);
    try {
      await deleteImportTemplate(t.id);
      toast({ title: 'Template eliminato' });
      await load();
      onChanged?.();
    } catch (err) {
      toast({ title: 'Errore eliminazione', description: (err as Error).message, variant: 'destructive' });
    } finally {
      setSavingId(null);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Gestisci template di importazione</DialogTitle>
        </DialogHeader>

        <div className="space-y-2 max-h-[50vh] overflow-y-auto">
          {isLoading ? (
            <div className="flex items-center justify-center py-8 text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin" />
            </div>
          ) : templates.length === 0 ? (
            <p className="text-sm text-muted-foreground py-6 text-center">Nessun template salvato.</p>
          ) : (
            templates.map(t => (
              <div key={t.id} className="flex items-center gap-2 border rounded-lg p-3">
                <div className="flex-1 min-w-0">
                  {editingId === t.id ? (
                    <Input
                      value={editName}
                      onChange={e => setEditName(e.target.value)}
                      onKeyDown={e => { if (e.key === 'Enter') confirmRename(t); if (e.key === 'Escape') cancelRename(); }}
                      autoFocus
                      className="h-8"
                    />
                  ) : (
                    <>
                      <p className="text-sm font-medium truncate">{t.nome}</p>
                      {t.descrizione && <p className="text-xs text-muted-foreground truncate">{t.descrizione}</p>}
                      <p className="text-xs text-muted-foreground">
                        Creato il {new Date(t.createdAt).toLocaleDateString('it-IT')}
                      </p>
                    </>
                  )}
                </div>
                <div className="flex items-center gap-1 shrink-0">
                  {editingId === t.id ? (
                    <>
                      <Button variant="ghost" size="icon" className="h-8 w-8" disabled={savingId === t.id}
                              onClick={() => confirmRename(t)}>
                        {savingId === t.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
                      </Button>
                      <Button variant="ghost" size="icon" className="h-8 w-8" onClick={cancelRename}>
                        <X className="h-4 w-4" />
                      </Button>
                    </>
                  ) : (
                    <>
                      <Button variant="ghost" size="icon" className="h-8 w-8" title="Rinomina"
                              disabled={savingId === t.id} onClick={() => startRename(t)}>
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive" title="Elimina"
                              disabled={savingId === t.id} onClick={() => handleDelete(t)}>
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </>
                  )}
                </div>
              </div>
            ))
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Chiudi</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default TemplateManagerDialog;
