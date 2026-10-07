import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Checkbox } from '@/components/ui/checkbox';
import { ArrowLeft, Download, FileSpreadsheet, Loader2, Upload, X, AlertCircle } from 'lucide-react';
import { cn } from '@/lib/utils';
import {
  previewImportProprietari,
  importaProprietari,
  type OwnerBulkImportResult,
  type OwnerImportPreviewResult,
} from '@/api/ownerApi';

// Copia di docs/import/template_importazione_proprietari.xlsx servita da frontend/public
const TEMPLATE_URL = `${import.meta.env.BASE_URL}templates/template_importazione_proprietari.xlsx`;

const OwnerBulkImport = () => {
  const navigate = useNavigate();
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [dragging, setDragging] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<OwnerBulkImportResult | null>(null);
  // 1 = upload, 2 = preview con selezione righe, 3 = risultati
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [preview, setPreview] = useState<OwnerImportPreviewResult | null>(null);
  const [selezione, setSelezione] = useState<Set<number>>(new Set());
  const [isImporting, setIsImporting] = useState(false);

  const righeSelezionabili = preview?.righe.filter(r => r.selezionabile) ?? [];
  const tutteSelezionate = righeSelezionabili.length > 0 && righeSelezionabili.every(r => selezione.has(r.numeroRiga));

  // Step 1 → 2: analisi del file, nessuna scrittura a DB
  const handleAnalizza = async () => {
    if (!file) return;
    setLoading(true);
    setError(null);
    try {
      const p = await previewImportProprietari(file);
      setPreview(p);
      setSelezione(new Set(p.righe.filter(r => r.selezionabile && r.selezionato).map(r => r.numeroRiga)));
      setStep(2);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Errore durante l\'analisi del file');
    } finally {
      setLoading(false);
    }
  };

  // Step 2 → 3: import delle sole righe selezionate (il file viene ricaricato)
  const handleConferma = async () => {
    if (!file || selezione.size === 0) return;
    setIsImporting(true);
    setError(null);
    try {
      setResult(await importaProprietari(file, Array.from(selezione).sort((a, b) => a - b)));
      setStep(3);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Errore durante l\'importazione');
    } finally {
      setIsImporting(false);
    }
  };

  const toggleTutte = () =>
    setSelezione(tutteSelezionate ? new Set() : new Set(righeSelezionabili.map(r => r.numeroRiga)));

  const reset = () => {
    setFile(null);
    setResult(null);
    setPreview(null);
    setSelezione(new Set());
    setError(null);
    setStep(1);
  };

  const scegliFile = (f: File | undefined) => {
    if (f) { setFile(f); setError(null); }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon" onClick={() => navigate('/owners')}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div>
          <h1 className="text-2xl font-bold">Importa proprietari da Excel</h1>
          <p className="text-sm text-muted-foreground">
            Una riga per immobile: il proprietario viene riconosciuto dal codice fiscale.
          </p>
        </div>
      </div>

      {step === 1 && (
        <Card>
          <CardContent className="p-6 space-y-4">
            <div
              className={`border-2 border-dashed rounded-lg p-8 text-center space-y-3 transition-colors ${
                dragging ? 'border-primary bg-primary/5' : 'border-border'
              }`}
              onDragOver={e => { e.preventDefault(); setDragging(true); }}
              onDragLeave={() => setDragging(false)}
              onDrop={e => { e.preventDefault(); setDragging(false); scegliFile(e.dataTransfer.files?.[0]); }}
            >
              <Upload className="h-8 w-8 mx-auto text-muted-foreground" />
              <div>
                <p className="font-medium text-sm">File proprietari e immobili</p>
                <p className="text-xs text-muted-foreground mt-1">XLSX o XLS compilato sul template</p>
              </div>
              {file ? (
                <div className="flex items-center justify-center gap-2 text-sm">
                  <FileSpreadsheet className="h-4 w-4 text-primary" />
                  <span className="font-medium truncate max-w-[220px]">{file.name}</span>
                  <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => setFile(null)}>
                    <X className="h-3.5 w-3.5" />
                  </Button>
                </div>
              ) : (
                <>
                  <input ref={inputRef} type="file" accept=".xlsx,.xls" className="hidden"
                         onChange={e => { scegliFile(e.target.files?.[0]); e.target.value = ''; }} />
                  <Button variant="outline" size="sm" onClick={() => inputRef.current?.click()}>Seleziona File</Button>
                </>
              )}
            </div>

            {error && (
              <div className="flex items-center gap-2 text-sm text-destructive">
                <AlertCircle className="h-4 w-4 shrink-0" /> {error}
              </div>
            )}

            <div className="flex flex-col-reverse sm:flex-row sm:items-center sm:justify-between gap-3">
              <a href={TEMPLATE_URL} download className="inline-flex items-center gap-2 text-sm text-primary hover:underline">
                <Download className="h-4 w-4" /> Scarica il template Excel
              </a>
              <Button disabled={!file || loading} onClick={handleAnalizza} className="gap-2">
                {loading && <Loader2 className="h-4 w-4 animate-spin" />}
                {loading ? 'Analisi…' : 'Analizza file'}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {step === 2 && preview && (
        <div className="space-y-4">
          <div className="grid grid-cols-3 gap-4">
            <Card>
              <CardContent className="pt-4">
                <p className="text-2xl font-bold text-green-600">{preview.righeOk}</p>
                <p className="text-sm text-muted-foreground">Righe da importare</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-4">
                <p className="text-2xl font-bold text-amber-500">{preview.righeDuplicato}</p>
                <p className="text-sm text-muted-foreground">Duplicati (saltati)</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-4">
                <p className="text-2xl font-bold text-destructive">{preview.righeErrore}</p>
                <p className="text-sm text-muted-foreground">Errori (saltati)</p>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardContent className="p-4 space-y-3">
              <label className={cn('flex items-center gap-2 text-sm', righeSelezionabili.length === 0 ? 'opacity-50' : 'cursor-pointer')}>
                <Checkbox
                  checked={tutteSelezionate}
                  disabled={righeSelezionabili.length === 0}
                  onCheckedChange={toggleTutte}
                />
                {tutteSelezionate ? 'Deseleziona tutto' : 'Seleziona tutto'}
                <span className="text-xs text-muted-foreground">({righeSelezionabili.length} righe OK)</span>
              </label>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-muted-foreground border-b">
                      <th className="w-8 py-2"></th>
                      <th className="py-2 pr-3 font-medium">Proprietario</th>
                      <th className="py-2 pr-3 font-medium">Immobile</th>
                      <th className="py-2 pr-3 font-medium">Città</th>
                      <th className="py-2 font-medium">Stato</th>
                    </tr>
                  </thead>
                  <tbody>
                    {preview.righe.map(r => (
                      <tr key={r.numeroRiga} className={cn('border-b align-top', !r.selezionabile && 'opacity-50')}>
                        <td className="py-2">
                          <Checkbox
                            checked={selezione.has(r.numeroRiga)}
                            disabled={!r.selezionabile}
                            onCheckedChange={v => {
                              const s = new Set(selezione);
                              if (v === true) s.add(r.numeroRiga); else s.delete(r.numeroRiga);
                              setSelezione(s);
                            }}
                          />
                        </td>
                        <td className="py-2 pr-3">
                          {r.cognome} {r.nome}
                          <span className="text-xs text-muted-foreground block">
                            {r.codFisc || '—'} · riga {r.numeroRiga}
                          </span>
                        </td>
                        <td className="py-2 pr-3">{r.nomeImmobile}</td>
                        <td className="py-2 pr-3">{r.citta}</td>
                        <td className="py-2">
                          {r.stato === 'ok' && (
                            <Badge variant="outline" className="text-green-600 border-green-600">✓ OK</Badge>
                          )}
                          {(r.stato === 'duplicato_immobile' || r.stato === 'duplicato_proprietario') && (
                            <Badge variant="outline" className="text-amber-600 border-amber-600">⚠ Duplicato</Badge>
                          )}
                          {r.stato === 'errore' && (
                            <Badge variant="destructive">✗ Errore</Badge>
                          )}
                          {r.messaggioStato && (
                            <p className="text-xs text-muted-foreground mt-1">{r.messaggioStato}</p>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>

          {error && (
            <div className="flex items-center gap-2 text-sm text-destructive">
              <AlertCircle className="h-4 w-4 shrink-0" /> {error}
            </div>
          )}

          <div className="flex justify-between mt-4">
            <Button variant="outline" onClick={() => { setStep(1); setError(null); }}>
              ← Cambia file
            </Button>
            <Button disabled={selezione.size === 0 || isImporting} onClick={handleConferma} className="gap-2">
              {isImporting && <Loader2 className="h-4 w-4 animate-spin" />}
              Importa {selezione.size} righe →
            </Button>
          </div>
        </div>
      )}

      {step === 3 && result && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Card>
              <CardContent className="pt-4">
                <p className="text-2xl font-bold text-green-600">{result.immobiliCreati}</p>
                <p className="text-sm text-muted-foreground">Immobili creati</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-4">
                <p className="text-2xl font-bold">{result.proprietariCreati}</p>
                <p className="text-sm text-muted-foreground">Proprietari creati</p>
              </CardContent>
            </Card>
          </div>

          <p className="text-sm text-muted-foreground">
            {result.righeProcessate} righe lette · {result.proprietariEsistenti} con proprietario già presente
            · {result.immobiliSaltati} immobili già presenti (saltati)
          </p>

          {result.righeInErrore > 0 && (
            <Card className="border-destructive">
              <CardHeader>
                <CardTitle className="text-sm text-destructive">
                  {result.righeInErrore} righe con errori (saltate)
                </CardTitle>
              </CardHeader>
              <CardContent className="overflow-x-auto">
                <table className="text-xs w-full">
                  <thead>
                    <tr className="text-left text-muted-foreground">
                      <th className="py-1 pr-3 font-medium">Riga</th>
                      <th className="py-1 pr-3 font-medium">Dati</th>
                      <th className="py-1 font-medium">Errore</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.errori.map(e => (
                      <tr key={e.numeroRiga} className="border-t align-top">
                        <td className="py-1 pr-3">{e.numeroRiga}</td>
                        <td className="py-1 pr-3">{e.descrizioneRiga}</td>
                        <td className="py-1 text-destructive">{e.messaggio}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </CardContent>
            </Card>
          )}

          {result.avvisi?.length > 0 && (
            <Card className="border-amber-400">
              <CardHeader>
                <CardTitle className="text-sm text-amber-700">
                  {result.avvisi.length} righe importate con avvisi
                </CardTitle>
              </CardHeader>
              <CardContent className="overflow-x-auto">
                <table className="text-xs w-full">
                  <thead>
                    <tr className="text-left text-muted-foreground">
                      <th className="py-1 pr-3 font-medium">Riga</th>
                      <th className="py-1 pr-3 font-medium">Dati</th>
                      <th className="py-1 font-medium">Avviso</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.avvisi.map(a => (
                      <tr key={a.numeroRiga} className="border-t align-top">
                        <td className="py-1 pr-3">{a.numeroRiga}</td>
                        <td className="py-1 pr-3">{a.descrizioneRiga}</td>
                        <td className="py-1 text-amber-700">{a.messaggio}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </CardContent>
            </Card>
          )}

          <div className="flex gap-2">
            <Button variant="outline" onClick={reset}>Importa un altro file</Button>
            <Button onClick={() => navigate('/owners')}>Vai ai proprietari</Button>
          </div>
        </div>
      )}
    </div>
  );
};

export default OwnerBulkImport;
