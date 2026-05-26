import { Card, CardContent } from '@/components/ui/card';
import { Settings, FileText, Bell, MapPin, CreditCard } from 'lucide-react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';

const TenantSettings = () => (
  <div className="space-y-6 max-w-4xl">
    <div>
      <h1 className="text-2xl font-bold">Configurazione Tenant</h1>
      <p className="text-sm text-muted-foreground">Gestisci i parametri del tuo tenant</p>
    </div>

    <Tabs defaultValue="company">
      <TabsList>
        <TabsTrigger value="company">Dati Aziendali</TabsTrigger>
        <TabsTrigger value="fiscal">Parametri Fiscali</TabsTrigger>
        <TabsTrigger value="documents">Policy Documentali</TabsTrigger>
        <TabsTrigger value="notifications">Notifiche</TabsTrigger>
      </TabsList>

      <TabsContent value="company" className="space-y-4 mt-4">
        <Card>
          <CardContent className="p-6 space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2"><Label>Ragione Sociale</Label><Input defaultValue="Casa Vacanze Italia SRL" /></div>
              <div className="space-y-2"><Label>Nome Display</Label><Input defaultValue="Casa Vacanze Italia" /></div>
              <div className="space-y-2"><Label>Codice Fiscale</Label><Input defaultValue="CVITRL80A01H501Z" /></div>
              <div className="space-y-2"><Label>Partita IVA</Label><Input defaultValue="IT12345678901" /></div>
              <div className="space-y-2"><Label>Email Amministrativa</Label><Input defaultValue="admin@casavacanze.it" /></div>
              <div className="space-y-2"><Label>PEC</Label><Input defaultValue="casavacanze@pec.it" /></div>
              <div className="space-y-2"><Label>Telefono</Label><Input defaultValue="+39 06 1234567" /></div>
              <div className="space-y-2"><Label>IBAN</Label><Input defaultValue="IT60X0542811101000000123456" /></div>
            </div>
            <div className="space-y-2"><Label>Indirizzo Sede Legale</Label><Input defaultValue="Via Roma 1, 00100 Roma RM" /></div>
            <div className="flex justify-end"><Button>Salva Modifiche</Button></div>
          </CardContent>
        </Card>
      </TabsContent>

      <TabsContent value="fiscal" className="space-y-4 mt-4">
        <Card>
          <CardContent className="p-6 space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2"><Label>Aliquota Ritenuta Primario (%)</Label><Input type="number" defaultValue="21" /></div>
              <div className="space-y-2"><Label>Aliquota Ritenuta Secondario (%)</Label><Input type="number" defaultValue="26" /></div>
              <div className="space-y-2"><Label>Codice Tributo F24</Label><Input defaultValue="1919" /></div>
              <div className="space-y-2"><Label>Finestra Emissione Documenti (gg)</Label><Input type="number" defaultValue="12" /></div>
            </div>
            <div className="flex items-center justify-between py-2">
              <div><Label>Cedolare Secca Abilitata</Label><p className="text-xs text-muted-foreground">Abilita cedolare secca per proprietari persona fisica</p></div>
              <Switch defaultChecked />
            </div>
            <div className="flex justify-end"><Button>Salva Parametri</Button></div>
          </CardContent>
        </Card>
      </TabsContent>

      <TabsContent value="documents" className="space-y-4 mt-4">
        <Card>
          <CardContent className="p-6 space-y-4">
            <div className="flex items-center justify-between py-2">
              <div><Label>Invio Automatico SDI</Label><p className="text-xs text-muted-foreground">Invia automaticamente i documenti al Sistema di Interscambio</p></div>
              <Switch defaultChecked />
            </div>
            <div className="flex items-center justify-between py-2">
              <div><Label>Deroga Ricevuta Abilitata</Label><p className="text-xs text-muted-foreground">Consenti ai PM di emettere ricevuta semplice con motivazione</p></div>
              <Switch />
            </div>
            <div className="flex items-center justify-between py-2">
              <div><Label>Numerazione Automatica</Label><p className="text-xs text-muted-foreground">Genera automaticamente la numerazione progressiva documenti</p></div>
              <Switch defaultChecked />
            </div>
            <div className="flex justify-end"><Button>Salva Policy</Button></div>
          </CardContent>
        </Card>
      </TabsContent>

      <TabsContent value="notifications" className="space-y-4 mt-4">
        <Card>
          <CardContent className="p-6 space-y-4">
            <div className="flex items-center justify-between py-2">
              <div><Label>Alert Scadenze Documenti</Label><p className="text-xs text-muted-foreground">Notifica quando documenti sono in scadenza</p></div>
              <Switch defaultChecked />
            </div>
            <div className="flex items-center justify-between py-2">
              <div><Label>Alert Scadenze F24</Label><p className="text-xs text-muted-foreground">Notifica prima della scadenza F24</p></div>
              <Switch defaultChecked />
            </div>
            <div className="flex items-center justify-between py-2">
              <div><Label>Notifiche Email</Label><p className="text-xs text-muted-foreground">Invia notifiche via email</p></div>
              <Switch defaultChecked />
            </div>
            <div className="flex justify-end"><Button>Salva Notifiche</Button></div>
          </CardContent>
        </Card>
      </TabsContent>
    </Tabs>
  </div>
);

export default TenantSettings;
