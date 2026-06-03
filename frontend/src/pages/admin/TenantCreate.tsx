import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { ArrowLeft, Building2, Mail, Save, Loader2 } from 'lucide-react';
import { createTenant } from '@/api/tenantApi';
import { useToast } from '@/hooks/use-toast';

const TenantCreate = () => {
  const navigate = useNavigate();
  const { toast } = useToast();
  const [isSaving, setIsSaving] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const [form, setForm] = useState({
    legalName: '',
    displayName: '',
    taxCode: '',
    vatNumber: '',
    administrativeEmail: '',
    pec: '',
    phone: '',
    legalAddress: '',
  });

  const update = (field: string, value: string) => {
    setServerError(null);
    setForm(prev => ({ ...prev, [field]: value }));
  };

  const validate = (): string | null => {
    if (!form.legalName.trim()) return 'Ragione sociale obbligatoria';
    if (!form.displayName.trim()) return 'Nome display obbligatorio';
    if (!form.taxCode.trim()) return 'Codice fiscale obbligatorio';
    if (form.taxCode.trim().length !== 16) return 'Il codice fiscale deve avere esattamente 16 caratteri';
    if (form.vatNumber.trim() && form.vatNumber.trim().length !== 11) return 'La Partita IVA deve avere esattamente 11 cifre';
    if (!form.administrativeEmail.trim()) return 'Email amministrativa obbligatoria';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.administrativeEmail.trim())) return 'Formato email non valido';
    if (!form.legalAddress.trim()) return 'Indirizzo sede legale obbligatorio';
    return null;
  };

  const handleSave = async () => {
    const validationError = validate();
    if (validationError) {
      toast({ title: 'Errore', description: validationError, variant: 'destructive' });
      return;
    }
    setIsSaving(true);
    setServerError(null);
    try {
      await createTenant({
        legalName:          form.legalName.trim(),
        displayName:        form.displayName.trim(),
        taxCode:            form.taxCode.trim(),
        vatNumber:          form.vatNumber.trim() || undefined,
        administrativeEmail: form.administrativeEmail.trim(),
        pec:                form.pec.trim() || undefined,
        phone:              form.phone.trim() || undefined,
        legalAddress:       form.legalAddress.trim(),
      });
      toast({ title: 'Tenant creato', description: `${form.displayName} è stato creato con successo.` });
      navigate('/admin/tenants');
    } catch (err) {
      const msg = (err as Error).message;
      setServerError(msg);
      toast({ title: 'Errore', description: msg, variant: 'destructive' });
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => navigate('/admin/tenants')}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <h1 className="text-2xl font-bold">Nuovo Tenant</h1>
      </div>

      {serverError && (
        <div className="rounded-md border border-destructive bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {serverError}
        </div>
      )}

      <div className="grid gap-6 md:grid-cols-2">
        {/* Dati Fiscali */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <Building2 className="h-4 w-4" /> Dati Aziendali
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label>Ragione Sociale *</Label>
              <Input value={form.legalName} onChange={e => update('legalName', e.target.value)} placeholder="Casa Vacanze Italia SRL" />
            </div>
            <div className="space-y-2">
              <Label>Nome Display *</Label>
              <Input value={form.displayName} onChange={e => update('displayName', e.target.value)} placeholder="Casa Vacanze Italia" />
            </div>
            <Separator />
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Codice Fiscale * (16 caratteri)</Label>
                <Input
                  value={form.taxCode}
                  onChange={e => update('taxCode', e.target.value.toUpperCase())}
                  placeholder="CVITRL80A01H501Z"
                  className="font-mono uppercase"
                  maxLength={16}
                />
              </div>
              <div className="space-y-2">
                <Label>Partita IVA (11 cifre)</Label>
                <Input
                  value={form.vatNumber}
                  onChange={e => update('vatNumber', e.target.value)}
                  placeholder="12345678901"
                  className="font-mono"
                  maxLength={11}
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label>Indirizzo Sede Legale *</Label>
              <Input value={form.legalAddress} onChange={e => update('legalAddress', e.target.value)} placeholder="Via Roma 1, 00100 Roma RM" />
            </div>
          </CardContent>
        </Card>

        {/* Contatti */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <Mail className="h-4 w-4" /> Contatti
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label>Email Amministrativa *</Label>
              <Input type="email" value={form.administrativeEmail} onChange={e => update('administrativeEmail', e.target.value)} placeholder="admin@example.it" />
            </div>
            <div className="space-y-2">
              <Label>PEC</Label>
              <Input type="email" value={form.pec} onChange={e => update('pec', e.target.value)} placeholder="example@pec.it" />
            </div>
            <div className="space-y-2">
              <Label>Telefono</Label>
              <Input value={form.phone} onChange={e => update('phone', e.target.value)} placeholder="+39 06 1234567" />
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="flex justify-end gap-3">
        <Button variant="outline" onClick={() => navigate('/admin/tenants')} disabled={isSaving}>
          Annulla
        </Button>
        <Button className="gap-2" onClick={handleSave} disabled={isSaving}>
          {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
          Crea Tenant
        </Button>
      </div>
    </div>
  );
};

export default TenantCreate;
