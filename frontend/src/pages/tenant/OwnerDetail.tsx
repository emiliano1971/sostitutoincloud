import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { ArrowLeft, User, Building2, CreditCard, Mail, Phone, FileText, PowerOff, Power, Edit, Loader2, AlertCircle } from 'lucide-react';
import { getOwnerById, updateOwnerStatus, updateOwner, type OwnerDetail as OwnerDetailType } from '@/api/ownerApi';
import { useLookup } from '@/contexts/LookupContext';
import { getProperties, type PropertyListItem } from '@/api/propertyApi';
import { getBookings, type BookingListItem } from '@/api/bookingApi';
import { useToast } from '@/hooks/use-toast';

const ownerTypeLabels: Record<string, string> = {
  persona_fisica: 'Persona Fisica',
  piva: 'Partita IVA',
  societa: 'Società',
};

const OwnerDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { toast } = useToast();
  const { lookups, getLabelByCodice } = useLookup();
  const [owner, setOwner] = useState<OwnerDetailType | null>(null);
  const [properties, setProperties] = useState<PropertyListItem[]>([]);
  const [recentBookings, setRecentBookings] = useState<BookingListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showDeactivate, setShowDeactivate] = useState(false);
  const [showEdit, setShowEdit] = useState(false);
  const [editForm, setEditForm] = useState({
    firstName: '', lastName: '', legalName: '', taxCode: '',
    vatNumber: '', email: '', phone: '', iban: '', fiscalRegimeCodice: 'cedolare_secca',
  });
  const [isSaving, setIsSaving] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

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

  const handleOpenEdit = () => {
    setEditForm({
      firstName: owner.firstName ?? '',
      lastName: owner.lastName ?? '',
      legalName: owner.legalName ?? '',
      taxCode: owner.taxCode ?? '',
      vatNumber: owner.vatNumber ?? '',
      email: owner.email ?? '',
      phone: owner.phone ?? '',
      iban: owner.iban ?? '',
      fiscalRegimeCodice: owner.fiscalRegime ?? 'cedolare_secca',
    });
    setEditError(null);
    setShowEdit(true);
  };

  const handleSaveEdit = async () => {
    if (!editForm.firstName.trim()) { setEditError('Nome obbligatorio'); return; }
    if (!editForm.taxCode.trim() || editForm.taxCode.trim().length !== 16) {
      setEditError('Il codice fiscale deve avere esattamente 16 caratteri'); return;
    }
    if (!editForm.email.trim()) { setEditError('Email obbligatoria'); return; }
    setIsSaving(true);
    setEditError(null);
    try {
      const updated = await updateOwner(owner.id, {
        firstName:         editForm.firstName.trim() || undefined,
        lastName:          editForm.lastName.trim() || undefined,
        legalName:         editForm.legalName.trim() || undefined,
        taxCode:           editForm.taxCode.trim(),
        vatNumber:         editForm.vatNumber.trim() || undefined,
        email:             editForm.email.trim(),
        phone:             editForm.phone.trim() || undefined,
        iban:              editForm.iban.trim() || undefined,
        fkRegimeFiscaleId: lookups?.regimiFiscali.find(r => r.codice === editForm.fiscalRegimeCodice)?.id,
      });
      setOwner(updated);
      setShowEdit(false);
      toast({ title: 'Proprietario aggiornato', description: `I dati di ${updated.firstName} ${updated.lastName} sono stati aggiornati.` });
    } catch (err) {
      setEditError((err as Error).message);
    } finally {
      setIsSaving(false);
    }
  };

  const handleToggleStatus = async () => {
    try {
      const updated = await updateOwnerStatus(owner.id, !owner.attivo);
      setOwner(updated);
      toast({
        title: updated.attivo ? 'Proprietario riattivato' : 'Proprietario disattivato',
        description: `${updated.firstName} ${updated.lastName} è stato ${updated.attivo ? 'riattivato' : 'disattivato'}.`,
      });
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    } finally {
      setShowDeactivate(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => navigate('/owners')}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div className="flex-1">
          <h1 className="text-2xl font-bold">{owner.firstName} {owner.lastName}</h1>
          <p className="text-sm text-muted-foreground">{ownerTypeLabels[owner.ownerType]} · {getLabelByCodice(lookups?.regimiFiscali ?? [], owner.fiscalRegime)}</p>
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
            <div className="flex justify-between"><span className="text-muted-foreground">Regime Fiscale</span><Badge variant="outline">{getLabelByCodice(lookups?.regimiFiscali ?? [], owner.fiscalRegime)}</Badge></div>
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
          <Button variant="outline" size="sm" className="gap-2" onClick={handleOpenEdit}>
            <Edit className="h-4 w-4" /> Modifica Dati
          </Button>
          <Button variant={owner.attivo ? 'destructive' : 'default'} size="sm" className="gap-2" onClick={() => setShowDeactivate(true)}>
            {owner.attivo ? <><PowerOff className="h-4 w-4" /> Disattiva Proprietario</> : <><Power className="h-4 w-4" /> Riattiva Proprietario</>}
          </Button>
        </CardContent>
      </Card>

      {/* Dialog modifica anagrafica */}
      <Dialog open={showEdit} onOpenChange={setShowEdit}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Modifica Dati Proprietario</DialogTitle>
            <DialogDescription>Aggiorna i dati anagrafici, i contatti e il regime fiscale.</DialogDescription>
          </DialogHeader>

          {editError && (
            <div className="rounded-md border border-destructive bg-destructive/10 px-4 py-2 text-sm text-destructive">
              {editError}
            </div>
          )}

          <div className="space-y-5 py-2">
            {/* Anagrafica */}
            <div className="space-y-3">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Anagrafica</p>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <Label>Nome *</Label>
                  <Input value={editForm.firstName} onChange={e => setEditForm(f => ({ ...f, firstName: e.target.value }))} />
                </div>
                <div className="space-y-1">
                  <Label>Cognome</Label>
                  <Input value={editForm.lastName} onChange={e => setEditForm(f => ({ ...f, lastName: e.target.value }))} />
                </div>
              </div>
              {owner.ownerType !== 'persona_fisica' && (
                <div className="space-y-1">
                  <Label>Ragione Sociale</Label>
                  <Input value={editForm.legalName} onChange={e => setEditForm(f => ({ ...f, legalName: e.target.value }))} />
                </div>
              )}
              <div className="space-y-1">
                <Label>Codice Fiscale * (16 caratteri)</Label>
                <Input
                  value={editForm.taxCode}
                  onChange={e => setEditForm(f => ({ ...f, taxCode: e.target.value.toUpperCase() }))}
                  className="font-mono uppercase"
                  maxLength={16}
                />
              </div>
              {owner.ownerType !== 'persona_fisica' && (
                <div className="space-y-1">
                  <Label>Partita IVA</Label>
                  <Input value={editForm.vatNumber} onChange={e => setEditForm(f => ({ ...f, vatNumber: e.target.value }))} className="font-mono" maxLength={11} />
                </div>
              )}
            </div>

            <Separator />

            {/* Contatti & IBAN */}
            <div className="space-y-3">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Contatti & IBAN</p>
              <div className="space-y-1">
                <Label>Email *</Label>
                <Input type="email" value={editForm.email} onChange={e => setEditForm(f => ({ ...f, email: e.target.value }))} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <Label>Telefono</Label>
                  <Input value={editForm.phone} onChange={e => setEditForm(f => ({ ...f, phone: e.target.value }))} />
                </div>
                <div className="space-y-1">
                  <Label>IBAN</Label>
                  <Input value={editForm.iban} onChange={e => setEditForm(f => ({ ...f, iban: e.target.value.toUpperCase() }))} className="font-mono" />
                </div>
              </div>
            </div>

            <Separator />

            {/* Fiscale */}
            <div className="space-y-3">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Regime Fiscale</p>
              <div className="space-y-1">
                <Label>Regime</Label>
                <Select value={editForm.fiscalRegimeCodice} onValueChange={v => setEditForm(f => ({ ...f, fiscalRegimeCodice: v }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {(lookups?.regimiFiscali ?? []).filter(r => r.attivo).map(r => (
                      <SelectItem key={r.codice} value={r.codice}>{r.descrizione}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setShowEdit(false)} disabled={isSaving}>Annulla</Button>
            <Button onClick={handleSaveEdit} disabled={isSaving} className="gap-2">
              {isSaving && <Loader2 className="h-4 w-4 animate-spin" />}
              Salva
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

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
