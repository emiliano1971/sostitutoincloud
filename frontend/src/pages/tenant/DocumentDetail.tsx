import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ArrowLeft, Building2, User, Home, Calendar, Receipt, Printer, Loader2, AlertCircle, ExternalLink } from 'lucide-react';
import { getDocumentById, type DocumentDetail as DocumentDetailType } from '@/api/documentApi';

const statusColors: Record<string, string> = {
  draft: 'bg-muted text-muted-foreground',
  ready: 'bg-primary/10 text-primary',
  sent_sdi: 'bg-warning/10 text-warning',
  accepted: 'bg-success/10 text-success',
  rejected: 'bg-destructive/10 text-destructive',
};

const statoDocLabels: Record<string, string> = {
  draft: 'Bozza',
  ready: 'Pronto',
  sent_sdi: 'Inviato SDI',
  accepted: 'Accettato',
  rejected: 'Rifiutato',
};

const fmt = (v?: number) => `€${Math.abs(v ?? 0).toLocaleString('it-IT', { minimumFractionDigits: 2 })}`;

const DocumentDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [doc, setDoc] = useState<DocumentDetailType | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    setIsLoading(true);
    setError(null);
    getDocumentById(Number(id))
      .then(setDoc)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, [id]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20 gap-2 text-muted-foreground">
        <Loader2 className="h-5 w-5 animate-spin" />
        <span>Caricamento documento…</span>
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

  if (!doc) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <p className="text-muted-foreground">Documento non trovato</p>
        <Button variant="link" onClick={() => navigate('/documents')}>Torna ai Documenti</Button>
      </div>
    );
  }

  const imponibile = doc.imponibile ?? ((doc.totalAmount ?? 0) - (doc.vatAmount ?? 0));
  const showIva = (doc.vatAmount ?? 0) > 0;
  const showBollo = (doc.bolloAmount ?? 0) > 0;

  return (
    <div className="print-document space-y-6 max-w-4xl print:max-w-none">
      {/* Stili stampa: nascondono il chrome dell'app (sidebar/nav/header) che vive
          fuori da questo componente, dove le classi Tailwind print: non arrivano. */}
      <style>{`
        @media print {
          /* 1. Nascondi sidebar, nav e header dell'app */
          aside, nav, [data-sidebar], .sidebar, header { display: none !important; }

          /* 2. Sfondo bianco, niente margini/padding esterni, font leggibile */
          html, body {
            background: #fff !important;
            margin: 0 !important;
            padding: 0 !important;
            color: #000 !important;
            font-size: 12pt !important;
          }
          main { margin: 0 !important; padding: 0 !important; }
          @page { margin: 1.2cm; }

          /* La pagina documento occupa tutta la larghezza */
          .print-document {
            max-width: none !important;
            margin: 0 !important;
            padding: 0 !important;
          }

          /* 3. Card: bordo sottile invece dello shadow, sfondo bianco */
          .print-document [class*="shadow"] {
            box-shadow: none !important;
            border: 1px solid #ccc !important;
            background: #fff !important;
          }
        }
      `}</style>

      {/* Back */}
      <div className="flex items-center gap-3 print:hidden">
        <Button variant="ghost" size="icon" onClick={() => navigate('/documents')}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div>
          <h1 className="text-xl font-bold">Documento Fiscale</h1>
          <p className="text-sm text-muted-foreground">{doc.recipientName}</p>
        </div>
      </div>

      {/* Card 1 — Header documento */}
      <Card>
        <CardContent className="p-6 flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-2">
            <p className="font-mono text-2xl font-bold tracking-tight">{doc.documentNumber}</p>
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="outline" className="text-xs uppercase">{doc.documentType}</Badge>
              <Badge variant="outline" className={`text-xs ${statusColors[doc.statoDocumento] ?? ''}`}>
                {statoDocLabels[doc.statoDocumento] ?? doc.statoDocumento}
              </Badge>
            </div>
          </div>
          <div className="text-right">
            <p className="text-xs text-muted-foreground">Data emissione</p>
            <p className="font-medium">{doc.issueDate}</p>
          </div>
        </CardContent>
      </Card>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Card 2 — Emittente (tenant) */}
        <Card>
          <CardHeader><CardTitle className="text-sm flex items-center gap-2"><Building2 className="h-4 w-4" /> Emittente</CardTitle></CardHeader>
          <CardContent className="space-y-1 text-sm">
            <p className="font-medium">{doc.tenantLegalName ?? 'N/D'}</p>
            {doc.tenantVatNumber && <p className="text-xs text-muted-foreground">P.IVA: {doc.tenantVatNumber}</p>}
            {doc.tenantTaxCode && <p className="text-xs text-muted-foreground">C.F.: {doc.tenantTaxCode}</p>}
            {doc.tenantLegalAddress && <p className="text-xs text-muted-foreground">{doc.tenantLegalAddress}</p>}
            {doc.tenantPec && <p className="text-xs text-muted-foreground">PEC: {doc.tenantPec}</p>}
          </CardContent>
        </Card>

        {/* Card 3 — Destinatario */}
        <Card>
          <CardHeader><CardTitle className="text-sm flex items-center gap-2"><User className="h-4 w-4" /> Destinatario</CardTitle></CardHeader>
          <CardContent className="space-y-1 text-sm">
            <p className="font-medium">{doc.recipientName ?? 'N/D'}</p>
            {doc.recipientTaxCode && <p className="text-xs text-muted-foreground">C.F.: {doc.recipientTaxCode}</p>}
          </CardContent>
        </Card>
      </div>

      {/* Card 4 — Riferimento prenotazione */}
      <Card>
        <CardHeader><CardTitle className="text-sm flex items-center gap-2"><Home className="h-4 w-4" /> Riferimento Prenotazione</CardTitle></CardHeader>
        <CardContent className="space-y-3 text-sm">
          <div className="flex justify-between"><span className="text-muted-foreground">Prenotazione</span><span className="font-medium">{doc.externalBookingId ?? 'N/D'}</span></div>
          <div className="flex justify-between"><span className="text-muted-foreground">Immobile</span><span className="font-medium">{doc.propertyName ?? 'N/D'}</span></div>
          <div className="flex justify-between"><span className="text-muted-foreground">Canale</span><span className="font-medium">{doc.channelName ?? 'N/D'}</span></div>
          <Separator />
          <div className="flex justify-between"><span className="text-muted-foreground flex items-center gap-1"><Calendar className="h-3.5 w-3.5" /> Check-in</span><span className="font-medium">{doc.checkinDate ?? 'N/D'}</span></div>
          <div className="flex justify-between"><span className="text-muted-foreground flex items-center gap-1"><Calendar className="h-3.5 w-3.5" /> Check-out</span><span className="font-medium">{doc.checkoutDate ?? 'N/D'}</span></div>
          {doc.fkBookingId && (
            <Button variant="outline" size="sm" className="gap-2 print:hidden" onClick={() => navigate(`/bookings/${doc.fkBookingId}`)}>
              <ExternalLink className="h-3.5 w-3.5" />
              Vai alla prenotazione
            </Button>
          )}
        </CardContent>
      </Card>

      {/* Card 5 — Importi fiscali */}
      <Card>
        <CardHeader><CardTitle className="text-sm flex items-center gap-2"><Receipt className="h-4 w-4" /> Importi Fiscali</CardTitle></CardHeader>
        <CardContent>
          <div className="space-y-2">
            <div className="flex justify-between py-1.5">
              <span className="text-sm text-muted-foreground">Imponibile</span>
              <span className="text-sm">{fmt(imponibile)}</span>
            </div>
            {showIva && (
              <div className="flex justify-between py-1.5">
                <span className="text-sm text-muted-foreground">IVA</span>
                <span className="text-sm">{fmt(doc.vatAmount)}</span>
              </div>
            )}
            <div className="flex justify-between py-1.5">
              <span className="text-sm text-muted-foreground">Ritenuta 21%</span>
              <span className="text-sm">{fmt(doc.ritenutaAmount)}</span>
            </div>
            {showBollo && (
              <div className="flex justify-between py-1.5">
                <span className="text-sm text-muted-foreground">Marca da bollo</span>
                <span className="text-sm">{fmt(doc.bolloAmount)}</span>
              </div>
            )}
            <div className="flex justify-between border-t pt-2 font-semibold">
              <span className="text-sm">Totale documento</span>
              <span className="text-sm">{fmt(doc.totalAmount)}</span>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Card 6 — Righe documento */}
      {doc.righe && doc.righe.length > 0 && (
        <Card>
          <CardHeader><CardTitle className="text-sm flex items-center gap-2"><Receipt className="h-4 w-4" /> Righe Documento</CardTitle></CardHeader>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Descrizione</TableHead>
                  <TableHead className="text-right">Imponibile</TableHead>
                  <TableHead className="text-right">IVA</TableHead>
                  <TableHead className="text-right">Totale</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {doc.righe.map((r, i) => (
                  <TableRow key={i}>
                    <TableCell className="text-sm">{r.descrizione}</TableCell>
                    <TableCell className="text-right text-sm">{fmt(r.importoNetto)}</TableCell>
                    <TableCell className="text-right text-sm">{fmt(r.importoIva)}</TableCell>
                    <TableCell className="text-right text-sm font-medium">{fmt(r.importoLordo)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {/* Azioni */}
      <div className="flex flex-wrap gap-3 justify-end print:hidden">
        <Button variant="outline" className="gap-2" onClick={() => navigate('/documents')}>
          <ArrowLeft className="h-4 w-4" />
          Torna ai Documenti
        </Button>
        <Button className="gap-2" onClick={() => window.print()}>
          <Printer className="h-4 w-4" />
          Stampa
        </Button>
      </div>
    </div>
  );
};

export default DocumentDetail;
