import { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Separator } from '@/components/ui/separator';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';
import { Calculator, MapPin, Users, Calendar, Shield, Loader2, AlertCircle } from 'lucide-react';
import {
  getTouristTaxRules,
  getTouristTaxRule,
  calculateTouristTax,
  type TouristTaxRuleListItem,
  type TouristTaxRuleDetail,
  type TouristTaxCalculation,
} from '@/api/touristTaxApi';

const TouristTaxSettings = () => {
  const [rules, setRules] = useState<TouristTaxRuleListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedRule, setSelectedRule] = useState<TouristTaxRuleDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // Simulator
  const [simNights, setSimNights] = useState('3');
  const [simGuests, setSimGuests] = useState<{ age: string }[]>([{ age: '35' }, { age: '33' }]);
  const [simZone, setSimZone] = useState('');
  const [simMonth, setSimMonth] = useState('7');
  const [simulation, setSimulation] = useState<TouristTaxCalculation | null>(null);

  const addGuest = () => setSimGuests(prev => [...prev, { age: '30' }]);
  const removeGuest = (i: number) => setSimGuests(prev => prev.filter((_, idx) => idx !== i));

  // Load list
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    getTouristTaxRules()
      .then(data => { if (active) setRules(data); })
      .catch(err => { if (active) setError(err instanceof Error ? err.message : 'Errore di caricamento'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  // Open detail by id
  const openDetail = (id: number) => {
    setDetailLoading(true);
    setSelectedRule(null);
    setSimulation(null);
    getTouristTaxRule(id)
      .then(detail => {
        setSelectedRule(detail);
        setSimZone(detail.zone[0]?.label || '');
      })
      .catch(err => setError(err instanceof Error ? err.message : 'Errore di caricamento dettaglio'))
      .finally(() => setDetailLoading(false));
  };

  // Recalculate simulation via backend whenever inputs change
  useEffect(() => {
    if (!selectedRule) return;
    let active = true;
    const month = parseInt(simMonth) || 1;
    const checkinDate = `2025-${String(month).padStart(2, '0')}-15`;
    calculateTouristTax(selectedRule.id, {
      nights: parseInt(simNights) || 1,
      checkinDate,
      zona: simZone || null,
      guestAges: simGuests.map(g => parseInt(g.age) || 30),
    })
      .then(res => { if (active) setSimulation(res); })
      .catch(() => { if (active) setSimulation(null); });
    return () => { active = false; };
  }, [selectedRule, simNights, simGuests, simZone, simMonth]);

  const exemptionsList = (selectedRule?.exemptions ?? '')
    .split('\n')
    .map(e => e.trim())
    .filter(Boolean);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Tassa di Soggiorno</h1>
        <p className="text-sm text-muted-foreground">Regole di calcolo per comune. La tassa viene calcolata automaticamente per ogni prenotazione in base all'immobile.</p>
      </div>

      {/* Municipalities table */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base flex items-center gap-2"><MapPin className="h-4 w-4" /> Comuni Configurati</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {loading ? (
            <div className="flex items-center justify-center gap-2 py-10 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Caricamento regole…
            </div>
          ) : error ? (
            <div className="flex items-center justify-center gap-2 py-10 text-sm text-destructive">
              <AlertCircle className="h-4 w-4" /> {error}
            </div>
          ) : rules.length === 0 ? (
            <div className="py-10 text-center text-sm text-muted-foreground">Nessuna regola configurata.</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Comune</TableHead>
                  <TableHead>Provincia</TableHead>
                  <TableHead className="text-center">Tariffa Base</TableHead>
                  <TableHead className="text-center">Max Notti</TableHead>
                  <TableHead className="text-center">Cap per Persona</TableHead>
                  <TableHead className="text-center">Fasce Età</TableHead>
                  <TableHead className="text-center">Stato</TableHead>
                  <TableHead className="w-20"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rules.map(rule => (
                  <TableRow key={rule.id} className="cursor-pointer hover:bg-muted/50" onClick={() => openDetail(rule.id)}>
                    <TableCell className="font-medium">{rule.comune}</TableCell>
                    <TableCell><Badge variant="outline" className="text-xs">{rule.provincia}</Badge></TableCell>
                    <TableCell className="text-center font-mono">€{Number(rule.importoPerNotte).toFixed(2)}</TableCell>
                    <TableCell className="text-center">{rule.maxNotti ?? '∞'}</TableCell>
                    <TableCell className="text-center">{rule.maxAmountPerPerson ? `€${rule.maxAmountPerPerson}` : '—'}</TableCell>
                    <TableCell className="text-center">{rule.fascieEtaCount}</TableCell>
                    <TableCell className="text-center">
                      <Badge variant={rule.attivo ? 'default' : 'secondary'}>
                        {rule.attivo ? 'Attivo' : 'Inattivo'}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Button variant="ghost" size="sm" className="text-xs" onClick={(e) => { e.stopPropagation(); openDetail(rule.id); }}>
                        Dettaglio
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Detail dialog */}
      <Dialog open={!!selectedRule || detailLoading} onOpenChange={(open) => { if (!open) { setSelectedRule(null); setSimulation(null); } }}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          {detailLoading && !selectedRule ? (
            <div className="flex items-center justify-center gap-2 py-10 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Caricamento dettaglio…
            </div>
          ) : selectedRule && (
            <>
              <DialogHeader>
                <DialogTitle className="flex items-center gap-2">
                  <MapPin className="h-5 w-5" />
                  Tassa di Soggiorno — {selectedRule.comune} ({selectedRule.provincia})
                </DialogTitle>
                <DialogDescription>{selectedRule.notes}</DialogDescription>
              </DialogHeader>

              <div className="space-y-4">
                {/* Base info */}
                <div className="grid grid-cols-3 gap-3">
                  <Card>
                    <CardContent className="p-3 text-center">
                      <p className="text-xs text-muted-foreground">Tariffa Base</p>
                      <p className="text-lg font-bold font-mono">€{Number(selectedRule.importoPerNotte).toFixed(2)}</p>
                      <p className="text-[10px] text-muted-foreground">per persona / notte</p>
                    </CardContent>
                  </Card>
                  <Card>
                    <CardContent className="p-3 text-center">
                      <p className="text-xs text-muted-foreground">Max Notti</p>
                      <p className="text-lg font-bold">{selectedRule.maxNotti ?? '∞'}</p>
                      <p className="text-[10px] text-muted-foreground">consecutive</p>
                    </CardContent>
                  </Card>
                  <Card>
                    <CardContent className="p-3 text-center">
                      <p className="text-xs text-muted-foreground">Cap / Persona</p>
                      <p className="text-lg font-bold">{selectedRule.maxAmountPerPerson ? `€${selectedRule.maxAmountPerPerson}` : '—'}</p>
                    </CardContent>
                  </Card>
                </div>

                {/* Age bands */}
                <Card>
                  <CardHeader className="py-2 px-4">
                    <CardTitle className="text-sm flex items-center gap-2"><Users className="h-3.5 w-3.5" /> Fasce di Età</CardTitle>
                  </CardHeader>
                  <CardContent className="px-4 pb-3 space-y-1">
                    {selectedRule.fascieEta.map((band, i) => (
                      <div key={i} className="flex justify-between text-sm py-1">
                        <span>{band.label} <span className="text-muted-foreground text-xs">({band.minAge}-{band.maxAge > 900 ? '∞' : band.maxAge} anni)</span></span>
                        <Badge variant={band.reductionPct >= 100 ? 'destructive' : band.reductionPct > 0 ? 'secondary' : 'outline'}>
                          {band.reductionPct >= 100 ? 'Esente' : band.reductionPct > 0 ? `-${band.reductionPct}%` : 'Piena'}
                        </Badge>
                      </div>
                    ))}
                  </CardContent>
                </Card>

                {/* Seasons */}
                <Card>
                  <CardHeader className="py-2 px-4">
                    <CardTitle className="text-sm flex items-center gap-2"><Calendar className="h-3.5 w-3.5" /> Stagioni</CardTitle>
                  </CardHeader>
                  <CardContent className="px-4 pb-3 space-y-1">
                    {selectedRule.stagioni.map((s, i) => (
                      <div key={i} className="flex justify-between text-sm py-1">
                        <span>{s.label} <span className="text-muted-foreground text-xs">({s.startDay}/{s.startMonth} — {s.endDay}/{s.endMonth})</span></span>
                        <Badge variant={s.reductionPct > 0 ? 'secondary' : 'outline'}>
                          {s.reductionPct > 0 ? `-${s.reductionPct}%` : 'Piena'}
                        </Badge>
                      </div>
                    ))}
                  </CardContent>
                </Card>

                {/* Zones */}
                {selectedRule.zone.length > 1 && (
                  <Card>
                    <CardHeader className="py-2 px-4">
                      <CardTitle className="text-sm flex items-center gap-2"><MapPin className="h-3.5 w-3.5" /> Zone</CardTitle>
                    </CardHeader>
                    <CardContent className="px-4 pb-3 space-y-1">
                      {selectedRule.zone.map((z, i) => (
                        <div key={i} className="flex justify-between text-sm py-1">
                          <span>{z.label}</span>
                          <Badge variant={z.reductionPct > 0 ? 'secondary' : 'outline'}>
                            {z.reductionPct > 0 ? `-${z.reductionPct}%` : 'Piena'}
                          </Badge>
                        </div>
                      ))}
                    </CardContent>
                  </Card>
                )}

                {/* Exemptions */}
                <Card>
                  <CardHeader className="py-2 px-4">
                    <CardTitle className="text-sm flex items-center gap-2"><Shield className="h-3.5 w-3.5" /> Esenzioni</CardTitle>
                  </CardHeader>
                  <CardContent className="px-4 pb-3">
                    <ul className="text-sm space-y-1">
                      {exemptionsList.map((e, i) => (
                        <li key={i} className="flex items-center gap-2 text-muted-foreground">
                          <span className="h-1 w-1 rounded-full bg-muted-foreground shrink-0" />
                          {e}
                        </li>
                      ))}
                    </ul>
                  </CardContent>
                </Card>

                <Separator />

                {/* Simulator */}
                <Card className="border-primary/30">
                  <CardHeader className="py-3 px-4">
                    <CardTitle className="text-sm flex items-center gap-2"><Calculator className="h-3.5 w-3.5" /> Simulatore di Calcolo</CardTitle>
                  </CardHeader>
                  <CardContent className="px-4 pb-4 space-y-3">
                    <div className="grid grid-cols-2 gap-3">
                      <div className="space-y-1">
                        <Label className="text-xs">Notti</Label>
                        <Input type="number" value={simNights} onChange={e => setSimNights(e.target.value)} min="1" />
                      </div>
                      <div className="space-y-1">
                        <Label className="text-xs">Mese Check-in</Label>
                        <Select value={simMonth} onValueChange={setSimMonth}>
                          <SelectTrigger><SelectValue /></SelectTrigger>
                          <SelectContent>
                            {['Gennaio','Febbraio','Marzo','Aprile','Maggio','Giugno','Luglio','Agosto','Settembre','Ottobre','Novembre','Dicembre'].map((m, i) => (
                              <SelectItem key={i} value={String(i + 1)}>{m}</SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </div>
                    </div>

                    {selectedRule.zone.length > 1 && (
                      <div className="space-y-1">
                        <Label className="text-xs">Zona</Label>
                        <Select value={simZone} onValueChange={setSimZone}>
                          <SelectTrigger><SelectValue /></SelectTrigger>
                          <SelectContent>
                            {selectedRule.zone.map(z => (
                              <SelectItem key={z.label} value={z.label}>{z.label}</SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </div>
                    )}

                    <div className="space-y-2">
                      <div className="flex items-center justify-between">
                        <Label className="text-xs">Ospiti</Label>
                        <Button variant="outline" size="sm" className="h-6 text-xs" onClick={addGuest}>+ Ospite</Button>
                      </div>
                      {simGuests.map((g, i) => (
                        <div key={i} className="flex items-center gap-2">
                          <Input
                            type="number"
                            className="h-8"
                            placeholder="Età"
                            value={g.age}
                            onChange={e => setSimGuests(prev => prev.map((p, j) => j === i ? { age: e.target.value } : p))}
                            min="0"
                          />
                          <span className="text-xs text-muted-foreground whitespace-nowrap">anni</span>
                          {simGuests.length > 1 && (
                            <Button variant="ghost" size="sm" className="h-6 text-xs text-destructive" onClick={() => removeGuest(i)}>✕</Button>
                          )}
                        </div>
                      ))}
                    </div>

                    {simulation && (
                      <>
                        <Separator />
                        <div className="space-y-2">
                          {simulation.perPerson.map((p, i) => (
                            <div key={i} className="flex justify-between text-xs">
                              <span className="text-muted-foreground">
                                Ospite {i + 1} ({p.age} anni)
                                {p.nightsCharged === 0 && ' — Esente'}
                              </span>
                              <span className="font-mono">
                                {p.nightsCharged > 0
                                  ? `€${Number(p.ratePerNight).toFixed(2)} × ${p.nightsCharged}n = €${Number(p.total).toFixed(2)}`
                                  : '€0.00'
                                }
                              </span>
                            </div>
                          ))}
                          <Separator />
                          <div className="flex justify-between text-sm font-bold">
                            <span>Totale Tassa di Soggiorno</span>
                            <span className="font-mono text-primary">€{Number(simulation.total).toFixed(2)}</span>
                          </div>
                        </div>
                      </>
                    )}
                  </CardContent>
                </Card>
              </div>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default TouristTaxSettings;
