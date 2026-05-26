import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Download, FileText } from 'lucide-react';
import { mockDocuments } from '@/data/mock-data';

const statusColors: Record<string, string> = {
  draft: 'bg-muted text-muted-foreground',
  ready: 'bg-primary/10 text-primary',
  sent_sdi: 'bg-warning/10 text-warning',
  accepted: 'bg-success/10 text-success',
  rejected: 'bg-destructive/10 text-destructive',
};

const OwnerDocuments = () => {
  // Owner sees only ricevute (receipts), not fatture (invoices)
  const docs = mockDocuments.filter(d => d.document_type === 'ricevuta').slice(0, 10);

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Documenti</h1>
      <p className="text-sm text-muted-foreground">{docs.length} documenti</p>

      <div className="space-y-3">
        {docs.map(d => (
          <Card key={d.document_id}>
            <CardContent className="p-4">
              <div className="flex items-start justify-between">
                <div className="flex gap-3">
                  <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center shrink-0">
                    <FileText className="h-4 w-4 text-primary" />
                  </div>
                  <div>
                    <p className="font-medium text-sm">{d.document_number}</p>
                    <p className="text-xs text-muted-foreground">{d.recipient_name} · {d.issue_date}</p>
                    <div className="flex gap-2 mt-1.5">
                      <Badge variant="outline" className="text-[10px]">{d.document_type}</Badge>
                      <Badge variant="outline" className={`text-[10px] ${statusColors[d.status]}`}>{d.status}</Badge>
                    </div>
                  </div>
                </div>
                <div className="text-right">
                  <p className="font-bold text-sm">€{d.total_amount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</p>
                  <Button variant="ghost" size="sm" className="mt-1 gap-1 text-xs"><Download className="h-3 w-3" /> PDF</Button>
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
};

export default OwnerDocuments;
