import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Upload, FileSpreadsheet, CheckCircle2, AlertCircle, AlertTriangle, Loader2, X, Settings2, ChevronDown, ChevronRight } from 'lucide-react';
import {
  uploadImportFiles, previewImportV2, confirmImport,
  getImportTemplates, saveImportTemplate,
  type ImportUploadResponse, type ImportPreview, type ImportResult, type ImportTemplate,
} from '@/api/importApi';
import TemplateManagerDialog from '@/components/TemplateManagerDialog';
import QuickContractDialog from '@/components/QuickContractDialog';
import { useToast } from '@/hooks/use-toast';

const steps = ['Upload Files', 'Mapping Colonne', 'Anteprima', 'Conferma', 'Risultato'];

const NONE = '__none__';

// Stati considerati "cancellati" di default — deve restare allineata alla costante
// STATI_CANCELLATI del backend (BookingImportService). Usata per preselezionare i checkbox.
const STATI_CANCELLATI = [
  'cancellata', 'cancelled', 'canceled',
  'annullata', 'annullato', 'annulled',
  'cancellata/a', 'no show',
  'rifiutata', 'rejected', 'expired',
];

interface StepNavProps {
  onBack?: () => void;
  onNext?: () => void;
  nextLabel?: string;
  nextDisabled?: boolean;
  loading?: boolean;
}

// Barra di navigazione dello step: renderizzata sia in cima che in fondo ad ogni step.
const StepNav = ({ onBack, onNext, nextLabel = 'Avanti', nextDisabled, loading }: StepNavProps) => (
  <div className="flex gap-3 justify-end">
    {onBack && <Button variant="outline" onClick={onBack} disabled={loading}>Indietro</Button>}
    {onNext && (
      <Button onClick={onNext} disabled={nextDisabled || loading} className="gap-2">
        {loading && <Loader2 className="h-4 w-4 animate-spin" />}
        {nextLabel}
      </Button>
    )}
  </div>
);

interface FieldDef { key: string; label: string; required?: boolean; }

const BOOKING_FIELDS: FieldDef[] = [
  { key: 'BOOKING_ID',     label: 'ID Prenotazione',      required: true },
  { key: 'ORIGINE',        label: 'Origine (canale)',     required: true },
  { key: 'STRUTTURA',      label: 'Struttura (cod. OTA)', required: true },
  { key: 'CHECKIN',        label: 'Check-in',             required: true },
  { key: 'CHECKOUT',       label: 'Check-out',            required: true },
  { key: 'IMPORTO_TOTALE', label: 'Importo totale',       required: true },
  { key: 'ADULTI',         label: 'Adulti' },
  { key: 'BAMBINI',        label: 'Bambini' },
  { key: 'NEONATI',        label: 'Neonati' },
  { key: 'COMMISSIONE',    label: 'Commissione canale' },
  { key: 'STATO',          label: 'Stato' },
  { key: 'CLIENTE_NOME',   label: 'Cliente nome' },
  { key: 'CLIENTE_COGNOME',label: 'Cliente cognome' },
];

const GUEST_FIELDS: FieldDef[] = [
  { key: 'BOOKING_ID',     label: 'ID Prenotazione (merge)', required: true },
  { key: 'NOME',           label: 'Nome' },
  { key: 'COGNOME',        label: 'Cognome' },
  { key: 'DATA_NASCITA',   label: 'Data di nascita' },
  { key: 'SESSO',          label: 'Sesso' },
  { key: 'COMUNE_NASCITA', label: 'Comune di nascita' },
  { key: 'DOCUMENTO',      label: 'Documento' },
  { key: 'NUM_DOCUMENTO',  label: 'Numero documento' },
  { key: 'NAZIONE',        label: 'Nazione' },
];

