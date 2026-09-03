import { useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Loader2, Calculator, AlertCircle, Globe } from 'lucide-react';
import ComuneAutocomplete from '@/components/ComuneAutocomplete';
import { toast } from '@/hooks/use-toast';
import {
  updateBookingGuest, calcolaCodiceFiscale, generaCfEstero,
  type BookingDetail, type GuestUpdateRequest,
} from '@/api/bookingApi';

interface GuestData {
  guestName?: string;
  guestTaxCode?: string;
  guestBirthDate?: string;
  guestSesso?: string;
  guestBirthPlace?: string;
  guestBirthBelfiore?: string;
  guestDocType?: string;
  guestDocNumber?: string;
  guestCountry?: string;
  guestAddress?: string;
  guestPhone?: string;
}

interface GuestEditDialogProps {
  bookingId: number;
  guest: GuestData;
  open: boolean;
  onClose: () => void;
  onSaved: (updated: BookingDetail) => void;
}

const DOC_TYPES = ['CARTA_IDENTITA', 'PASSAPORTO', 'PATENTE', 'ALTRO'];

const GuestEditDialog = ({ bookingId, guest, open, onClose, onSaved }: GuestEditDialogProps) => {
  const [guestName, setGuestName] = useState(guest.guestName ?? '');
  const [birthDate, setBirthDate] = useState(guest.guestBirthDate ?? '');
  const [sesso, setSesso] = useState(guest.guestSesso ?? '');
  const [birthPlace, setBirthPlace] = useState(guest.guestBirthPlace ?? '');
  const [birthBelfiore, setBirthBelfiore] = useState(guest.guestBirthBelfiore ?? '');
  const [docType, setDocType] = useState(guest.guestDocType ?? '');
  const [docNumber, setDocNumber] = useState(guest.guestDocNumber ?? '');
  const [indirizzo, setIndirizzo] = useState(guest.guestAddress ?? '');
  const [telefono, setTelefono] = useState(guest.guestPhone ?? '');
  const [nazione, setNazione] = useState<'Italia' | 'Straniero'>(
    guest.guestCountry === 'Straniero' ? 'Straniero' : 'Italia');
  const [taxCode, setTaxCode] = useState(guest.guestTaxCode ?? '');
  const [calcolando, setCalcolando] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Split nome/cognome dal nome completo
  const parts = guestName.trim().split(/\s+/);
  const cognome = parts[0] ?? '';
  const nome = parts.slice(1).join(' ') || parts[0] || '';

  const canCalc = !!(nome && cognome && birthDate && sesso && birthPlace);

  const handleCalcCf = async () => {
    setCalcolando(true);
    try {
      const cf = await calcolaCodiceFiscale(cognome, nome, birthDate, sesso, birthPlace);
      setTaxCode(cf);
    } catch (e) {
      toast({
        title: 'Impossibile calcolare CF',
        description: (e as Error).message,
        variant: 'destructive',
      });
    } finally {
      setCalcolando(false);
    }
  };

  const handleGeneraEstero = async () => {
    setCalcolando(true);
    try {
      const cf = await generaCfEstero();
      setTaxCode(cf);
      toast({ title: `CF estero generato: ${cf}` });
    } catch (e) {
      toast({ title: 'Impossibile generare CF estero', description: (e as Error).message, variant: 'destructive' });
    } finally {
      setCalcolando(false);
    }
  };

  const handleSave = async () => {
    setError(null);
    setSaving(true);
    try {
      const payload: GuestUpdateRequest = {
        guestName: guestName.trim(),
        guestTaxCode: taxCode || undefined,
        guestBirthDate: birthDate || undefined,
        guestSesso: sesso || undefined,
        guestBirthPlace: birthPlace || undefined,
        guestBirthBelfiore: birthBelfiore || undefined,
        guestDocType: docType || undefined,
        guestDocNumber: docNumber || undefined,
        guestCountry: nazione,
        guestAddress: indirizzo.trim() || undefined,
        guestPhone: telefono.trim() || undefined,
      };
      const updated = await updateBookingGuest(bookingId, payload);
      toast({ title: 'Dati ospite aggiornati' });
      onSaved(updated);
      onClose();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onClose(); }}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Modifica anagrafica ospite</DialogTitle>
          <DialogDescription>Aggiorna i dati dell'ospite e calcola il codice fiscale.</DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          <div className="space-y-1">
            <Label className="text-xs">Nome completo</Label>
            <Input value={guestName} onChange={e => setGuestName(e.target.value)} placeholder="Nome e cognome" />
          </div>

          <div className="space-y-1">
            <Label className="text-xs">Nazione</Label>
            <Select value={nazione} onValueChange={v => setNazione(v as 'Italia' | 'Straniero')}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="Italia">Italia</SelectItem>
                <SelectItem value="Straniero">Straniero</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {nazione === 'Italia' && (
            <>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <Label className="text-xs">Data di nascita</Label>
                  <Input type="date" value={birthDate} onChange={e => setBirthDate(e.target.value)} />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs">Sesso</Label>
                  <Select value={sesso} onValueChange={setSesso}>
                    <SelectTrigger><SelectValue placeholder="—" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="M">M</SelectItem>
                      <SelectItem value="F">F</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div className="space-y-1">
                <Label className="text-xs">Comune di nascita</Label>
                <ComuneAutocomplete
                  value={birthPlace}
                  onChange={(comune, belfiore) => { setBirthPlace(comune); setBirthBelfiore(belfiore); }}
                  placeholder="Cerca comune…"
                />
              </div>
            </>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <Label className="text-xs">Tipo documento</Label>
              <Select value={docType} onValueChange={setDocType}>
                <SelectTrigger><SelectValue placeholder="—" /></SelectTrigger>
                <SelectContent>
                  {DOC_TYPES.map(t => <SelectItem key={t} value={t}>{t.replace('_', ' ')}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Numero documento</Label>
              <Input value={docNumber} onChange={e => setDocNumber(e.target.value)} />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <Label className="text-xs">Indirizzo</Label>
              <Input value={indirizzo} onChange={e => setIndirizzo(e.target.value)} placeholder="Via, civico, città" />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Telefono</Label>
              <Input value={telefono} onChange={e => setTelefono(e.target.value)} placeholder="+39 …" />
            </div>
          </div>

          <div className="space-y-1">
            <Label className="text-xs">Codice Fiscale</Label>
            <div className="flex gap-2">
              <Input
                value={taxCode}
                readOnly
                className="font-mono"
                placeholder={nazione === 'Straniero' ? 'Verrà generato automaticamente' : '—'}
              />
              {nazione === 'Italia' ? (
                <Button type="button" variant="outline" onClick={handleCalcCf} disabled={!canCalc || calcolando} className="gap-2 shrink-0">
                  {calcolando ? <Loader2 className="h-4 w-4 animate-spin" /> : <Calculator className="h-4 w-4" />}
                  Calcola CF
                </Button>
              ) : (
                <Button type="button" variant="outline" onClick={handleGeneraEstero} disabled={calcolando} className="gap-2 shrink-0">
                  {calcolando ? <Loader2 className="h-4 w-4 animate-spin" /> : <Globe className="h-4 w-4" />}
                  Genera CF Straniero
                </Button>
              )}
            </div>
          </div>

          {error && (
            <div className="flex items-center gap-2 text-destructive text-sm">
              <AlertCircle className="h-4 w-4" /> {error}
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={saving}>Annulla</Button>
          <Button onClick={handleSave} disabled={saving || !guestName.trim()}>
            {saving && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
            Salva
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default GuestEditDialog;
