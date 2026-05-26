import { useParams, useNavigate } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { ArrowLeft, Building2, MapPin, User, Hash, Globe, Link2, Power, PowerOff, FileText } from 'lucide-react';
import { mockProperties, mockOwners, mockBookings } from '@/data/mock-data';
import { useState } from 'react';
import { useToast } from '@/hooks/use-toast';

const otaLabels: Record<string, string> = {
  airbnb_id: 'Airbnb',
  booking_id: 'Booking.com',
  vrbo_id: 'Vrbo',
  tripadvisor_id: 'TripAdvisor',
  expedia_id: 'Expedia',
};

const PropertyDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { toast } = useToast();
  const property = mockProperties.find(p => p.property_id === id);
  const [showDeactivate, setShowDeactivate] = useState(false);
  const [showAssignOwner, setShowAssignOwner] = useState(false);
  const [selectedOwner, setSelectedOwner] = useState('');

  if (!property) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-4">
        <p className="text-muted-foreground">Immobile non trovato</p>
        <Button variant="outline" onClick={() => navigate('/properties')}>Torna agli immobili</Button>
      </div>
    );
  }

  const owner = mockOwners.find(o => o.owner_id === property.owner_id);
  const tenantOwners = mockOwners.filter(o => o.tenant_id === property.tenant_id && o.status === 'active');
  const propertyBookings = mockBookings.filter(b => b.property_id === property.property_id).slice(0, 5);

  const handleDeactivate = () => {
    toast({ title: property.status === 'active' ? 'Immobile disattivato' : 'Immobile riattivato', description: `${property.display_name} è stato ${property.status === 'active' ? 'disattivato' : 'riattivato'}.` });
    setShowDeactivate(false);
  };

  const handleAssignOwner = () => {
    const newOwner = mockOwners.find(o => o.owner_id === selectedOwner);
    if (newOwner) {
      toast({ title: 'Proprietario assegnato', description: `${newOwner.first_name} ${newOwner.last_name} è ora il proprietario di ${property.display_name}.` });
    }
    setShowAssignOwner(false);
    setSelectedOwner('');
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => navigate('/properties')}><ArrowLeft className="h-4 w-4" /></Button>
        <div className="flex-1">
          <h1 className="text-2xl font-bold">{property.display_name}</h1>
          <p className="text-sm text-muted-foreground">{property.internal_code} · {property.city}</p>
        </div>
        <Badge variant={property.status === 'active' ? 'default' : 'secondary'} className="text-sm">
          {property.status === 'active' ? 'Attivo' : 'Inattivo'}
        </Badge>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Info generali */}
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2 text-base"><Building2 className="h-4 w-4" /> Informazioni Generali</CardTitle></CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex justify-between"><span className="text-muted-foreground">Codice interno</span><span className="font-mono">{property.internal_code}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Tipologia</span><Badge variant="outline">{property.property_type}</Badge></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Indirizzo</span><span>{property.address}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Città</span><span>{property.city}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Regione</span><span>{property.region}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Creato il</span><span>{property.created_at}</span></div>
          </CardContent>
        </Card>

        {/* CIN & Codici OTA */}
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2 text-base"><Hash className="h-4 w-4" /> Codice CIN & Mappatura OTA</CardTitle></CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex justify-between items-center">
              <span className="text-muted-foreground">Codice CIN</span>
              <span className="font-mono font-semibold text-primary">{property.cin_code}</span>
            </div>
            <Separator />
            <p className="text-xs text-muted-foreground pt-1">Codici OTA per mappatura importazioni</p>
            {Object.entries(property.ota_codes).filter(([, v]) => v).map(([key, value]) => (
              <div key={key} className="flex justify-between items-center">
                <span className="flex items-center gap-2 text-muted-foreground"><Globe className="h-3 w-3" />{otaLabels[key] || key}</span>
                <span className="font-mono text-xs">{value}</span>
              </div>
            ))}
            {Object.values(property.ota_codes).filter(Boolean).length === 0 && (
              <p className="text-xs text-muted-foreground italic">Nessun codice OTA configurato</p>
            )}
          </CardContent>
        </Card>

        {/* Proprietario */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2 text-base"><User className="h-4 w-4" /> Proprietario</CardTitle>
              <Button variant="outline" size="sm" className="gap-1" onClick={() => setShowAssignOwner(true)}>
                <Link2 className="h-3 w-3" /> Cambia
              </Button>
            </div>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            {owner ? (
              <>
                <div className="flex justify-between"><span className="text-muted-foreground">Nome</span><span className="font-medium">{owner.first_name} {owner.last_name}</span></div>
                <Separator />
                <div className="flex justify-between"><span className="text-muted-foreground">CF</span><span className="font-mono text-xs">{owner.tax_code}</span></div>
                <Separator />
                <div className="flex justify-between"><span className="text-muted-foreground">Regime fiscale</span><Badge variant="outline">{owner.fiscal_regime.replace('_', ' ')}</Badge></div>
                <Separator />
                <div className="flex justify-between"><span className="text-muted-foreground">Email</span><span>{owner.email}</span></div>
              </>
            ) : (
              <p className="text-muted-foreground italic">Nessun proprietario assegnato</p>
            )}
          </CardContent>
        </Card>

        {/* Statistiche */}
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2 text-base"><MapPin className="h-4 w-4" /> Statistiche</CardTitle></CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex justify-between"><span className="text-muted-foreground">Annunci attivi</span><span className="font-semibold">{property.listings_count}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Prenotazioni totali</span><span className="font-semibold">{property.bookings_count}</span></div>
          </CardContent>
        </Card>
      </div>

      {/* Ultime prenotazioni */}
      <Card>
        <CardHeader><CardTitle className="text-base">Ultime Prenotazioni</CardTitle></CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Ospite</TableHead>
                <TableHead>Canale</TableHead>
                <TableHead>Check-in</TableHead>
                <TableHead>Check-out</TableHead>
                <TableHead className="text-right">Importo</TableHead>
                <TableHead>Stato</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {propertyBookings.length === 0 ? (
                <TableRow><TableCell colSpan={6} className="text-center text-muted-foreground py-8">Nessuna prenotazione</TableCell></TableRow>
              ) : propertyBookings.map(b => (
                <TableRow key={b.booking_id} className="cursor-pointer hover:bg-muted/50" onClick={() => navigate(`/bookings/${b.booking_id}`)}>
                  <TableCell className="font-medium">{b.guest_name}</TableCell>
                  <TableCell><Badge variant="outline" className="text-xs">{b.channel_name}</Badge></TableCell>
                  <TableCell className="text-sm">{b.checkin_date}</TableCell>
                  <TableCell className="text-sm">{b.checkout_date}</TableCell>
                  <TableCell className="text-right font-mono">€{b.gross_amount.toFixed(2)}</TableCell>
                  <TableCell><Badge variant="secondary" className="text-xs">{b.booking_status}</Badge></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* Azioni */}
      <Card>
        <CardContent className="p-4 flex gap-3">
          <Button variant="default" size="sm" className="gap-2" onClick={() => navigate(`/properties/${property.property_id}/contracts`)}>
            <FileText className="h-4 w-4" /> Contratto & Regole Costi
          </Button>
          <Button variant={property.status === 'active' ? 'destructive' : 'default'} size="sm" className="gap-2" onClick={() => setShowDeactivate(true)}>
            {property.status === 'active' ? <><PowerOff className="h-4 w-4" /> Disattiva Immobile</> : <><Power className="h-4 w-4" /> Riattiva Immobile</>}
          </Button>
        </CardContent>
      </Card>

      {/* Dialog disattiva */}
      <Dialog open={showDeactivate} onOpenChange={setShowDeactivate}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{property.status === 'active' ? 'Disattiva' : 'Riattiva'} Immobile</DialogTitle>
            <DialogDescription>
              {property.status === 'active'
                ? `Sei sicuro di voler disattivare "${property.display_name}"? L'immobile non sarà più disponibile per nuove prenotazioni.`
                : `Vuoi riattivare "${property.display_name}"?`}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowDeactivate(false)}>Annulla</Button>
            <Button variant={property.status === 'active' ? 'destructive' : 'default'} onClick={handleDeactivate}>Conferma</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Dialog assegna proprietario */}
      <Dialog open={showAssignOwner} onOpenChange={setShowAssignOwner}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Assegna Proprietario</DialogTitle>
            <DialogDescription>Seleziona il proprietario a cui assegnare "{property.display_name}"</DialogDescription>
          </DialogHeader>
          <Select value={selectedOwner} onValueChange={setSelectedOwner}>
            <SelectTrigger><SelectValue placeholder="Seleziona proprietario..." /></SelectTrigger>
            <SelectContent>
              {tenantOwners.map(o => (
                <SelectItem key={o.owner_id} value={o.owner_id}>
                  {o.first_name} {o.last_name} — {o.tax_code}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowAssignOwner(false)}>Annulla</Button>
            <Button onClick={handleAssignOwner} disabled={!selectedOwner}>Assegna</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default PropertyDetail;
