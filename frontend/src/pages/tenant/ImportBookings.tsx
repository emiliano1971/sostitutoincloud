import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Upload, FileSpreadsheet, CheckCircle2, AlertCircle, Building2, Network, Loader2 } from 'lucide-react';
import { uploadImportFile, confirmImport, type ImportPreview, type ImportResult } from '@/api/importApi';
import { useToast } from '@/hooks/use-toast';

const steps = ['Upload File', 'Anteprima', 'Conferma', 'Risultato'];

type ImportSource = 'airbnb' | 'booking' | 'alloggiati' | 'channelmanager';

const sourceInfo: Record<ImportSource, { label: string; color: string; description: string; format: string }> = {
  airbnb:        { label: 'Airbnb',          color: '#FF5A5F', description: 'Importa il file CSV esportato dalla sezione "Prenotazioni" di Airbnb Host Dashboard', format: 'CSV (UTF-8)' },
  booking:       { label: 'Booking.com',     color: '#003580', description: "Importa il file CSV esportato dall'Extranet di Booking.com → Prenotazioni → Esporta",  format: 'CSV (UTF-8)' },
  alloggiati:    { label: 'AlloggiatiWeb',   color: '#1D4ED8', description: 'Importa il file TXT nel formato tracciato ministeriale AlloggiatiWeb (Questura)',         format: 'TXT tracciato AlloggiatiWeb' },
  channelmanager:{ label: 'Channel Manager', color: '#7C3AED', description: 'Importa il file CSV esportato dal tuo Channel Manager',                                  format: 'CSV (UTF-8)' },
};

