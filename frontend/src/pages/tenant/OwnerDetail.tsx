import { useParams, useNavigate } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { ArrowLeft, User, Building2, CreditCard, Mail, Phone, FileText, PowerOff, Power } from 'lucide-react';
import { mockOwners, mockProperties, mockBookings, mockSettlements } from '@/data/mock-data';
import { useState } from 'react';
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
  const owner = mockOwners.find(o => o.owner_id === id);
  const [showDeactivate, setShowDeactivate] = useState(false);

  if (!owner) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-4">
        <p className="text-muted-foreground">Proprietario non trovato</p>
        <Button variant="outline" onClick={() => navigate('/owners')}>Torna ai proprietari</Button>
      </div>
    );
  }

  const ownerProperties = mockProperties.filter(p => p.owner_id === owner.owner_id);
  const ownerBookings = mockBookings.filter(b => b.owner_name === `${owner.first_name} ${owner.last_name}`).slice(0, 5);
  const ownerSettlements = mockSettlements.filter(s => s.owner_id === owner.owner_id);

  const totalRevenue = ownerBookings.reduce((sum, b) => sum + b.gross_amount, 0);
  const totalNet = ownerBookings.reduce((sum, b) => sum + b.owner_net_amount, 0);

  const handleToggleStatus = () => {
    toast({
      title: owner.status === 'active' ? 'Proprietario disattivato' : 'Proprietario riattivato',
      description: `${owner.first_name} ${owner.last_name} è stato ${owner.status === 'active' ? 'disattivato' : 'riattivato'}.`,
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
          <h1 className="text-2xl font-bold">{owner.first_name} {owner.last_name}</h1>
          <p className="text-sm text-muted-foreground">{ownerTypeLabels[owner.owner_type]} · {regimeLabels[owner.fiscal_regime]}</p>
        </div>
        <Badge variant={owner.status === 'active' ? 'default' : 'secondary'} className="text-sm">
          {owner.status === 'active' ? 'Attivo' : 'Inattivo'}
        </Badge>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Anagrafica */}
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2 text-base"><User className="h-4 w-4" /> Anagrafica</CardTitle></CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex justify-between"><span className="text-muted-foreground">Nome</span><span className="font-medium">{owner.first_name} {owner.last_name}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Tipo</span><Badge variant="outline">{ownerTypeLabels[owner.owner_type]}</Badge></div>
            <Separator />
            {owner.legal_name && (
              <>
                <div className="flex justify-between"><span className="text-muted-foreground">Ragione Sociale</span><span>{owner.legal_name}</span></div>
                <Separator />
              </>
            )}
            <div className="flex justify-between"><span className="text-muted-foreground">Codice Fiscale</span><span className="font-mono text-xs">{owner.tax_code}</span></div>
            <Separator />
            {owner.vat_number && (
              <>
                <div className="flex justify-between"><span className="text-muted-foreground">P.IVA</span><span className="font-mono text-xs">{owner.vat_number}</span></div>
                <Separator />
              </>
            )}
            <div className="flex justify-between"><span className="text-muted-foreground">Registrato il</span><span>{owner.created_at}</span></div>
          </CardContent>
        </Card>

        {/* Dati Fiscali & Contatto */}
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2 text-base"><CreditCard className="h-4 w-4" /> Dati Fiscali & Contatto</CardTitle></CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex justify-between"><span className="text-muted-foreground">Regime Fiscale</span><Badge variant="outline">{regimeLabels[owner.fiscal_regime]}</Badge></div>
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
            <div className="flex justify-between"><span className="text-muted-foreground">Immobili gestiti</span><span className="font-semibold">{ownerProperties.length}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Prenotazioni recenti</span><span className="font-semibold">{ownerBookings.length}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Lordo recente</span><span className="font-mono font-semibold">€{totalRevenue.toFixed(2)}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Netto proprietario</span><span className="font-mono font-semibold text-primary">€{totalNet.toFixed(2)}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Liquidazioni</span><span className="font-semibold">{ownerSettlements.length}</span></div>
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
            {ownerProperties.length === 0 ? (
              <p className="text-sm text-muted-foreground italic">Nessun immobile associato</p>
            ) : ownerProperties.map(p => (
              <div
                key={p.property_id}
                className="flex items-center justify-between p-2 rounded-md hover:bg-muted/50 cursor-pointer"
                onClick={() => navigate(`/properties/${p.property_id}`)}
              >
                <div>
                  <p className="font-medium text-sm">{p.display_name}</p>
                  <p className="text-xs text-muted-foreground">{p.internal_code} · {p.city} · CIN: {p.cin_code}</p>
                </div>
                <Badge variant={p.status === 'active' ? 'default' : 'secondary'} className="text-xs">{p.status === 'active' ? 'Attivo' : 'Inattivo'}</Badge>
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
              {ownerBookings.length === 0 ? (
                <TableRow><TableCell colSpan={8} className="text-center text-muted-foreground py-8">Nessuna prenotazione</TableCell></TableRow>
              ) : ownerBookings.map(b => (
                <TableRow key={b.booking_id} className="cursor-pointer hover:bg-muted/50" onClick={() => navigate(`/bookings/${b.booking_id}`)}>
                  <TableCell className="font-medium">{b.guest_name}</TableCell>
                  <TableCell className="text-sm">{b.property_name}</TableCell>
                  <TableCell><Badge variant="outline" className="text-xs">{b.channel_name}</Badge></TableCell>
                  <TableCell className="text-sm">{b.checkin_date}</TableCell>
                  <TableCell className="text-sm">{b.checkout_date}</TableCell>
                  <TableCell className="text-right font-mono">€{b.gross_amount.toFixed(2)}</TableCell>
                  <TableCell className="text-right font-mono text-primary">€{b.owner_net_amount.toFixed(2)}</TableCell>
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
          <Button variant={owner.status === 'active' ? 'destructive' : 'default'} size="sm" className="gap-2" onClick={() => setShowDeactivate(true)}>
            {owner.status === 'active' ? <><PowerOff className="h-4 w-4" /> Disattiva Proprietario</> : <><Power className="h-4 w-4" /> Riattiva Proprietario</>}
          </Button>
        </CardContent>
      </Card>

      {/* Dialog conferma */}
      <Dialog open={showDeactivate} onOpenChange={setShowDeactivate}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{owner.status === 'active' ? 'Disattiva' : 'Riattiva'} Proprietario</DialogTitle>
            <DialogDescription>
              {owner.status === 'active'
                ? `Sei sicuro di voler disattivare "${owner.first_name} ${owner.last_name}"? Non verranno più generate liquidazioni e documenti.`
                : `Vuoi riattivare "${owner.first_name} ${owner.last_name}"?`}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowDeactivate(false)}>Annulla</Button>
            <Button variant={owner.status === 'active' ? 'destructive' : 'default'} onClick={handleToggleStatus}>Conferma</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default OwnerDetail;
