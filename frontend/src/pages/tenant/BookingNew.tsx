import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { ArrowLeft, Loader2, AlertCircle } from 'lucide-react';
import ComuneAutocomplete from '@/components/ComuneAutocomplete';
import { get } from '@/lib/apiClient';
import { getProperties, type PropertyListItem } from '@/api/propertyApi';
import { createBooking, type BookingCreateRequest } from '@/api/bookingApi';
import { useToast } from '@/hooks/use-toast';

interface CanaleOtaOption {
  id: number;
  nome: string;
  attivo: boolean;
}

// Codici allineati a GuestEditDialog: lo stesso booking può essere modificato di lì.
const DOC_TYPES: Array<{ codice: string; label: string }> = [
  { codice: 'CARTA_IDENTITA', label: 'Carta di identità' },
  { codice: 'PASSAPORTO', label: 'Passaporto' },
  { codice: 'PATENTE', label: 'Patente' },
];

// Sentinella del Select: Radix non ammette SelectItem con value vuoto.
const NESSUN_CANALE = 'none';

/** Giorno successivo a una data yyyy-MM-dd, per il min del check-out. */
const giornoDopo = (iso: string): string | undefined => {
  if (!iso) return undefined;
  const d = new Date(`${iso}T00:00:00`);
  if (Number.isNaN(d.getTime())) return undefined;
  d.setDate(d.getDate() + 1);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const g = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${g}`;
};

const BookingNew = () => {
  const navigate = useNavigate();
  const { toast } = useToast();

  const [properties, setProperties] = useState<PropertyListItem[]>([]);
  const [canali, setCanali] = useState<CanaleOtaOption[]>([]);

  // Dati prenotazione
  const [propertyId, setPropertyId] = useState('');
  const [canaleId, setCanaleId] = useState(NESSUN_CANALE);
  const [externalBookingId, setExternalBookingId] = useState('');
  const [checkin, setCheckin] = useState('');
  const [checkout, setCheckout] = useState('');
  const [guests, setGuests] = useState('1');
  const [grossAmount, setGrossAmount] = useState('');

  // Dati ospite
  const [guestName, setGuestName] = useState('');
  const [taxCode, setTaxCode] = useState('');
  const [birthDate, setBirthDate] = useState('');
  const [sesso, setSesso] = useState('');
  const [birthPlace, setBirthPlace] = useState('');
  const [docType, setDocType] = useState('');
  const [docNumber, setDocNumber] = useState('');
  const [country, setCountry] = useState('Italia');
  const [address, setAddress] = useState('');
  const [phone, setPhone] = useState('');

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getProperties(true)
      .then(setProperties)
      .catch(e => toast({ title: 'Impossibile caricare gli immobili', description: (e as Error).message, variant: 'destructive' }));
    get<CanaleOtaOption[]>('/canali-ota')
      .then(list => setCanali(list.filter(c => c.attivo)))
      .catch(e => toast({ title: 'Impossibile caricare i canali OTA', description: (e as Error).message, variant: 'destructive' }));
  }, [toast]);

  const validate = (): string | null => {
    if (!propertyId) return 'Selezionare l\'immobile';
    if (!checkin) return 'Il check-in è obbligatorio';
    if (!checkout) return 'Il check-out è obbligatorio';
    if (checkout <= checkin) return 'Il check-out deve essere successivo al check-in';
    if (!guests || Number(guests) < 1) return 'Il numero di ospiti deve essere almeno 1';
    if (!grossAmount || Number(grossAmount) <= 0) return 'Il lordo ospite deve essere maggiore di zero';
    if (!guestName.trim()) return 'Il nome dell\'ospite è obbligatorio';
    return null;
  };

  const handleSave = async () => {
    const errore = validate();
    if (errore) {
      setError(errore);
      return;
    }
    setError(null);
    setSaving(true);
    try {
      const payload: BookingCreateRequest = {
        fkPropertyId: Number(propertyId),
        fkCanaleOtaId: canaleId !== NESSUN_CANALE ? Number(canaleId) : undefined,
        externalBookingId: externalBookingId.trim() || undefined,
        checkinDate: checkin,
        checkoutDate: checkout,
        guests: Number(guests),
        grossAmount: Number(grossAmount),
        guestName: guestName.trim(),
        guestTaxCode: taxCode.trim() || undefined,
        guestBirthDate: birthDate || undefined,
        guestSesso: sesso || undefined,
        guestBirthPlace: birthPlace.trim() || undefined,
        guestDocType: docType || undefined,
        guestDocNumber: docNumber.trim() || undefined,
        guestCountry: country.trim() || undefined,
        guestAddress: address.trim() || undefined,
        guestPhone: phone.trim() || undefined,
      };
      const created = await createBooking(payload);
      toast({ title: 'Prenotazione creata', description: created.externalBookingId });
      navigate(`/bookings/${created.id}`);
    } catch (e) {
      const messaggio = (e as Error).message;
      setError(messaggio);
      toast({ title: 'Impossibile salvare la prenotazione', description: messaggio, variant: 'destructive' });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon" onClick={() => navigate('/bookings')} title="Torna alle prenotazioni">
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div>
          <h1 className="text-2xl font-bold">Nuova Prenotazione</h1>
          <p className="text-sm text-muted-foreground">
            Split economico, ritenuta e tassa di soggiorno sono calcolati automaticamente al salvataggio.
          </p>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        {/* ── Dati prenotazione ───────────────────────────────────────────── */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Dati Prenotazione</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-1">
              <Label className="text-xs">Immobile *</Label>
              <Select
                value={propertyId}
                onValueChange={v => { setPropertyId(v); setCanaleId(NESSUN_CANALE); }}
              >
                <SelectTrigger><SelectValue placeholder="Seleziona immobile" /></SelectTrigger>
                <SelectContent>
                  {properties.map(p => (
                    <SelectItem key={p.id} value={String(p.id)}>
                      {p.displayName} — {p.city}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <Label className="text-xs">Canale OTA</Label>
              <Select value={canaleId} onValueChange={setCanaleId}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={NESSUN_CANALE}>Nessun canale</SelectItem>
                  {canali.map(c => (
                    <SelectItem key={c.id} value={String(c.id)}>{c.nome}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <Label className="text-xs">ID Prenotazione</Label>
              <Input
                value={externalBookingId}
                onChange={e => setExternalBookingId(e.target.value)}
                placeholder="Lascia vuoto per generazione automatica (MAN-...)"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label className="text-xs">Check-in *</Label>
                <Input type="date" value={checkin} onChange={e => setCheckin(e.target.value)} />
              </div>
              <div className="space-y-1">
                <Label className="text-xs">Check-out *</Label>
                <Input
                  type="date"
                  value={checkout}
                  min={giornoDopo(checkin)}
                  onChange={e => setCheckout(e.target.value)}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label className="text-xs">N. Ospiti *</Label>
                <Input type="number" min={1} step={1} value={guests} onChange={e => setGuests(e.target.value)} />
              </div>
              <div className="space-y-1">
                <Label className="text-xs">Lordo Ospite (€) *</Label>
                <Input
                  type="number"
                  min={0.01}
                  step="0.01"
                  value={grossAmount}
                  onChange={e => setGrossAmount(e.target.value)}
                  placeholder="0,00"
                />
              </div>
            </div>
          </CardContent>
        </Card>

        {/* ── Dati ospite ─────────────────────────────────────────────────── */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Dati Ospite</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-1">
              <Label className="text-xs">Nome e Cognome *</Label>
              <Input value={guestName} onChange={e => setGuestName(e.target.value)} placeholder="Cognome Nome" />
            </div>

            <div className="space-y-1">
              <Label className="text-xs">Codice Fiscale</Label>
              <Input
                value={taxCode}
                onChange={e => setTaxCode(e.target.value.toUpperCase())}
                className="font-mono"
                placeholder="—"
              />
              <p className="text-xs text-muted-foreground">
                Se vuoto viene calcolato automaticamente se presenti i dati anagrafici
                (data di nascita, sesso e comune di nascita).
              </p>
            </div>

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
                    <SelectItem value="M">Uomo</SelectItem>
                    <SelectItem value="F">Donna</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="space-y-1">
              <Label className="text-xs">Comune di nascita</Label>
              <ComuneAutocomplete
                value={birthPlace}
                onChange={(comune) => setBirthPlace(comune)}
                placeholder="Cerca comune…"
                requireValidComune={false}
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label className="text-xs">Tipo documento</Label>
                <Select value={docType} onValueChange={setDocType}>
                  <SelectTrigger><SelectValue placeholder="—" /></SelectTrigger>
                  <SelectContent>
                    {DOC_TYPES.map(t => <SelectItem key={t.codice} value={t.codice}>{t.label}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1">
                <Label className="text-xs">N. Documento</Label>
                <Input value={docNumber} onChange={e => setDocNumber(e.target.value)} />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label className="text-xs">Paese</Label>
                <Input value={country} onChange={e => setCountry(e.target.value)} />
              </div>
              <div className="space-y-1">
                <Label className="text-xs">Telefono</Label>
                <Input value={phone} onChange={e => setPhone(e.target.value)} placeholder="+39 …" />
              </div>
            </div>

            <div className="space-y-1">
              <Label className="text-xs">Indirizzo</Label>
              <Input value={address} onChange={e => setAddress(e.target.value)} placeholder="Via, civico, città" />
            </div>
          </CardContent>
        </Card>
      </div>

      {error && (
        <div className="flex items-center gap-2 text-destructive text-sm">
          <AlertCircle className="h-4 w-4" /> {error}
        </div>
      )}

      <div className="flex items-center justify-end gap-2">
        <Button variant="outline" onClick={() => navigate('/bookings')} disabled={saving}>
          Annulla
        </Button>
        <Button onClick={handleSave} disabled={saving}>
          {saving && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
          Salva prenotazione
        </Button>
      </div>
    </div>
  );
};

export default BookingNew;
