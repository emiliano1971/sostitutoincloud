import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Upload, FileSpreadsheet, CheckCircle2, AlertCircle, Building2, Globe, Network } from 'lucide-react';
import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useNavigate } from 'react-router-dom';

const steps = ['Upload File', 'Anteprima', 'Conferma', 'Risultato'];

type ImportSource = 'airbnb' | 'booking' | 'alloggiati' | 'channelmanager';

const sourceConfig: Record<ImportSource, {
  label: string;
  color: string;
  fileName: string;
  rows: number;
  size: string;
  newCount: number;
  dupeCount: number;
  description: string;
  format: string;
  previewRows: { guest: string; property: string; checkin: string; checkout: string; amount: string; status: string }[];
}> = {
  airbnb: {
    label: 'Airbnb',
    color: 'bg-[#FF5A5F]/10 text-[#FF5A5F]',
    fileName: 'airbnb_reservations_mar2025.csv',
    rows: 8,
    size: '186 KB',
    newCount: 7,
    dupeCount: 1,
    description: 'Importa il file CSV esportato dalla sezione "Prenotazioni" di Airbnb Host Dashboard',
    format: 'CSV esportato da Airbnb (UTF-8)',
    previewRows: [
      { guest: 'Marco Bianchi', property: 'Loft Navigli', checkin: '2025-03-02', checkout: '2025-03-05', amount: '€ 420', status: 'nuova' },
      { guest: 'Sophie Müller', property: 'Suite Duomo', checkin: '2025-03-08', checkout: '2025-03-12', amount: '€ 780', status: 'nuova' },
      { guest: 'James Wilson', property: 'Loft Navigli', checkin: '2025-03-10', checkout: '2025-03-13', amount: '€ 390', status: 'nuova' },
      { guest: 'Anna Rossi', property: 'Bilocale Brera', checkin: '2025-03-14', checkout: '2025-03-16', amount: '€ 260', status: 'duplicata' },
      { guest: 'Pierre Dupont', property: 'Suite Duomo', checkin: '2025-03-18', checkout: '2025-03-22', amount: '€ 920', status: 'nuova' },
    ],
  },
  booking: {
    label: 'Booking.com',
    color: 'bg-[#003580]/10 text-[#003580]',
    fileName: 'booking_export_marzo_2025.xlsx',
    rows: 14,
    size: '312 KB',
    newCount: 12,
    dupeCount: 2,
    description: 'Importa il file XLSX esportato dall\'Extranet di Booking.com → Prenotazioni → Esporta',
    format: 'XLSX esportato da Booking.com Extranet',
    previewRows: [
      { guest: 'Luca Ferretti', property: 'Suite Duomo', checkin: '2025-03-01', checkout: '2025-03-04', amount: '€ 540', status: 'nuova' },
      { guest: 'Elena Voronova', property: 'Bilocale Brera', checkin: '2025-03-03', checkout: '2025-03-07', amount: '€ 680', status: 'nuova' },
      { guest: 'Thomas Becker', property: 'Loft Navigli', checkin: '2025-03-06', checkout: '2025-03-09', amount: '€ 375', status: 'nuova' },
      { guest: 'Giulia Marchetti', property: 'Suite Duomo', checkin: '2025-03-11', checkout: '2025-03-14', amount: '€ 510', status: 'duplicata' },
      { guest: 'Yuki Tanaka', property: 'Bilocale Brera', checkin: '2025-03-15', checkout: '2025-03-19', amount: '€ 720', status: 'nuova' },
      { guest: 'Carlos Mendez', property: 'Loft Navigli', checkin: '2025-03-20', checkout: '2025-03-23', amount: '€ 405', status: 'duplicata' },
    ],
  },
  alloggiati: {
    label: 'AlloggiatiWeb',
    color: 'bg-[#1D4ED8]/10 text-[#1D4ED8]',
    fileName: 'alloggiati_schedine_mar2025.txt',
    rows: 18,
    size: '42 KB',
    newCount: 16,
    dupeCount: 2,
    description: 'Importa il file TXT nel formato tracciato ministeriale AlloggiatiWeb (Questura)',
    format: 'TXT tracciato record AlloggiatiWeb (pos. fissa)',
    previewRows: [
      { guest: 'BIANCHI MARCO', property: 'Loft Navigli', checkin: '02/03/2025', checkout: '05/03/2025', amount: 'IT · CI AX1234567', status: 'match' },
      { guest: 'MÜLLER SOPHIE', property: 'Suite Duomo', checkin: '08/03/2025', checkout: '12/03/2025', amount: 'DE · PP C1234567', status: 'match' },
      { guest: 'WILSON JAMES', property: 'Loft Navigli', checkin: '10/03/2025', checkout: '13/03/2025', amount: 'GB · PP 987654321', status: 'match' },
      { guest: 'ROSSI ANNA', property: '—', checkin: '14/03/2025', checkout: '16/03/2025', amount: 'IT · CI AY9876543', status: 'no match' },
      { guest: 'DUPONT PIERRE', property: 'Suite Duomo', checkin: '18/03/2025', checkout: '22/03/2025', amount: 'FR · PP 12AB34567', status: 'match' },
    ],
  },
  channelmanager: {
    label: 'Channel Manager',
    color: 'bg-[#7C3AED]/10 text-[#7C3AED]',
    fileName: 'channelmanager_export_mar2025.xml',
    rows: 22,
    size: '478 KB',
    newCount: 19,
    dupeCount: 3,
    description: 'Importa il file XML/CSV esportato dal tuo Channel Manager (Avaibook, Kross Booking, Octorate, Wubook, Lodgify, Smoobu, Beds24, Guesty, Cloudbeds, Hostaway)',
    format: 'XML o CSV esportato dal Channel Manager',
    previewRows: [
      { guest: 'Roberto Conti', property: 'Loft Navigli', checkin: '2025-03-01', checkout: '2025-03-04', amount: '€ 465', status: 'nuova' },
      { guest: 'Anja Lindqvist', property: 'Suite Duomo', checkin: '2025-03-05', checkout: '2025-03-09', amount: '€ 840', status: 'nuova' },
      { guest: 'David Chen', property: 'Bilocale Brera', checkin: '2025-03-07', checkout: '2025-03-10', amount: '€ 390', status: 'nuova' },
      { guest: 'Maria Kowalski', property: 'Loft Navigli', checkin: '2025-03-12', checkout: '2025-03-15', amount: '€ 435', status: 'duplicata' },
      { guest: 'Hassan Al-Rashid', property: 'Suite Duomo', checkin: '2025-03-17', checkout: '2025-03-21', amount: '€ 760', status: 'nuova' },
      { guest: 'Chiara Moretti', property: 'Bilocale Brera', checkin: '2025-03-22', checkout: '2025-03-25', amount: '€ 345', status: 'duplicata' },
    ],
  },
};

