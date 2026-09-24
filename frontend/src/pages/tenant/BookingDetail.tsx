import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { Switch } from '@/components/ui/switch';
import { Input } from '@/components/ui/input';
import { ArrowLeft, FileText, Receipt, ReceiptText, User, Home, Calendar, CreditCard, Loader2, AlertCircle, Pencil, Check, X, RotateCcw } from 'lucide-react';
import GuestEditDialog from '@/components/GuestEditDialog';
import {
  getBookingById,
  updateBookingSplit,
  type BookingDetail as BookingDetailType,
  type BookingUpdateSplitRequest,
} from '@/api/bookingApi';
import { generateDocument, type DocumentGenerateResponse } from '@/api/documentApi';
import type { Booking, OwnerProfile, Property } from '@/types';
import { toast } from '@/hooks/use-toast';
import { useLookup } from '@/contexts/LookupContext';
import InvoicePMDialog from '@/components/booking/InvoicePMDialog';
import ReceiptOwnerDialog from '@/components/booking/ReceiptOwnerDialog';

const paymentLabels: Record<string, string> = {
  pending: 'In attesa',
  paid: 'Pagato',
  failed: 'Fallito',
  refunded: 'Rimborsato',
};

const settlementLabels: Record<string, string> = {
  pending: 'In attesa',
  calculated: 'Calcolata',
  approved: 'Approvata',
  paid: 'Pagata',
};

// Stato della ricevuta owner: le descrizioni della lookup stato_documento sono orientate
// allo SDI ("Pronto per invio SDI", "Inviato a SDI") — corrette per la fattura PM, fuorvianti
// per la ricevuta, che è un documento interno e non viene trasmessa. Qui le etichette sono
// quindi locali; per gli stati non previsti si ricade sulla lookup.
const statoRicevutaLabels: Record<string, string> = {
  draft: 'Bozza',
  ready: 'Emesso',
};

// Colori badge per stato settlement: null/pending grigio, calculated blu, approved arancione, paid verde
const settlementBadgeColors: Record<string, string> = {
  pending: 'bg-muted text-muted-foreground',
  calculated: 'bg-blue-100 text-blue-700 dark:bg-blue-950/30 dark:text-blue-300',
  approved: 'bg-warning/10 text-warning',
  paid: 'bg-success/10 text-success',
};

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
    channel_name: b.channelName ?? '',
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
    regime_fiscale_codice: b.regimeFiscaleCodice ?? '',
    created_at: b.createdAt,
    documenti: b.documenti,
  };
}

const BookingDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { lookups, getLabelByCodice } = useLookup();
  const [invoiceOpen, setInvoiceOpen] = useState(false);
  const [receiptOpen, setReceiptOpen] = useState(false);
  const [guestEditOpen, setGuestEditOpen] = useState(false);
  const [booking, setBooking] = useState<BookingDetailType | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [generatedReceipt, setGeneratedReceipt] = useState<DocumentGenerateResponse | null>(null);
  const [generatedInvoice, setGeneratedInvoice] = useState<DocumentGenerateResponse | null>(null);
  const [savingReceipt, setSavingReceipt] = useState(false);
  const [savingInvoice, setSavingInvoice] = useState(false);
  const [isUpdatingSplit, setIsUpdatingSplit] = useState(false);
  const [editingOta, setEditingOta] = useState(false);
  const [otaValue, setOtaValue] = useState('');
  // Editor della commissione OTA: si digita in percentuale o in euro. I due valori
  // restano sincronizzati, ma al server si manda sempre e solo l'importo.
  const [otaEditMode, setOtaEditMode] = useState<'pct' | 'eur'>('pct');
  const [otaPctValue, setOtaPctValue] = useState('');

  const reloadBooking = async () => {
    if (!id) return;
    const refreshed = await getBookingById(Number(id));
    setBooking(refreshed);
  };

  // Gli importi non si mandano mai al server: si manda l'input (flag tassa / override
  // commissione) e si riceve indietro la prenotazione con lo split ricalcolato.
  const handleUpdateSplit = async (patch: BookingUpdateSplitRequest) => {
    if (!id) return;
    setIsUpdatingSplit(true);
    try {
      const updated = await updateBookingSplit(Number(id), patch);
      setBooking(updated);
      toast({ title: 'Split ricalcolato' });
    } catch (err) {
      toast({
        title: 'Errore',
        description: err instanceof Error ? err.message : 'Errore imprevisto',
        variant: 'destructive',
      });
    } finally {
      setIsUpdatingSplit(false);
      setEditingOta(false);
    }
  };

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

      // Esito dell'auto-invio SDI (tenant_settings.sdi_auto_send): si aggiunge al toast di
      // emissione, non lo sostituisce. I campi arrivano solo per la fattura PM con auto-invio
      // attivo; un auto-invio fallito NON invalida l'emissione, resta l'invio manuale.
      if (doc.sdiAutoGenerato) {
        toast({
          title: '📤 File SDI generato automaticamente',
          description: `Progressivo: ${doc.sdiProgressivo ?? '—'}`,
        });
      } else if (doc.sdiDatiIncompleti) {
        toast({
          title: '⚠️ SDI non generato automaticamente',
          description: 'Dati ospite incompleti. Completare l\'anagrafica e inviare manualmente.',
        });
      } else if (doc.sdiAutoSendError) {
        toast({
          title: '⚠️ SDI auto-invio fallito',
          description: `${doc.sdiAutoSendError}. Riprovare manualmente.`,
        });
      }
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
  const aliquotaRitenuta =
    booking.splitEconomico?.aliquotaRitenuta != null
      ? Math.round(booking.splitEconomico.aliquotaRitenuta)
      : booking.splitEconomico?.ownerNetAmount &&
        booking.splitEconomico?.withholdingAmount
        ? Math.round(
            (booking.splitEconomico.withholdingAmount /
              booking.splitEconomico.ownerNetAmount)
            * 100
          )
        : 21;
  // Blocco delle modifiche allo split: stessa condizione del backend (documenti fiscali
  // presenti), perché gli importi sono già stampati su fattura/ricevuta.
  const hasDocuments = (booking.documenti?.length ?? 0) > 0;

  // Base su cui il contratto applica le percentuali: col flag attivo la tassa è già stata
  // scorporata dal lordo, quindi la % della commissione va letta su quella base o non
  // corrisponderebbe a quella del contratto.
  const baseCalcolo = split.grossAmount
    - (split.touristTaxIncludedInGross ? (split.touristTaxAmount ?? 0) : 0);
  // Due decimali come nell'editor (otaPctDaImporto): con precisioni diverse il badge e il
  // valore precompilato nel campo sembravano due percentuali differenti.
  const pctOf = (v: number) => (baseCalcolo > 0 ? ((v / baseCalcolo) * 100).toFixed(2) : '0.00');
  const otaPct = pctOf(split.otaCommissionAmount ?? 0);
  // Override OTA attivo: il backend lo segnala nella descrizione ("importo forzato" se
  // diverge dalla regola, "importo impostato" se la regola OTA non esiste).
  const otaHaOverride = /importo (forzato|impostato)/.test(split.otaDescrizione ?? '');

  // Conversioni dell'editor OTA: baseCalcolo è già la base corretta (al netto della tassa
  // se inclusa), quindi le due funzioni sono l'unico punto che la usa.
  const otaPctDaImporto = (importo: number) =>
    baseCalcolo > 0 ? ((importo / baseCalcolo) * 100).toFixed(2) : '0.00';
  const otaImportoDaPct = (pct: number) => ((pct / 100) * baseCalcolo).toFixed(2);

  const apriEditorOta = () => {
    const importo = split.otaCommissionAmount ?? 0;
    setEditingOta(true);
    setOtaEditMode('pct');
    setOtaPctValue(otaPctDaImporto(importo));
    setOtaValue(String(importo));
  };

  const chiudiEditorOta = () => {
    setEditingOta(false);
    setOtaEditMode('pct');
  };

  const splitRows = [
    { label: 'Lordo ospite', value: split.grossAmount },
    // Tassa già compresa nel lordo: si rende esplicito lo scorporo e la base effettiva su cui
    // il backend calcola provvigioni, netto proprietario e ritenuta (la tassa è incassata per
    // conto del Comune, non è reddito del proprietario).
    ...(split.touristTaxIncludedInGross && split.touristTaxAmount > 0
      ? [
          { label: 'Tassa soggiorno (scorporata dal lordo)', value: -split.touristTaxAmount },
          { label: 'Base di calcolo', value: split.grossAmount - split.touristTaxAmount, bold: true },
        ]
      : []),
    // descrizione = regola di contratto applicata, assente sugli split storici
    { label: 'Commissione OTA', value: -split.otaCommissionAmount, editable: 'ota', descrizione: split.otaDescrizione },
    { label: 'Pulizie', value: -split.cleaningAmount },
    { label: 'Provvigione PM', value: -split.pmFeeAmount, descrizione: split.pmFeeDescrizione },
    ...(split.ivaScorporataPm && split.ivaScorporataPm > 0
      ? [{ label: 'di cui IVA 22% (scorporata sui servizi PM)', value: split.ivaScorporataPm, note: true }]
      : []),
    { label: 'Netto proprietario', value: split.ownerNetAmount, bold: true },
    { label: `Ritenuta ${aliquotaRitenuta}%`, value: -split.withholdingAmount },
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

  const datiFatturazioneMancanti =
    !booking.guestTaxCode ||
    booking.guestTaxCode.trim() === '';

  return (
    <div className="space-y-6 max-w-4xl">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon" onClick={() => navigate(-1)}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div>
          <h1 className="text-xl font-bold">Prenotazione {booking.externalBookingId}</h1>
          <p className="text-sm text-muted-foreground">{[booking.channelName, booking.propertyName].filter(Boolean).join(' · ')}</p>
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
            <div className="flex justify-between">
              <span className="text-muted-foreground">Regime fiscale</span>
              {booking.regimeFiscaleCodice && (
                // Descrizione dalla lookup regimiFiscali (es. "Cedolare secca"); se il
                // codice non è in lookup getLabelByCodice ricade sul codice stesso.
                <Badge variant="outline">
                  {getLabelByCodice(lookups.regimiFiscali, booking.regimeFiscaleCodice)}
                </Badge>
              )}
            </div>
          </CardContent>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0">
              <CardTitle className="text-sm flex items-center gap-2"><User className="h-4 w-4" /> Ospite</CardTitle>
              <Button variant="ghost" size="sm" className="h-7 gap-1" onClick={() => setGuestEditOpen(true)}>
                <Pencil className="h-3.5 w-3.5" /> Modifica
              </Button>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              <p className="font-medium">{booking.guestName}</p>
              {booking.guestTaxCode && (
                <div className="flex justify-between"><span className="text-muted-foreground">Codice fiscale</span><span className="font-mono">{booking.guestTaxCode}</span></div>
              )}
              {booking.guestBirthDate && (
                <div className="flex justify-between"><span className="text-muted-foreground">Data nascita</span><span>{booking.guestBirthDate}</span></div>
              )}
              {booking.guestBirthPlace && (
                <div className="flex justify-between"><span className="text-muted-foreground">Comune nascita</span><span>{booking.guestBirthPlace}</span></div>
              )}
              {(booking.guestDocType || booking.guestDocNumber) && (
                <div className="flex justify-between"><span className="text-muted-foreground">Documento</span><span>{[booking.guestDocType?.replace('_', ' '), booking.guestDocNumber].filter(Boolean).join(' · ')}</span></div>
              )}
              {datiFatturazioneMancanti ? (
                <Badge variant="outline" className="mt-1 text-xs text-orange-600 border-orange-300">
                  Dati fatturazione: incompleti
                </Badge>
              ) : (
                <Badge variant="outline" className="mt-1 text-xs text-success border-success/40">
                  Dati completi
                </Badge>
              )}
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
          {split.warnings && split.warnings.length > 0 && (
            <div className="mb-4 rounded-md border border-amber-300 bg-amber-50 dark:bg-amber-950/20 p-3 text-sm text-amber-800 dark:text-amber-300">
              <div className="flex items-center gap-2 font-medium mb-1">
                <AlertCircle className="h-4 w-4" /> Attenzione
              </div>
              <ul className="list-disc pl-5 space-y-0.5">
                {split.warnings.map((w, i) => <li key={i}>{w}</li>)}
              </ul>
            </div>
          )}
          {/* Input dello split, non una voce di costo: cambia la base su cui tutto il resto
              è calcolato, quindi sta sopra alle righe degli importi. */}
          <div className="flex items-center justify-between py-2 border-b mb-2">
            <div>
              <span className="text-sm font-medium">Tassa soggiorno inclusa nel lordo</span>
              {hasDocuments && (
                <p className="text-xs text-muted-foreground">Non modificabile: documenti fiscali emessi</p>
              )}
            </div>
            <Switch
              checked={split.touristTaxIncludedInGross ?? false}
              disabled={hasDocuments || isUpdatingSplit}
              // Si ripassa l'OTA attuale: senza, il backend riceverebbe null ("torna alle
              // regole") e il cambio del flag azzererebbe la commissione impostata dal PM.
              onCheckedChange={(val) => handleUpdateSplit({
                touristTaxIncludedInGross: val,
                otaCommissionOverride: split.otaCommissionAmount ?? 0,
              })}
            />
          </div>
          {/* pr-6: spazio per le icone OTA posizionate fuori dal flusso, così tutti gli
              importi restano allineati sullo stesso bordo destro */}
          <div className="space-y-2 pr-6">
            {splitRows.map((row, i) => (
              <div key={i} className={`flex justify-between py-1.5 ${row.bold ? 'border-t pt-2 font-semibold' : ''} ${'highlight' in row && row.highlight ? 'bg-amber-50 dark:bg-amber-950/20 rounded px-2 -mx-2' : ''}`}>
                <div className="flex flex-col">
                  <span className={`text-sm ${row.bold ? '' : 'text-muted-foreground'} ${'note' in row && row.note ? 'italic pl-3' : ''}`}>{row.label}</span>
                  {'descrizione' in row && row.descrizione && (
                    <span className="text-xs text-muted-foreground">{row.descrizione}</span>
                  )}
                </div>
                {'editable' in row && row.editable === 'ota' ? (
                  editingOta ? (
                    <div className="flex items-center gap-1">
                      <span className="text-sm text-destructive">-</span>
                      {/* Toggle % / € — cambiando modalità il valore dell'altra unità viene
                          ricalcolato, così non si perde quanto già digitato. */}
                      <button
                        onClick={() => {
                          setOtaEditMode('pct');
                          setOtaPctValue(otaPctDaImporto(parseFloat(otaValue || '0')));
                        }}
                        title="Modifica in percentuale"
                        className={`text-xs px-1 rounded border ${
                          otaEditMode === 'pct'
                            ? 'bg-primary text-primary-foreground border-primary'
                            : 'text-muted-foreground'
                        }`}
                      >
                        %
                      </button>
                      <button
                        onClick={() => {
                          setOtaEditMode('eur');
                          setOtaValue(otaImportoDaPct(parseFloat(otaPctValue || '0')));
                        }}
                        title="Modifica in euro"
                        className={`text-xs px-1 rounded border ${
                          otaEditMode === 'eur'
                            ? 'bg-primary text-primary-foreground border-primary'
                            : 'text-muted-foreground'
                        }`}
                      >
                        €
                      </button>

                      {otaEditMode === 'pct' ? (
                        <Input
                          type="number"
                          value={otaPctValue}
                          onChange={e => {
                            setOtaPctValue(e.target.value);
                            setOtaValue(otaImportoDaPct(parseFloat(e.target.value || '0')));
                          }}
                          className="w-20 h-6 text-xs"
                          min="0"
                          max="100"
                          step="0.01"
                          autoFocus
                          placeholder="0.00"
                        />
                      ) : (
                        <Input
                          type="number"
                          value={otaValue}
                          onChange={e => {
                            setOtaValue(e.target.value);
                            setOtaPctValue(otaPctDaImporto(parseFloat(e.target.value || '0')));
                          }}
                          className="w-24 h-6 text-xs"
                          min="0"
                          step="0.01"
                          autoFocus
                          placeholder="0.00"
                        />
                      )}

                      {/* L'altra unità sempre visibile come riferimento */}
                      <span className="text-xs text-muted-foreground whitespace-nowrap">
                        {otaEditMode === 'pct'
                          ? `= €${(parseFloat(otaValue || '0') || 0).toFixed(2)}`
                          : `= ${otaPctValue || '0.00'}%`}
                      </span>

                      <button
                        onClick={() => handleUpdateSplit({ otaCommissionOverride: parseFloat(otaValue) })}
                        disabled={isUpdatingSplit || otaValue.trim() === '' || Number.isNaN(parseFloat(otaValue))}
                        title="Applica"
                        className="disabled:opacity-40"
                      >
                        <Check className="h-3 w-3 text-green-600" />
                      </button>
                      <button onClick={chiudiEditorOta} title="Annulla">
                        <X className="h-3 w-3 text-destructive" />
                      </button>
                    </div>
                  ) : (
                    <div className="relative flex items-center gap-2">
                      <span className="text-xs text-muted-foreground">({otaPct}%)</span>
                      <span className="text-sm text-destructive">-{fmt(row.value)}</span>

                      {/* Icone fuori dal flusso (nel pr-6 del contenitore): inline spostavano
                          l'importo OTA rispetto a quelli delle altre righe */}
                      {!hasDocuments && (
                        <div className="absolute left-full ml-1.5 flex items-center gap-1">
                          <button
                            onClick={apriEditorOta}
                            disabled={isUpdatingSplit}
                            title="Modifica commissione"
                            className="text-muted-foreground hover:text-foreground disabled:opacity-40"
                          >
                            <Pencil className="h-3 w-3" />
                          </button>
                          {/* null = nessun override: il backend ricalcola l'OTA dalle regole di contratto */}
                          {otaHaOverride && (
                            <button
                              onClick={() => handleUpdateSplit({ otaCommissionOverride: null })}
                              disabled={isUpdatingSplit}
                              title="Ripristina commissione da regole contratto"
                              className="text-muted-foreground hover:text-foreground disabled:opacity-40"
                            >
                              <RotateCcw className="h-3 w-3" />
                            </button>
                          )}
                        </div>
                      )}
                    </div>
                  )
                ) : (
                  <span className={`text-sm ${'note' in row && row.note ? 'text-muted-foreground' : row.value < 0 ? 'text-destructive' : ''} ${row.bold ? 'text-foreground' : ''} ${'highlight' in row && row.highlight ? 'text-amber-700 dark:text-amber-400 font-medium' : ''}`}>
                    {'note' in row && row.note ? '' : row.value < 0 ? '-' : ''}{fmt(row.value)}
                  </span>
                )}
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* Status */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Un riquadro per documento fiscale: cliccabile se il documento è stato emesso.
            Stesso pattern della card Liquidazione. */}
        {[
          { label: 'Fattura PM', doc: existingInvoice, icon: FileText, sdi: true },
          { label: 'Ricevuta Owner', doc: existingReceipt, icon: ReceiptText, sdi: false },
        ].map(({ label, doc, icon: Icon, sdi }) => (
          <Card
            key={label}
            className={doc ? 'cursor-pointer transition-colors hover:bg-accent' : undefined}
            onClick={doc ? () => navigate(`/documents/${doc.id}`) : undefined}
          >
            <CardContent className="p-4 text-center">
              <Icon className="h-5 w-5 mx-auto text-muted-foreground mb-2" />
              <p className="text-xs text-muted-foreground">{label}</p>
              <Badge variant="outline" className="mt-1">
                {!doc
                  ? 'Da emettere'
                  : sdi
                    ? getLabelByCodice(lookups?.statiDocumento ?? [], doc.statoDocumento)
                    : statoRicevutaLabels[doc.statoDocumento]
                      ?? getLabelByCodice(lookups?.statiDocumento ?? [], doc.statoDocumento)}
              </Badge>
              {doc && (
                <p className="mt-1 font-mono text-[11px] text-muted-foreground">{doc.documentNumber}</p>
              )}
            </CardContent>
          </Card>
        ))}
        <Card>
          <CardContent className="p-4 text-center">
            <CreditCard className="h-5 w-5 mx-auto text-muted-foreground mb-2" />
            <p className="text-xs text-muted-foreground">Pagamento</p>
            <Badge variant="outline" className="mt-1">{paymentLabels[booking.paymentStatus] ?? booking.paymentStatus}</Badge>
          </CardContent>
        </Card>
        <Card
          className={booking.settlementId != null ? 'cursor-pointer transition-colors hover:bg-accent' : undefined}
          onClick={booking.settlementId != null ? () => navigate(`/settlements/${booking.settlementId}`) : undefined}
        >
          <CardContent className="p-4 text-center">
            <Receipt className="h-5 w-5 mx-auto text-muted-foreground mb-2" />
            <p className="text-xs text-muted-foreground">Liquidazione</p>
            <Badge className={`mt-1 ${settlementBadgeColors[booking.settlementStato ?? 'pending'] ?? settlementBadgeColors.pending}`}>
              {settlementLabels[booking.settlementStato ?? 'pending'] ?? 'In attesa'}
            </Badge>
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
          onSent={reloadBooking}
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

        <GuestEditDialog
          bookingId={booking.id}
          open={guestEditOpen}
          onClose={() => setGuestEditOpen(false)}
          guest={{
            guestName: booking.guestName,
            guestTaxCode: booking.guestTaxCode,
            guestBirthDate: booking.guestBirthDate,
            guestSesso: booking.guestSesso,
            guestBirthPlace: booking.guestBirthPlace,
            guestBirthBelfiore: booking.guestBirthBelfiore,
            guestDocType: booking.guestDocType,
            guestDocNumber: booking.guestDocNumber,
            guestCountry: booking.guestCountry,
            guestAddress: booking.guestAddress,
            guestPhone: booking.guestPhone,
          }}
          onSaved={(updated) => setBooking(updated)}
        />
      </div>
    </div>
  );
};

export default BookingDetail;
