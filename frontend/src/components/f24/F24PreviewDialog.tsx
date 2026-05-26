import { useRef } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Printer, Download } from 'lucide-react';
import type { F24Record } from '@/types';
import { mockTenants } from '@/data/mock-data';

interface F24PreviewDialogProps {
  f24: F24Record | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const F24PreviewDialog = ({ f24, open, onOpenChange }: F24PreviewDialogProps) => {
  const printRef = useRef<HTMLDivElement>(null);
  if (!f24) return null;

  const tenant = mockTenants.find(t => t.tenant_id === f24.tenant_id)!;
  const [year, month] = f24.period.split('-');
  const monthNames = ['', 'Gennaio', 'Febbraio', 'Marzo', 'Aprile', 'Maggio', 'Giugno', 'Luglio', 'Agosto', 'Settembre', 'Ottobre', 'Novembre', 'Dicembre'];
  const periodLabel = `${monthNames[parseInt(month)]} ${year}`;

  // Parse deadline
  const deadlineParts = f24.deadline_date.split('-');

  const handlePrint = () => {
    const content = printRef.current;
    if (!content) return;
    const win = window.open('', '_blank');
    if (!win) return;
    win.document.write(`
      <html><head><title>F24 - ${f24.period}</title>
      <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Courier New', monospace; background: white; }
        @media print { body { margin: 0; } }
        ${f24Styles}
      </style></head><body>${content.innerHTML}</body></html>
    `);
    win.document.close();
    win.print();
  };

  // Split codice fiscale into individual chars for boxes
  const cfChars = (tenant.tax_code || '').split('');

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[900px] max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center justify-between">
            <span>F24 — Periodo {periodLabel}</span>
            <div className="flex gap-2">
              <Button size="sm" variant="outline" onClick={handlePrint}>
                <Printer className="h-4 w-4 mr-1.5" /> Stampa
              </Button>
            </div>
          </DialogTitle>
        </DialogHeader>

        <div ref={printRef}>
          <div className="f24-form">
            {/* HEADER */}
            <div className="f24-header">
              <div className="f24-header-left">
                <div className="f24-title">MODELLO DI PAGAMENTO UNIFICATO</div>
              </div>
              <div className="f24-header-right">
                <div className="f24-logo-box">
                  <span className="f24-logo-text">F24</span>
                </div>
              </div>
            </div>

            {/* CONTRIBUENTE */}
            <div className="f24-section">
              <div className="f24-section-header">CONTRIBUENTE</div>
              <div className="f24-section-body">
                <div className="f24-row">
                  <div className="f24-field f24-field-wide">
                    <div className="f24-field-label">Codice fiscale</div>
                    <div className="f24-cf-boxes">
                      {Array.from({ length: 16 }).map((_, i) => (
                        <div key={i} className="f24-cf-box">{cfChars[i] || ''}</div>
                      ))}
                    </div>
                  </div>
                </div>
                <div className="f24-row">
                  <div className="f24-field f24-field-wide">
                    <div className="f24-field-label">Cognome, denominazione o ragione sociale</div>
                    <div className="f24-field-value">{tenant.legal_name}</div>
                  </div>
                </div>
                <div className="f24-row">
                  <div className="f24-field">
                    <div className="f24-field-label">Nome</div>
                    <div className="f24-field-value"></div>
                  </div>
                  <div className="f24-field f24-field-sm">
                    <div className="f24-field-label">Data di nascita</div>
                    <div className="f24-field-value"></div>
                  </div>
                  <div className="f24-field f24-field-xs">
                    <div className="f24-field-label">Sesso</div>
                    <div className="f24-field-value"></div>
                  </div>
                </div>
                <div className="f24-row">
                  <div className="f24-field">
                    <div className="f24-field-label">Domicilio fiscale</div>
                    <div className="f24-field-value">{tenant.legal_address}</div>
                  </div>
                  <div className="f24-field f24-field-sm">
                    <div className="f24-field-label">Prov.</div>
                    <div className="f24-field-value">{tenant.legal_address.match(/([A-Z]{2})$/)?.[1] || ''}</div>
                  </div>
                </div>
              </div>
            </div>

            {/* ERARIO */}
            <div className="f24-section">
              <div className="f24-section-header">SEZIONE ERARIO</div>
              <div className="f24-section-body">
                <table className="f24-table">
                  <thead>
                    <tr>
                      <th>Codice tributo</th>
                      <th>Rateazione / Regione / Prov. / Mese rif.</th>
                      <th>Anno di riferimento</th>
                      <th>Importi a debito versati</th>
                      <th>Importi a credito compensati</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td className="f24-mono">{f24.tax_code}</td>
                      <td className="f24-mono">{String(parseInt(month)).padStart(4, '0')}</td>
                      <td className="f24-mono">{year}</td>
                      <td className="f24-amount">{f24.total_amount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</td>
                      <td className="f24-amount"></td>
                    </tr>
                    {/* Empty rows */}
                    {[1, 2, 3].map(n => (
                      <tr key={n} className="f24-empty-row">
                        <td></td><td></td><td></td><td></td><td></td>
                      </tr>
                    ))}
                    <tr className="f24-total-row">
                      <td colSpan={3} className="f24-total-label">TOTALE</td>
                      <td className="f24-amount f24-total-amount">{f24.total_amount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</td>
                      <td className="f24-amount"></td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            {/* SEZIONE INPS - empty */}
            <div className="f24-section">
              <div className="f24-section-header">SEZIONE INPS</div>
              <div className="f24-section-body">
                <table className="f24-table">
                  <thead>
                    <tr>
                      <th>Codice sede</th>
                      <th>Causale contributo</th>
                      <th>Matricola INPS / Codice INPS / Filiale azienda</th>
                      <th>Periodo di riferimento: da</th>
                      <th>Periodo di riferimento: a</th>
                      <th>Importi a debito versati</th>
                      <th>Importi a credito compensati</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr className="f24-empty-row"><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
                  </tbody>
                </table>
              </div>
            </div>

            {/* SEZIONE REGIONI - empty */}
            <div className="f24-section">
              <div className="f24-section-header">SEZIONE REGIONI</div>
              <div className="f24-section-body">
                <table className="f24-table">
                  <thead>
                    <tr>
                      <th>Codice regione</th>
                      <th>Codice tributo</th>
                      <th>Rateazione / Mese rif.</th>
                      <th>Anno di riferimento</th>
                      <th>Importi a debito versati</th>
                      <th>Importi a credito compensati</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr className="f24-empty-row"><td></td><td></td><td></td><td></td><td></td><td></td></tr>
                  </tbody>
                </table>
              </div>
            </div>

            {/* SEZIONE IMU E ALTRI TRIBUTI LOCALI - empty */}
            <div className="f24-section">
              <div className="f24-section-header">SEZIONE IMU E ALTRI TRIBUTI LOCALI</div>
              <div className="f24-section-body">
                <table className="f24-table">
                  <thead>
                    <tr>
                      <th>Codice ente / Codice comune</th>
                      <th>Ravv.</th>
                      <th>Immob. variati</th>
                      <th>Acc.</th>
                      <th>Saldo</th>
                      <th>Numero immobili</th>
                      <th>Codice tributo</th>
                      <th>Rateazione / Mese rif.</th>
                      <th>Anno di riferimento</th>
                      <th>Importi a debito versati</th>
                      <th>Importi a credito compensati</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr className="f24-empty-row"><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
                  </tbody>
                </table>
              </div>
            </div>

            {/* SALDO FINALE */}
            <div className="f24-footer">
              <div className="f24-saldo">
                <div className="f24-saldo-row">
                  <span className="f24-saldo-label">SALDO FINALE</span>
                  <span className="f24-saldo-value">€ {f24.total_amount.toLocaleString('it-IT', { minimumFractionDigits: 2 })}</span>
                </div>
              </div>
              <div className="f24-footer-info">
                <div className="f24-row">
                  <div className="f24-field">
                    <div className="f24-field-label">Data versamento</div>
                    <div className="f24-field-value">{f24.payment_date || f24.deadline_date}</div>
                  </div>
                  <div className="f24-field">
                    <div className="f24-field-label">Banca / Ufficio postale / Agente della riscossione</div>
                    <div className="f24-field-value">{f24.status === 'paid' ? 'BANCA INTESA SANPAOLO' : ''}</div>
                  </div>
                </div>
              </div>
            </div>

            {/* Info */}
            <div className="f24-info-bar">
              <span>Ritenute n. {f24.withholdings_count} — Codice tributo {f24.tax_code} — Periodo {periodLabel}</span>
              <span className="f24-status-badge" data-status={f24.status}>
                {f24.status === 'paid' ? 'PAGATO' : f24.status === 'sent' ? 'INVIATO' : f24.status === 'ready' ? 'PRONTO' : 'BOZZA'}
              </span>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
};

const f24Styles = `
  .f24-form {
    font-family: 'Courier New', 'Lucida Console', monospace;
    font-size: 11px;
    color: #1a1a1a;
    background: white;
    border: 2px solid #1a365d;
    padding: 0;
    max-width: 820px;
    margin: 0 auto;
  }
  .f24-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: #1a365d;
    color: white;
    padding: 10px 16px;
  }
  .f24-title {
    font-size: 13px;
    font-weight: bold;
    letter-spacing: 1.5px;
    text-transform: uppercase;
  }
  .f24-logo-box {
    background: white;
    color: #1a365d;
    font-size: 28px;
    font-weight: 900;
    padding: 4px 14px;
    border-radius: 4px;
    letter-spacing: 2px;
  }
  .f24-section {
    border-top: 2px solid #1a365d;
  }
  .f24-section-header {
    background: #e8edf3;
    color: #1a365d;
    font-weight: bold;
    font-size: 10px;
    letter-spacing: 1px;
    text-transform: uppercase;
    padding: 5px 12px;
    border-bottom: 1px solid #b0bec5;
  }
  .f24-section-body {
    padding: 8px 12px;
  }
  .f24-row {
    display: flex;
    gap: 8px;
    margin-bottom: 6px;
  }
  .f24-field {
    flex: 1;
    min-width: 0;
  }
  .f24-field-wide { flex: 2; }
  .f24-field-sm { flex: 0.6; }
  .f24-field-xs { flex: 0.3; }
  .f24-field-label {
    font-size: 8px;
    color: #546e7a;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin-bottom: 2px;
  }
  .f24-field-value {
    border: 1px solid #b0bec5;
    background: #fafbfc;
    padding: 3px 6px;
    min-height: 22px;
    font-size: 11px;
    font-family: 'Courier New', monospace;
  }
  .f24-cf-boxes {
    display: flex;
    gap: 2px;
  }
  .f24-cf-box {
    width: 22px;
    height: 24px;
    border: 1px solid #b0bec5;
    background: #fafbfc;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: bold;
    font-size: 13px;
    font-family: 'Courier New', monospace;
  }
  .f24-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 10px;
  }
  .f24-table th {
    background: #f0f2f5;
    border: 1px solid #b0bec5;
    padding: 4px 6px;
    text-align: center;
    font-size: 8px;
    text-transform: uppercase;
    color: #546e7a;
    font-weight: 600;
  }
  .f24-table td {
    border: 1px solid #b0bec5;
    padding: 4px 6px;
    text-align: center;
    min-height: 22px;
    height: 24px;
    font-family: 'Courier New', monospace;
    background: #fafbfc;
  }
  .f24-mono { font-weight: bold; }
  .f24-amount { text-align: right !important; font-weight: bold; }
  .f24-empty-row td { background: #fafbfc; }
  .f24-total-row td { background: #e8edf3; font-weight: bold; }
  .f24-total-label { text-align: right !important; font-weight: bold; letter-spacing: 1px; }
  .f24-total-amount { font-size: 12px; }
  .f24-footer {
    border-top: 2px solid #1a365d;
    padding: 10px 12px;
  }
  .f24-saldo {
    margin-bottom: 8px;
  }
  .f24-saldo-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: #1a365d;
    color: white;
    padding: 8px 14px;
    font-weight: bold;
    font-size: 14px;
  }
  .f24-saldo-label { letter-spacing: 2px; }
  .f24-saldo-value { font-size: 16px; }
  .f24-info-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: #f0f2f5;
    border-top: 1px solid #b0bec5;
    padding: 6px 12px;
    font-size: 10px;
    color: #546e7a;
  }
  .f24-status-badge {
    padding: 2px 10px;
    border-radius: 3px;
    font-weight: bold;
    font-size: 10px;
    letter-spacing: 1px;
  }
  .f24-status-badge[data-status="paid"] { background: #c6f6d5; color: #22543d; }
  .f24-status-badge[data-status="sent"] { background: #fefcbf; color: #744210; }
  .f24-status-badge[data-status="ready"] { background: #bee3f8; color: #2a4365; }
  .f24-status-badge[data-status="draft"] { background: #e2e8f0; color: #4a5568; }
`;

export default F24PreviewDialog;
