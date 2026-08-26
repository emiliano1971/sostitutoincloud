import { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Separator } from '@/components/ui/separator';
import { ArrowLeft, Building2, Hash, Globe, Save, Loader2 } from 'lucide-react';
import ComuneAutocomplete from '../../components/ComuneAutocomplete';
import { getOwners, type OwnerListItem } from '@/api/ownerApi';
import { getPropertyById, updateProperty, type OtaCode } from '@/api/propertyApi';
import { useToast } from '@/hooks/use-toast';
import { useLookup } from '@/contexts/LookupContext';

// Cerca l'externalId di un canale nel set di codici OTA restituiti dal backend.
// Il GET restituisce canaleCodiceName come NOME del canale (es. "Booking.com"),
// quindi il match è fatto contro codice e nome (case-insensitive).
const findOta = (codes: OtaCode[], ...names: string[]): string => {
  const set = names.map(n => n.toLowerCase());
  return codes.find(c => set.includes((c.canaleCodiceName ?? '').toLowerCase()))?.externalId ?? '';
};

const PropertyEdit = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const propertyId = Number(id);
  const { toast } = useToast();
  const { lookups } = useLookup();
  const [tenantOwners, setTenantOwners] = useState<OwnerListItem[]>([]);
  const [isSaving, setIsSaving] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [ownerError, setOwnerError] = useState(false);
  const ownerFieldRef = useRef<HTMLDivElement>(null);

  const [form, setForm] = useState({
    display_name: '',
    internal_code: '',
    address: '',
    city: '',
    region: '',
    property_type: 'LT',
    cin_code: '',
    owner_id: '',
  });

  // Codici OTA per canale: { [codiceCanale]: externalId }
  const [otaCodes, setOtaCodes] = useState<Record<string, string>>({});
  // Codici OTA grezzi caricati dal backend (canaleCodiceName = nome canale), mappati per codice appena i lookup sono pronti.
  const [loadedOta, setLoadedOta] = useState<OtaCode[] | null>(null);
  const updateOta = (codice: string, value: string) => setOtaCodes(prev => ({ ...prev, [codice]: value }));

  useEffect(() => {
    getOwners(true).then(setTenantOwners).catch(() => {});
  }, []);

  // Mappa i codici OTA caricati (per nome canale) su { codice: externalId } quando i lookup sono disponibili.
  useEffect(() => {
    if (!loadedOta || !lookups) return;
    const rec: Record<string, string> = {};
    for (const c of lookups.canaliOta.filter(x => x.attivo)) {
      const v = findOta(loadedOta, c.codice, c.descrizione);
      if (v) rec[c.codice] = v;
    }
    setOtaCodes(rec);
  }, [loadedOta, lookups]);

  // Carica l'immobile e precompila tutti i campi (inclusi i codici OTA).
  useEffect(() => {
    if (!propertyId) return;
    setIsLoading(true);
    getPropertyById(propertyId)
      .then(p => {
        setForm({
          display_name: p.displayName ?? '',
          internal_code: p.internalCode ?? '',
          address: p.address ?? '',
          city: p.city ?? '',
          region: p.region ?? '',
          property_type: p.propertyType ?? 'LT',
          cin_code: p.cinCode ?? '',
          owner_id: p.fkOwnerId ? String(p.fkOwnerId) : '',
        });
        setLoadedOta(p.otaCodes ?? []);
      })
      .catch(err => toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' }))
      .finally(() => setIsLoading(false));
  }, [propertyId, toast]);

  const update = (field: string, value: string) => setForm(prev => ({ ...prev, [field]: value }));

  const handleSave = async () => {
    if (!form.display_name || !form.internal_code || !form.city) {
      toast({ title: 'Errore', description: 'Compila almeno nome, codice interno e città.', variant: 'destructive' });
      return;
    }
    // Proprietario obbligatorio: non chiamare il backend se mancante
    if (!form.owner_id) {
      setOwnerError(true);
      ownerFieldRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      ownerFieldRef.current?.querySelector('button')?.focus();
      return;
    }
    const otaCodesList = Object.entries(otaCodes)
      .filter(([, v]) => v.trim() !== '')
      .map(([k, v]) => ({ canaleCodiceName: k, externalId: v }));
    setIsSaving(true);
    try {
      await updateProperty(propertyId, {
        displayName:  form.display_name,
        internalCode: form.internal_code,
        propertyType: form.property_type,
        address:      form.address || undefined,
        city:         form.city,
        region:       form.region || undefined,
        cinCode:      form.cin_code || undefined,
        fkOwnerId:    form.owner_id ? Number(form.owner_id) : undefined,
        otaCodes:     otaCodesList.length > 0 ? otaCodesList : undefined,
      });
      toast({ title: 'Immobile aggiornato', description: `${form.display_name} è stato aggiornato con successo.` });
      navigate(`/properties/${propertyId}`);
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20 text-muted-foreground">
        <Loader2 className="h-6 w-6 animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => navigate(`/properties/${propertyId}`)}><ArrowLeft className="h-4 w-4" /></Button>
        <h1 className="text-2xl font-bold">Modifica Immobile — {form.display_name}</h1>
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
                {/* initialValue: mostra il comune già salvato senza avviare una ricerca al mount */}
                <ComuneAutocomplete
                  value={form.city}
                  initialValue={form.city}
                  placeholder="es. Roma"
                  requireValidComune
                  // Città svuotata (testo non valido): anche la regione derivata va azzerata.
                  onChange={nome => setForm(prev => ({ ...prev, city: nome, region: nome ? prev.region : '' }))}
                  onSelect={c => setForm(prev => ({ ...prev, city: c.nome, region: c.regione }))}
                />
              </div>
              <div className="space-y-2">
                <Label>Regione</Label>
                <Input
                  value={form.region}
                  readOnly
                  tabIndex={-1}
                  placeholder="dal comune"
                  className="bg-muted text-muted-foreground cursor-not-allowed"
                />
                <p className="text-xs text-muted-foreground">Compilata automaticamente dal comune selezionato</p>
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
              <div className="space-y-2" ref={ownerFieldRef}>
                <Label>Proprietario *</Label>
                <Select value={form.owner_id} onValueChange={v => { update('owner_id', v); setOwnerError(false); }}>
                  <SelectTrigger className={ownerError ? 'border-destructive focus:ring-destructive' : ''}>
                    <SelectValue placeholder="Seleziona proprietario..." />
                  </SelectTrigger>
                  <SelectContent>
                    {tenantOwners.map(o => (
                      <SelectItem key={o.id} value={String(o.id)}>
                        {o.firstName} {o.lastName} — {o.taxCode}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {ownerError && <p className="text-sm text-destructive">Seleziona un proprietario</p>}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle className="flex items-center gap-2 text-base"><Globe className="h-4 w-4" /> Codici OTA (Mappatura Import)</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              <p className="text-xs text-muted-foreground">Inserisci gli ID delle piattaforme OTA per associare automaticamente le prenotazioni importate a questo immobile.</p>
              <div className="space-y-3">
                {lookups?.canaliOta.filter(c => c.attivo).map(canale => (
                  <div key={canale.codice} className="space-y-1">
                    <Label className="text-xs">{canale.descrizione}</Label>
                    <Input
                      value={otaCodes[canale.codice] ?? ''}
                      onChange={e => updateOta(canale.codice, e.target.value)}
                      placeholder={`es. ID_${canale.codice}`}
                      className="font-mono text-sm"
                    />
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      <div className="flex justify-end gap-3">
        <Button variant="outline" onClick={() => navigate(`/properties/${propertyId}`)} disabled={isSaving}>Annulla</Button>
        <Button className="gap-2" onClick={handleSave} disabled={isSaving}>
          {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
          Salva Modifiche
        </Button>
      </div>
    </div>
  );
};

export default PropertyEdit;
