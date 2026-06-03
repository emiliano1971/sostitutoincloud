import { useEffect, useState } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { getSettings, updateSettings, type TenantSettingsDTO } from '@/api/settingsApi';

type SaveStatus = { type: 'success' | 'error'; message: string } | null;

const TenantSettings = () => {
  const [settings, setSettings] = useState<TenantSettingsDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saveStatus, setSaveStatus] = useState<SaveStatus>(null);
  const [saving, setSaving] = useState(false);
  const [companyForm, setCompanyForm] = useState({
    legalName: '', displayName: '', taxCode: '', vatNumber: '',
    administrativeEmail: '', pec: '', phone: '', legalAddress: '',
  });

  useEffect(() => {
    getSettings()
      .then(s => { setSettings(s); })
      .catch(() => setError('Errore nel caricamento delle impostazioni'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (settings) {
      setCompanyForm({
        legalName: settings.legalName ?? '',
        displayName: settings.displayName ?? '',
        taxCode: settings.taxCode ?? '',
        vatNumber: settings.vatNumber ?? '',
        administrativeEmail: settings.administrativeEmail ?? '',
        pec: settings.pec ?? '',
        phone: settings.phone ?? '',
        legalAddress: settings.legalAddress ?? '',
      });
    }
  }, [settings]);

  const showStatus = (status: SaveStatus) => {
    setSaveStatus(status);
    setTimeout(() => setSaveStatus(null), 4000);
  };

  const handleSave = async (data: Partial<TenantSettingsDTO>) => {
    setSaving(true);
    try {
      const updated = await updateSettings(data);
      setSettings(updated);
      showStatus({ type: 'success', message: 'Impostazioni salvate con successo' });
    } catch {
      showStatus({ type: 'error', message: 'Errore durante il salvataggio' });
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="p-6 text-muted-foreground">Caricamento...</div>;
  if (error || !settings) return <div className="p-6 text-destructive">{error ?? 'Errore'}</div>;

  return (
    <div className="space-y-6 max-w-4xl">
      <div>
        <h1 className="text-2xl font-bold">Configurazione Tenant</h1>
        <p className="text-sm text-muted-foreground">Gestisci i parametri del tuo tenant</p>
      </div>

      {saveStatus && (
        <div className={`px-4 py-3 rounded-lg text-sm font-medium border ${
          saveStatus.type === 'success'
            ? 'bg-success/10 text-success border-success/20'
            : 'bg-destructive/10 text-destructive border-destructive/20'
        }`}>
          {saveStatus.message}
        </div>
      )}

      <Tabs defaultValue="company">
        <TabsList>
          <TabsTrigger value="company">Dati Aziendali</TabsTrigger>
          <TabsTrigger value="fiscal">Parametri Fiscali</TabsTrigger>
          <TabsTrigger value="documents">Policy Documentali</TabsTrigger>
          <TabsTrigger value="notifications">Notifiche</TabsTrigger>
        </TabsList>

        {/* Tab Dati Aziendali */}
        <TabsContent value="company" className="space-y-4 mt-4">
          <Card>
            <CardContent className="p-6 space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label>Ragione Sociale</Label>
                  <Input value={companyForm.legalName} onChange={e => setCompanyForm(f => ({ ...f, legalName: e.target.value }))} />
                </div>
                <div className="space-y-2">
                  <Label>Nome Display</Label>
                  <Input value={companyForm.displayName} onChange={e => setCompanyForm(f => ({ ...f, displayName: e.target.value }))} />
                </div>
                <div className="space-y-2">
                  <Label>Codice Fiscale</Label>
                  <Input value={companyForm.taxCode} onChange={e => setCompanyForm(f => ({ ...f, taxCode: e.target.value }))} className="font-mono" />
                </div>
                <div className="space-y-2">
                  <Label>Partita IVA</Label>
                  <Input value={companyForm.vatNumber} onChange={e => setCompanyForm(f => ({ ...f, vatNumber: e.target.value }))} className="font-mono" />
                </div>
                <div className="space-y-2">
                  <Label>Email Amministrativa</Label>
                  <Input type="email" value={companyForm.administrativeEmail} onChange={e => setCompanyForm(f => ({ ...f, administrativeEmail: e.target.value }))} />
                </div>
                <div className="space-y-2">
                  <Label>PEC</Label>
                  <Input type="email" value={companyForm.pec} onChange={e => setCompanyForm(f => ({ ...f, pec: e.target.value }))} />
                </div>
                <div className="space-y-2">
                  <Label>Telefono</Label>
                  <Input value={companyForm.phone} onChange={e => setCompanyForm(f => ({ ...f, phone: e.target.value }))} />
                </div>
              </div>
              <div className="space-y-2">
                <Label>Indirizzo Sede Legale</Label>
                <Input value={companyForm.legalAddress} onChange={e => setCompanyForm(f => ({ ...f, legalAddress: e.target.value }))} />
              </div>
              <div className="flex justify-end">
                <Button disabled={saving} onClick={() => handleSave(companyForm)}>
                  {saving ? 'Salvataggio...' : 'Salva Modifiche'}
                </Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Tab Parametri Fiscali */}
        <TabsContent value="fiscal" className="space-y-4 mt-4">
          <Card>
            <CardContent className="p-6 space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label>Aliquota Ritenuta Primario (%)</Label>
                  <Input type="number" defaultValue={settings.withholdingRatePrimary}
                    id="withholdingRatePrimary" />
                </div>
                <div className="space-y-2">
                  <Label>Aliquota Ritenuta Secondario (%)</Label>
                  <Input type="number" defaultValue={settings.withholdingRateSecondary}
                    id="withholdingRateSecondary" />
                </div>
                <div className="space-y-2">
                  <Label>Codice Tributo F24</Label>
                  <Input defaultValue={settings.codiceTributoF24} id="codiceTributoF24" />
                </div>
                <div className="space-y-2">
                  <Label>Finestra Emissione Documenti (gg)</Label>
                  <Input type="number" defaultValue={settings.documentWindowDays}
                    id="documentWindowDays" />
                </div>
              </div>
              <div className="flex items-center justify-between py-2">
                <div>
                  <Label>Cedolare Secca Abilitata</Label>
                  <p className="text-xs text-muted-foreground">Abilita cedolare secca per proprietari persona fisica</p>
                </div>
                <Switch id="cedolareSeccaEnabled" defaultChecked={settings.cedolareSeccaEnabled} />
              </div>
              <div className="flex justify-end">
                <Button disabled={saving} onClick={() => {
                  handleSave({
                    withholdingRatePrimary: parseFloat((document.getElementById('withholdingRatePrimary') as HTMLInputElement).value),
                    withholdingRateSecondary: parseFloat((document.getElementById('withholdingRateSecondary') as HTMLInputElement).value),
                    codiceTributoF24: (document.getElementById('codiceTributoF24') as HTMLInputElement).value,
                    documentWindowDays: parseInt((document.getElementById('documentWindowDays') as HTMLInputElement).value),
                    cedolareSeccaEnabled: (document.getElementById('cedolareSeccaEnabled') as HTMLButtonElement).getAttribute('data-state') === 'checked',
                  });
                }}>
                  {saving ? 'Salvataggio...' : 'Salva Parametri'}
                </Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Tab Policy Documentali */}
        <TabsContent value="documents" className="space-y-4 mt-4">
          <Card>
            <CardContent className="p-6 space-y-4">
              <div className="flex items-center justify-between py-2">
                <div>
                  <Label>Invio Automatico SDI</Label>
                  <p className="text-xs text-muted-foreground">Invia automaticamente i documenti al Sistema di Interscambio</p>
                </div>
                <Switch id="sdiAutoSend" defaultChecked={settings.sdiAutoSend} />
              </div>
              <div className="flex items-center justify-between py-2">
                <div>
                  <Label>Deroga Ricevuta Abilitata</Label>
                  <p className="text-xs text-muted-foreground">Consenti ai PM di emettere ricevuta semplice con motivazione</p>
                </div>
                <Switch id="derogaRicevutaEnabled" defaultChecked={settings.derogaRicevutaEnabled} />
              </div>
              <div className="flex items-center justify-between py-2">
                <div>
                  <Label>Numerazione Automatica</Label>
                  <p className="text-xs text-muted-foreground">Genera automaticamente la numerazione progressiva documenti</p>
                </div>
                <Switch id="numerazioneAutomatica" defaultChecked={settings.numerazioneAutomatica} />
              </div>
              <div className="flex justify-end">
                <Button disabled={saving} onClick={() => {
                  handleSave({
                    sdiAutoSend: (document.getElementById('sdiAutoSend') as HTMLButtonElement).getAttribute('data-state') === 'checked',
                    derogaRicevutaEnabled: (document.getElementById('derogaRicevutaEnabled') as HTMLButtonElement).getAttribute('data-state') === 'checked',
                    numerazioneAutomatica: (document.getElementById('numerazioneAutomatica') as HTMLButtonElement).getAttribute('data-state') === 'checked',
                  });
                }}>
                  {saving ? 'Salvataggio...' : 'Salva Policy'}
                </Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Tab Notifiche */}
        <TabsContent value="notifications" className="space-y-4 mt-4">
          <Card>
            <CardContent className="p-6 space-y-4">
              <div className="flex items-center justify-between py-2">
                <div>
                  <Label>Alert Scadenze Documenti</Label>
                  <p className="text-xs text-muted-foreground">Notifica quando documenti sono in scadenza</p>
                </div>
                <Switch id="alertScadenzeDocumenti" defaultChecked={settings.alertScadenzeDocumenti} />
              </div>
              <div className="flex items-center justify-between py-2">
                <div>
                  <Label>Alert Scadenze F24</Label>
                  <p className="text-xs text-muted-foreground">Notifica prima della scadenza F24</p>
                </div>
                <Switch id="alertScadenzeF24" defaultChecked={settings.alertScadenzeF24} />
              </div>
              <div className="flex items-center justify-between py-2">
                <div>
                  <Label>Notifiche Email</Label>
                  <p className="text-xs text-muted-foreground">Invia notifiche via email</p>
                </div>
                <Switch id="notificheEmail" defaultChecked={settings.notificheEmail} />
              </div>
              <div className="flex justify-end">
                <Button disabled={saving} onClick={() => {
                  handleSave({
                    alertScadenzeDocumenti: (document.getElementById('alertScadenzeDocumenti') as HTMLButtonElement).getAttribute('data-state') === 'checked',
                    alertScadenzeF24: (document.getElementById('alertScadenzeF24') as HTMLButtonElement).getAttribute('data-state') === 'checked',
                    notificheEmail: (document.getElementById('notificheEmail') as HTMLButtonElement).getAttribute('data-state') === 'checked',
                  });
                }}>
                  {saving ? 'Salvataggio...' : 'Salva Notifiche'}
                </Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
};

export default TenantSettings;
