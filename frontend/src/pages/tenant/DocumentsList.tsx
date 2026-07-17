import { useState, useEffect, useMemo } from 'react';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Search, Filter, Eye, Loader2, AlertCircle, ChevronsUpDown, ChevronUp, ChevronDown, Info, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { getDocuments, type DocumentListItem } from '@/api/documentApi';
import { useNavigate, useSearchParams } from 'react-router-dom';

const statusColors: Record<string, string> = {
  draft: 'bg-muted text-muted-foreground',
  ready: 'bg-primary/10 text-primary',
  sent_sdi: 'bg-warning/10 text-warning',
  accepted: 'bg-success/10 text-success',
  rejected: 'bg-destructive/10 text-destructive',
  error: 'bg-destructive/10 text-destructive',
};

type SortDir = 'asc' | 'desc';

interface SortableThProps {
  label: string;
  colKey: string;
  sortKey: string;
  sortDir: SortDir;
  onSort: (key: string) => void;
  align?: 'left' | 'right';
}

const SortableTh = ({ label, colKey, sortKey, sortDir, onSort, align = 'left' }: SortableThProps) => {
  const active = sortKey === colKey;
  const Icon = active ? (sortDir === 'asc' ? ChevronUp : ChevronDown) : ChevronsUpDown;
  return (
    <TableHead
      className={`cursor-pointer select-none ${align === 'right' ? 'text-right' : ''}`}
      onClick={() => onSort(colKey)}
    >
      <span className={`inline-flex items-center gap-1 ${align === 'right' ? 'flex-row-reverse' : ''}`}>
        {label}
        <Icon className={`h-3.5 w-3.5 ${active ? 'text-primary' : 'text-muted-foreground/40'}`} />
      </span>
    </TableHead>
  );
};

const DocumentsList = () => {
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [docs, setDocs] = useState<DocumentListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [sortKey, setSortKey] = useState<string>('issueDate');
  const [sortDir, setSortDir] = useState<SortDir>('desc');
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const ownerIdParam = searchParams.get('ownerId');
  const ownerIdFilter = ownerIdParam ? parseInt(ownerIdParam) : null;

  const handleSort = (key: string) => {
    if (key === sortKey) {
      setSortDir(prev => (prev === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir(key === 'issueDate' ? 'desc' : 'asc');
    }
  };

  useEffect(() => {
    setIsLoading(true);
    setError(null);
    getDocuments(statusFilter !== 'all' ? { stato: statusFilter } : {})
      .then(setDocs)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, [statusFilter]);

  const filtered = docs.filter(d =>
    (search === '' ||
      d.documentNumber.toLowerCase().includes(search.toLowerCase()) ||
      d.recipientName.toLowerCase().includes(search.toLowerCase()) ||
      (d.ownerName ?? '').toLowerCase().includes(search.toLowerCase()))
    && (ownerIdFilter == null || d.fkOwnerId === ownerIdFilter)
  );

  const ownerFilterName = ownerIdFilter != null
    ? (docs.find(d => d.fkOwnerId === ownerIdFilter)?.ownerName ?? `owner #${ownerIdFilter}`)
    : null;

  const sorted = useMemo(() => {
    return [...filtered].sort((a, b) => {
      const valA = (a as Record<string, unknown>)[sortKey];
      const valB = (b as Record<string, unknown>)[sortKey];
      const dir = sortDir === 'asc' ? 1 : -1;
      if (valA == null) return 1;
      if (valB == null) return -1;
      if (typeof valA === 'number' && typeof valB === 'number') return (valA - valB) * dir;
      return String(valA).localeCompare(String(valB), 'it') * dir;
    });
  }, [filtered, sortKey, sortDir]);

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

      {ownerIdFilter != null && (
        <div className="flex items-start gap-2 rounded-md border border-primary/20 bg-primary/5 p-3 text-sm">
          <Info className="h-4 w-4 mt-0.5 text-primary shrink-0" />
          <span className="flex-1 text-muted-foreground">
            Documenti filtrati per proprietario — <strong>{ownerFilterName}</strong>
          </span>
          <button
            type="button"
            className="text-muted-foreground hover:text-foreground"
            title="Rimuovi filtro proprietario"
            onClick={() => navigate('/documents')}
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

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
                  <SortableTh label="Numero" colKey="documentNumber" sortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                  <SortableTh label="Tipo" colKey="documentType" sortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                  <SortableTh label="Destinatario" colKey="recipientName" sortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                  <SortableTh label="Proprietario" colKey="ownerName" sortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                  <SortableTh label="Immobile" colKey="propertyName" sortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                  <SortableTh label="Data" colKey="issueDate" sortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                  <SortableTh label="Totale €" colKey="totalAmount" sortKey={sortKey} sortDir={sortDir} onSort={handleSort} align="right" />
                  <SortableTh label="Stato SDI" colKey="statoDocumento" sortKey={sortKey} sortDir={sortDir} onSort={handleSort} />
                  <TableHead className="w-10"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {sorted.slice(0, 20).map(d => (
                  <TableRow key={d.id}>
                    <TableCell className="font-mono text-xs">{d.documentNumber}</TableCell>
                    <TableCell><Badge variant="outline" className="text-xs">{d.documentType}</Badge></TableCell>
                    <TableCell className="text-sm font-medium">{d.recipientName}</TableCell>
                    <TableCell className="text-sm">
                      {d.ownerName && d.fkOwnerId ? (
                        <button
                          type="button"
                          className="text-primary hover:underline"
                          title="Vedi gli F24 di questo proprietario"
                          onClick={() => navigate(`/f24?ownerId=${d.fkOwnerId}`)}
                        >
                          {d.ownerName}
                        </button>
                      ) : (
                        '—'
                      )}
                    </TableCell>
                    <TableCell className="text-sm">{d.propertyName}</TableCell>
                    <TableCell className="text-sm">{d.issueDate}</TableCell>
                    <TableCell className="text-right font-medium">€{d.totalAmount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</TableCell>
                    <TableCell><Badge variant="outline" className={statusColors[d.statoDocumento]}>{d.statoDocumento}</Badge></TableCell>
                    <TableCell><Button variant="ghost" size="icon" className="h-7 w-7" onClick={eventvo => navigate(`/documents/${d.id}`)}><Eye className="h-3.5 w-3.5" /></Button></TableCell>
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