const ImportBookings = () => {
  const [step, setStep] = useState(0);
  const [source, setSource] = useState<ImportSource>('airbnb');
  const navigate = useNavigate();
  const config = sourceConfig[source];
  const isAlloggiati = source === 'alloggiati';

  const handleSourceChange = (val: string) => {
    setSource(val as ImportSource);
    setStep(0);
  };

  return (
    <div className="space-y-6 max-w-4xl">
      <div>
        <h1 className="text-2xl font-bold">Import Dati</h1>
        <p className="text-sm text-muted-foreground">Importa prenotazioni o schedine alloggiati da sorgenti esterne</p>
      </div>

      {/* Source tabs */}
      <Tabs value={source} onValueChange={handleSourceChange}>
        <TabsList className="grid w-full grid-cols-4">
          <TabsTrigger value="airbnb" className="gap-2">
            <div className="h-2 w-2 rounded-full bg-[#FF5A5F]" /> Airbnb
          </TabsTrigger>
          <TabsTrigger value="booking" className="gap-2">
            <div className="h-2 w-2 rounded-full bg-[#003580]" /> Booking.com
          </TabsTrigger>
          <TabsTrigger value="alloggiati" className="gap-2">
            <Building2 className="h-3.5 w-3.5" /> AlloggiatiWeb
          </TabsTrigger>
          <TabsTrigger value="channelmanager" className="gap-2">
            <Network className="h-3.5 w-3.5" /> Channel Manager
          </TabsTrigger>
        </TabsList>
      </Tabs>

      {/* Info card */}
      <Card className="border-l-4" style={{ borderLeftColor: source === 'airbnb' ? '#FF5A5F' : source === 'booking' ? '#003580' : source === 'alloggiati' ? '#1D4ED8' : '#7C3AED' }}>
        <CardContent className="p-4">
          <p className="text-sm">{config.description}</p>
          <p className="text-xs text-muted-foreground mt-1">Formato atteso: <strong>{config.format}</strong></p>
        </CardContent>
      </Card>

      {/* Steps */}
      <div className="flex items-center gap-2">
        {steps.map((s, i) => (
          <div key={s} className="flex items-center gap-2">
            <div className={`h-7 w-7 rounded-full flex items-center justify-center text-xs font-medium ${
              i <= step ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground'
            }`}>
              {i < step ? <CheckCircle2 className="h-4 w-4" /> : i + 1}
            </div>
            <span className={`text-sm hidden sm:inline ${i <= step ? 'font-medium' : 'text-muted-foreground'}`}>{s}</span>
            {i < steps.length - 1 && <div className="w-8 h-px bg-border" />}
          </div>
        ))}
      </div>

      {/* Step 0: Upload */}
      {step === 0 && (
        <Card>
          <CardContent className="p-8">
            <div className="border-2 border-dashed border-border rounded-lg p-12 text-center space-y-4">
              <Upload className="h-12 w-12 mx-auto text-muted-foreground" />
              <div>
                <p className="font-medium">Trascina il file qui o clicca per selezionare</p>
                <p className="text-sm text-muted-foreground mt-1">
                  {isAlloggiati
                    ? 'Formato supportato: TXT tracciato AlloggiatiWeb (max 5MB)'
                    : `Formati supportati: ${source === 'airbnb' ? 'CSV' : 'XLSX'} (max 10MB)`}
                </p>
              </div>
              <Button onClick={() => setStep(1)}>Seleziona File</Button>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Step 1: Preview */}
      {step === 1 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              Anteprima Import
              <Badge variant="outline" className={config.color}>{config.label}</Badge>
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center gap-3 p-3 rounded-lg bg-muted/50">
              <FileSpreadsheet className="h-8 w-8 text-primary" />
              <div>
                <p className="text-sm font-medium">{config.fileName}</p>
                <p className="text-xs text-muted-foreground">{config.rows} righe · {config.size}</p>
              </div>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div className="p-3 rounded-lg bg-success/10">
                <p className="font-medium text-success">{config.newCount} {isAlloggiati ? 'match trovati' : 'nuove'}</p>
                <p className="text-xs text-muted-foreground">{isAlloggiati ? 'Schedine abbinate a prenotazioni' : 'Prenotazioni da importare'}</p>
              </div>
              <div className="p-3 rounded-lg bg-warning/10">
                <p className="font-medium text-warning">{config.dupeCount} {isAlloggiati ? 'senza match' : 'duplicate'}</p>
                <p className="text-xs text-muted-foreground">{isAlloggiati ? 'Da abbinare manualmente' : 'Già presenti, saranno ignorate'}</p>
              </div>
            </div>

            {/* Preview table */}
            <div className="border rounded-lg overflow-hidden">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Ospite</TableHead>
                    <TableHead>Immobile</TableHead>
                    <TableHead>Check-in</TableHead>
                    <TableHead>Check-out</TableHead>
                    <TableHead>{isAlloggiati ? 'Nazione · Doc' : 'Importo'}</TableHead>
                    <TableHead>Stato</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {config.previewRows.map((row, i) => (
                    <TableRow key={i}>
                      <TableCell className="text-sm font-medium">{row.guest}</TableCell>
                      <TableCell className="text-sm">{row.property}</TableCell>
                      <TableCell className="text-sm">{row.checkin}</TableCell>
                      <TableCell className="text-sm">{row.checkout}</TableCell>
                      <TableCell className="text-sm font-mono">{row.amount}</TableCell>
                      <TableCell>
                        <Badge variant="outline" className={
                          row.status === 'nuova' || row.status === 'match'
                            ? 'bg-success/10 text-success'
                            : row.status === 'duplicata'
                            ? 'bg-warning/10 text-warning'
                            : 'bg-destructive/10 text-destructive'
                        }>
                          {row.status}
                        </Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <div className="flex gap-3 justify-end">
              <Button variant="outline" onClick={() => setStep(0)}>Indietro</Button>
              <Button onClick={() => setStep(2)}>Conferma Import</Button>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Step 2: Confirm */}
      {step === 2 && (
        <Card>
          <CardHeader><CardTitle className="text-base">Conferma Import</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm">
              {isAlloggiati
                ? <>Stai per importare <strong>{config.newCount} schedine alloggiati</strong> e abbinarle alle prenotazioni esistenti. {config.dupeCount} schedine senza match richiederanno abbinamento manuale.</>
                : <>Stai per importare <strong>{config.newCount} prenotazioni</strong> da {config.label}. {config.dupeCount > 0 && <>{config.dupeCount} duplicate saranno ignorate. </>}Questa azione non può essere annullata.</>
              }
            </p>
            <div className="flex gap-3 justify-end">
              <Button variant="outline" onClick={() => setStep(1)}>Indietro</Button>
              <Button onClick={() => setStep(3)}>Procedi</Button>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Step 3: Result */}
      {step === 3 && (
        <Card>
          <CardContent className="p-8 text-center space-y-4">
            <CheckCircle2 className="h-16 w-16 mx-auto text-success" />
            <div>
              <p className="text-lg font-bold">Import Completato</p>
              <p className="text-sm text-muted-foreground">
                {isAlloggiati
                  ? `${config.newCount} schedine importate e abbinate con successo`
                  : `${config.newCount} prenotazioni ${config.label} importate con successo`
                }
              </p>
            </div>
            <div className="flex gap-3 justify-center">
              <Button variant="outline" onClick={() => setStep(0)}>Nuovo Import</Button>
              <Button onClick={() => navigate('/bookings')}>Vai alle Prenotazioni</Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
};

export default ImportBookings;
