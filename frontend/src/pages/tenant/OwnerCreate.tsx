import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Separator } from '@/components/ui/separator';
import { ArrowLeft, User, Phone, CreditCard, Save, Loader2 } from 'lucide-react';
import { createOwner } from '@/api/ownerApi';
import { useLookup } from '@/contexts/LookupContext';
import { useToast } from '@/hooks/use-toast';

const OwnerCreate = () => {
  const navigate = useNavigate();
  const { toast } = useToast();
  const { lookups } = useLookup();
  const [isSaving, setIsSaving] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const [form, setForm] = useState({
    ownerType: 'persona_fisica',
    firstName: '',
    lastName: '',
    legalName: '',
    taxCode: '',
    vatNumber: '',
    email: '',
    phone: '',
    iban: '',
    regimeCodice: '',
  });

  const update = (field: string, value: string) => {
    setServerError(null);
    setForm(prev => ({ ...prev, [field]: value }));
  };

  const isPersonaFisica = form.ownerType === 'persona_fisica';

  const validate = (): string | null => {
    if (!form.ownerType) return 'Tipo proprietario obbligatorio';
    if (isPersonaFisica && !form.firstName.trim()) return 'Nome obbligatorio';
    if (isPersonaFisica && !form.lastName.trim()) return 'Cognome obbligatorio';
    if (!isPersonaFisica && !form.legalName.trim()) return 'Ragione sociale obbligatoria';
    if (!form.taxCode.trim()) return 'Codice fiscale obbligatorio';
    if (form.taxCode.trim().length !== 16) return 'Il codice fiscale deve avere esattamente 16 caratteri';
    if (!form.regimeCodice) return 'Regime fiscale obbligatorio';
    if (!form.email.trim()) return 'Email obbligatoria';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) return 'Formato email non valido';
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
      await createOwner({
        ownerType: form.ownerType,
        firstName: form.firstName.trim() || undefined,
        lastName: form.lastName.trim() || undefined,
        legalName: form.legalName.trim() || undefined,
        taxCode: form.taxCode.trim(),
        vatNumber: form.vatNumber.trim() || undefined,
        email: form.email.trim(),
        phone: form.phone.trim() || undefined,
        iban: form.iban.trim() || undefined,
        fkRegimeFiscaleId: lookups?.regimiFiscali.find(r => r.codice === form.regimeCodice)?.id,
      });
      toast({ title: 'Proprietario creato', description: `${form.firstName || form.legalName} è stato aggiunto.` });
      navigate('/owners');
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
        <Button variant="ghost" size="icon" onClick={() => navigate('/owners')}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <h1 className="text-2xl font-bold">Nuovo Proprietario</h1>
      </div>

      {serverError && (
        <div className="rounded-md border border-destructive bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {serverError}
        </div>
      )}

      <div className="grid gap-6 md:grid-cols-2">
        {/* Anagrafica */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <User className="h-4 w-4" /> Anagrafica
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label>Tipo proprietario *</Label>
              <Select value={form.ownerType} onValueChange={v => update('ownerType', v)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="persona_fisica">Persona Fisica</SelectItem>
                  <SelectItem value="piva">Partita IVA</SelectItem>
                  <SelectItem value="societa">Società</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {isPersonaFisica ? (
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label>Nome *</Label>
                  <Input value={form.firstName} onChange={e => update('firstName', e.target.value)} placeholder="Mario" />
                </div>
                <div className="space-y-2">
                  <Label>Cognome *</Label>
                  <Input value={form.lastName} onChange={e => update('lastName', e.target.value)} placeholder="Rossi" />
                </div>
              </div>
            ) : (
              <div className="space-y-2">
                <Label>Ragione Sociale *</Label>
                <Input value={form.legalName} onChange={e => update('legalName', e.target.value)} placeholder="Rossi S.r.l." />
              </div>
            )}

            <Separator />

            <div className="space-y-2">
              <Label>Codice Fiscale * (16 caratteri)</Label>
              <Input
                value={form.taxCode}
                onChange={e => update('taxCode', e.target.value.toUpperCase())}
                placeholder="RSSMRA80A01H501Z"
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
          </CardContent>
        </Card>

        <div className="space-y-6">
          {/* Contatti */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <Phone className="h-4 w-4" /> Contatti
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label>Email *</Label>
                <Input
                  type="email"
                  value={form.email}
                  onChange={e => update('email', e.target.value)}
                  placeholder="mario.rossi@email.it"
                />
              </div>
              <div className="space-y-2">
                <Label>Telefono</Label>
                <Input value={form.phone} onChange={e => update('phone', e.target.value)} placeholder="+39 333 1234567" />
              </div>
              <div className="space-y-2">
                <Label>IBAN</Label>
                <Input
                  value={form.iban}
                  onChange={e => update('iban', e.target.value.toUpperCase())}
                  placeholder="IT60X0542811101000000123456"
                  className="font-mono"
                />
              </div>
            </CardContent>
          </Card>

          {/* Fiscale */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <CreditCard className="h-4 w-4" /> Regime Fiscale
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                <Label>Regime</Label>
                <Select value={form.regimeCodice} onValueChange={v => update('regimeCodice', v)}>
                  <SelectTrigger><SelectValue placeholder="Seleziona regime fiscale" /></SelectTrigger>
                  <SelectContent>
                    {(lookups?.regimiFiscali ?? []).filter(r => r.attivo).map(r => (
                      <SelectItem key={r.codice} value={r.codice}>{r.descrizione}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      <div className="flex justify-end gap-3">
        <Button variant="outline" onClick={() => navigate('/owners')} disabled={isSaving}>
          Annulla
        </Button>
        <Button className="gap-2" onClick={handleSave} disabled={isSaving}>
          {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
          Crea Proprietario
        </Button>
      </div>
    </div>
  );
};

export default OwnerCreate;
