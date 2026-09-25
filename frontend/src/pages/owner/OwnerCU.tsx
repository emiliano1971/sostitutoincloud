import { useEffect, useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Download, Loader2, AlertCircle } from 'lucide-react';
import { toast } from '@/hooks/use-toast';
import { getOwnerCu } from '@/api/ownerApi';
import { downloadOwnerCuPdf, type CuListItem } from '@/api/cuApi';
import { labelStatoCu } from '@/lib/statiLabels';

const statusColors: Record<string, string> = {
  draft: 'bg-muted text-muted-foreground',
  generated: 'bg-primary/10 text-primary',
  delivered: 'bg-warning/10 text-warning',
  sent: 'bg-success/10 text-success',
};

const fmtEuro = (v?: number) =>
  `€${(v ?? 0).toLocaleString('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const fmtDate = (s?: string) => (s ? new Date(s).toLocaleDateString('it-IT') : '—');

const OwnerCU = () => {
  const [cus, setCus] = useState<CuListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [downloadingId, setDownloadingId] = useState<number | null>(null);

  useEffect(() => {
    setLoading(true);
    // /api/owner/cu ricava il proprietario dal token: nessun ownerId dal client.
    getOwnerCu()
      .then(setCus)
      .catch(err => setError(err instanceof Error ? err.message : 'Errore nel caricamento delle CU'))
      .finally(() => setLoading(false));
  }, []);

  const handleDownload = async (cu: CuListItem) => {
    setDownloadingId(cu.id);
    try {
      await downloadOwnerCuPdf(cu.id, cu.taxYear, cu.ownerName);
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
        <span>Caricamento CU…</span>
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
        <h1 className="text-2xl font-bold">Certificazioni Uniche</h1>
        <p className="text-sm text-muted-foreground">CU disponibili per download</p>
      </div>

      <Card>
        <CardContent className="p-0">
          {cus.length === 0 ? (
            <div className="py-12 text-center text-muted-foreground">Nessuna CU disponibile</div>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Anno</TableHead>
                    <TableHead className="text-right">Compensi</TableHead>
                    <TableHead className="text-right">Ritenute</TableHead>
                    <TableHead>Stato</TableHead>
                    <TableHead>Generata il</TableHead>
                    <TableHead className="w-12"></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {cus.map(cu => (
                    <TableRow key={cu.id}>
                      <TableCell className="font-medium">{cu.taxYear}</TableCell>
                      <TableCell className="text-right">{fmtEuro(cu.totalCompensi)}</TableCell>
                      <TableCell className="text-right text-destructive">{fmtEuro(cu.totalRitenute)}</TableCell>
                      <TableCell>
                        <Badge variant="outline" className={`text-xs ${statusColors[cu.stato] ?? ''}`}>
                          {labelStatoCu(cu.stato)}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-sm whitespace-nowrap">{fmtDate(cu.generatedAt)}</TableCell>
                      <TableCell>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-7 w-7"
                          title="Scarica PDF"
                          onClick={() => handleDownload(cu)}
                          disabled={downloadingId === cu.id}
                        >
                          {downloadingId === cu.id
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

export default OwnerCU;
