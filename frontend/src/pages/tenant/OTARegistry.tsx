import { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { Plus, Pencil, Globe, Loader2, AlertCircle } from 'lucide-react';
import { get, post, put, patch } from '@/lib/apiClient';
import { useToast } from '@/hooks/use-toast';

interface CanaleOtaDTO {
  id: number;
  codice: string;
  nome: string;
  commissioneDefaultPct: number;
  touristTaxIncluded: boolean;
  touristTaxCollection: string;
  attivo: boolean;
}

const OTARegistry = () => {
  const { toast } = useToast();
  const [channels, setChannels] = useState<CanaleOtaDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<CanaleOtaDTO | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const [formNome, setFormNome] = useState('');
  const [formCodice, setFormCodice] = useState('');
  const [formCommission, setFormCommission] = useState('');
  const [formTaxIncluded, setFormTaxIncluded] = useState(false);
  const [formTaxCollection, setFormTaxCollection] = useState('contanti');
  const [formAttivo, setFormAttivo] = useState(true);

  useEffect(() => {
    get<CanaleOtaDTO[]>('/canali-ota')
      .then(setChannels)
      .catch(err => setLoadError(err.message))
      .finally(() => setIsLoading(false));
  }, []);

  const openEdit = (ch: CanaleOtaDTO) => {
    setEditing(ch);
    setFormNome(ch.nome);
    setFormCodice(ch.codice);
    setFormCommission(ch.commissioneDefaultPct.toString());
    setFormTaxIncluded(ch.touristTaxIncluded);
    setFormTaxCollection(ch.touristTaxCollection ?? 'contanti');
    setFormAttivo(ch.attivo);
    setSaveError(null);
    setEditOpen(true);
  };

  const openNew = () => {
    setEditing(null);
    setFormNome('');
    setFormCodice('');
    setFormCommission('');
    setFormTaxIncluded(false);
    setFormTaxCollection('contanti');
    setFormAttivo(true);
    setSaveError(null);
    setEditOpen(true);
  };

  const handleSave = async () => {
    if (!formNome.trim()) { setSaveError('Nome obbligatorio'); return; }
    if (!editing && !formCodice.trim()) { setSaveError('Codice obbligatorio'); return; }
    const pct = parseFloat(formCommission);
    setIsSaving(true);
    setSaveError(null);
    try {
      if (editing) {
        const updated = await put<CanaleOtaDTO>(`/canali-ota/${editing.id}`, {
          nome: formNome.trim(),
          commissioneDefaultPct: isNaN(pct) ? 0 : pct,
          touristTaxIncluded: formTaxIncluded,
          touristTaxCollection: formTaxCollection,
          attivo: formAttivo,
        });
        setChannels(prev => prev.map(c => c.id === editing.id ? updated : c));
        toast({ title: 'OTA aggiornata' });
      } else {
        const created = await post<CanaleOtaDTO>('/canali-ota', {
          codice: formCodice.trim().toLowerCase(),
          nome: formNome.trim(),
          commissioneDefaultPct: isNaN(pct) ? 0 : pct,
          touristTaxIncluded: formTaxIncluded,
          touristTaxCollection: formTaxCollection,
        });
        setChannels(prev => [...prev, created]);
        toast({ title: 'OTA aggiunta' });
      }
      setEditOpen(false);
    } catch (err) {
      setSaveError((err as Error).message);
    } finally {
      setIsSaving(false);
    }
  };

  const handleToggleStatus = async (ch: CanaleOtaDTO) => {
    try {
      const updated = await patch<CanaleOtaDTO>(`/canali-ota/${ch.id}/status`, { attivo: !ch.attivo });
      setChannels(prev => prev.map(c => c.id === ch.id ? updated : c));
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Anagrafica OTA</h1>
          <p className="text-sm text-muted-foreground">Gestisci i canali OTA con le commissioni di default e le impostazioni sulla tassa di soggiorno.</p>
        </div>
        <Button size="sm" className="gap-2" onClick={openNew}>
          <Plus className="h-4 w-4" /> Aggiungi OTA
        </Button>
      </div>

      <Card>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="flex items-center justify-center py-16 gap-2 text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin" /> Caricamento canali OTA…
            </div>
          ) : loadError ? (
            <div className="flex items-center justify-center py-16 gap-2 text-destructive">
              <AlertCircle className="h-5 w-5" /> {loadError}
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Canale</TableHead>
                  <TableHead className="text-center">Commissione Default</TableHead>
                  <TableHead className="text-center">Tassa Soggiorno nel Totale</TableHead>
                  <TableHead className="text-center">Riscossione Tassa</TableHead>
                  <TableHead className="text-center">Stato</TableHead>
                  <TableHead className="w-20"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {channels.map(ch => (
                  <TableRow key={ch.id}>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <div className="h-6 w-6 rounded bg-primary/10 flex items-center justify-center">
                          <Globe className="h-3.5 w-3.5 text-primary" />
                        </div>
                        <div>
                          <span className="font-medium text-sm">{ch.nome}</span>
                          <span className="ml-2 text-xs text-muted-foreground font-mono">{ch.codice}</span>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="text-center">
                      <Badge variant="secondary" className="font-mono">{ch.commissioneDefaultPct}%</Badge>
                    </TableCell>
                    <TableCell className="text-center">
                      {ch.touristTaxIncluded
                        ? <Badge className="bg-amber-100 text-amber-800 hover:bg-amber-100">Inclusa</Badge>
                        : <Badge variant="outline">Esclusa</Badge>}
                    </TableCell>
                    <TableCell className="text-center">
                      <Badge variant="outline" className="text-xs">
                        {ch.touristTaxCollection === 'payment_link' ? 'Payment Link'
                          : ch.touristTaxCollection === 'contanti' ? 'Contanti' : 'Altro'}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-center">
                      <Badge
                        variant={ch.attivo ? 'default' : 'secondary'}
                        className="cursor-pointer"
                        onClick={() => handleToggleStatus(ch)}
                      >
                        {ch.attivo ? 'Attivo' : 'Inattivo'}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => openEdit(ch)}>
                        <Pencil className="h-3.5 w-3.5" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Card className="border-muted">
        <CardHeader>
          <CardTitle className="text-base">Come funziona</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground space-y-2">
          <p><strong className="text-foreground">Commissione Default</strong> — La percentuale verrà proposta automaticamente quando si configura la regola "Commissione OTA" nel contratto di un immobile.</p>
          <p><strong className="text-foreground">Tassa di Soggiorno nel Totale</strong> — Indica se l'OTA include la tassa di soggiorno nel prezzo lordo della prenotazione (<Badge className="bg-amber-100 text-amber-800 hover:bg-amber-100 text-[10px]">Inclusa</Badge>) oppure se va sommata separatamente (<Badge variant="outline" className="text-[10px]">Esclusa</Badge>).</p>
          <p><strong className="text-foreground">Modalità di Riscossione</strong> — Come viene riscossa la tassa di soggiorno dall'ospite: Contanti, Payment Link, o Altro.</p>
        </CardContent>
      </Card>

      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? 'Modifica OTA' : 'Nuova OTA'}</DialogTitle>
            <DialogDescription>Configura il canale e le impostazioni di default.</DialogDescription>
          </DialogHeader>

          {saveError && (
            <div className="rounded-md border border-destructive bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {saveError}
            </div>
          )}

          <div className="space-y-4">
            {!editing && (
              <div className="space-y-2">
                <Label>Codice *</Label>
                <Input
                  value={formCodice}
                  onChange={e => setFormCodice(e.target.value.toLowerCase())}
                  placeholder="es. airbnb"
                  className="font-mono"
                />
                <p className="text-xs text-muted-foreground">Identificativo univoco, solo lettere minuscole e underscore</p>
              </div>
            )}
            <div className="space-y-2">
              <Label>Nome Canale *</Label>
              <Input value={formNome} onChange={e => setFormNome(e.target.value)} placeholder="es. Airbnb" />
            </div>
            <div className="space-y-2">
              <Label>Commissione Default (%)</Label>
              <Input type="number" step="0.1" min="0" max="100" value={formCommission}
                onChange={e => setFormCommission(e.target.value)} placeholder="es. 15" />
            </div>
            <div className="flex items-center justify-between rounded-lg border p-3">
              <div>
                <Label>Tassa di Soggiorno inclusa nel totale</Label>
                <p className="text-xs text-muted-foreground">L'OTA include la tassa nel prezzo lordo</p>
              </div>
              <Switch checked={formTaxIncluded} onCheckedChange={setFormTaxIncluded} />
            </div>
            <div className="space-y-2">
              <Label>Modalità Riscossione Tassa</Label>
              <Select value={formTaxCollection} onValueChange={setFormTaxCollection}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="contanti">Contanti</SelectItem>
                  <SelectItem value="payment_link">Payment Link</SelectItem>
                  <SelectItem value="altro">Altro</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {editing && (
              <div className="flex items-center justify-between rounded-lg border p-3">
                <div>
                  <Label>Canale Attivo</Label>
                  <p className="text-xs text-muted-foreground">Disattiva per nascondere il canale dalle selezioni</p>
                </div>
                <Switch checked={formAttivo} onCheckedChange={setFormAttivo} />
              </div>
            )}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setEditOpen(false)} disabled={isSaving}>Annulla</Button>
            <Button onClick={handleSave} disabled={isSaving || !formNome.trim()} className="gap-2">
              {isSaving && <Loader2 className="h-4 w-4 animate-spin" />}
              {editing ? 'Salva' : 'Aggiungi'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default OTARegistry;
