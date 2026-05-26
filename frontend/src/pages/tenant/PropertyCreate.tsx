import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Separator } from '@/components/ui/separator';
import { ArrowLeft, Building2, Hash, Globe, Save } from 'lucide-react';
import { mockOwners } from '@/data/mock-data';
import { useToast } from '@/hooks/use-toast';

const PropertyCreate = () => {
  const navigate = useNavigate();
  const { toast } = useToast();
  const tenantOwners = mockOwners.filter(o => o.tenant_id === 't1' && o.status === 'active');

  const [form, setForm] = useState({
    display_name: '',
    internal_code: '',
    address: '',
    city: '',
    region: '',
    property_type: 'LT',
    cin_code: '',
    owner_id: '',
    airbnb_id: '',
    booking_id: '',
    vrbo_id: '',
    tripadvisor_id: '',
    expedia_id: '',
  });

  const update = (field: string, value: string) => setForm(prev => ({ ...prev, [field]: value }));

  const handleSave = () => {
    if (!form.display_name || !form.internal_code || !form.city) {
      toast({ title: 'Errore', description: 'Compila almeno nome, codice interno e città.', variant: 'destructive' });
      return;
    }
    toast({ title: 'Immobile creato', description: `${form.display_name} è stato creato con successo.` });
    navigate('/properties');
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => navigate('/properties')}><ArrowLeft className="h-4 w-4" /></Button>
        <h1 className="text-2xl font-bold">Nuovo Immobile</h1>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2 text-base"><Building2 className="h-4 w-4" /> Informazioni Generali</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label>Nome immobile *</Label>
              <Input value={form.display_name} onChange={e => update('display_name', e.target.value)} placeholder="es. Appartamento Trastevere" />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Codice interno *</Label>
                <Input value={form.internal_code} onChange={e => update('internal_code', e.target.value)} placeholder="es. ROM-004" />
              </div>
              <div className="space-y-2">
                <Label>Tipologia</Label>
                <Select value={form.property_type} onValueChange={v => update('property_type', v)}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="LT">Locazione Turistica (LT)</SelectItem>
                    <SelectItem value="CAV">Casa Vacanze (CAV)</SelectItem>
                    <SelectItem value="B&B">B&B</SelectItem>
                    <SelectItem value="Affittacamere">Affittacamere</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="space-y-2">
              <Label>Indirizzo</Label>
              <Input value={form.address} onChange={e => update('address', e.target.value)} placeholder="Via/Piazza..." />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Città *</Label>
                <Input value={form.city} onChange={e => update('city', e.target.value)} placeholder="es. Roma" />
              </div>
              <div className="space-y-2">
                <Label>Regione</Label>
                <Input value={form.region} onChange={e => update('region', e.target.value)} placeholder="es. Lazio" />
              </div>
            </div>
          </CardContent>
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader><CardTitle className="flex items-center gap-2 text-base"><Hash className="h-4 w-4" /> Codice CIN & Proprietario</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label>Codice CIN</Label>
                <Input value={form.cin_code} onChange={e => update('cin_code', e.target.value)} placeholder="es. IT058091C1A2B3C4D5" className="font-mono" />
                <p className="text-xs text-muted-foreground">Codice Identificativo Nazionale assegnato dalla BDSR</p>
              </div>
              <Separator />
              <div className="space-y-2">
                <Label>Proprietario</Label>
                <Select value={form.owner_id} onValueChange={v => update('owner_id', v)}>
                  <SelectTrigger><SelectValue placeholder="Seleziona proprietario..." /></SelectTrigger>
                  <SelectContent>
                    {tenantOwners.map(o => (
                      <SelectItem key={o.owner_id} value={o.owner_id}>
                        {o.first_name} {o.last_name} — {o.tax_code}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle className="flex items-center gap-2 text-base"><Globe className="h-4 w-4" /> Codici OTA (Mappatura Import)</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              <p className="text-xs text-muted-foreground">Inserisci gli ID delle piattaforme OTA per associare automaticamente le prenotazioni importate a questo immobile.</p>
              <div className="space-y-3">
                <div className="space-y-1">
                  <Label className="text-xs">Airbnb Listing ID</Label>
                  <Input value={form.airbnb_id} onChange={e => update('airbnb_id', e.target.value)} placeholder="es. 12345678" className="font-mono text-sm" />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs">Booking.com Property ID</Label>
                  <Input value={form.booking_id} onChange={e => update('booking_id', e.target.value)} placeholder="es. 9876543" className="font-mono text-sm" />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs">Vrbo Property ID</Label>
                  <Input value={form.vrbo_id} onChange={e => update('vrbo_id', e.target.value)} placeholder="es. VR-001122" className="font-mono text-sm" />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs">TripAdvisor ID</Label>
                  <Input value={form.tripadvisor_id} onChange={e => update('tripadvisor_id', e.target.value)} placeholder="es. TP-887766" className="font-mono text-sm" />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs">Expedia Property ID</Label>
                  <Input value={form.expedia_id} onChange={e => update('expedia_id', e.target.value)} placeholder="es. EX-556677" className="font-mono text-sm" />
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      <div className="flex justify-end gap-3">
        <Button variant="outline" onClick={() => navigate('/properties')}>Annulla</Button>
        <Button className="gap-2" onClick={handleSave}><Save className="h-4 w-4" /> Crea Immobile</Button>
      </div>
    </div>
  );
};

export default PropertyCreate;
