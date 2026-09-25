import { useState, useEffect, Fragment } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { Switch } from '@/components/ui/switch';
import { Input } from '@/components/ui/input';
import { ArrowLeft, FileText, Receipt, ReceiptText, User, Home, Calendar, CreditCard, Loader2, AlertCircle, Pencil, Check, X, RotateCcw, RefreshCw, Plus, Trash2, ChevronDown, ChevronUp } from 'lucide-react';
import { getContractRules, type ContractRule } from '@/api/contractApi';
import { cn } from '@/lib/utils';
import GuestEditDialog from '@/components/GuestEditDialog';
import {
  getBookingById,
  updateBookingSplit,
  ricalcolaSplit,
  aggiungiVoceExtra,
  aggiornaVoceExtra,
  eliminaVoceExtra,
  type BookingSplitRiga,
  type BookingDetail as BookingDetailType,
  type BookingUpdateSplitRequest,
} from '@/api/bookingApi';
import { generateDocument, type DocumentGenerateResponse } from '@/api/documentApi';
import type { Booking, OwnerProfile, Property } from '@/types';
import { toast } from '@/hooks/use-toast';
import { useLookup } from '@/contexts/LookupContext';
import InvoicePMDialog from '@/components/booking/InvoicePMDialog';
import ReceiptOwnerDialog from '@/components/booking/ReceiptOwnerDialog';
import { labelStatoPrenotazione } from '@/lib/statiLabels';

