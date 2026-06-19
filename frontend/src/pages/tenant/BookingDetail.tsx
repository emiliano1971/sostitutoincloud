import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { ArrowLeft, FileText, Receipt, User, Home, Calendar, CreditCard, Loader2, AlertCircle } from 'lucide-react';
import { getBookingById, type BookingDetail as BookingDetailType } from '@/api/bookingApi';
import { generateDocument, type DocumentGenerateResponse } from '@/api/documentApi';
import type { Booking, OwnerProfile, Property } from '@/types';
import { toast } from '@/hooks/use-toast';
import InvoicePMDialog from '@/components/booking/InvoicePMDialog';
import ReceiptOwnerDialog from '@/components/booking/ReceiptOwnerDialog';

function toDialogBooking(b: BookingDetailType): Booking {
  const s = b.splitEconomico;
  return {
    booking_id: String(b.id),
    tenant_id: String(b.fkTenantId),
    property_id: String(b.fkPropertyId),
    property_name: b.propertyName,
    owner_name: b.ownerName,
    guest_name: b.guestName,
    external_booking_id: b.externalBookingId,
    channel_name: b.channelName,
    guest_tax_code: b.guestTaxCode ?? '',
    checkin_date: b.checkinDate,
    checkout_date: b.checkoutDate,
    nights: b.nights,
    guests: b.guests,
    gross_amount: s.grossAmount,
    ota_commission_amount: s.otaCommissionAmount,
    cleaning_amount: s.cleaningAmount,
    pm_fee_amount: s.pmFeeAmount,
    owner_net_amount: s.ownerNetAmount,
    withholding_amount: s.withholdingAmount,
    tourist_tax_amount: s.touristTaxAmount,
    tourist_tax_included_in_gross: s.touristTaxIncludedInGross,
    tourist_tax_collection: (b.touristTaxCollection as Booking['tourist_tax_collection']) ?? 'altro',
    booking_status: b.statoPrenotazione as Booking['booking_status'],
    payment_status: b.paymentStatus as Booking['payment_status'],
    document_status: b.documentStatus as Booking['document_status'],
    settlement_status: b.settlementStatus as Booking['settlement_status'],
    fiscal_scenario_code: b.fiscalScenarioCode ?? '',
    created_at: b.createdAt,
  };
}

const BookingDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [invoiceOpen, setInvoiceOpen] = useState(false);
  const [receiptOpen, setReceiptOpen] = useState(false);
  const [booking, setBooking] = useState<BookingDetailType | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [generatedReceipt, setGeneratedReceipt] = useState<DocumentGenerateResponse | null>(null);
  const [generatedInvoice, setGeneratedInvoice] = useState<DocumentGenerateResponse | null>(null);
  const [savingReceipt, setSavingReceipt] = useState(false);
  const [savingInvoice, setSavingInvoice] = useState(false);

  const handleEmetti = async (
    tipoDocumento: 'ricevuta_owner' | 'fattura_pm',
    setSaving: (v: boolean) => void,
    setGenerated: (d: DocumentGenerateResponse) => void,
  ) => {
    if (!id) return;
    setSaving(true);
    try {
      const doc = await generateDocument({ bookingId: Number(id), tipoDocumento });
      setGenerated(doc);
      toast({ title: 'Documento emesso', description: `Numero ${doc.documentNumber}` });
      // ricarica il booking per aggiornare i badge di stato
      const refreshed = await getBookingById(Number(id));
      setBooking(refreshed);
    } catch (err) {
      toast({
        title: 'Errore generazione documento',
        description: err instanceof Error ? err.message : 'Errore imprevisto',
      });
    } finally {
      setSaving(false);
    }
  };

  useEffect(() => {
    if (!id) return;
    setIsLoading(true);
    getBookingById(Number(id))
      .then(setBooking)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, [id]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20 gap-2 text-muted-foreground">
        <Loader2 className="h-5 w-5 animate-spin" />
        <span>Caricamento prenotazione…</span>
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

  if (!booking) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <p className="text-muted-foreground">Prenotazione non trovata</p>
        <Button variant="link" onClick={() => navigate(-1)}>Torna indietro</Button>
      </div>
    );
  }

  const { splitEconomico: split } = booking;
  const splitRows = [
    { label: 'Lordo ospite', value: split.grossAmount },
    { label: 'Commissione OTA', value: -split.otaCommissionAmount },
    { label: 'Pulizie', value: -split.cleaningAmount },
    { label: 'Provvigione PM', value: -split.pmFeeAmount },
    { label: 'Netto proprietario', value: split.ownerNetAmount, bold: true },
    { label: 'Ritenuta 21%', value: -split.withholdingAmount },
    { label: 'Liquidazione proprietario', value: split.liquidazioneOwner, bold: true },
    { label: `Tassa di Soggiorno ${split.touristTaxIncludedInGross ? '(incl. nel lordo)' : '(extra)'}`, value: split.touristTaxAmount, highlight: true },
  ];

  const fmt = (v: number) => `€${Math.abs(v).toLocaleString('it-IT', { minimumFractionDigits: 2 })}`;
  const dialogBooking = toDialogBooking(booking);

  // Documento già emesso per tipo (codice lookup tipo_documento: 'ricevuta' / 'fattura')
  const getDocumento = (tipo: string) =>
    booking.documenti?.find(d => d.tipoDocumento === tipo);
  const existingReceipt = getDocumento('ricevuta');
  const existingInvoice = getDocumento('fattura');

  // Dati reali dal backend per i dialog (sostituiscono i mock hardcoded)
  const dialogOwner = {
    tax_code: booking.ownerTaxCode ?? '',
    iban: booking.ownerIban ?? '',
    email: booking.ownerEmail ?? '',
  } as unknown as OwnerProfile;
  const dialogProperty = {
    address: booking.propertyAddress ?? '',
    city: booking.propertyCity ?? '',
    internal_code: booking.propertyInternalCode ?? '',
  } as unknown as Property;
  const tenantData = {
    legal_name: booking.tenantLegalName ?? '',
    vat_number: booking.tenantVatNumber ?? '',
    tax_code: booking.tenantTaxCode ?? '',
    address: booking.tenantLegalAddress ?? '',
    pec: booking.tenantPec ?? '',
  };

  return (
    <div className="space-y-6 max-w-4xl">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon" onClick={() => navigate(-1)}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div>
          <h1 className="text-xl font-bold">Prenotazione {booking.externalBookingId}</h1>
          <p className="text-sm text-muted-foreground">{booking.channelName} · {booking.propertyName}</p>
        </div>
        <Badge className="ml-auto">{booking.statoPrenotazione}</Badge>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <Card>
          <CardHeader><CardTitle className="text-sm flex items-center gap-2"><Calendar className="h-4 w-4" /> Dettagli Soggiorno</CardTitle></CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex justify-between"><span className="text-muted-foreground">Check-in</span><span className="font-medium">{booking.checkinDate}</span></div>
            <div className="flex justify-between"><span className="text-muted-foreground">Check-out</span><span className="font-medium">{booking.checkoutDate}</span></div>
            <div className="flex justify-between"><span className="text-muted-foreground">Notti</span><span className="font-medium">{booking.nights}</span></div>
            <div className="flex justify-between"><span className="text-muted-foreground">Ospiti</span><span className="font-medium">{booking.guests}</span></div>
            <Separator />
            <div className="flex justify-between"><span className="text-muted-foreground">Scenario fiscale</span><Badge variant="outline">{booking.fiscalScenarioCode}</Badge></div>
          </CardContent>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader><CardTitle className="text-sm flex items-center gap-2"><User className="h-4 w-4" /> Ospite</CardTitle></CardHeader>
            <CardContent>
              <p className="font-medium">{booking.guestName}</p>
              <Badge variant="outline" className="mt-2 text-xs">Dati fatturazione: incompleti</Badge>
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle className="text-sm flex items-center gap-2"><Home className="h-4 w-4" /> Proprietario</CardTitle></CardHeader>
            <CardContent>
              <p className="font-medium">{booking.ownerName}</p>
              <p className="text-xs text-muted-foreground mt-1">{booking.propertyName}</p>
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

      {/* Status */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card>
          <CardContent className="p-4 text-center">
            <FileText className="h-5 w-5 mx-auto text-muted-foreground mb-2" />
            <p className="text-xs text-muted-foreground">Documento</p>
            <Badge variant="outline" className="mt-1">{booking.documentStatus}</Badge>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 text-center">
            <CreditCard className="h-5 w-5 mx-auto text-muted-foreground mb-2" />
            <p className="text-xs text-muted-foreground">Pagamento</p>
            <Badge variant="outline" className="mt-1">{booking.paymentStatus}</Badge>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 text-center">
            <Receipt className="h-5 w-5 mx-auto text-muted-foreground mb-2" />
            <p className="text-xs text-muted-foreground">Liquidazione</p>
            <Badge variant="outline" className="mt-1">{booking.settlementStatus}</Badge>
          </CardContent>
        </Card>
      </div>

      {/* Action Buttons */}
      <div className="flex flex-wrap gap-3">
        <InvoicePMDialog
          open={invoiceOpen}
          onOpenChange={setInvoiceOpen}
          booking={dialogBooking}
          owner={dialogOwner}
          property={dialogProperty}
          tenantData={tenantData}
          generatedDoc={generatedInvoice}
          existingDoc={existingInvoice}
          isSaving={savingInvoice}
          onEmetti={() => handleEmetti('fattura_pm', setSavingInvoice, setGeneratedInvoice)}
        />
        <ReceiptOwnerDialog
          open={receiptOpen}
          onOpenChange={setReceiptOpen}
          booking={dialogBooking}
          owner={dialogOwner}
          property={dialogProperty}
          generatedDoc={generatedReceipt}
          existingDoc={existingReceipt}
          isSaving={savingReceipt}
          onEmetti={() => handleEmetti('ricevuta_owner', setSavingReceipt, setGeneratedReceipt)}
        />
      </div>
    </div>
  );
};

export default BookingDetail;
