import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Search, Filter, Eye } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { mockDocuments } from '@/data/mock-data';

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

  const docs = mockDocuments
    .filter(d => d.tenant_id === 't1')
    .filter(d => statusFilter === 'all' || d.status === statusFilter)
    .filter(d => search === '' || d.document_number.toLowerCase().includes(search.toLowerCase()) || d.recipient_name.toLowerCase().includes(search.toLowerCase()));

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Documenti Fiscali</h1>
        <p className="text-sm text-muted-foreground">{docs.length} documenti</p>
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
              {docs.slice(0, 20).map(d => (
                <TableRow key={d.document_id}>
                  <TableCell className="font-mono text-xs">{d.document_number}</TableCell>
                  <TableCell><Badge variant="outline" className="text-xs">{d.document_type}</Badge></TableCell>
                  <TableCell className="text-sm font-medium">{d.recipient_name}</TableCell>
                  <TableCell className="text-sm">{d.property_name}</TableCell>
                  <TableCell className="text-sm">{d.issue_date}</TableCell>
                  <TableCell className="text-right font-medium">€{d.total_amount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</TableCell>
                  <TableCell><Badge variant="outline" className={statusColors[d.status]}>{d.status}</Badge></TableCell>
                  <TableCell><Button variant="ghost" size="icon" className="h-7 w-7"><Eye className="h-3.5 w-3.5" /></Button></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
};

export default DocumentsList;
