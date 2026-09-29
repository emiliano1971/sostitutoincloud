import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { ArrowLeft, Download, FileSpreadsheet, Loader2, Upload, X, AlertCircle } from 'lucide-react';
import { importOwnersBulk, type OwnerBulkImportResult } from '@/api/ownerApi';

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

  const handleImport = async () => {
    if (!file) return;
    setLoading(true);
    setError(null);
    try {
      setResult(await importOwnersBulk(file));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Errore durante l\'importazione');
    } finally {
      setLoading(false);
    }
  };

  const reset = () => {
    setFile(null);
    setResult(null);
    setError(null);
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

      {!result && (
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
              <Button disabled={!file || loading} onClick={handleImport} className="gap-2">
                {loading && <Loader2 className="h-4 w-4 animate-spin" />}
                {loading ? 'Importazione…' : 'Importa'}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {result && (
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
