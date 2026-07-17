import { useEffect, useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { ExternalLink, Loader2, AlertCircle } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import {
  getPropertyContracts, addPropertyContract, type PropertyContractRule,
} from '@/api/propertyApi';

interface QuickContractDialogProps {
  propertyId?: number;
  propertyName?: string;
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}

// Solo questi due tipi sono ammessi come voce di rimanenza dal backend.
const TIPI_RIMANENZA = [
  { value: 'commissione_pm',           label: 'Commissione PM' },
  { value: 'provvigione_proprietario', label: 'Provvigione Proprietario' },
];

const QuickContractDialog = ({ propertyId, propertyName, open, onClose, onSaved }: QuickContractDialogProps) => {
  const { toast } = useToast();
  const [rules, setRules] = useState<PropertyContractRule[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [tipo, setTipo] = useState('commissione_pm');
  const [valore, setValore] = useState('');
  const [error, setError] = useState<string | null>(null);

  const loadRules = async () => {
    if (!propertyId) return;
    setIsLoading(true);
    try {
      setRules(await getPropertyContracts(propertyId));
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (open && propertyId) {
      setError(null);
      setValore('');
      setTipo('commissione_pm');
      loadRules();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, propertyId]);

  const handleSave = async () => {
    if (!propertyId) return;
    setError(null);
    setIsSaving(true);
    try {
      await addPropertyContract(propertyId, {
        tipo,
        calcMode: 'percentuale',   // fisso per la regola rapida
        valore: Number(valore) || 0,
        isRemainder: true,         // sempre rimanenza
        ordine: 99,
        attivo: true,
      });
      toast({ title: 'Regola salvata' });
      await loadRules();
      onSaved();
    } catch (err) {
      // 400 tipico: "Esiste già una voce rimanenza"
      setError((err as Error).message || 'Errore durante il salvataggio della regola');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={o => { if (!o) onClose(); }}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Configura regola di rimanenza — {propertyName ?? 'Immobile'}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {/* Regole esistenti */}
          <div>
            <p className="text-sm font-medium mb-2">Regole esistenti</p>
            {isLoading ? (
              <div className="flex items-center justify-center py-6 text-muted-foreground">
                <Loader2 className="h-5 w-5 animate-spin" />
              </div>
            ) : rules.length === 0 ? (
              <p className="text-sm text-muted-foreground py-2">Nessuna regola configurata.</p>
            ) : (
              <div className="border rounded-lg overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Tipo</TableHead>
                      <TableHead>Modalità</TableHead>
                      <TableHead>Valore</TableHead>
                      <TableHead>Rimanenza</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {rules.map(r => (
                      <TableRow key={r.id}>
                        <TableCell className="text-sm">{r.tipoLabel ?? r.tipo}</TableCell>
                        <TableCell className="text-sm">{r.calcModeLabel ?? r.calcMode}</TableCell>
                        <TableCell className="text-sm font-mono">{r.valore}</TableCell>
                        <TableCell>
                          {r.isRemainder
                            ? <Badge variant="outline" className="bg-primary/10 text-primary border-primary/20">Sì</Badge>
                            : <span className="text-xs text-muted-foreground">—</span>}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </div>

          {/* Aggiungi regola rimanenza */}
          <div className="rounded-lg border bg-muted/30 p-4 space-y-3">
            <div>
              <p className="text-sm font-medium">Aggiungi regola rimanenza</p>
              <p className="text-xs text-muted-foreground">
                La regola di rimanenza assorbe il residuo del lordo dopo le altre voci.
                Necessaria per il calcolo dello split.
              </p>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label className="text-xs">Tipo</Label>
                <Select value={tipo} onValueChange={setTipo}>
                  <SelectTrigger className="h-9"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {TIPI_RIMANENZA.map(t => (
                      <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1">
                <Label className="text-xs">Valore %</Label>
                <Input
                  type="number"
                  min={0}
                  max={100}
                  value={valore}
                  onChange={e => setValore(e.target.value)}
                  placeholder="es. 20"
                  className="h-9"
                />
              </div>
            </div>
            {error && (
              <div className="flex items-center gap-2 text-destructive text-sm">
                <AlertCircle className="h-4 w-4" /> {error}
              </div>
            )}
            <div className="flex justify-end">
              <Button size="sm" className="gap-2" onClick={handleSave} disabled={isSaving}>
                {isSaving && <Loader2 className="h-4 w-4 animate-spin" />}
                Salva regola
              </Button>
            </div>
          </div>
        </div>

        <DialogFooter className="sm:justify-between gap-2">
          {propertyId ? (
            <a href={`${import.meta.env.BASE_URL}properties/${propertyId}/contracts`} target="_blank" rel="noopener noreferrer">
              <Button variant="outline" size="sm" className="gap-2">
                <ExternalLink className="h-4 w-4" /> Vai ai contratti completi
              </Button>
            </a>
          ) : <span />}
          <Button variant="outline" size="sm" onClick={onClose}>Chiudi</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default QuickContractDialog;
