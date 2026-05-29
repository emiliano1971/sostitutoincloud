import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { ArrowLeft, Building2, MapPin, User, Hash, Globe, Link2, Power, PowerOff, FileText, Loader2, AlertCircle } from 'lucide-react';
import { getPropertyById, type PropertyDetail as PropertyDetailType } from '@/api/propertyApi';
import { getOwnerById, getOwners, type OwnerListItem } from '@/api/ownerApi';
import { getBookings, type BookingListItem } from '@/api/bookingApi';
import { useToast } from '@/hooks/use-toast';

const PropertyDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { toast } = useToast();
  const [property, setProperty] = useState<PropertyDetailType | null>(null);
  const [owner, setOwner] = useState<OwnerListItem | null>(null);
  const [tenantOwners, setTenantOwners] = useState<OwnerListItem[]>([]);
  const [propertyBookings, setPropertyBookings] = useState<BookingListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showDeactivate, setShowDeactivate] = useState(false);
  const [showAssignOwner, setShowAssignOwner] = useState(false);
  const [selectedOwner, setSelectedOwner] = useState('');

  useEffect(() => {
    if (!id) return;
    setIsLoading(true);
    getPropertyById(Number(id))
      .then(propData => {
        setProperty(propData);
        return Promise.all([
          propData.fkOwnerId ? getOwnerById(propData.fkOwnerId) : Promise.resolve(null),
          getOwners(true),
          getBookings({ page: 0, size: 5 }),
        ]).then(([ownerData, allOwners, allBookings]) => {
          setOwner(ownerData);
          setTenantOwners(allOwners);
          setPropertyBookings(allBookings.filter(b => b.fkPropertyId === propData.id));
        });
      })
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, [id]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20 gap-2 text-muted-foreground">
        <Loader2 className="h-5 w-5 animate-spin" />
        <span>Caricamento immobile…</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center py-20 gap-2 text-destructive">
        <AlertCircle className="h-5 w-5" />
        <span>{error}</span>
      </div>
    );
  }

  if (!property) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-4">
        <p className="text-muted-foreground">Immobile non trovato</p>
        <Button variant="outline" onClick={() => navigate('/properties')}>Torna agli immobili</Button>
      </div>
    );
  }

  const handleDeactivate = () => {
    toast({
      title: property.attivo ? 'Immobile disattivato' : 'Immobile riattivato',
      description: `${property.displayName} è stato ${property.attivo ? 'disattivato' : 'riattivato'}.`,
    });
    setShowDeactivate(false);
  };

  const handleAssignOwner = () => {
    const newOwner = tenantOwners.find(o => String(o.id) === selectedOwner);
    if (newOwner) {
      toast({
        title: 'Proprietario assegnato',
        description: `${newOwner.firstName} ${newOwner.lastName} è ora il proprietario di ${property.displayName}.`,
      });
    }
    setShowAssignOwner(false);
    setSelectedOwner('');
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => navigate('/properties')}><ArrowLeft className="h-4 w-4" /></Button>
        <div className="flex-1">
          <h1 className="text-2xl font-bold">{property.displayName}</h1>
          <p className="text-sm text-muted-foreground">{property.internalCode} · {property.city}</p>
        </div>
        <Badge variant={property.attivo ? 'default' : 'secondary'} className="text-sm">
          {property.attivo ? 'Attivo' : 'Inattivo'}
        </Badge>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Info generali */}
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2 text-base"><Building2 className="h-4 w-4" /> Informazioni Generali</CardTitle></CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex justify-between"><span className="text-muted-foreground">Codice interno</span><span className="font-mono">{property.internalCode}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Tipologia</span><Badge variant="outline">{property.propertyType}</Badge></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Indirizzo</span><span>{property.address}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Città</span><span>{property.city}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Regione</span><span>{property.region}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Creato il</span><span>{property.createdAt}</span></div>
          </CardContent>
        </Card>

        {/* CIN & Codici OTA */}
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2 text-base"><Hash className="h-4 w-4" /> Codice CIN & Mappatura OTA</CardTitle></CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex justify-between items-center">
              <span className="text-muted-foreground">Codice CIN</span>
              <span className="font-mono font-semibold text-primary">{property.cinCode}</span>
            </div>
            <Separator />
            <p className="text-xs text-muted-foreground pt-1">Codici OTA per mappatura importazioni</p>
            {property.otaCodes.length > 0 ? property.otaCodes.map(ota => (
              <div key={ota.canaleCodiceName} className="flex justify-between items-center">
                <span className="flex items-center gap-2 text-muted-foreground"><Globe className="h-3 w-3" />{ota.canaleCodiceName}</span>
                <span className="font-mono text-xs">{ota.externalId}</span>
              </div>
            )) : (
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
                <div className="flex justify-between"><span className="text-muted-foreground">Nome</span><span className="font-medium">{owner.firstName} {owner.lastName}</span></div>
                <Separator />
                <div className="flex justify-between"><span className="text-muted-foreground">CF</span><span className="font-mono text-xs">{owner.taxCode}</span></div>
                <Separator />
                <div className="flex justify-between"><span className="text-muted-foreground">Regime fiscale</span><Badge variant="outline">{owner.fiscalRegime.replace('_', ' ')}</Badge></div>
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
            <div className="flex justify-between"><span className="text-muted-foreground">Annunci attivi</span><span className="font-semibold">{property.listingsCount}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Prenotazioni totali</span><span className="font-semibold">{property.bookingsCount}</span></div>
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
                <TableRow key={b.id} className="cursor-pointer hover:bg-muted/50" onClick={() => navigate(`/bookings/${b.id}`)}>
                  <TableCell className="font-medium">{b.guestName}</TableCell>
                  <TableCell><Badge variant="outline" className="text-xs">{b.channelName}</Badge></TableCell>
                  <TableCell className="text-sm">{b.checkinDate}</TableCell>
                  <TableCell className="text-sm">{b.checkoutDate}</TableCell>
                  <TableCell className="text-right font-mono">€{b.grossAmount.toFixed(2)}</TableCell>
                  <TableCell><Badge variant="secondary" className="text-xs">{b.statoPrenotazione}</Badge></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* Azioni */}
      <Card>
        <CardContent className="p-4 flex gap-3">
          <Button variant="default" size="sm" className="gap-2" onClick={() => navigate(`/properties/${property.id}/contracts`)}>
            <FileText className="h-4 w-4" /> Contratto & Regole Costi
          </Button>
          <Button variant={property.attivo ? 'destructive' : 'default'} size="sm" className="gap-2" onClick={() => setShowDeactivate(true)}>
            {property.attivo ? <><PowerOff className="h-4 w-4" /> Disattiva Immobile</> : <><Power className="h-4 w-4" /> Riattiva Immobile</>}
          </Button>
        </CardContent>
      </Card>

      {/* Dialog disattiva */}
      <Dialog open={showDeactivate} onOpenChange={setShowDeactivate}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{property.attivo ? 'Disattiva' : 'Riattiva'} Immobile</DialogTitle>
            <DialogDescription>
              {property.attivo
                ? `Sei sicuro di voler disattivare "${property.displayName}"? L'immobile non sarà più disponibile per nuove prenotazioni.`
                : `Vuoi riattivare "${property.displayName}"?`}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowDeactivate(false)}>Annulla</Button>
            <Button variant={property.attivo ? 'destructive' : 'default'} onClick={handleDeactivate}>Conferma</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Dialog assegna proprietario */}
      <Dialog open={showAssignOwner} onOpenChange={setShowAssignOwner}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Assegna Proprietario</DialogTitle>
            <DialogDescription>Seleziona il proprietario a cui assegnare "{property.displayName}"</DialogDescription>
          </DialogHeader>
          <Select value={selectedOwner} onValueChange={setSelectedOwner}>
            <SelectTrigger><SelectValue placeholder="Seleziona proprietario..." /></SelectTrigger>
            <SelectContent>
              {tenantOwners.map(o => (
                <SelectItem key={o.id} value={String(o.id)}>
                  {o.firstName} {o.lastName} — {o.taxCode}
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