const ImportBookings = () => {
  const { toast } = useToast();
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [step, setStep] = useState(0);
  const [source, setSource] = useState<ImportSource>('airbnb');
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [isConfirming, setIsConfirming] = useState(false);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [isDragging, setIsDragging] = useState(false);

  const info = sourceInfo[source];
  const isLive = source === 'airbnb' || source === 'booking';

  const handleSourceChange = (val: string) => {
    setSource(val as ImportSource);
    setStep(0);
    setPreview(null);
    setUploadError(null);
  };

  const processFile = async (file: File) => {
    setUploadError(null);
    setIsUploading(true);
    try {
      const prev = await uploadImportFile(file);
      setPreview(prev);
      setStep(1);
    } catch (err) {
      setUploadError((err as Error).message);
    } finally {
      setIsUploading(false);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) processFile(file);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    const file = e.dataTransfer.files?.[0];
    if (file) processFile(file);
  };

  const handleConfirm = async () => {
    if (!preview) return;
    const newIds = preview.rows.filter(r => r.status === 'nuova').map(r => r.externalBookingId);
    setIsConfirming(true);
    try {
      const res = await confirmImport(preview.importSessionId, newIds);
      setResult(res);
      setStep(3);
    } catch (err) {
      toast({ title: 'Errore import', description: (err as Error).message, variant: 'destructive' });
    } finally {
      setIsConfirming(false);
    }
  };

  const statusBadge = (status: string, msg?: string) => {
    const cls = status === 'nuova'
      ? 'bg-success/10 text-success border-success/20'
      : status === 'duplicata'
      ? 'bg-warning/10 text-warning border-warning/20'
      : 'bg-destructive/10 text-destructive border-destructive/20';
    return <Badge variant="outline" className={cls} title={msg}>{status}</Badge>;
  };

  return (
    <div className="space-y-6 max-w-4xl">
      <div>
        <h1 className="text-2xl font-bold">Import Dati</h1>
        <p className="text-sm text-muted-foreground">Importa prenotazioni da sorgenti esterne</p>
      </div>

      <Tabs value={source} onValueChange={handleSourceChange}>
        <TabsList className="grid w-full grid-cols-4">
          <TabsTrigger value="airbnb" className="gap-2">
            <div className="h-2 w-2 rounded-full bg-[#FF5A5F]" /> Airbnb
          </TabsTrigger>
          <TabsTrigger value="booking" className="gap-2">
            <div className="h-2 w-2 rounded-full bg-[#003580]" /> Booking.com
          </TabsTrigger>
          <TabsTrigger value="alloggiati" className="gap-2">
            <Building2 className="h-3.5 w-3.5" /> AlloggiatiWeb
          </TabsTrigger>
          <TabsTrigger value="channelmanager" className="gap-2">
            <Network className="h-3.5 w-3.5" /> Channel Manager
          </TabsTrigger>
        </TabsList>
      </Tabs>

      <Card className="border-l-4" style={{ borderLeftColor: info.color }}>
        <CardContent className="p-4">
          <p className="text-sm">{info.description}</p>
          <p className="text-xs text-muted-foreground mt-1">Formato atteso: <strong>{info.format}</strong></p>
        </CardContent>
      </Card>

      {/* Stepper */}
      <div className="flex items-center gap-2">
        {steps.map((s, i) => (
          <div key={s} className="flex items-center gap-2">
            <div className={`h-7 w-7 rounded-full flex items-center justify-center text-xs font-medium ${
              i <= step ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground'
            }`}>
              {i < step ? <CheckCircle2 className="h-4 w-4" /> : i + 1}
            </div>
            <span className={`text-sm hidden sm:inline ${i <= step ? 'font-medium' : 'text-muted-foreground'}`}>{s}</span>
            {i < steps.length - 1 && <div className="w-8 h-px bg-border" />}
          </div>
        ))}
      </div>

      {/* Step 0: Upload */}
      {step === 0 && (
        <Card>
          <CardContent className="p-8">
            {!isLive ? (
              <div className="text-center py-12 space-y-3">
                <p className="text-lg font-medium text-muted-foreground">Coming Soon</p>
                <p className="text-sm text-muted-foreground">L'import da {info.label} sarà disponibile in una versione futura.</p>
              </div>
            ) : (
              <div
                className={`border-2 border-dashed rounded-lg p-12 text-center space-y-4 transition-colors ${
                  isDragging ? 'border-primary bg-primary/5' : 'border-border'
                }`}
                onDragOver={e => { e.preventDefault(); setIsDragging(true); }}
                onDragLeave={() => setIsDragging(false)}
                onDrop={handleDrop}
              >
                {isUploading ? (
                  <div className="space-y-3">
                    <Loader2 className="h-12 w-12 mx-auto animate-spin text-primary" />
                    <p className="font-medium">Analisi in corso…</p>
                  </div>
                ) : (
                  <>
                    <Upload className="h-12 w-12 mx-auto text-muted-foreground" />
                    <div>
                      <p className="font-medium">Trascina il file qui o clicca per selezionare</p>
                      <p className="text-sm text-muted-foreground mt-1">CSV (max 10MB)</p>
                    </div>
                    {uploadError && (
                      <div className="flex items-center justify-center gap-2 text-destructive text-sm">
                        <AlertCircle className="h-4 w-4" /> {uploadError}
                      </div>
                    )}
                    <input ref={fileInputRef} type="file" accept=".csv" className="hidden" onChange={handleFileChange} />
                    <Button onClick={() => fileInputRef.current?.click()}>Seleziona File</Button>
                  </>
                )}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Step 1: Preview */}
      {step === 1 && preview && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              Anteprima Import
              <Badge variant="outline">{preview.fileName}</Badge>
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center gap-3 p-3 rounded-lg bg-muted/50">
              <FileSpreadsheet className="h-8 w-8 text-primary" />
              <div>
                <p className="text-sm font-medium">{preview.fileName}</p>
                <p className="text-xs text-muted-foreground">{preview.totalRows} righe totali</p>
              </div>
            </div>

            <div className="grid grid-cols-3 gap-3 text-sm">
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
            </div>

            <div className="border rounded-lg overflow-hidden">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>#</TableHead>
                    <TableHead>Ospite</TableHead>
                    <TableHead>Immobile</TableHead>
                    <TableHead>Canale</TableHead>
                    <TableHead>Check-in</TableHead>
                    <TableHead>Importo</TableHead>
                    <TableHead>Stato</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {preview.rows.map(row => (
                    <TableRow key={row.rowNumber} className={row.status === 'errore' ? 'opacity-60' : ''}>
                      <TableCell className="text-xs text-muted-foreground">{row.rowNumber}</TableCell>
                      <TableCell className="text-sm font-medium">{row.guestName}</TableCell>
                      <TableCell className="text-sm">{row.propertyName ?? row.propertyCode}</TableCell>
                      <TableCell className="text-sm">{row.channelName ?? row.channelCode}</TableCell>
                      <TableCell className="text-sm">{row.checkinDate}</TableCell>
                      <TableCell className="text-sm font-mono">
                        {row.grossAmount != null ? `€ ${row.grossAmount.toFixed(2)}` : '—'}
                      </TableCell>
                      <TableCell>{statusBadge(row.status, row.errorMessage ?? undefined)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <div className="flex gap-3 justify-end">
              <Button variant="outline" onClick={() => { setStep(0); setPreview(null); }}>Indietro</Button>
              <Button onClick={() => setStep(2)} disabled={preview.newCount === 0}>
                Conferma Import ({preview.newCount} prenotazioni)
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Step 2: Confirm */}
      {step === 2 && preview && (
        <Card>
          <CardHeader><CardTitle className="text-base">Conferma Import</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm">
              Stai per importare <strong>{preview.newCount} prenotazioni</strong> da {info.label}.
              {preview.dupeCount > 0 && <> {preview.dupeCount} duplicate saranno ignorate.</>}
              {' '}Questa azione non può essere annullata.
            </p>
            <div className="flex gap-3 justify-end">
              <Button variant="outline" onClick={() => setStep(1)} disabled={isConfirming}>Indietro</Button>
              <Button onClick={handleConfirm} disabled={isConfirming} className="gap-2">
                {isConfirming && <Loader2 className="h-4 w-4 animate-spin" />}
                Procedi
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Step 3: Result */}
      {step === 3 && result && (
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
              <Button variant="outline" onClick={() => { setStep(0); setPreview(null); setResult(null); }}>
                Nuovo Import
              </Button>
              <Button onClick={() => navigate('/bookings')}>Vai alle Prenotazioni</Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
};

export default ImportBookings;
