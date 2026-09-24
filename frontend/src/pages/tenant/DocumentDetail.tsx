import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ArrowLeft, Building2, User, Home, Calendar, Receipt, Download, Loader2, AlertCircle, ExternalLink, Send, CheckCircle2, XCircle, AlertTriangle, Landmark, FileCheck } from 'lucide-react';
import { getDocumentById, downloadDocumentPdf, inviaSdi, downloadSdiXml, type DocumentDetail as DocumentDetailType } from '@/api/documentApi';
import { useToast } from '@/hooks/use-toast';

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

const f24BadgeLabels: Record<string, string> = {
  draft: 'Bozza', ready: 'In attesa', sent: 'Inviato', paid: 'Pagato ✓', error: 'Errore',
};
const f24BadgeColors: Record<string, string> = {
  draft: 'bg-muted text-muted-foreground',
  ready: 'bg-muted text-muted-foreground',
  sent: 'bg-primary/10 text-primary',
  paid: 'bg-success/10 text-success border-success/20',
  error: 'bg-destructive/10 text-destructive',
};

const cuBadgeLabels: Record<string, string> = {
  draft: 'Bozza', generated: 'Generata', delivered: 'Consegnata', sent: 'Inviata AdE ✓',
};
const cuBadgeColors: Record<string, string> = {
  draft: 'bg-muted text-muted-foreground',
  generated: 'bg-primary/10 text-primary',
  delivered: 'bg-warning/10 text-warning border-warning/20',
  sent: 'bg-success/10 text-success border-success/20',
};

// Stato liquidazione — stesse etichette e colori di BookingDetail e DocumentsList
const settlementLabels: Record<string, string> = {
  pending: 'In attesa',
  calculated: 'Calcolata',
  approved: 'Approvata',
  paid: 'Pagata',
};

const settlementBadgeColors: Record<string, string> = {
  pending: 'bg-muted text-muted-foreground',
  calculated: 'bg-blue-100 text-blue-700 dark:bg-blue-950/30 dark:text-blue-300',
  approved: 'bg-warning/10 text-warning',
  paid: 'bg-success/10 text-success',
};

const DocumentDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { toast } = useToast();
  const [doc, setDoc] = useState<DocumentDetailType | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isDownloading, setIsDownloading] = useState(false);
  const [isSendingSdi, setIsSendingSdi] = useState(false);

  useEffect(() => {
    if (!id) return;
    setIsLoading(true);
    setError(null);
    getDocumentById(Number(id))
      .then(setDoc)
      .catch(err => setError(err.message))
      .finally(() => setIsLoading(false));
  }, [id]);

  // PDF generato server-side (GET /api/documents/{id}/pdf), non più window.print()
  const handleDownloadPdf = async () => {
    if (!doc) return;
    setIsDownloading(true);
    try {
      await downloadDocumentPdf(doc.id, doc.documentNumber);
    } catch (err) {
      toast({
        title: 'Errore download PDF',
        description: err instanceof Error ? err.message : 'Errore imprevisto',
        variant: 'destructive',
      });
    } finally {
      setIsDownloading(false);
    }
  };

  const handleDownloadSdiXml = async () => {
    if (!doc?.sdiProgressivo) return;
    try {
      await downloadSdiXml(doc.sdiProgressivo);
    } catch (err) {
      toast({
        title: 'Errore download XML SDI',
        description: err instanceof Error ? err.message : 'Errore imprevisto',
        variant: 'destructive',
      });
    }
  };

  // Invio SDI: solo fatture PM. Il backend rifiuta con 422 se già inviata.
  const handleInviaSdi = async () => {
    if (!doc) return;
    setIsSendingSdi(true);
    try {
      const res = await inviaSdi(doc.id);
      toast({ title: 'File SDI generato con progressivo:', description: res.progressivo });
      const aggiornato = await getDocumentById(doc.id);
      setDoc(aggiornato);
    } catch (err) {
      toast({
        title: 'Errore invio SDI',
        description: err instanceof Error ? err.message : 'Errore imprevisto',
        variant: 'destructive',
      });
    } finally {
      setIsSendingSdi(false);
    }
  };

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

  // Importi letti dal DTO, mai ricalcolati: imponibile, IVA e totale sono persistiti su
  // fiscal_document al momento dell'emissione ed è quello il valore fiscalmente valido.
  const imponibile = doc.imponibile ?? 0;
  const showIva = (doc.vatAmount ?? 0) > 0;
  const showBollo = (doc.bolloAmount ?? 0) > 0;

  // Ricevuta owner (lookup tipo_documento.codice = 'ricevuta'): gli importi seguono
  // le stesse voci del PDF — canone lordo, ritenuta, bollo, netto a pagare.
  const isRicevutaOwner = doc.documentType === 'ricevuta';
  // Solo le fatture PM vanno allo SDI. Stati che implicano un file già trasmesso.
  const isFatturaPm = doc.documentType === 'fattura';
  const sdiRifiutato = doc.statoDocumento === 'rejected';
  const sdiErrore = doc.statoDocumento === 'error';
  // La card mostra i dati di trasmissione appena esiste un progressivo, qualunque
  // sia l'esito. 'error' può precedere l'invio (generazione fallita): in quel caso
  // non c'è progressivo e resta il messaggio "non ancora trasmessa".
  const sdiTrasmesso = Boolean(doc.sdiProgressivo);
  // Periodo F24 nel formato "MM/yyyy": lo scompongo per il link con i filtri della lista.
  const [meseF24, annoF24] = (doc.f24Periodo ?? '').split('/');
  const annoDocumento = doc.issueDate ? doc.issueDate.slice(0, 4) : null;
  // Nuovo invio ammesso finché lo SDI non ha confermato: dopo uno scarto o un
  // errore la fattura va corretta e ritrasmessa.
  const puoInviareSdi = isFatturaPm && !['sent_sdi', 'accepted'].includes(doc.statoDocumento);
  const puoRiprovareSdi = isFatturaPm && (sdiRifiutato || sdiErrore);
  const ritenuta = doc.ritenutaAmount ?? 0;
  // Netto = canone - ritenuta. Il bollo NON è scalato: coerente con il PDF
  // (ricevuta-owner.html) e con SettlementService (net = total - withholding).
  const nettoPagare = imponibile - ritenuta;
  // L'aliquota ritenuta non è esposta dal DTO: la ricavo dagli importi memorizzati
  // (es. 21% primo immobile, 26% dal secondo), così l'etichetta non è hardcodata.
  const aliquotaRitenuta = imponibile > 0
    ? Number((ritenuta / imponibile * 100).toFixed(1)).toLocaleString('it-IT')
    : '';
  const labelRitenuta = aliquotaRitenuta ? `Ritenuta ${aliquotaRitenuta}%` : 'Ritenuta';

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
          <div className="flex justify-between">
            <span className="text-muted-foreground">Prenotazione</span>
            {doc.fkBookingId ? (
              <button
                type="button"
                onClick={() => navigate(`/bookings/${doc.fkBookingId}`)}
                className="text-primary hover:underline font-mono text-sm"
              >
                {doc.externalBookingId ?? doc.fkBookingId}
              </button>
            ) : (
              <span className="font-medium">{doc.externalBookingId ?? 'N/D'}</span>
            )}
          </div>
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
          {isRicevutaOwner ? (
            /* Ricevuta owner: stesse voci del PDF */
            <div className="space-y-2">
              <div className="flex justify-between py-1.5">
                <span className="text-sm text-muted-foreground">Canone lordo</span>
                <span className="text-sm">{fmt(imponibile)}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-sm text-muted-foreground">{labelRitenuta}</span>
                <span className="text-sm text-destructive">-{fmt(ritenuta)}</span>
              </div>
              {showBollo && (
                <>
                  <div className="flex justify-between py-1.5">
                    <span className="text-sm text-muted-foreground">Marca da bollo</span>
                    <span className="text-sm">{fmt(doc.bolloAmount)}</span>
                  </div>
                  <p className="text-xs text-muted-foreground">
                    La marca da bollo è indicata a fini informativi e non è scalata dal netto a pagare
                  </p>
                </>
              )}
              <div className="flex justify-between border-t pt-2">
                <span className="text-sm font-semibold">Netto a pagare</span>
                <span className="text-sm font-bold text-success">{fmt(nettoPagare)}</span>
              </div>
            </div>
          ) : (
            /* Fattura PM (e altri tipi): voci invariate */
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
              {/* Nessuna riga ritenuta: la ritenuta d'acconto riguarda il canone
                  del proprietario, quindi è pertinente solo alla ricevuta owner. */}
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
          )}
        </CardContent>
      </Card>

      {/* Ricevuta owner: dove è finita la ritenuta (F24), la CU dell'anno e la liquidazione */}
      {isRicevutaOwner && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <Card
            className={doc.f24RecordId ? 'cursor-pointer transition-colors hover:bg-accent' : undefined}
            onClick={doc.f24RecordId ? () => navigate(`/f24?anno=${annoF24}&mese=${meseF24}`) : undefined}
          >
            <CardHeader><CardTitle className="text-sm flex items-center gap-2"><Landmark className="h-4 w-4" /> Versamento F24</CardTitle></CardHeader>
            <CardContent className="space-y-2 text-sm">
              {doc.f24RecordId ? (
                <>
                  <Badge variant="outline" className={f24BadgeColors[doc.f24Stato ?? ''] ?? f24BadgeColors.ready}>
                    {f24BadgeLabels[doc.f24Stato ?? ''] ?? doc.f24Stato}
                  </Badge>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Periodo</span>
                    <span className="font-mono text-xs">{doc.f24Periodo}</span>
                  </div>
                </>
              ) : (
                <>
                  <Badge variant="outline" className="bg-muted text-muted-foreground">Non ancora in F24</Badge>
                  <p className="text-xs text-muted-foreground">
                    La ritenuta non è ancora stata inclusa in nessun versamento F24
                  </p>
                </>
              )}
            </CardContent>
          </Card>

          <Card
            className={doc.cuRecordId ? 'cursor-pointer transition-colors hover:bg-accent' : undefined}
            onClick={doc.cuRecordId ? () => navigate('/cu') : undefined}
          >
            <CardHeader><CardTitle className="text-sm flex items-center gap-2"><FileCheck className="h-4 w-4" /> Certificazione Unica</CardTitle></CardHeader>
            <CardContent className="space-y-2 text-sm">
              {doc.cuRecordId ? (
                <>
                  <Badge variant="outline" className={cuBadgeColors[doc.cuStato ?? ''] ?? cuBadgeColors.draft}>
                    {cuBadgeLabels[doc.cuStato ?? ''] ?? doc.cuStato}
                  </Badge>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Anno fiscale</span>
                    <span className="font-mono text-xs">{doc.cuTaxYear}</span>
                  </div>
                </>
              ) : (
                <>
                  <Badge variant="outline" className="bg-muted text-muted-foreground">Non in CU</Badge>
                  <p className="text-xs text-muted-foreground">
                    Nessuna CU generata per l'anno {annoDocumento ?? '—'}
                  </p>
                </>
              )}
            </CardContent>
          </Card>

          <Card
            className={doc.settlementId ? 'cursor-pointer transition-colors hover:bg-accent' : undefined}
            onClick={doc.settlementId ? () => navigate(`/settlements/${doc.settlementId}`) : undefined}
          >
            <CardHeader><CardTitle className="text-sm flex items-center gap-2"><Receipt className="h-4 w-4" /> Liquidazione</CardTitle></CardHeader>
            <CardContent className="space-y-2 text-sm">
              {doc.settlementId ? (
                <>
                  <Badge className={settlementBadgeColors[doc.settlementStato ?? 'pending'] ?? settlementBadgeColors.pending}>
                    {settlementLabels[doc.settlementStato ?? 'pending'] ?? doc.settlementStato}
                  </Badge>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Liquidazione</span>
                    <span className="font-mono text-xs">#{doc.settlementId}</span>
                  </div>
                </>
              ) : (
                <>
                  <Badge variant="outline" className="bg-muted text-muted-foreground">Non ancora liquidata</Badge>
                  <p className="text-xs text-muted-foreground">
                    La prenotazione non è ancora inclusa in nessuna liquidazione
                  </p>
                </>
              )}
            </CardContent>
          </Card>
        </div>
      )}

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
                {/* Totali letti dal documento (imponibile / vat_amount / total_amount),
                    non sommati dalle righe: il documento emesso è la fonte autorevole. */}
                <TableRow className="border-t-2 font-semibold hover:bg-transparent">
                  <TableCell className="text-sm">Totale documento</TableCell>
                  <TableCell className="text-right text-sm">{fmt(doc.imponibile)}</TableCell>
                  <TableCell className="text-right text-sm">{fmt(doc.vatAmount)}</TableCell>
                  <TableCell className="text-right text-sm">{fmt(doc.totalAmount)}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {/* Card — Trasmissione SDI (solo fatture PM) */}
      {isFatturaPm && (
        <Card>
          <CardHeader><CardTitle className="text-sm flex items-center gap-2"><Send className="h-4 w-4" /> Trasmissione SDI</CardTitle></CardHeader>
          <CardContent className="space-y-3 text-sm">
            {sdiTrasmesso ? (
              <>
                <div className="flex flex-wrap items-center gap-2">
                  {doc.statoDocumento === 'accepted' ? (
                    <Badge variant="outline" className="bg-success/10 text-success border-success/20 gap-1">
                      <CheckCircle2 className="h-3.5 w-3.5" /> Accettato AdE
                    </Badge>
                  ) : sdiRifiutato ? (
                    <Badge variant="outline" className="bg-destructive/10 text-destructive border-destructive/20 gap-1">
                      <XCircle className="h-3.5 w-3.5" /> Rifiutato SDI
                    </Badge>
                  ) : sdiErrore ? (
                    <Badge variant="outline" className="bg-warning/10 text-warning border-warning/20 gap-1">
                      <AlertTriangle className="h-3.5 w-3.5" /> Errore SDI
                    </Badge>
                  ) : (
                    <>
                      <Badge variant="outline" className="bg-success/10 text-success border-success/20 gap-1">
                        <CheckCircle2 className="h-3.5 w-3.5" /> Inviato SDI
                      </Badge>
                      <span className="text-xs text-muted-foreground">in attesa di risposta AdE</span>
                    </>
                  )}
                </div>

                {sdiRifiutato && doc.sdiErrorMsg && (
                  <div className="rounded-md border border-destructive/20 bg-destructive/5 px-3 py-2 text-xs text-destructive">
                    <span className="font-medium">Motivo:</span> {doc.sdiErrorMsg}
                  </div>
                )}
                {sdiErrore && doc.sdiErrorMsg && (
                  <div className="rounded-md border border-warning/20 bg-warning/5 px-3 py-2 text-xs text-warning">
                    <span className="font-medium">Dettaglio:</span> {doc.sdiErrorMsg}
                  </div>
                )}

                <div className="flex justify-between">
                  <span className="text-muted-foreground">Progressivo</span>
                  <span className="font-mono text-xs">{doc.sdiProgressivo}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Data invio</span>
                  <span>{doc.sdiSentAt ? new Date(doc.sdiSentAt).toLocaleString('it-IT') : '—'}</span>
                </div>
                <div className="flex justify-between gap-4">
                  <span className="text-muted-foreground shrink-0">File XML</span>
                  {doc.sdiFilePath ? (
                    <button
                      type="button"
                      onClick={handleDownloadSdiXml}
                      title="Scarica il file XML inviato allo SDI"
                      className="font-mono text-xs text-right break-all text-primary underline cursor-pointer"
                    >
                      {doc.sdiFilePath}
                    </button>
                  ) : (
                    <span className="font-mono text-xs text-right">—</span>
                  )}
                </div>

                {puoRiprovareSdi && (
                  <Button variant="outline" size="sm" className="gap-2" onClick={handleInviaSdi} disabled={isSendingSdi}>
                    {isSendingSdi ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Send className="h-3.5 w-3.5" />}
                    Riprova
                  </Button>
                )}
              </>
            ) : (
              <>
                <p className="text-muted-foreground">Fattura non ancora trasmessa allo SDI.</p>
                {sdiErrore && doc.sdiErrorMsg && (
                  <div className="rounded-md border border-warning/20 bg-warning/5 px-3 py-2 text-xs text-warning">
                    <span className="font-medium">Dettaglio:</span> {doc.sdiErrorMsg}
                  </div>
                )}
              </>
            )}
          </CardContent>
        </Card>
      )}

      {/* Azioni */}
      <div className="flex flex-wrap gap-3 justify-end print:hidden">
        <Button variant="outline" className="gap-2" onClick={() => navigate('/documents')}>
          <ArrowLeft className="h-4 w-4" />
          Torna ai Documenti
        </Button>
        <Button className="gap-2" onClick={handleDownloadPdf} disabled={isDownloading}>
          {isDownloading
            ? <Loader2 className="h-4 w-4 animate-spin" />
            : <Download className="h-4 w-4" />}
          {isDownloading ? 'Generazione…' : 'Scarica PDF'}
        </Button>
        {puoInviareSdi && (
          <Button variant="outline" className="gap-2" onClick={handleInviaSdi} disabled={isSendingSdi}>
            {isSendingSdi
              ? <Loader2 className="h-4 w-4 animate-spin" />
              : <Send className="h-4 w-4" />}
            {isSendingSdi ? 'Invio…' : 'Invia SDI'}
          </Button>
        )}
      </div>
    </div>
  );
};

export default DocumentDetail;
