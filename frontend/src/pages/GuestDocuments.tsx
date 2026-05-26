import { useSearchParams, useNavigate } from 'react-router-dom';
import { mockBookings, mockOwners, mockProperties } from '@/data/mock-data';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Cloud, FileText, Receipt, Printer, ArrowLeft } from 'lucide-react';
import loginBg from '@/assets/login-bg.jpg';

const fmt = (v: number) => `€${Math.abs(v).toLocaleString('it-IT', { minimumFractionDigits: 2 })}`;

const GuestDocuments = () => {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const checkin = params.get('checkin');
  const checkout = params.get('checkout');
  const taxCode = params.get('cf')?.toUpperCase();

  const booking = mockBookings.find(
    b => b.checkin_date === checkin && b.checkout_date === checkout && b.guest_tax_code.toUpperCase() === taxCode
  );

  if (!booking) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4 relative" style={{ backgroundImage: `url(${loginBg})`, backgroundSize: 'cover', backgroundPosition: 'center' }}>
        <div className="absolute inset-0 bg-black/40 backdrop-blur-[2px]" />
        <Card className="relative z-10 max-w-md w-full">
          <CardContent className="pt-6 text-center space-y-4">
            <p className="text-destructive font-medium">Nessuna prenotazione trovata con i dati inseriti.</p>
            <Button variant="outline" onClick={() => navigate('/login')} className="gap-2">
              <ArrowLeft className="h-4 w-4" /> Torna al login
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  const owner = mockOwners.find(o => o.owner_id === booking.owner_name ? false : true) || mockOwners[0];
  const ownerMatch = mockOwners.find(o => `${o.first_name} ${o.last_name}` === booking.owner_name);
  const property = mockProperties.find(p => p.property_id === booking.property_id);

  const tenantData = {
    legal_name: 'Casa Vacanze Italia SRL',
    vat_number: 'IT12345678901',
    tax_code: 'CVITRL80A01H501Z',
    address: 'Via Roma 1, 00100 Roma RM',
    pec: 'casavacanze@pec.it',
  };

  // Invoice data
  const riaddebitoOta = booking.ota_commission_amount;
  const riaddebitoPulizie = booking.cleaning_amount;
  const provvigionePm = booking.pm_fee_amount;
  const imponibile = riaddebitoOta + riaddebitoPulizie + provvigionePm;
  const vatRate = 0.22;
  const vatAmount = Math.round(imponibile * vatRate * 100) / 100;
  const totaleFattura = Math.round((imponibile + vatAmount) * 100) / 100;
  const invoiceNumber = `FT-2025-${String(parseInt(booking.booking_id.replace('b', ''))).padStart(4, '0')}`;

  // Receipt data
  const canonLocazione = booking.gross_amount;
  const bolloApplicabile = canonLocazione > 77.47;
  const receiptNumber = `RIC-2025-${String(parseInt(booking.booking_id.replace('b', ''))).padStart(4, '0')}`;

  return (
    <div className="min-h-screen p-4 relative" style={{ backgroundImage: `url(${loginBg})`, backgroundSize: 'cover', backgroundPosition: 'center' }}>
      <div className="absolute inset-0 bg-black/40 backdrop-blur-[2px]" />
      <div className="relative z-10 max-w-4xl mx-auto space-y-6 py-8">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-primary flex items-center justify-center">
              <Cloud className="h-6 w-6 text-primary-foreground" />
            </div>
            <div>
              <h1 className="text-xl font-bold text-white">I tuoi documenti fiscali</h1>
              <p className="text-sm text-white/70">Benvenuto/a {booking.guest_name}</p>
            </div>
          </div>
          <Button variant="outline" size="sm" onClick={() => navigate('/login')} className="gap-2 bg-white/10 text-white border-white/20 hover:bg-white/20">
            <ArrowLeft className="h-4 w-4" /> Indietro
          </Button>
        </div>

        {/* Riepilogo prenotazione */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Riepilogo Prenotazione</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
              <div><span className="text-muted-foreground text-xs block">Immobile</span><span className="font-medium">{booking.property_name}</span></div>
              <div><span className="text-muted-foreground text-xs block">Check-in</span><span className="font-medium">{booking.checkin_date}</span></div>
              <div><span className="text-muted-foreground text-xs block">Check-out</span><span className="font-medium">{booking.checkout_date}</span></div>
              <div><span className="text-muted-foreground text-xs block">Notti</span><span className="font-medium">{booking.nights}</span></div>
            </div>
          </CardContent>
        </Card>

        {/* Fattura PM */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base flex items-center gap-2"><FileText className="h-5 w-5" /> Fattura</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="border rounded-lg p-6 space-y-5 text-sm">
              <div className="flex justify-between items-start">
                <div>
                  <h2 className="font-bold">{tenantData.legal_name}</h2>
                  <p className="text-xs text-muted-foreground mt-1">P.IVA: {tenantData.vat_number} · C.F.: {tenantData.tax_code}</p>
                  <p className="text-xs text-muted-foreground">{tenantData.address}</p>
                </div>
                <div className="text-right">
                  <Badge className="text-xs mb-1">FATTURA</Badge>
                  <p className="font-mono text-xs font-bold">{invoiceNumber}</p>
                </div>
              </div>
              <Separator />
              <div className="border rounded-md overflow-hidden">
                <div className="grid grid-cols-12 gap-2 bg-muted/50 p-2 text-xs font-semibold text-muted-foreground">
                  <div className="col-span-6">Descrizione</div>
                  <div className="col-span-3 text-right">Imponibile</div>
                  <div className="col-span-3 text-right">IVA 22%</div>
                </div>
                <div className="grid grid-cols-12 gap-2 p-2 text-xs border-t">
                  <div className="col-span-6">Riaddebito commissione OTA</div>
                  <div className="col-span-3 text-right">{fmt(riaddebitoOta)}</div>
                  <div className="col-span-3 text-right">{fmt(Math.round(riaddebitoOta * vatRate * 100) / 100)}</div>
                </div>
                <div className="grid grid-cols-12 gap-2 p-2 text-xs border-t">
                  <div className="col-span-6">Riaddebito pulizia finale</div>
                  <div className="col-span-3 text-right">{fmt(riaddebitoPulizie)}</div>
                  <div className="col-span-3 text-right">{fmt(Math.round(riaddebitoPulizie * vatRate * 100) / 100)}</div>
                </div>
                <div className="grid grid-cols-12 gap-2 p-2 text-xs border-t">
                  <div className="col-span-6">Provvigione gestione immobiliare</div>
                  <div className="col-span-3 text-right">{fmt(provvigionePm)}</div>
                  <div className="col-span-3 text-right">{fmt(Math.round(provvigionePm * vatRate * 100) / 100)}</div>
                </div>
              </div>
              <div className="flex justify-end">
                <div className="w-64 space-y-1">
                  <div className="flex justify-between text-xs"><span className="text-muted-foreground">Imponibile</span><span>{fmt(imponibile)}</span></div>
                  <div className="flex justify-between text-xs"><span className="text-muted-foreground">IVA (22%)</span><span>{fmt(vatAmount)}</span></div>
                  <Separator />
                  <div className="flex justify-between font-bold">{<><span>Totale</span><span>{fmt(totaleFattura)}</span></>}</div>
                </div>
              </div>
            </div>
            <div className="flex justify-end mt-3">
              <Button size="sm" className="gap-2" onClick={() => window.print()}><Printer className="h-4 w-4" /> Stampa / PDF</Button>
            </div>
          </CardContent>
        </Card>

        {/* Ricevuta Owner */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base flex items-center gap-2"><Receipt className="h-5 w-5" /> Ricevuta</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="border rounded-lg p-6 space-y-5 text-sm">
              <div className="flex justify-between items-start">
                <div>
                  <h2 className="font-bold">{booking.owner_name}</h2>
                  {ownerMatch && <p className="text-xs text-muted-foreground mt-1">C.F.: {ownerMatch.tax_code}</p>}
                </div>
                <div className="text-right">
                  <Badge variant="outline" className="text-xs mb-1">RICEVUTA</Badge>
                  <p className="font-mono text-xs font-bold">{receiptNumber}</p>
                </div>
              </div>
              <Separator />
              <div className="border rounded-md overflow-hidden">
                <div className="grid grid-cols-12 gap-2 bg-muted/50 p-2 text-xs font-semibold text-muted-foreground">
                  <div className="col-span-6">Descrizione</div>
                  <div className="col-span-2 text-right">Qtà</div>
                  <div className="col-span-2 text-right">Prezzo Unit.</div>
                  <div className="col-span-2 text-right">Totale</div>
                </div>
                <div className="grid grid-cols-12 gap-2 p-2 text-xs border-t">
                  <div className="col-span-6">Canone di locazione turistica breve<br /><span className="text-muted-foreground">Dal {booking.checkin_date} al {booking.checkout_date}</span></div>
                  <div className="col-span-2 text-right">{booking.nights} notti</div>
                  <div className="col-span-2 text-right">{fmt(canonLocazione / booking.nights)}</div>
                  <div className="col-span-2 text-right font-medium">{fmt(canonLocazione)}</div>
                </div>
              </div>
              <div className="flex justify-end">
                <div className="w-64 space-y-1">
                  <div className="flex justify-between text-xs"><span className="text-muted-foreground">Canone</span><span>{fmt(canonLocazione)}</span></div>
                  <div className="flex justify-between text-xs"><span className="text-muted-foreground">IVA</span><span className="italic">Fuori campo</span></div>
                  {bolloApplicabile && <div className="flex justify-between text-xs"><span className="text-muted-foreground">Bollo</span><span>€2,00</span></div>}
                  <Separator />
                  <div className="flex justify-between font-bold"><span>Totale</span><span>{fmt(canonLocazione)}</span></div>
                </div>
              </div>
              <p className="text-xs text-muted-foreground italic">Operazione fuori campo IVA – Art. 4 D.L. 50/2017</p>
            </div>
            <div className="flex justify-end mt-3">
              <Button size="sm" variant="outline" className="gap-2" onClick={() => window.print()}><Printer className="h-4 w-4" /> Stampa / PDF</Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default GuestDocuments;
