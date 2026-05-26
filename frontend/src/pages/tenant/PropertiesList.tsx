import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { Search, Plus, Eye, Globe } from 'lucide-react';
import { mockProperties } from '@/data/mock-data';

const otaLabels: Record<string, string> = {
  airbnb_id: 'Airbnb',
  booking_id: 'Booking.com',
  vrbo_id: 'Vrbo',
  tripadvisor_id: 'TripAdvisor',
  expedia_id: 'Expedia',
};

const PropertiesList = () => {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const props = mockProperties.filter(p => p.tenant_id === 't1')
    .filter(p => search === '' || p.display_name.toLowerCase().includes(search.toLowerCase()) || p.internal_code.toLowerCase().includes(search.toLowerCase()) || p.cin_code.toLowerCase().includes(search.toLowerCase()));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Immobili</h1>
          <p className="text-sm text-muted-foreground">{props.length} immobili</p>
        </div>
        <Button size="sm" className="gap-2" onClick={() => navigate('/properties/new')}><Plus className="h-4 w-4" /> Nuovo Immobile</Button>
      </div>

      <Card>
        <CardContent className="p-4">
          <div className="relative max-w-sm">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input placeholder="Cerca per nome, codice o CIN..." value={search} onChange={e => setSearch(e.target.value)} className="pl-9" />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Codice</TableHead>
                <TableHead>Nome</TableHead>
                <TableHead>CIN</TableHead>
                <TableHead>Città</TableHead>
                <TableHead>Tipo</TableHead>
                <TableHead>OTA</TableHead>
                <TableHead className="text-right">Prenotazioni</TableHead>
                <TableHead>Stato</TableHead>
                <TableHead className="w-10"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {props.map(p => {
                const activeOtas = Object.entries(p.ota_codes).filter(([, v]) => v);
                return (
                  <TableRow key={p.property_id} className="cursor-pointer" onClick={() => navigate(`/properties/${p.property_id}`)}>
                    <TableCell className="font-mono text-xs">{p.internal_code}</TableCell>
                    <TableCell className="font-medium">{p.display_name}</TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">{p.cin_code}</TableCell>
                    <TableCell className="text-sm">{p.city}</TableCell>
                    <TableCell><Badge variant="outline" className="text-xs">{p.property_type}</Badge></TableCell>
                    <TableCell>
                      {activeOtas.length > 0 ? (
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <span className="flex items-center gap-1 text-xs text-muted-foreground cursor-help">
                              <Globe className="h-3 w-3" /> {activeOtas.length}
                            </span>
                          </TooltipTrigger>
                          <TooltipContent>
                            {activeOtas.map(([k, v]) => (
                              <div key={k} className="text-xs">{otaLabels[k]}: {v}</div>
                            ))}
                          </TooltipContent>
                        </Tooltip>
                      ) : (
                        <span className="text-xs text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    <TableCell className="text-right">{p.bookings_count}</TableCell>
                    <TableCell><Badge variant={p.status === 'active' ? 'default' : 'secondary'}>{p.status === 'active' ? 'Attivo' : 'Inattivo'}</Badge></TableCell>
                    <TableCell><Button variant="ghost" size="icon" className="h-7 w-7"><Eye className="h-3.5 w-3.5" /></Button></TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
};

export default PropertiesList;