const paymentLabels: Record<string, string> = {
  pending: 'In attesa',
  received: 'Ricevuto',   // valore reale dell'enum payment_status
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

/** Etichetta del tipo di regola: quella del backend (tipoLabel), altrimenti una di ripiego. */
const labelTipoVoce = (r: ContractRule) => {
  const labels: Record<string, string> = {
    commissione_ota: 'Commissione OTA',
    pulizie: 'Pulizie',
    cambio_biancheria: 'Cambio biancheria',
    commissione_pm: 'Commissione PM',
    provvigione_proprietario: 'Provvigione proprietario',
    extra: 'Extra',
  };
  return r.tipoLabel || labels[r.tipo] || r.tipo;
};

/** Valore della regola in forma leggibile, es. "18% sul lordo", "€50,00 fisso". */
const formatRegola = (r: ContractRule) => {
  const euro = (v: number) => `€${v.toLocaleString('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  const pct = (v: number) => `${v.toLocaleString('it-IT', { maximumFractionDigits: 2 })}%`;
  if (r.isRemainder || r.calcMode === 'rimanenza') return 'rimanenza';
  switch (r.calcMode) {
    case 'percentuale':
    case 'percentuale_lordo': return `${pct(r.valore)} sul lordo`;
    case 'percentuale_netto': return `${pct(r.valore)} sul netto`;
    case 'fisso': return `${euro(r.valore)} fisso`;
    case 'fisso_per_notte': return `${euro(r.valore)} /notte`;
    case 'fisso_per_persona': return `${euro(r.valore)} /persona`;
    default: return r.calcModeLabel ? `${r.valore} (${r.calcModeLabel})` : String(r.valore);
  }
};

/** Voci dello split con importo impostabile a mano, e campo di override corrispondente. */
type VoceModificabile = 'ota' | 'pulizie' | 'pm';
const CAMPO_OVERRIDE = {
  ota: 'otaCommissionOverride',
  pulizie: 'cleaningOverride',
  pm: 'pmFeeOverride',
} as const satisfies Record<VoceModificabile, keyof BookingUpdateSplitRequest>;

/**
 * Editor inline di un importo dello split: si digita in percentuale (sulla base di calcolo)
 * o in euro, i due valori restano sincronizzati e l'altra unità è sempre visibile come
 * riferimento. Al server si manda sempre e solo l'importo (onApplica).
 */
const EditorImporto = ({
  importoIniziale, base, disabled, onApplica, onAnnulla,
}: {
  importoIniziale: number;
  /** Base su cui leggere la % (lordo, al netto della tassa se inclusa). */
  base: number;
  disabled: boolean;
  onApplica: (importo: number) => void;
  onAnnulla: () => void;
}) => {
  const pctDaImporto = (importo: number) =>
    base > 0 ? ((importo / base) * 100).toFixed(2) : '0.00';
  const importoDaPct = (pct: number) => ((pct / 100) * base).toFixed(2);

  const [mode, setMode] = useState<'pct' | 'eur'>('pct');
  const [valore, setValore] = useState(String(importoIniziale));
  const [pct, setPct] = useState(pctDaImporto(importoIniziale));

  return (
    <div className="flex items-center gap-1">
      <span className="text-sm text-destructive">-</span>
      {/* Toggle % / € — cambiando modalità il valore dell'altra unità viene
          ricalcolato, così non si perde quanto già digitato. */}
      <button
        onClick={() => { setMode('pct'); setPct(pctDaImporto(parseFloat(valore || '0'))); }}
        title="Modifica in percentuale"
        className={`text-xs px-1 rounded border ${
          mode === 'pct' ? 'bg-primary text-primary-foreground border-primary' : 'text-muted-foreground'
        }`}
      >
        %
      </button>
      <button
        onClick={() => { setMode('eur'); setValore(importoDaPct(parseFloat(pct || '0'))); }}
        title="Modifica in euro"
        className={`text-xs px-1 rounded border ${
          mode === 'eur' ? 'bg-primary text-primary-foreground border-primary' : 'text-muted-foreground'
        }`}
      >
        €
      </button>

      {mode === 'pct' ? (
        <Input
          type="number"
          value={pct}
          onChange={e => { setPct(e.target.value); setValore(importoDaPct(parseFloat(e.target.value || '0'))); }}
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
          value={valore}
          onChange={e => { setValore(e.target.value); setPct(pctDaImporto(parseFloat(e.target.value || '0'))); }}
          className="w-24 h-6 text-xs"
          min="0"
          step="0.01"
          autoFocus
          placeholder="0.00"
        />
      )}

      {/* L'altra unità sempre visibile come riferimento */}
      <span className="text-xs text-muted-foreground whitespace-nowrap">
        {mode === 'pct' ? `= €${(parseFloat(valore || '0') || 0).toFixed(2)}` : `= ${pct || '0.00'}%`}
      </span>

      <button
        onClick={() => onApplica(parseFloat(valore))}
        disabled={disabled || valore.trim() === '' || Number.isNaN(parseFloat(valore))}
        title="Applica"
        className="disabled:opacity-40"
      >
        <Check className="h-3 w-3 text-green-600" />
      </button>
      <button onClick={onAnnulla} title="Annulla">
        <X className="h-3 w-3 text-destructive" />
      </button>
    </div>
  );
};

/** Riga visualizzata nello split economico (voci di costo, totali, note). */
interface RigaSplitView {
  key?: number;
  label: string;
  value: number;
  bold?: boolean;
  note?: boolean;
  highlight?: boolean;
  /** Voce modificabile a mano con l'editor %/€ (override via PATCH /split). */
  editable?: VoceModificabile;
  /** Regola di contratto applicata, sotto la voce. */
  descrizione?: string;
  /** Percentuale sulla base di calcolo, solo per le righe da booking_split_economico. */
  pct?: string;
  /** source della riga split: 'manuale' e 'import' hanno un'indicazione sotto la voce. */
  source?: string;
  /** Voce extra inserita dal PM: modificabile ed eliminabile (matita + cestino). */
  extra?: BookingSplitRiga;
  /** Ultima voce di costo: dopo di lei si mostrano "Aggiungi voce" e il form. */
  ultimaVoceCosto?: boolean;
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
  // Voce dello split aperta nell'editor %/€ (una alla volta): OTA, pulizie o PM.
  const [editingVoce, setEditingVoce] = useState<VoceModificabile | null>(null);
  // Card "Regole Contratto": caricata alla prima apertura (regoleCaricate evita di
  // ricaricare a ogni click anche quando l'immobile non ha regole).
  const [showRegole, setShowRegole] = useState(false);
  const [regole, setRegole] = useState<ContractRule[]>([]);
  const [loadingRegole, setLoadingRegole] = useState(false);
  const [regoleCaricate, setRegoleCaricate] = useState(false);
  // Voci extra dello split: form inline di aggiunta / modifica (una alla volta)
  const [showAggiungiVoce, setShowAggiungiVoce] = useState(false);
  const [editingRigaId, setEditingRigaId] = useState<number | null>(null);
  const [voceForm, setVoceForm] = useState({ descrizione: '', importo: '', includeInFatturaPm: true });

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
      setEditingVoce(null);
    }
  };

  // Ricalcolo completo dalle regole di contratto correnti (body vuoto): riporta alla regola
  // anche una commissione OTA forzata dal PM o arrivata dal file.
  // Regole contratto dell'immobile: stesso endpoint della pagina Contratti
  // (GET /properties/{id}/contracts), caricate solo alla prima apertura della card.
  const handleToggleRegole = async () => {
    if (!showRegole && !regoleCaricate && booking) {
      setLoadingRegole(true);
      try {
        setRegole(await getContractRules(booking.fkPropertyId));
        setRegoleCaricate(true);
      } catch {
        // la card mostra il messaggio "nessuna regola"
      } finally {
        setLoadingRegole(false);
      }
    }
    setShowRegole(!showRegole);
  };

  const handleRicalcola = async () => {
    if (!id) return;
    setIsUpdatingSplit(true);
    try {
      const updated = await ricalcolaSplit(Number(id));
      setBooking(updated);
      toast({
        title: 'Split ricalcolato',
        description: 'Importi aggiornati dalle regole contratto correnti',
      });
    } catch (err) {
      toast({
        title: 'Errore',
        description: err instanceof Error ? err.message : 'Errore imprevisto',
        variant: 'destructive',
      });
    } finally {
      setIsUpdatingSplit(false);
      setEditingVoce(null);
    }
  };

  const chiudiFormVoce = () => {
    setShowAggiungiVoce(false);
    setEditingRigaId(null);
  };

  const handleSalvaVoce = async () => {
    if (!id) return;
    setIsUpdatingSplit(true);
    try {
      const data = {
        descrizione: voceForm.descrizione.trim(),
        importo: parseFloat(voceForm.importo),
        includeInFatturaPm: voceForm.includeInFatturaPm,
      };
      const updated = editingRigaId
        ? await aggiornaVoceExtra(Number(id), editingRigaId, data)
        : await aggiungiVoceExtra(Number(id), data);
      setBooking(updated);
      toast({ title: editingRigaId ? 'Voce aggiornata' : 'Voce aggiunta' });
      chiudiFormVoce();
    } catch (err) {
      toast({
        title: 'Errore',
        description: err instanceof Error ? err.message : 'Errore imprevisto',
        variant: 'destructive',
      });
    } finally {
      setIsUpdatingSplit(false);
    }
  };

  const handleEliminaVoce = async (rigaId: number) => {
    if (!id || !confirm('Eliminare questa voce?')) return;
    setIsUpdatingSplit(true);
    try {
      const updated = await eliminaVoceExtra(Number(id), rigaId);
      setBooking(updated);
      toast({ title: 'Voce eliminata' });
      if (editingRigaId === rigaId) chiudiFormVoce();
    } catch (err) {
      toast({
        title: 'Errore',
        description: err instanceof Error ? err.message : 'Errore imprevisto',
        variant: 'destructive',
      });
    } finally {
      setIsUpdatingSplit(false);
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
    // Navigando da una prenotazione all'altra il componente resta montato: le regole
    // caricate appartengono all'immobile precedente, si ripartisce da card chiusa e vuota.
    setShowRegole(false);
    setRegole([]);
    setRegoleCaricate(false);
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
  // Due decimali come nell'editor (EditorImporto): con precisioni diverse il badge e il
  // valore precompilato nel campo sembravano due percentuali differenti.
  const pctOf = (v: number) => (baseCalcolo > 0 ? ((v / baseCalcolo) * 100).toFixed(2) : '0.00');

  // Override attivo, per mostrare il ripristino. OTA: lo segnala la descrizione ("importo
  // forzato" se diverge dalla regola, "importo impostato" senza regola OTA). Pulizie e PM:
  // la riga split è 'manuale'.
  const rigaDi = (tipo: string) => booking.righeSplit?.find(r => r.tipoVoce === tipo);
  const importoManuale = (tipo: string) => {
    const r = rigaDi(tipo);
    return r?.source === 'manuale' ? r.importo : null;
  };
  const haOverride: Record<VoceModificabile, boolean> = {
    ota: /importo (forzato|impostato)/.test(split.otaDescrizione ?? ''),
    pulizie: importoManuale('pulizie') != null,
    pm: importoManuale('commissione_pm') != null,
  };

  // Per il backend un override assente (null) vuol dire "torna alla regola". A ogni PATCH
  // si ripassano quindi gli importi manuali attuali: l'OTA sempre (come già al cambio del
  // flag tassa), pulizie e PM solo se impostate a mano — altrimenti una percentuale verrebbe
  // congelata e non seguirebbe più la base. Ricalcola invece manda {} (tutto dalle regole).
  const overrideCorrenti = (): BookingUpdateSplitRequest => ({
    otaCommissionOverride: split.otaCommissionAmount ?? 0,
    cleaningOverride: importoManuale('pulizie'),
    pmFeeOverride: importoManuale('commissione_pm'),
  });
  const impostaVoce = (voce: VoceModificabile, importo: number | null) =>
    handleUpdateSplit({ ...overrideCorrenti(), [CAMPO_OVERRIDE[voce]]: importo });

  // Voci di costo: dalle righe di booking_split_economico se presenti, altrimenti dai campi
  // flat dello split (prenotazioni pre-migrazione 018). La tassa di soggiorno ha una riga
  // split ma resta mostrata a parte, in fondo, come prima.
  // La riga OTA mantiene editable: 'ota', quindi usa lo stesso editor e le stesse icone.
  // Le voci extra si accodano sempre: il fallback sui campi flat vale solo per le voci
  // calcolate, altrimenti su un booking pre-migrazione una voce extra nasconderebbe
  // OTA/pulizie/PM (la lista righeSplit conterrebbe solo lei).
  const righeCosto = (booking.righeSplit ?? [])
    .filter(r => r.tipoVoce !== 'tassa_soggiorno' && r.tipoVoce !== 'extra');
  const righeExtra = (booking.righeSplit ?? []).filter(r => r.tipoVoce === 'extra');
  const vociCalcolate: RigaSplitView[] = righeCosto.length > 0
    ? righeCosto.map(r => ({
        key: r.id,
        label: r.descrizione,
        value: -r.importo,
        pct: pctOf(r.importo),
        source: r.source,
        ...(r.tipoVoce === 'commissione_ota'
          ? { editable: 'ota' as const, descrizione: split.otaDescrizione }
          : r.tipoVoce === 'pulizie'
            ? { editable: 'pulizie' as const }
            : r.tipoVoce === 'commissione_pm'
              ? { editable: 'pm' as const, descrizione: split.pmFeeDescrizione }
              : {}),
      }))
    : [
        // descrizione = regola di contratto applicata, assente sugli split storici
        { label: 'Commissione OTA', value: -split.otaCommissionAmount, editable: 'ota', descrizione: split.otaDescrizione },
        { label: 'Pulizie', value: -split.cleaningAmount, editable: 'pulizie' },
        { label: 'Provvigione PM', value: -split.pmFeeAmount, editable: 'pm', descrizione: split.pmFeeDescrizione },
      ];
  const vociCosto: RigaSplitView[] = [
    ...vociCalcolate,
    ...righeExtra.map(r => ({
      key: r.id,
      label: r.descrizione,
      value: -r.importo,
      pct: pctOf(r.importo),
      // niente source: su una voce aggiunta dal PM "importo modificato" sarebbe fuorviante
      descrizione: r.includeInFatturaPm ? undefined : 'fuori fattura PM',
      extra: r,
    })),
  ].map((v, i, all) => (i === all.length - 1 ? { ...v, ultimaVoceCosto: true } : v));

  const splitRows: RigaSplitView[] = [
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
    ...vociCosto,
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
    // Regime del PM: decide lo scorporo IVA nell'anteprima della fattura (RF19 = senza IVA)
    regimeFiscalePm: split.regimeFiscalePm,
  };
  // Righe che entreranno nella fattura PM: stesse di DocumentGenerationService / PDF / XML
  // (booking_split_economico con include_in_fattura_pm e importo > 0, voci extra comprese).
  const righeFatturaPm = (booking.righeSplit ?? []).filter(r => r.includeInFatturaPm && r.importo > 0);

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
        <Badge className="ml-auto">{labelStatoPrenotazione(booking.statoPrenotazione)}</Badge>
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

      {/* Regole Contratto dell'immobile: card collassata, caricata alla prima apertura */}
      <Card>
        <CardHeader className="cursor-pointer py-3 px-4" onClick={handleToggleRegole}>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <FileText className="h-4 w-4 text-muted-foreground" />
              <CardTitle className="text-sm font-medium">Regole Contratto</CardTitle>
              {regole.length > 0 && (
                <Badge variant="outline" className="text-xs">{regole.length}</Badge>
              )}
            </div>
            {showRegole
              ? <ChevronUp className="h-4 w-4 text-muted-foreground" />
              : <ChevronDown className="h-4 w-4 text-muted-foreground" />}
          </div>
        </CardHeader>
        {showRegole && (
          <CardContent className="pt-0 px-4 pb-4">
            {loadingRegole ? (
              <div className="text-xs text-muted-foreground text-center py-2">Caricamento...</div>
            ) : regole.length === 0 ? (
              <div className="text-xs text-muted-foreground text-center py-2">
                Nessuna regola contratto configurata per questo immobile
              </div>
            ) : (
              <div className="space-y-2">
                {regole.map(r => {
                  // Regola legata a un canale diverso da quello della prenotazione: non
                  // concorre a questo split (il calcolatore usa solo canale corrente o generiche).
                  const altroCanale = !!r.canaleName && !!booking.channelName && r.canaleName !== booking.channelName;
                  return (
                    <div
                      key={r.id}
                      className={cn('flex items-start justify-between text-xs py-1 border-b last:border-0',
                        altroCanale && 'opacity-50')}
                    >
                      <div className="flex flex-col gap-0.5">
                        <span className="font-medium">{labelTipoVoce(r)}</span>
                        {r.canaleName && (
                          <span className="text-muted-foreground">
                            {r.canaleName}{altroCanale ? ' — non applicata (altro canale)' : ''}
                          </span>
                        )}
                      </div>
                      <span className="text-right font-mono">{formatRegola(r)}</span>
                    </div>
                  );
                })}
              </div>
            )}
          </CardContent>
        )}
      </Card>

      {/* Split Economico */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-sm flex items-center gap-2"><CreditCard className="h-4 w-4" /> Split Economico</CardTitle>
            {!hasDocuments && (
              <button
                onClick={handleRicalcola}
                disabled={isUpdatingSplit}
                className="flex items-center gap-1 text-xs font-medium text-primary hover:text-primary/80 disabled:opacity-50"
                title="Ricalcola dalle regole contratto correnti"
              >
                <RefreshCw className={cn('h-4 w-4', isUpdatingSplit && 'animate-spin')} />
                Ricalcola
              </button>
            )}
          </div>
        </CardHeader>
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
              // Si ripassano gli importi manuali attuali (overrideCorrenti): senza, il backend
              // riceverebbe null ("torna alle regole") e il cambio del flag azzererebbe OTA,
              // pulizie e PM impostate dal PM.
              onCheckedChange={(val) => handleUpdateSplit({
                ...overrideCorrenti(),
                touristTaxIncludedInGross: val,
              })}
            />
          </div>
          {/* pr-6: spazio per le icone OTA posizionate fuori dal flusso, così tutti gli
              importi restano allineati sullo stesso bordo destro */}
          <div className="space-y-2 pr-6">
            {splitRows.map((row, i) => (
              <Fragment key={row.key ?? `r${i}`}>
              <div className={`flex justify-between py-1.5 ${row.bold ? 'border-t pt-2 font-semibold' : ''} ${'highlight' in row && row.highlight ? 'bg-amber-50 dark:bg-amber-950/20 rounded px-2 -mx-2' : ''}`}>
                <div className="flex flex-col">
                  <span className={`text-sm ${row.bold ? '' : 'text-muted-foreground'} ${'note' in row && row.note ? 'italic pl-3' : ''}`}>{row.label}</span>
                  {'descrizione' in row && row.descrizione && (
                    <span className="text-xs text-muted-foreground">{row.descrizione}</span>
                  )}
                  {/* Origine dell'importo (source della riga split); 'calcolato' = nessuna indicazione */}
                  {row.source === 'manuale' && (
                    <span className="text-xs text-amber-600">importo modificato</span>
                  )}
                  {row.source === 'import' && (
                    <span className="text-xs text-muted-foreground">da file</span>
                  )}
                </div>
                {row.editable ? (
                  editingVoce === row.editable ? (
                    <EditorImporto
                      importoIniziale={Math.abs(row.value)}
                      base={baseCalcolo}
                      disabled={isUpdatingSplit}
                      onApplica={(importo) => impostaVoce(row.editable!, importo)}
                      onAnnulla={() => setEditingVoce(null)}
                    />
                  ) : (
                    <div className="relative flex items-center gap-2">
                      <span className="text-xs text-muted-foreground">({row.pct ?? pctOf(Math.abs(row.value))}%)</span>
                      <span className="text-sm text-destructive">-{fmt(row.value)}</span>

                      {/* Icone fuori dal flusso (nel pr-6 del contenitore): inline spostavano
                          l'importo rispetto a quelli delle altre righe */}
                      {!hasDocuments && (
                        <div className="absolute left-full ml-1.5 flex items-center gap-1">
                          <button
                            onClick={() => setEditingVoce(row.editable!)}
                            disabled={isUpdatingSplit}
                            title="Modifica importo"
                            className="text-muted-foreground hover:text-foreground disabled:opacity-40"
                          >
                            <Pencil className="h-3 w-3" />
                          </button>
                          {/* null = nessun override: il backend ricalcola la voce dalle regole di contratto */}
                          {haOverride[row.editable] && (
                            <button
                              onClick={() => impostaVoce(row.editable!, null)}
                              disabled={isUpdatingSplit}
                              title="Ripristina da regole contratto"
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
                  <div className="relative flex items-center gap-2">
                    {/* % sulla base di calcolo: solo per le voci da booking_split_economico */}
                    {row.pct && <span className="text-xs text-muted-foreground">({row.pct}%)</span>}
                    <span className={`text-sm ${'note' in row && row.note ? 'text-muted-foreground' : row.value < 0 ? 'text-destructive' : ''} ${row.bold ? 'text-foreground' : ''} ${'highlight' in row && row.highlight ? 'text-amber-700 dark:text-amber-400 font-medium' : ''}`}>
                      {'note' in row && row.note ? '' : row.value < 0 ? '-' : ''}{fmt(row.value)}
                    </span>
                    {/* Voce extra: matita + cestino fuori dal flusso (nel pr-6), come le icone OTA */}
                    {row.extra && !hasDocuments && (
                      <div className="absolute left-full ml-1.5 flex items-center gap-1">
                        <button
                          onClick={() => {
                            const r = row.extra!;
                            setEditingRigaId(r.id);
                            setShowAggiungiVoce(false);
                            setVoceForm({
                              descrizione: r.descrizione,
                              importo: String(r.importo),
                              includeInFatturaPm: r.includeInFatturaPm,
                            });
                          }}
                          disabled={isUpdatingSplit}
                          title="Modifica voce"
                          className="text-muted-foreground hover:text-foreground disabled:opacity-40"
                        >
                          <Pencil className="h-3 w-3" />
                        </button>
                        <button
                          onClick={() => handleEliminaVoce(row.extra!.id)}
                          disabled={isUpdatingSplit}
                          title="Elimina voce"
                          className="text-destructive hover:text-destructive/80 disabled:opacity-40"
                        >
                          <Trash2 className="h-3 w-3" />
                        </button>
                      </div>
                    )}
                  </div>
                )}
              </div>
              {/* Dopo l'ultima voce di costo: pulsante "Aggiungi voce" e form inline
                  (aggiunta o modifica di una voce extra), solo senza documenti emessi. */}
              {row.ultimaVoceCosto && !hasDocuments && (
                showAggiungiVoce || editingRigaId != null ? (
                  <div className="border rounded-md p-3 space-y-2 bg-muted/30">
                    <div className="flex gap-2">
                      <Input
                        placeholder="Descrizione voce (es. Parcheggio)"
                        value={voceForm.descrizione}
                        onChange={e => setVoceForm({ ...voceForm, descrizione: e.target.value })}
                        className="flex-1 h-7 text-xs"
                        autoFocus
                      />
                      <Input
                        type="number"
                        placeholder="€"
                        value={voceForm.importo}
                        onChange={e => setVoceForm({ ...voceForm, importo: e.target.value })}
                        className="w-24 h-7 text-xs"
                        min="0.01"
                        step="0.01"
                      />
                    </div>
                    <div className="flex items-center justify-between">
                      <label className="flex items-center gap-2 text-xs text-muted-foreground cursor-pointer">
                        <Switch
                          checked={voceForm.includeInFatturaPm}
                          onCheckedChange={v => setVoceForm({ ...voceForm, includeInFatturaPm: v })}
                          className="scale-75"
                        />
                        Includi in fattura PM
                      </label>
                      <div className="flex gap-1">
                        <button
                          onClick={handleSalvaVoce}
                          disabled={
                            isUpdatingSplit
                            || !voceForm.descrizione.trim()
                            || !(parseFloat(voceForm.importo) > 0)
                          }
                          className="text-xs px-2 py-1 bg-primary text-primary-foreground rounded disabled:opacity-50"
                        >
                          Salva
                        </button>
                        <button onClick={chiudiFormVoce} className="text-xs px-2 py-1 border rounded">
                          Annulla
                        </button>
                      </div>
                    </div>
                  </div>
                ) : (
                  <button
                    onClick={() => {
                      setShowAggiungiVoce(true);
                      setEditingRigaId(null);
                      setVoceForm({ descrizione: '', importo: '', includeInFatturaPm: true });
                    }}
                    disabled={isUpdatingSplit}
                    className="flex items-center gap-1 text-xs text-primary hover:text-primary/80 disabled:opacity-50"
                  >
                    <Plus className="h-3 w-3" />
                    Aggiungi voce
                  </button>
                )
              )}
              </Fragment>
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
          righeFattura={righeFatturaPm}
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