const ImportBookings = () => {
  const { toast } = useToast();
  const navigate = useNavigate();
  const bookingInputRef = useRef<HTMLInputElement>(null);
  const guestInputRef = useRef<HTMLInputElement>(null);

  const [step, setStep] = useState(0);

  // Step 0
  const [bookingFile, setBookingFile] = useState<File | null>(null);
  const [guestFile, setGuestFile] = useState<File | null>(null);
  const [dragTarget, setDragTarget] = useState<'booking' | 'guest' | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [headerRow, setHeaderRow] = useState<number>(0);
  const [advancedOpen, setAdvancedOpen] = useState(false);

  // Template di mapping
  const [templates, setTemplates] = useState<ImportTemplate[]>([]);
  const [selectedTemplate, setSelectedTemplate] = useState<ImportTemplate | null>(null);
  const [managerOpen, setManagerOpen] = useState(false);

  // Step 1
  const [uploadResponse, setUploadResponse] = useState<ImportUploadResponse | null>(null);
  const [bookingMapping, setBookingMapping] = useState<Record<string, string>>({});
  const [guestMapping, setGuestMapping] = useState<Record<string, string>>({});
  const [isPreviewing, setIsPreviewing] = useState(false);
  const [saveAsTemplate, setSaveAsTemplate] = useState(false);
  const [templateName, setTemplateName] = useState('');
  const [templateDesc, setTemplateDesc] = useState('');
  const [statiDaEscludere, setStatiDaEscludere] = useState<Set<string>>(new Set());

  // Step 2/3
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [isConfirming, setIsConfirming] = useState(false);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [contractDialogOpen, setContractDialogOpen] = useState(false);
  const [contractDialogProperty, setContractDialogProperty] = useState<{ id: number; name: string } | null>(null);

  const hasGuest = !!uploadResponse?.guestSessionId;

  const loadTemplates = async () => {
    try {
      setTemplates(await getImportTemplates());
    } catch (err) {
      // non bloccante: i template sono opzionali
      console.warn('Caricamento template fallito', err);
    }
  };

  useEffect(() => {
    loadTemplates();
  }, []);

  // Filtra un mapping template tenendo solo i campi le cui colonne esistono nel file caricato.
  const filterMappingToColumns = (
    mapping: Record<string, string>,
    columns: string[],
  ): Record<string, string> => {
    const cols = new Set(columns);
    const out: Record<string, string> = {};
    for (const [field, col] of Object.entries(mapping ?? {})) {
      if (cols.has(col)) out[field] = col;
    }
    return out;
  };

  const handleUpload = async () => {
    if (!bookingFile) return;
    setUploadError(null);
    setIsUploading(true);
    try {
      const resp = await uploadImportFiles(bookingFile, guestFile ?? undefined, headerRow);
      setUploadResponse(resp);
      // All'apertura dello step 1: se è stato scelto un template precompila il mapping
      // (solo per le colonne presenti nel file), altrimenti usa il mapping suggerito.
      if (selectedTemplate) {
        setBookingMapping(filterMappingToColumns(selectedTemplate.bookingMapping, resp.bookingColumns));
        setGuestMapping(filterMappingToColumns(selectedTemplate.guestMapping ?? {}, resp.guestColumns ?? []));
      } else {
        setBookingMapping({ ...resp.suggestedBookingMapping });
        setGuestMapping({ ...(resp.suggestedGuestMapping ?? {}) });
      }
      // preseleziona da escludere i valori STATO che matchano la lista cancellati di default
      const preselected = new Set<string>();
      (resp.statoColumnValues ?? []).forEach(v => {
        const norm = v.trim().toLowerCase();
        if (STATI_CANCELLATI.includes(norm)) preselected.add(norm);
      });
      setStatiDaEscludere(preselected);
      // default nome template = nome file senza estensione
      setTemplateName(bookingFile.name.replace(/\.[^.]+$/, ''));
      setStep(1);
    } catch (err) {
      setUploadError((err as Error).message);
    } finally {
      setIsUploading(false);
    }
  };

  const setMapping = (
    setter: React.Dispatch<React.SetStateAction<Record<string, string>>>,
    field: string,
    value: string,
  ) => {
    setter(prev => {
      const next = { ...prev };
      if (value === NONE || !value) delete next[field];
      else next[field] = value;
      return next;
    });
  };

  const canPreview =
    BOOKING_FIELDS.filter(f => f.required).every(f => !!bookingMapping[f.key]) &&
    (!hasGuest || !!guestMapping['BOOKING_ID']);

  const handlePreview = async () => {
    if (!uploadResponse) return;
    setIsPreviewing(true);
    try {
      // Salvataggio template (opzionale) — non deve bloccare la preview in caso di errore.
      if (saveAsTemplate && templateName.trim()) {
        try {
          await saveImportTemplate({
            nome: templateName.trim(),
            descrizione: templateDesc.trim() || undefined,
            headerRow,
            bookingMapping,
            guestMapping: hasGuest ? guestMapping : {},
          });
          toast({ title: 'Template salvato' });
          await loadTemplates();
        } catch {
          toast({
            title: 'Template non salvato',
            description: 'Nome già esistente — scegli un altro nome',
            variant: 'destructive',
          });
        }
      }
      const prev = await previewImportV2({
        bookingSessionId: uploadResponse.bookingSessionId,
        guestSessionId: uploadResponse.guestSessionId,
        mapping: {
          bookingMapping,
          guestMapping: hasGuest ? guestMapping : undefined,
          statiDaEscludere: [...statiDaEscludere],
        },
      });
      setPreview(prev);
      setStep(2);
    } catch (err) {
      toast({ title: 'Errore anteprima', description: (err as Error).message, variant: 'destructive' });
    } finally {
      setIsPreviewing(false);
    }
  };

  const handleConfirm = async () => {
    if (!preview) return;
    const newIds = preview.rows.filter(r => r.status === 'nuova').map(r => r.externalBookingId);
    setIsConfirming(true);
    try {
      const res = await confirmImport(preview.importSessionId, newIds);
      setResult(res);
      setStep(4);
    } catch (err) {
      toast({ title: 'Errore import', description: (err as Error).message, variant: 'destructive' });
    } finally {
      setIsConfirming(false);
    }
  };

  const resetAll = () => {
    setStep(0);
    setBookingFile(null);
    setGuestFile(null);
    setUploadResponse(null);
    setBookingMapping({});
    setGuestMapping({});
    setPreview(null);
    setResult(null);
    setUploadError(null);
    setSelectedTemplate(null);
    setSaveAsTemplate(false);
    setTemplateName('');
    setTemplateDesc('');
    setStatiDaEscludere(new Set());
    setHeaderRow(0);
    setAdvancedOpen(false);
  };

  const statusBadge = (status: string, msg?: string) => {
    const cls = status === 'nuova'
      ? 'bg-success/10 text-success border-success/20'
      : status === 'duplicata'
      ? 'bg-warning/10 text-warning border-warning/20'
      : 'bg-destructive/10 text-destructive border-destructive/20';
    return <Badge variant="outline" className={cls} title={msg}>{status}</Badge>;
  };

  const dropZone = (kind: 'booking' | 'guest') => {
    const file = kind === 'booking' ? bookingFile : guestFile;
    const setFile = kind === 'booking' ? setBookingFile : setGuestFile;
    const inputRef = kind === 'booking' ? bookingInputRef : guestInputRef;
    const required = kind === 'booking';
    return (
      <div
        className={`border-2 border-dashed rounded-lg p-8 text-center space-y-3 transition-colors ${
          dragTarget === kind ? 'border-primary bg-primary/5' : 'border-border'
        }`}
        onDragOver={e => { e.preventDefault(); setDragTarget(kind); }}
        onDragLeave={() => setDragTarget(null)}
        onDrop={e => { e.preventDefault(); setDragTarget(null); const f = e.dataTransfer.files?.[0]; if (f) setFile(f); }}
      >
        <Upload className="h-8 w-8 mx-auto text-muted-foreground" />
        <div>
          <p className="font-medium text-sm">
            {kind === 'booking' ? 'File Prenotazioni' : 'File Ospiti'}
            {required ? <span className="text-destructive"> *</span> : <span className="text-muted-foreground"> (opzionale)</span>}
          </p>
          <p className="text-xs text-muted-foreground mt-1">XLSX o CSV (max 10MB)</p>
        </div>
        {file ? (
          <div className="flex items-center justify-center gap-2 text-sm">
            <FileSpreadsheet className="h-4 w-4 text-primary" />
            <span className="font-medium truncate max-w-[180px]">{file.name}</span>
            <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => setFile(null)}>
              <X className="h-3.5 w-3.5" />
            </Button>
          </div>
        ) : (
          <>
            <input ref={inputRef} type="file" accept=".csv,.xlsx" className="hidden"
                   onChange={e => { const f = e.target.files?.[0]; if (f) setFile(f); }} />
            <Button variant="outline" size="sm" onClick={() => inputRef.current?.click()}>Seleziona File</Button>
          </>
        )}
      </div>
    );
  };

  const toggleStatoEscluso = (val: string) => {
    const norm = val.trim().toLowerCase();
    setStatiDaEscludere(prev => {
      const next = new Set(prev);
      if (next.has(norm)) next.delete(norm); else next.add(norm);
      return next;
    });
  };

  const statoColumnValues = uploadResponse?.statoColumnValues ?? null;

  const mappingCard = (
    title: string,
    fields: FieldDef[],
    columns: string[],
    mapping: Record<string, string>,
    setter: React.Dispatch<React.SetStateAction<Record<string, string>>>,
  ) => (
    <Card className="flex-1">
      <CardHeader><CardTitle className="text-base">{title}</CardTitle></CardHeader>
      <CardContent className="space-y-3">
        {fields.map(f => (
          <div key={f.key} className="space-y-2">
            <div className="grid grid-cols-2 gap-3 items-center">
              <span className="text-sm">
                {f.label}{f.required && <span className="text-destructive"> *</span>}
              </span>
              <Select value={mapping[f.key] ?? NONE} onValueChange={v => setMapping(setter, f.key, v)}>
                <SelectTrigger className="h-9"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>-- Non mappare --</SelectItem>
                  {columns.map(c => <SelectItem key={c} value={c}>{c}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>

            {/* Sotto la colonna STATO: scelta dei valori da escludere dall'import */}
            {f.key === 'STATO' && statoColumnValues && statoColumnValues.length > 0 && (
              <div className="rounded-lg border bg-muted/30 p-3 space-y-2">
                <p className="text-xs font-medium">
                  Valori trovati nel file — seleziona quelli da ESCLUDERE dall'import:
                </p>
                <div className="space-y-1.5">
                  {statoColumnValues.map(val => {
                    const escluso = statiDaEscludere.has(val.trim().toLowerCase());
                    return (
                      <div key={val} className="flex items-center gap-2">
                        <Checkbox
                          id={`stato-${val}`}
                          checked={escluso}
                          onCheckedChange={() => toggleStatoEscluso(val)}
                        />
                        <Label htmlFor={`stato-${val}`} className="text-sm cursor-pointer flex-1">
                          {val}
                        </Label>
                        <span className={`text-xs ${escluso ? 'text-destructive' : 'text-success'}`}>
                          {escluso ? 'Escludi' : 'Importa'}
                        </span>
                      </div>
                    );
                  })}
                </div>
                <p className="text-xs text-muted-foreground">
                  Le righe con i valori selezionati non verranno importate.
                </p>
              </div>
            )}
          </div>
        ))}
      </CardContent>
    </Card>
  );

  return (
    <div className="space-y-6 max-w-5xl">
      <div>
        <h1 className="text-2xl font-bold">Import Dati</h1>
        <p className="text-sm text-muted-foreground">Importa prenotazioni con file ospiti e mapping colonne personalizzato</p>
      </div>

      {/* Stepper */}
      <div className="flex items-center gap-2 flex-wrap">
        {steps.map((s, i) => (
          <div key={s} className="flex items-center gap-2">
            <div className={`h-7 w-7 rounded-full flex items-center justify-center text-xs font-medium ${
              i <= step ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground'
            }`}>
              {i < step ? <CheckCircle2 className="h-4 w-4" /> : i + 1}
            </div>
            <span className={`text-sm hidden sm:inline ${i <= step ? 'font-medium' : 'text-muted-foreground'}`}>{s}</span>
            {i < steps.length - 1 && <div className="w-6 h-px bg-border" />}
          </div>
        ))}
      </div>

      {/* Step 0: Upload Files */}
      {step === 0 && (
        <div className="space-y-4">
          <StepNav onNext={handleUpload} nextLabel="Avanti" nextDisabled={!bookingFile} loading={isUploading} />
          <Card>
          <CardContent className="p-6 space-y-4">
            {/* Template di importazione */}
            <div className="rounded-lg border bg-muted/30 p-4 space-y-3">
              <div className="flex items-center justify-between gap-2">
                <span className="text-sm font-medium">Template di importazione</span>
                <Button variant="link" size="sm" className="h-auto p-0 gap-1"
                        onClick={() => setManagerOpen(true)}>
                  <Settings2 className="h-3.5 w-3.5" /> Gestisci template
                </Button>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 items-center">
                <span className="text-sm text-muted-foreground">Usa template salvato</span>
                <Select
                  value={selectedTemplate ? String(selectedTemplate.id) : NONE}
                  onValueChange={v => {
                    const tpl = v === NONE ? null : templates.find(t => String(t.id) === v) ?? null;
                    setSelectedTemplate(tpl);
                    // precompila la riga intestazioni dal template e apri le opzioni avanzate se serve
                    setHeaderRow(tpl?.headerRow ?? 0);
                    if (tpl && tpl.headerRow > 0) setAdvancedOpen(true);
                  }}
                >
                  <SelectTrigger className="h-9"><SelectValue placeholder="-- Nessun template --" /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value={NONE}>-- Nessun template --</SelectItem>
                    {templates.map(t => (
                      <SelectItem key={t.id} value={String(t.id)}>
                        {t.nome}{t.descrizione ? ` (${t.descrizione})` : ''}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              {selectedTemplate && (
                <div className="flex items-center gap-2 text-sm text-success">
                  <CheckCircle2 className="h-4 w-4" />
                  <span>Template selezionato: <strong>{selectedTemplate.nome}</strong> — il mapping verrà applicato al passo successivo</span>
                  <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => setSelectedTemplate(null)}>
                    <X className="h-3.5 w-3.5" />
                  </Button>
                </div>
              )}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {dropZone('booking')}
              {dropZone('guest')}
            </div>
            {/* Opzioni avanzate: riga intestazioni */}
            <div className="border-t pt-3">
              <button type="button"
                      className="flex items-center gap-1 text-sm font-medium text-muted-foreground hover:text-foreground"
                      onClick={() => setAdvancedOpen(o => !o)}>
                {advancedOpen ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                Opzioni avanzate
              </button>
              {advancedOpen && (
                <div className="mt-3 space-y-1">
                  <Label htmlFor="headerRow" className="text-sm">Riga intestazioni (0 = prima riga)</Label>
                  <Input
                    id="headerRow"
                    type="number"
                    min={0}
                    max={20}
                    value={headerRow}
                    onChange={e => setHeaderRow(Math.max(0, Math.min(20, Number(e.target.value) || 0)))}
                    className="h-9 w-20"
                  />
                  <p className="text-xs text-muted-foreground">
                    Imposta un valore &gt; 0 se il file contiene righe di titolo prima delle intestazioni
                    delle colonne. Es: Airbnb export → 6
                  </p>
                </div>
              )}
            </div>

            {uploadError && (
              <div className="flex items-center gap-2 text-destructive text-sm">
                <AlertCircle className="h-4 w-4" /> {uploadError}
              </div>
            )}
            <StepNav onNext={handleUpload} nextLabel="Avanti" nextDisabled={!bookingFile} loading={isUploading} />
          </CardContent>
          </Card>
        </div>
      )}

      {/* Step 1: Mapping Colonne */}
      {step === 1 && uploadResponse && (
        <div className="space-y-4">
          <StepNav onBack={() => setStep(0)} onNext={handlePreview} nextLabel="Genera Anteprima"
                   nextDisabled={!canPreview} loading={isPreviewing} />
          <div className="flex flex-col md:flex-row gap-4">
            {mappingCard('Mapping Prenotazioni', BOOKING_FIELDS, uploadResponse.bookingColumns, bookingMapping, setBookingMapping)}
            {hasGuest && mappingCard('Mapping Ospiti', GUEST_FIELDS, uploadResponse.guestColumns ?? [], guestMapping, setGuestMapping)}
          </div>
          {!canPreview && (
            <div className="flex items-center gap-2 text-warning text-sm">
              <AlertTriangle className="h-4 w-4" /> Mappa tutti i campi obbligatori (*) per continuare.
            </div>
          )}

          {/* Salva questo mapping come template */}
          <Card>
            <CardContent className="p-4 space-y-3">
              <div className="flex items-center gap-2">
                <Checkbox id="saveTpl" checked={saveAsTemplate}
                          onCheckedChange={v => setSaveAsTemplate(v === true)} />
                <Label htmlFor="saveTpl" className="text-sm cursor-pointer">Salva questo mapping come template</Label>
              </div>
              {saveAsTemplate && (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div className="space-y-1">
                    <Label className="text-xs">Nome template <span className="text-destructive">*</span></Label>
                    <Input value={templateName} onChange={e => setTemplateName(e.target.value)}
                           placeholder="Nome template" className="h-9" />
                  </div>
                  <div className="space-y-1">
                    <Label className="text-xs">Descrizione</Label>
                    <Input value={templateDesc} onChange={e => setTemplateDesc(e.target.value)}
                           placeholder="Opzionale" className="h-9" />
                  </div>
                </div>
              )}
            </CardContent>
          </Card>

          <StepNav onBack={() => setStep(0)} onNext={handlePreview} nextLabel="Genera Anteprima"
                   nextDisabled={!canPreview} loading={isPreviewing} />
        </div>
      )}

      {/* Step 2: Anteprima */}
      {step === 2 && preview && (
        <div className="space-y-4">
          <StepNav onBack={() => setStep(1)} onNext={() => setStep(3)}
                   nextLabel={`Conferma Import (${preview.newCount} prenotazioni)`}
                   nextDisabled={preview.newCount === 0} />
          <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              Anteprima Import
              <Badge variant="outline">{preview.fileName}</Badge>
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className={`grid grid-cols-2 gap-3 text-sm ${preview.excludedCount > 0 ? 'sm:grid-cols-5' : 'sm:grid-cols-4'}`}>
              <div className="p-3 rounded-lg bg-success/10">
                <p className="font-medium text-success">{preview.newCount} nuove</p>
                <p className="text-xs text-muted-foreground">Da importare</p>
              </div>
              <div className="p-3 rounded-lg bg-warning/10">
                <p className="font-medium text-warning">{preview.dupeCount} duplicate</p>
                <p className="text-xs text-muted-foreground">Già presenti</p>
              </div>
              <div className="p-3 rounded-lg bg-destructive/10">
                <p className="font-medium text-destructive">{preview.errorCount} errori</p>
                <p className="text-xs text-muted-foreground">Non importabili</p>
              </div>
              <div className="p-3 rounded-lg bg-muted">
                <p className="font-medium">{preview.warningCount ?? 0} avvisi</p>
                <p className="text-xs text-muted-foreground">Split economico</p>
              </div>
              {preview.excludedCount > 0 && (
                <div className="p-3 rounded-lg bg-muted">
                  <p className="font-medium text-muted-foreground">{preview.excludedCount} escluse</p>
                  <p className="text-xs text-muted-foreground">Prenotazioni cancellate</p>
                </div>
              )}
            </div>

            <div className="border rounded-lg overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>ID Prenotazione</TableHead>
                    <TableHead>#</TableHead>
                    <TableHead>Ospite</TableHead>
                    <TableHead>Immobile</TableHead>
                    <TableHead>Canale</TableHead>
                    <TableHead>Check-in</TableHead>
                    <TableHead>Importo</TableHead>
                    <TableHead>Stato</TableHead>
                    <TableHead>Warnings</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {preview.rows.map(row => (
                    <TableRow key={row.rowNumber} className={row.status === 'errore' ? 'opacity-60' : ''}>
                      <TableCell className="text-xs font-mono">
                        {row.externalBookingId && row.externalBookingId.length > 15
                          ? <span title={row.externalBookingId}>{row.externalBookingId.slice(0, 15)}…</span>
                          : row.externalBookingId}
                      </TableCell>
                      <TableCell className="text-xs text-muted-foreground">{row.rowNumber}</TableCell>
                      <TableCell className="text-sm font-medium">{row.guestName}</TableCell>
                      <TableCell className="text-sm">{row.propertyName ?? row.propertyCode}</TableCell>
                      <TableCell className="text-sm">{row.channelName ?? row.channelCode}</TableCell>
                      <TableCell className="text-sm">{row.checkinDate}</TableCell>
                      <TableCell className="text-sm font-mono">
                        {row.grossAmount != null ? `€ ${row.grossAmount.toFixed(2)}` : '—'}
                      </TableCell>
                      <TableCell>{statusBadge(row.status, row.errorMessage ?? undefined)}</TableCell>
                      <TableCell className="text-xs">
                        {row.errorMessage
                          ? <span className="text-destructive">{row.errorMessage}</span>
                          : (row.splitWarnings && row.splitWarnings.length > 0)
                          ? <div className="space-y-0.5">
                              {row.splitWarnings.map((w, i) => {
                                const isRimanenza = w.toLowerCase().includes('rimanenza') && !!row.fkPropertyId;
                                return (
                                  <div key={i} className="flex items-start gap-1 text-warning">
                                    <AlertTriangle className="h-3.5 w-3.5 shrink-0 mt-0.5" />
                                    {isRimanenza ? (
                                      <button
                                        type="button"
                                        className="text-left underline cursor-pointer hover:text-warning/80"
                                        onClick={() => {
                                          setContractDialogProperty({
                                            id: row.fkPropertyId!,
                                            name: row.propertyName ?? row.propertyCode,
                                          });
                                          setContractDialogOpen(true);
                                        }}
                                      >
                                        {w}
                                      </button>
                                    ) : (
                                      <span>{w}</span>
                                    )}
                                  </div>
                                );
                              })}
                            </div>
                          : ''}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <StepNav onBack={() => setStep(1)} onNext={() => setStep(3)}
                     nextLabel={`Conferma Import (${preview.newCount} prenotazioni)`}
                     nextDisabled={preview.newCount === 0} />
          </CardContent>
          </Card>
        </div>
      )}

      {/* Step 3: Conferma */}
      {step === 3 && preview && (
        <div className="space-y-4">
          <StepNav onBack={() => setStep(2)} onNext={handleConfirm} nextLabel="Procedi" loading={isConfirming} />
          <Card>
          <CardHeader><CardTitle className="text-base">Conferma Import</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm">
              Stai per importare <strong>{preview.newCount} prenotazioni</strong>.
              {preview.dupeCount > 0 && <> {preview.dupeCount} duplicate saranno ignorate.</>}
              {' '}Questa azione non può essere annullata.
            </p>
            <StepNav onBack={() => setStep(2)} onNext={handleConfirm} nextLabel="Procedi" loading={isConfirming} />
          </CardContent>
          </Card>
        </div>
      )}

      {/* Step 4: Risultato */}
      {step === 4 && result && (
        <Card>
          <CardContent className="p-8 text-center space-y-4">
            <CheckCircle2 className="h-16 w-16 mx-auto text-success" />
            <div>
              <p className="text-lg font-bold">Import Completato</p>
              <p className="text-sm text-muted-foreground">
                {result.imported} importate · {result.skipped} saltate · {result.errors} errori
              </p>
            </div>
            {result.errorMessages.length > 0 && (
              <div className="text-left bg-destructive/10 rounded-lg p-3 space-y-1">
                {result.errorMessages.map((m, i) => (
                  <p key={i} className="text-xs text-destructive">{m}</p>
                ))}
              </div>
            )}
            <div className="flex gap-3 justify-center">
              <Button variant="outline" onClick={resetAll}>Nuovo Import</Button>
              <Button onClick={() => navigate('/bookings')}>Vai alle Prenotazioni</Button>
            </div>
          </CardContent>
        </Card>
      )}

      <TemplateManagerDialog
        open={managerOpen}
        onOpenChange={setManagerOpen}
        onChanged={async () => {
          const list = await getImportTemplates();
          setTemplates(list);
          // se il template selezionato è stato eliminato/rinominato via il manager, riconcilia
          if (selectedTemplate && !list.some(t => t.id === selectedTemplate.id)) {
            setSelectedTemplate(null);
          }
        }}
      />

      <QuickContractDialog
        propertyId={contractDialogProperty?.id}
        propertyName={contractDialogProperty?.name}
        open={contractDialogOpen}
        onClose={() => setContractDialogOpen(false)}
        onSaved={() => {
          setContractDialogOpen(false);
          toast({
            title: 'Regola salvata',
            description: "Torna allo step 1 e rigenera l'anteprima per aggiornare i warnings",
          });
        }}
      />
    </div>
  );
};

export default ImportBookings;
