import { useEffect, useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Download, Loader2, AlertCircle } from 'lucide-react';
import { toast } from '@/hooks/use-toast';
import { getOwnerDocuments } from '@/api/ownerApi';
import { downloadOwnerDocumentPdf, type DocumentListItem } from '@/api/documentApi';

const statusColors: Record<string, string> = {
  draft: 'bg-muted text-muted-foreground',
  ready: 'bg-primary/10 text-primary',
  sent_sdi: 'bg-warning/10 text-warning',
  accepted: 'bg-success/10 text-success',
  rejected: 'bg-destructive/10 text-destructive',
};

// La lookup stato_documento ha descrizioni orientate allo SDI ("Pronto per invio SDI"),
// che non riguardano la ricevuta: qui le etichette sono locali, come in BookingDetail.
const statusLabels: Record<string, string> = {
  draft: 'Bozza',
  ready: 'Emesso',
};

const fmtEuro = (v?: number) =>
  `€${(v ?? 0).toLocaleString('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const fmtDate = (s?: string) => (s ? new Date(s).toLocaleDateString('it-IT') : '—');

const OwnerDocuments = () => {
  const [docs, setDocs] = useState<DocumentListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [downloadingId, setDownloadingId] = useState<number | null>(null);

  useEffect(() => {
    setLoading(true);
    // /api/owner/documents restituisce già solo le ricevute del proprietario
    // autenticato: nessun filtro per tipo o per nome lato client.
    getOwnerDocuments()
      .then(setDocs)
      .catch(err => setError(err instanceof Error ? err.message : 'Errore nel caricamento dei documenti'))
      .finally(() => setLoading(false));
  }, []);

  const handleDownload = async (doc: DocumentListItem) => {
    setDownloadingId(doc.id);
    try {
      await downloadOwnerDocumentPdf(doc.id, doc.documentNumber);
    } catch (err) {
      toast({
        title: 'Errore download PDF',
        description: err instanceof Error ? err.message : 'Errore imprevisto',
        variant: 'destructive',
      });
    } finally {
      setDownloadingId(null);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16 text-muted-foreground gap-2">
        <Loader2 className="h-5 w-5 animate-spin" />
        <span>Caricamento documenti…</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center py-16 text-destructive gap-2">
        <AlertCircle className="h-5 w-5" />
        <span>{error}</span>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-bold">Documenti</h1>
        <p className="text-sm text-muted-foreground">{docs.length} ricevute</p>
      </div>

      <Card>
        <CardContent className="p-0">
          {docs.length === 0 ? (
            <div className="py-12 text-center text-muted-foreground">Nessun documento</div>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Numero</TableHead>
                    <TableHead>Data emissione</TableHead>
                    <TableHead>Immobile</TableHead>
                    <TableHead className="text-right">Importo</TableHead>
                    <TableHead>Stato</TableHead>
                    <TableHead className="w-12"></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {docs.map(d => (
                    <TableRow key={d.id}>
                      <TableCell className="font-mono text-xs">{d.documentNumber}</TableCell>
                      <TableCell className="text-sm whitespace-nowrap">{fmtDate(d.issueDate)}</TableCell>
                      <TableCell className="text-sm">{d.propertyName ?? '—'}</TableCell>
                      <TableCell className="text-right font-medium">{fmtEuro(d.totalAmount)}</TableCell>
                      <TableCell>
                        <Badge variant="outline" className={`text-xs ${statusColors[d.statoDocumento] ?? ''}`}>
                          {statusLabels[d.statoDocumento] ?? d.statoDocumento}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-7 w-7"
                          title="Scarica PDF"
                          onClick={() => handleDownload(d)}
                          disabled={downloadingId === d.id}
                        >
                          {downloadingId === d.id
                            ? <Loader2 className="h-3.5 w-3.5 animate-spin" />
                            : <Download className="h-3.5 w-3.5" />}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default OwnerDocuments;
