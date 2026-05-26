import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { Plus, Pencil, Globe } from 'lucide-react';
import { mockOTARegistry, type OTAChannel } from '@/data/ota-registry';
import { useToast } from '@/hooks/use-toast';

const OTARegistry = () => {
  const { toast } = useToast();
  const [channels, setChannels] = useState<OTAChannel[]>(mockOTARegistry);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<OTAChannel | null>(null);

  // Form state
  const [formName, setFormName] = useState('');
  const [formCommission, setFormCommission] = useState('');
  const [formTaxIncluded, setFormTaxIncluded] = useState(false);
  const [formTaxCollection, setFormTaxCollection] = useState<'contanti' | 'payment_link' | 'altro'>('contanti');

  const openEdit = (ch: OTAChannel) => {
    setEditing(ch);
    setFormName(ch.name);
    setFormCommission(ch.default_commission_pct.toString());
    setFormTaxIncluded(ch.tourist_tax_included);
    setFormTaxCollection(ch.tourist_tax_collection);
    setEditOpen(true);
  };

  const openNew = () => {
    setEditing(null);
    setFormName('');
    setFormCommission('');
    setFormTaxIncluded(false);
    setFormTaxCollection('contanti');
    setEditOpen(true);
  };

  const handleSave = () => {
    const pct = parseFloat(formCommission);
    if (!formName.trim()) return;

    if (editing) {
      setChannels(prev => prev.map(c => c.ota_id === editing.ota_id ? {
        ...c, name: formName, default_commission_pct: isNaN(pct) ? 0 : pct,
        tourist_tax_included: formTaxIncluded, tourist_tax_collection: formTaxCollection,
      } : c));
      toast({ title: 'OTA aggiornata' });
    } else {
      setChannels(prev => [...prev, {
        ota_id: `ota-${Date.now()}`, name: formName, logo_color: '#6B7280',
        default_commission_pct: isNaN(pct) ? 0 : pct,
        tourist_tax_included: formTaxIncluded, tourist_tax_collection: formTaxCollection,
        status: 'active',
      }]);
      toast({ title: 'OTA aggiunta' });
    }
    setEditOpen(false);
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
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Canale</TableHead>
                <TableHead className="text-center">Commissione Default</TableHead>
                <TableHead className="text-center">Tassa Soggiorno nel Totale</TableHead>
                <TableHead className="text-center">Riscossione Tassa</TableHead>
                <TableHead className="text-center">Stato</TableHead>
                <TableHead className="w-16"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {channels.map(ch => (
                <TableRow key={ch.ota_id}>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <div className="h-6 w-6 rounded flex items-center justify-center" style={{ backgroundColor: ch.logo_color + '20' }}>
                        <Globe className="h-3.5 w-3.5" style={{ color: ch.logo_color }} />
                      </div>
                      <span className="font-medium text-sm">{ch.name}</span>
                    </div>
                  </TableCell>
                  <TableCell className="text-center">
                    <Badge variant="secondary" className="font-mono">{ch.default_commission_pct}%</Badge>
                  </TableCell>
                  <TableCell className="text-center">
                    {ch.tourist_tax_included
                      ? <Badge className="bg-amber-100 text-amber-800 hover:bg-amber-100">Inclusa</Badge>
                      : <Badge variant="outline">Esclusa</Badge>
                    }
                  </TableCell>
                  <TableCell className="text-center">
                    <Badge variant="outline" className="text-xs capitalize">
                      {ch.tourist_tax_collection === 'payment_link' ? 'Payment Link' : ch.tourist_tax_collection === 'contanti' ? 'Contanti' : 'Altro'}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-center">
                    <Badge variant={ch.status === 'active' ? 'default' : 'secondary'}>
                      {ch.status === 'active' ? 'Attivo' : 'Inattivo'}
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

      {/* Edit/Add Dialog */}
      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? 'Modifica OTA' : 'Nuova OTA'}</DialogTitle>
            <DialogDescription>Configura il canale e le impostazioni di default.</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>Nome Canale</Label>
              <Input value={formName} onChange={e => setFormName(e.target.value)} placeholder="es. Airbnb" />
            </div>
            <div className="space-y-2">
              <Label>Commissione Default (%)</Label>
              <Input type="number" step="0.1" min="0" max="100" value={formCommission} onChange={e => setFormCommission(e.target.value)} placeholder="es. 15" />
            </div>
            <div className="flex items-center justify-between rounded-lg border p-3">
              <div>
                <Label>Tassa di Soggiorno inclusa nel totale</Label>
                <p className="text-xs text-muted-foreground">L'OTA include la tassa nel prezzo lordo della prenotazione</p>
              </div>
              <Switch checked={formTaxIncluded} onCheckedChange={setFormTaxIncluded} />
            </div>
            <div className="space-y-2">
              <Label>Modalità Riscossione Tassa</Label>
              <Select value={formTaxCollection} onValueChange={(v: 'contanti' | 'payment_link' | 'altro') => setFormTaxCollection(v)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="contanti">Contanti</SelectItem>
                  <SelectItem value="payment_link">Payment Link</SelectItem>
                  <SelectItem value="altro">Altro</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditOpen(false)}>Annulla</Button>
            <Button onClick={handleSave} disabled={!formName.trim()}>
              {editing ? 'Salva' : 'Aggiungi'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default OTARegistry;
