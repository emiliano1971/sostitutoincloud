import { useState, useEffect } from 'react';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Search, Filter, Eye, Loader2, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { getDocuments, type DocumentListItem } from '@/api/documentApi';

const statusColors: Record<string, string> = {
  draft: 'bg-muted text-muted-foreground',
  ready: 'bg-primary/10 text-primary',
  sent_sdi: 'bg-warning/10 text-warning',
  accepted: 'bg-success/10 text-success',
  rejected: 'bg-destructive/10 text-destructive',
  error: 'bg-destructive/10 text-destructive',
};

const DocumentsList = () => {
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [docs, setDocs] = useState<DocumentListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setIsLoading(true);
    setError(null);
    getDocuments(statusFilter !== 'all' ? { stato: statusFilter } : {})
      .then(setDocs)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, [statusFilter]);

  const filtered = docs.filter(d =>
    search === '' ||
    d.documentNumber.toLowerCase().includes(search.toLowerCase()) ||
    d.recipientName.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Documenti Fiscali</h1>
        <p className="text-sm text-muted-foreground">
          {isLoading ? 'Caricamento…' : `${filtered.length} documenti`}
        </p>
      </div>

      <Card>
        <CardContent className="p-4">
          <div className="flex flex-wrap gap-3">
            <div className="relative flex-1 min-w-[200px]">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input placeholder="Cerca numero, destinatario..." value={search} onChange={e => setSearch(e.target.value)} className="pl-9" />
            </div>
            <Select value={statusFilter} onValueChange={setStatusFilter}>
              <SelectTrigger className="w-[160px]"><Filter className="h-3.5 w-3.5 mr-2" /><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Tutti gli stati</SelectItem>
                <SelectItem value="draft">Bozza</SelectItem>
                <SelectItem value="ready">Pronto</SelectItem>
                <SelectItem value="sent_sdi">Inviato SDI</SelectItem>
                <SelectItem value="accepted">Accettato</SelectItem>
                <SelectItem value="rejected">Rifiutato</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="flex items-center justify-center py-16 text-muted-foreground gap-2">
              <Loader2 className="h-5 w-5 animate-spin" />
              <span>Caricamento documenti…</span>
            </div>
          ) : error ? (
            <div className="flex items-center justify-center py-16 text-destructive gap-2">
              <AlertCircle className="h-5 w-5" />
              <span>{error}</span>
            </div>
          ) : filtered.length === 0 ? (
            <div className="flex items-center justify-center py-16 text-muted-foreground">
              Nessun documento
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Numero</TableHead>
                  <TableHead>Tipo</TableHead>
                  <TableHead>Destinatario</TableHead>
                  <TableHead>Immobile</TableHead>
                  <TableHead>Data</TableHead>
                  <TableHead className="text-right">Totale €</TableHead>
                  <TableHead>Stato SDI</TableHead>
                  <TableHead className="w-10"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filtered.slice(0, 20).map(d => (
                  <TableRow key={d.id}>
                    <TableCell className="font-mono text-xs">{d.documentNumber}</TableCell>
                    <TableCell><Badge variant="outline" className="text-xs">{d.documentType}</Badge></TableCell>
                    <TableCell className="text-sm font-medium">{d.recipientName}</TableCell>
                    <TableCell className="text-sm">{d.propertyName}</TableCell>
                    <TableCell className="text-sm">{d.issueDate}</TableCell>
                    <TableCell className="text-right font-medium">€{d.totalAmount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</TableCell>
                    <TableCell><Badge variant="outline" className={statusColors[d.statoDocumento]}>{d.statoDocumento}</Badge></TableCell>
                    <TableCell><Button variant="ghost" size="icon" className="h-7 w-7"><Eye className="h-3.5 w-3.5" /></Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default DocumentsList;
