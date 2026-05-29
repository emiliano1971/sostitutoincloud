import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { ArrowLeft, User, Building2, CreditCard, Mail, Phone, FileText, PowerOff, Power, Loader2, AlertCircle } from 'lucide-react';
import { getOwnerById, type OwnerDetail as OwnerDetailType } from '@/api/ownerApi';
import { getProperties, type PropertyListItem } from '@/api/propertyApi';
import { getBookings, type BookingListItem } from '@/api/bookingApi';
import { useToast } from '@/hooks/use-toast';

const regimeLabels: Record<string, string> = {
  cedolare_secca: 'Cedolare Secca',
  iva_10: 'IVA 10%',
  ordinario: 'Ordinario',
};

const ownerTypeLabels: Record<string, string> = {
  persona_fisica: 'Persona Fisica',
  piva: 'Partita IVA',
  societa: 'Società',
};

const OwnerDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { toast } = useToast();
  const [owner, setOwner] = useState<OwnerDetailType | null>(null);
  const [properties, setProperties] = useState<PropertyListItem[]>([]);
  const [recentBookings, setRecentBookings] = useState<BookingListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showDeactivate, setShowDeactivate] = useState(false);

  useEffect(() => {
    if (!id) return;
    setIsLoading(true);
    getOwnerById(Number(id))
      .then(ownerData => {
        setOwner(ownerData);
        const fullName = `${ownerData.firstName} ${ownerData.lastName}`;
        return Promise.all([
          getProperties(),
          getBookings({ page: 0, size: 5 }),
        ]).then(([allProps, allBookings]) => {
          setProperties(allProps.filter(p => p.ownerName === fullName));
          setRecentBookings(allBookings.filter(b => b.ownerName === fullName));
        });
      })
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, [id]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20 gap-2 text-muted-foreground">
        <Loader2 className="h-5 w-5 animate-spin" />
        <span>Caricamento proprietario…</span>
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

  if (!owner) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-4">
        <p className="text-muted-foreground">Proprietario non trovato</p>
        <Button variant="outline" onClick={() => navigate('/owners')}>Torna ai proprietari</Button>
      </div>
    );
  }

  const handleToggleStatus = () => {
    toast({
      title: owner.attivo ? 'Proprietario disattivato' : 'Proprietario riattivato',
      description: `${owner.firstName} ${owner.lastName} è stato ${owner.attivo ? 'disattivato' : 'riattivato'}.`,
    });
    setShowDeactivate(false);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => navigate('/owners')}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div className="flex-1">
          <h1 className="text-2xl font-bold">{owner.firstName} {owner.lastName}</h1>
          <p className="text-sm text-muted-foreground">{ownerTypeLabels[owner.ownerType]} · {regimeLabels[owner.fiscalRegime]}</p>
        </div>
        <Badge variant={owner.attivo ? 'default' : 'secondary'} className="text-sm">
          {owner.attivo ? 'Attivo' : 'Inattivo'}
        </Badge>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Anagrafica */}
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2 text-base"><User className="h-4 w-4" /> Anagrafica</CardTitle></CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex justify-between"><span className="text-muted-foreground">Nome</span><span className="font-medium">{owner.firstName} {owner.lastName}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Tipo</span><Badge variant="outline">{ownerTypeLabels[owner.ownerType]}</Badge></div>
            <Separator />
            {owner.legalName && (
              <>
                <div className="flex justify-between"><span className="text-muted-foreground">Ragione Sociale</span><span>{owner.legalName}</span></div>
                <Separator />
              </>
            )}
            <div className="flex justify-between"><span className="text-muted-foreground">Codice Fiscale</span><span className="font-mono text-xs">{owner.taxCode}</span></div>
            <Separator />
            {owner.vatNumber && (
              <>
                <div className="flex justify-between"><span className="text-muted-foreground">P.IVA</span><span className="font-mono text-xs">{owner.vatNumber}</span></div>
                <Separator />
              </>
            )}
            <div className="flex justify-between"><span className="text-muted-foreground">Registrato il</span><span>{owner.createdAt}</span></div>
          </CardContent>
        </Card>

        {/* Dati Fiscali & Contatto */}
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2 text-base"><CreditCard className="h-4 w-4" /> Dati Fiscali & Contatto</CardTitle></CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex justify-between"><span className="text-muted-foreground">Regime Fiscale</span><Badge variant="outline">{regimeLabels[owner.fiscalRegime]}</Badge></div>
            <Separator />
            <div className="flex justify-between items-center">
              <span className="text-muted-foreground flex items-center gap-1"><Mail className="h-3 w-3" /> Email</span>
              <span>{owner.email}</span>
            </div>
            <Separator />
            <div className="flex justify-between items-center">
              <span className="text-muted-foreground flex items-center gap-1"><Phone className="h-3 w-3" /> Telefono</span>
              <span>{owner.phone}</span>
            </div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">IBAN</span><span className="font-mono text-xs">{owner.iban}</span></div>
          </CardContent>
        </Card>

        {/* Riepilogo Economico */}
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2 text-base"><FileText className="h-4 w-4" /> Riepilogo Economico</CardTitle></CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex justify-between"><span className="text-muted-foreground">Immobili gestiti</span><span className="font-semibold">{owner.propertiesCount}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Prenotazioni recenti</span><span className="font-semibold">{owner.bookingsCount}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Lordo recente</span><span className="font-mono font-semibold">€{owner.totalGrossAmount.toFixed(2)}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Netto proprietario</span><span className="font-mono font-semibold text-primary">€{owner.totalOwnerNet.toFixed(2)}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Liquidazioni</span><span className="font-semibold">{owner.settlementsCount}</span></div>
          </CardContent>
        </Card>

        {/* Immobili associati */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2 text-base"><Building2 className="h-4 w-4" /> Immobili Associati</CardTitle>
              <Button variant="outline" size="sm" onClick={() => navigate('/properties/new')}>+ Aggiungi</Button>
            </div>
          </CardHeader>
          <CardContent className="space-y-2">
            {properties.length === 0 ? (
              <p className="text-sm text-muted-foreground italic">Nessun immobile associato</p>
            ) : properties.map(p => (
              <div
                key={p.id}
                className="flex items-center justify-between p-2 rounded-md hover:bg-muted/50 cursor-pointer"
                onClick={() => navigate(`/properties/${p.id}`)}
              >
                <div>
                  <p className="font-medium text-sm">{p.displayName}</p>
                  <p className="text-xs text-muted-foreground">{p.internalCode} · {p.city} · CIN: {p.cinCode}</p>
                </div>
                <Badge variant={p.attivo ? 'default' : 'secondary'} className="text-xs">{p.attivo ? 'Attivo' : 'Inattivo'}</Badge>
              </div>
            ))}
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
                <TableHead>Immobile</TableHead>
                <TableHead>Canale</TableHead>
                <TableHead>Check-in</TableHead>
                <TableHead>Check-out</TableHead>
                <TableHead className="text-right">Lordo</TableHead>
                <TableHead className="text-right">Netto</TableHead>
                <TableHead>Stato</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {recentBookings.length === 0 ? (
                <TableRow><TableCell colSpan={8} className="text-center text-muted-foreground py-8">Nessuna prenotazione</TableCell></TableRow>
              ) : recentBookings.map(b => (
                <TableRow key={b.id} className="cursor-pointer hover:bg-muted/50" onClick={() => navigate(`/bookings/${b.id}`)}>
                  <TableCell className="font-medium">{b.guestName}</TableCell>
                  <TableCell className="text-sm">{b.propertyName}</TableCell>
                  <TableCell><Badge variant="outline" className="text-xs">{b.channelName}</Badge></TableCell>
                  <TableCell className="text-sm">{b.checkinDate}</TableCell>
                  <TableCell className="text-sm">{b.checkoutDate}</TableCell>
                  <TableCell className="text-right font-mono">€{b.grossAmount.toFixed(2)}</TableCell>
                  <TableCell className="text-right font-mono text-primary">€{b.ownerNetAmount.toFixed(2)}</TableCell>
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
          <Button variant={owner.attivo ? 'destructive' : 'default'} size="sm" className="gap-2" onClick={() => setShowDeactivate(true)}>
            {owner.attivo ? <><PowerOff className="h-4 w-4" /> Disattiva Proprietario</> : <><Power className="h-4 w-4" /> Riattiva Proprietario</>}
          </Button>
        </CardContent>
      </Card>

      {/* Dialog conferma */}
      <Dialog open={showDeactivate} onOpenChange={setShowDeactivate}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{owner.attivo ? 'Disattiva' : 'Riattiva'} Proprietario</DialogTitle>
            <DialogDescription>
              {owner.attivo
                ? `Sei sicuro di voler disattivare "${owner.firstName} ${owner.lastName}"? Non verranno più generate liquidazioni e documenti.`
                : `Vuoi riattivare "${owner.firstName} ${owner.lastName}"?`}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowDeactivate(false)}>Annulla</Button>
            <Button variant={owner.attivo ? 'destructive' : 'default'} onClick={handleToggleStatus}>Conferma</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default OwnerDetail;
