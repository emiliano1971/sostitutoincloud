import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { ArrowLeft, Building2, BarChart3, Power, PowerOff, Edit, Loader2, AlertCircle } from 'lucide-react';
import { getTenantById, updateTenantStatus, updateTenant, type TenantDetail as TenantDetailType } from '@/api/tenantApi';
import { useToast } from '@/hooks/use-toast';

const statusColor: Record<string, string> = {
  active:    'bg-success/10 text-success border-success/20',
  draft:     'bg-muted text-muted-foreground',
  suspended: 'bg-warning/10 text-warning border-warning/20',
  closed:    'bg-destructive/10 text-destructive border-destructive/20',
};

const TenantDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { toast } = useToast();
  const [tenant, setTenant] = useState<TenantDetailType | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isToggling, setIsToggling] = useState(false);
  const [showEdit, setShowEdit] = useState(false);
  const [editForm, setEditForm] = useState({
    legalName: '', displayName: '', taxCode: '', vatNumber: '',
    administrativeEmail: '', pec: '', phone: '', legalAddress: '',
  });
  const [isSaving, setIsSaving] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    getTenantById(Number(id))
      .then(setTenant)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, [id]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20 gap-2 text-muted-foreground">
        <Loader2 className="h-5 w-5 animate-spin" /> Caricamento tenant…
      </div>
    );
  }

  if (error || !tenant) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-4">
        <div className="flex items-center gap-2 text-destructive">
          <AlertCircle className="h-5 w-5" />
          <span>{error ?? 'Tenant non trovato'}</span>
        </div>
        <Button variant="outline" onClick={() => navigate('/admin/tenants')}>Torna ai tenant</Button>
      </div>
    );
  }

  const handleToggleStatus = async () => {
    const nuovoStato = tenant.stato === 'active' ? 'suspended' : 'active';
    setIsToggling(true);
    try {
      const updated = await updateTenantStatus(tenant.id, nuovoStato);
      setTenant(updated);
      toast({
        title: updated.stato === 'active' ? 'Tenant riattivato' : 'Tenant sospeso',
        description: `${updated.displayName} è ora ${updated.stato}.`,
      });
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    } finally {
      setIsToggling(false);
    }
  };

  const canToggle = tenant.stato === 'active' || tenant.stato === 'suspended';

  const handleOpenEdit = () => {
    setEditForm({
      legalName:          tenant.legalName ?? '',
      displayName:        tenant.displayName ?? '',
      taxCode:            tenant.taxCode ?? '',
      vatNumber:          tenant.vatNumber ?? '',
      administrativeEmail: tenant.administrativeEmail ?? '',
      pec:                tenant.pec ?? '',
      phone:              tenant.phone ?? '',
      legalAddress:       tenant.legalAddress ?? '',
    });
    setEditError(null);
    setShowEdit(true);
  };

  const handleSaveEdit = async () => {
    if (!editForm.legalName.trim())          { setEditError('Ragione sociale obbligatoria'); return; }
    if (!editForm.displayName.trim())        { setEditError('Nome display obbligatorio'); return; }
    if (!editForm.administrativeEmail.trim()){ setEditError('Email amministrativa obbligatoria'); return; }
    if (!editForm.legalAddress.trim())       { setEditError('Indirizzo sede legale obbligatorio'); return; }
    if (editForm.taxCode && editForm.taxCode.trim().length !== 16) {
      setEditError('Il codice fiscale deve avere esattamente 16 caratteri'); return;
    }
    setIsSaving(true);
    setEditError(null);
    try {
      const updated = await updateTenant(tenant.id, {
        legalName:          editForm.legalName.trim() || undefined,
        displayName:        editForm.displayName.trim() || undefined,
        taxCode:            editForm.taxCode.trim() || undefined,
        vatNumber:          editForm.vatNumber.trim() || undefined,
        administrativeEmail: editForm.administrativeEmail.trim() || undefined,
        pec:                editForm.pec.trim() || undefined,
        phone:              editForm.phone.trim() || undefined,
        legalAddress:       editForm.legalAddress.trim() || undefined,
      });
      setTenant(updated);
      setShowEdit(false);
      toast({ title: 'Tenant aggiornato', description: `I dati di ${updated.displayName} sono stati aggiornati.` });
    } catch (err) {
      setEditError((err as Error).message);
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
        <div className="flex-1">
          <h1 className="text-2xl font-bold">{tenant.displayName}</h1>
          <p className="text-sm text-muted-foreground">{tenant.legalName}</p>
        </div>
        <Badge variant="outline" className={statusColor[tenant.stato] ?? ''}>{tenant.stato}</Badge>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Anagrafica */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2 text-base">
                <Building2 className="h-4 w-4" /> Anagrafica
              </CardTitle>
              <Button variant="outline" size="sm" className="gap-1" onClick={handleOpenEdit}>
                <Edit className="h-3.5 w-3.5" /> Modifica Dati
              </Button>
            </div>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex justify-between"><span className="text-muted-foreground">Ragione Sociale</span><span className="font-medium">{tenant.legalName}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Codice Fiscale</span><span className="font-mono text-xs">{tenant.taxCode}</span></div>
            <Separator />
            {tenant.vatNumber && (
              <>
                <div className="flex justify-between"><span className="text-muted-foreground">Partita IVA</span><span className="font-mono text-xs">{tenant.vatNumber}</span></div>
                <Separator />
              </>
            )}
            <div className="flex justify-between"><span className="text-muted-foreground">Email</span><span>{tenant.administrativeEmail}</span></div>
            <Separator />
            {tenant.pec && (
              <>
                <div className="flex justify-between"><span className="text-muted-foreground">PEC</span><span>{tenant.pec}</span></div>
                <Separator />
              </>
            )}
            {tenant.phone && (
              <>
                <div className="flex justify-between"><span className="text-muted-foreground">Telefono</span><span>{tenant.phone}</span></div>
                <Separator />
              </>
            )}
            <div className="flex justify-between"><span className="text-muted-foreground">Sede Legale</span><span className="text-right max-w-[60%]">{tenant.legalAddress}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Creato il</span><span>{tenant.createdAt ? new Date(tenant.createdAt).toLocaleDateString('it-IT') : '—'}</span></div>
          </CardContent>
        </Card>

        {/* Statistiche + Stato */}
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <BarChart3 className="h-4 w-4" /> Statistiche
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <div className="flex justify-between"><span className="text-muted-foreground">Immobili</span><span className="font-semibold">{tenant.propertiesCount}</span></div>
              <Separator />
              <div className="flex justify-between"><span className="text-muted-foreground">Proprietari</span><span className="font-semibold">{tenant.ownersCount}</span></div>
              <Separator />
              <div className="flex justify-between"><span className="text-muted-foreground">Prenotazioni</span><span className="font-semibold">{tenant.bookingsCount}</span></div>
              {tenant.activatedAt && (
                <>
                  <Separator />
                  <div className="flex justify-between"><span className="text-muted-foreground">Attivato il</span><span>{new Date(tenant.activatedAt).toLocaleDateString('it-IT')}</span></div>
                </>
              )}
            </CardContent>
          </Card>

          {/* Stato */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Gestione Stato</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium">Stato attuale</p>
                  <Badge variant="outline" className={`mt-1 ${statusColor[tenant.stato] ?? ''}`}>{tenant.stato}</Badge>
                </div>
              </div>
              {canToggle && (
                <Button
                  variant={tenant.stato === 'active' ? 'destructive' : 'default'}
                  size="sm"
                  className="gap-2 w-full"
                  disabled={isToggling}
                  onClick={handleToggleStatus}
                >
                  {isToggling
                    ? <Loader2 className="h-4 w-4 animate-spin" />
                    : tenant.stato === 'active'
                      ? <><PowerOff className="h-4 w-4" /> Sospendi Tenant</>
                      : <><Power className="h-4 w-4" /> Riattiva Tenant</>}
                </Button>
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      {/* Dialog modifica anagrafica */}
      <Dialog open={showEdit} onOpenChange={setShowEdit}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Modifica Dati Tenant</DialogTitle>
            <DialogDescription>Aggiorna i dati anagrafici e di contatto del tenant.</DialogDescription>
          </DialogHeader>

          {editError && (
            <div className="rounded-md border border-destructive bg-destructive/10 px-4 py-2 text-sm text-destructive">
              {editError}
            </div>
          )}

          <div className="space-y-4 py-2">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1">
                <Label>Ragione Sociale *</Label>
                <Input value={editForm.legalName} onChange={e => setEditForm(f => ({ ...f, legalName: e.target.value }))} />
              </div>
              <div className="space-y-1">
                <Label>Nome Display *</Label>
                <Input value={editForm.displayName} onChange={e => setEditForm(f => ({ ...f, displayName: e.target.value }))} />
              </div>
              <div className="space-y-1">
                <Label>Codice Fiscale (16 caratteri)</Label>
                <Input value={editForm.taxCode} onChange={e => setEditForm(f => ({ ...f, taxCode: e.target.value.toUpperCase() }))} className="font-mono uppercase" maxLength={16} />
              </div>
              <div className="space-y-1">
                <Label>Partita IVA</Label>
                <Input value={editForm.vatNumber} onChange={e => setEditForm(f => ({ ...f, vatNumber: e.target.value }))} className="font-mono" maxLength={11} />
              </div>
              <div className="space-y-1">
                <Label>Email Amministrativa *</Label>
                <Input type="email" value={editForm.administrativeEmail} onChange={e => setEditForm(f => ({ ...f, administrativeEmail: e.target.value }))} />
              </div>
              <div className="space-y-1">
                <Label>PEC</Label>
                <Input type="email" value={editForm.pec} onChange={e => setEditForm(f => ({ ...f, pec: e.target.value }))} />
              </div>
              <div className="space-y-1">
                <Label>Telefono</Label>
                <Input value={editForm.phone} onChange={e => setEditForm(f => ({ ...f, phone: e.target.value }))} />
              </div>
            </div>
            <div className="space-y-1">
              <Label>Indirizzo Sede Legale *</Label>
              <Input value={editForm.legalAddress} onChange={e => setEditForm(f => ({ ...f, legalAddress: e.target.value }))} />
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
    </div>
  );
};

export default TenantDetail;
