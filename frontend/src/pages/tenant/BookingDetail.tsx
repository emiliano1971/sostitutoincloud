import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { ArrowLeft, FileText, Receipt, User, Home, Calendar, CreditCard } from 'lucide-react';
import { mockBookings, mockOwners, mockProperties } from '@/data/mock-data';
import InvoicePMDialog from '@/components/booking/InvoicePMDialog';
import ReceiptOwnerDialog from '@/components/booking/ReceiptOwnerDialog';

const BookingDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [invoiceOpen, setInvoiceOpen] = useState(false);
  const [receiptOpen, setReceiptOpen] = useState(false);
  const booking = mockBookings.find(b => b.booking_id === id);

  if (!booking) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <p className="text-muted-foreground">Prenotazione non trovata</p>
        <Button variant="link" onClick={() => navigate(-1)}>Torna indietro</Button>
      </div>
    );
  }

  const owner = mockOwners.find(o => `${o.first_name} ${o.last_name}` === booking.owner_name);
  const property = mockProperties.find(p => p.property_id === booking.property_id);
  const tenantData = { legal_name: 'Casa Vacanze Italia SRL', vat_number: 'IT12345678901', tax_code: 'CVITRL80A01H501Z', address: 'Via Roma 1, 00100 Roma RM', pec: 'casavacanze@pec.it' };

  const splitRows = [
    { label: 'Lordo ospite', value: booking.gross_amount },
    { label: 'Commissione OTA', value: -booking.ota_commission_amount },
    { label: 'Pulizie', value: -booking.cleaning_amount },
    { label: 'Provvigione PM', value: -booking.pm_fee_amount },
    { label: 'Netto proprietario', value: booking.owner_net_amount, bold: true },
    { label: 'Ritenuta 21%', value: -booking.withholding_amount },
    { label: 'Liquidazione proprietario', value: booking.owner_net_amount - booking.withholding_amount, bold: true },
    { label: `Tassa di Soggiorno ${booking.tourist_tax_included_in_gross ? '(incl. nel lordo)' : '(extra)'}`, value: booking.tourist_tax_amount, highlight: true },
  ];

  const fmt = (v: number) => `€${Math.abs(v).toLocaleString('it-IT', { minimumFractionDigits: 2 })}`;

  return (
    <div className="space-y-6 max-w-4xl">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon" onClick={() => navigate(-1)}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div>
          <h1 className="text-xl font-bold">Prenotazione {booking.external_booking_id}</h1>
          <p className="text-sm text-muted-foreground">{booking.channel_name} · {booking.property_name}</p>
        </div>
        <Badge className="ml-auto">{booking.booking_status}</Badge>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <Card>
          <CardHeader><CardTitle className="text-sm flex items-center gap-2"><Calendar className="h-4 w-4" /> Dettagli Soggiorno</CardTitle></CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex justify-between"><span className="text-muted-foreground">Check-in</span><span className="font-medium">{booking.checkin_date}</span></div>
            <div className="flex justify-between"><span className="text-muted-foreground">Check-out</span><span className="font-medium">{booking.checkout_date}</span></div>
            <div className="flex justify-between"><span className="text-muted-foreground">Notti</span><span className="font-medium">{booking.nights}</span></div>
            <div className="flex justify-between"><span className="text-muted-foreground">Ospiti</span><span className="font-medium">{booking.guests}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Scenario fiscale</span><Badge variant="outline">{booking.fiscal_scenario_code}</Badge></div>
          </CardContent>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader><CardTitle className="text-sm flex items-center gap-2"><User className="h-4 w-4" /> Ospite</CardTitle></CardHeader>
            <CardContent>
              <p className="font-medium">{booking.guest_name}</p>
              <Badge variant="outline" className="mt-2 text-xs">Dati fatturazione: incompleti</Badge>
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle className="text-sm flex items-center gap-2"><Home className="h-4 w-4" /> Proprietario</CardTitle></CardHeader>
            <CardContent>
              <p className="font-medium">{booking.owner_name}</p>
              <p className="text-xs text-muted-foreground mt-1">{booking.property_name}</p>
            </CardContent>
          </Card>
        </div>
      </div>

      {/* Split Economico */}
      <Card>
        <CardHeader><CardTitle className="text-sm flex items-center gap-2"><CreditCard className="h-4 w-4" /> Split Economico</CardTitle></CardHeader>
        <CardContent>
          <div className="space-y-2">
            {splitRows.map((row, i) => (
              <div key={i} className={`flex justify-between py-1.5 ${row.bold ? 'border-t pt-2 font-semibold' : ''} ${'highlight' in row && row.highlight ? 'bg-amber-50 dark:bg-amber-950/20 rounded px-2 -mx-2' : ''}`}>
                <span className={row.bold ? 'text-sm' : 'text-sm text-muted-foreground'}>{row.label}</span>
                <span className={`text-sm ${row.value < 0 ? 'text-destructive' : ''} ${row.bold ? 'text-foreground' : ''} ${'highlight' in row && row.highlight ? 'text-amber-700 dark:text-amber-400 font-medium' : ''}`}>
                  {row.value < 0 ? '-' : ''}{fmt(row.value)}
                </span>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* Status & Actions */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card>
          <CardContent className="p-4 text-center">
            <FileText className="h-5 w-5 mx-auto text-muted-foreground mb-2" />
            <p className="text-xs text-muted-foreground">Documento</p>
            <Badge variant="outline" className="mt-1">{booking.document_status}</Badge>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 text-center">
            <CreditCard className="h-5 w-5 mx-auto text-muted-foreground mb-2" />
            <p className="text-xs text-muted-foreground">Pagamento</p>
            <Badge variant="outline" className="mt-1">{booking.payment_status}</Badge>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 text-center">
            <Receipt className="h-5 w-5 mx-auto text-muted-foreground mb-2" />
            <p className="text-xs text-muted-foreground">Liquidazione</p>
            <Badge variant="outline" className="mt-1">{booking.settlement_status}</Badge>
          </CardContent>
        </Card>
      </div>

      {/* Action Buttons */}
      <div className="flex flex-wrap gap-3">
        <InvoicePMDialog
          open={invoiceOpen}
          onOpenChange={setInvoiceOpen}
          booking={booking}
          owner={owner}
          property={property}
          tenantData={tenantData}
        />
        <ReceiptOwnerDialog
          open={receiptOpen}
          onOpenChange={setReceiptOpen}
          booking={booking}
          owner={owner}
          property={property}
        />
      </div>
    </div>
  );
};

export default BookingDetail;
