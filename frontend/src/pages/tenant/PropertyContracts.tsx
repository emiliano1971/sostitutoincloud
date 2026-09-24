import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Separator } from '@/components/ui/separator';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ArrowLeft, Plus, Trash2, CheckCircle2, Pencil, Calculator, Loader2, AlertCircle } from 'lucide-react';
import { get } from '@/lib/apiClient';
import { getPropertyById, type PropertyDetail } from '@/api/propertyApi';
import {
  getContractRules,
  createContractRule,
  updateContractRule,
  deleteContractRule,
  type ContractRule,
  type ContractRuleCreate,
} from '@/api/contractApi';
import { useToast } from '@/hooks/use-toast';

type CostRuleType = 'pulizie' | 'commissione_ota' | 'cambio_biancheria' | 'commissione_pm' | 'provvigione_proprietario';
type CalcMode = 'fisso' /*| 'percentuale'*/ | 'fisso_per_notte' | 'fisso_per_persona' | 'percentuale_lordo' | 'percentuale_netto' | 'rimanenza';

interface CanaleOtaDTO {
  id: number;
  codice: string;
  nome: string;
  commissioneDefaultPct: number;
  touristTaxIncluded: boolean;
  touristTaxCollection: string;
  attivo: boolean;
}

const ruleTypeLabels: Record<CostRuleType, string> = {
  pulizie: 'Pulizie Abitazione',
  commissione_ota: 'Commissione OTA',
  cambio_biancheria: 'Cambio Biancheria',
  commissione_pm: 'Commissione PM',
  provvigione_proprietario: 'Provvigione Proprietario',
};

// Unica fonte delle label delle modalità di calcolo lato client (usata dal select).
// Allineata a PropertyContractService.CALC_MODE_LABELS, che alimenta i badge della lista.
const calcModeLabels: Record<CalcMode, string> = {
  fisso: 'Importo Fisso (€)',
  fisso_per_notte: 'Fisso per Notte (€/notte)',
  fisso_per_persona: 'Fisso per Persona (€/persona)',
  /*percentuale: 'Percentuale (%)',*/
  percentuale_lordo: 'Percentuale sul Lordo (%)',
  percentuale_netto: 'Percentuale sul Netto (%)',
  rimanenza: 'Rimanenza automatica',
};

// Ordine di presentazione nel select. 'Rimanenza' è esclusa da qui perché
// ammessa solo per alcuni tipi di voce (vedi calcModesForType).
const calcModeOrder: CalcMode[] = [
  'fisso',
  'fisso_per_notte',
  'fisso_per_persona',
  /*'percentuale',*/
  'percentuale_lordo',
];

// La rimanenza è ammessa solo su Commissione PM / Provvigione Proprietario:
// stesso vincolo applicato dal backend (PropertyContractService.TIPI_RIMANENZA_AMMESSA).
const tipiRimanenzaAmmessa: CostRuleType[] = ['commissione_pm', 'provvigione_proprietario'];

// La percentuale sul netto ha senso solo sul compenso del PM, perché la sua base è il
// lordo meno tutte le altre voci (PropertyContractService.TIPI_PERCENTUALE_NETTO_AMMESSA).
const tipiPercentualeNettoAmmessa: CostRuleType[] = ['commissione_pm'];

const calcModesForType = (tipo: CostRuleType): CalcMode[] => {
  const modes = [...calcModeOrder];
  if (tipiPercentualeNettoAmmessa.includes(tipo)) modes.push('percentuale_netto');
  if (tipiRimanenzaAmmessa.includes(tipo)) modes.push('rimanenza');
  return modes;
};

const PropertyContracts = () => {
  const { id } = useParams();
  const propertyId = Number(id);
  const navigate = useNavigate();
  const { toast } = useToast();

  const [property, setProperty] = useState<PropertyDetail | null>(null);
  const [rules, setRules] = useState<ContractRule[]>([]);
  const [otaChannels, setOtaChannels] = useState<CanaleOtaDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [showAddRule, setShowAddRule] = useState(false);
  const [editingRule, setEditingRule] = useState<ContractRule | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  // Simulation params
  const [simGross, setSimGross] = useState('1000');
  const [simNights, setSimNights] = useState('3');
  const [simGuests, setSimGuests] = useState('2');

  // New rule form state
  const [newType, setNewType] = useState<CostRuleType>('pulizie');
  const [newCalcMode, setNewCalcMode] = useState<CalcMode>('fisso');
  const [newValue, setNewValue] = useState('');
  const [newOtaChannelId, setNewOtaChannelId] = useState<string>('');

  useEffect(() => {
    if (!propertyId) return;
    setIsLoading(true);
    setLoadError(null);
    Promise.all([
      getPropertyById(propertyId),
      getContractRules(propertyId),
      get<CanaleOtaDTO[]>('/canali-ota'),
    ])
      .then(([prop, contractRules, channels]) => {
        setProperty(prop);
        setRules(contractRules);
        setOtaChannels(channels.filter(c => c.attivo));
      })
      .catch(err => setLoadError(err.message))
      .finally(() => setIsLoading(false));
  }, [propertyId]);

  const reloadRules = () => getContractRules(propertyId).then(setRules);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (loadError || !property) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-4">
        <AlertCircle className="h-8 w-8 text-destructive" />
        <p className="text-muted-foreground">{loadError || 'Immobile non trovato'}</p>
        <Button variant="outline" onClick={() => navigate('/properties')}>Torna agli immobili</Button>
      </div>
    );
  }

  const sampleGross = parseFloat(simGross) || 1000;
  const sampleNights = parseInt(simNights) || 3;
  const sampleGuests = parseInt(simGuests) || 2;

  const hasRemainder = rules.some(r => r.isRemainder);
  const remainderRuleType = rules.find(r => r.isRemainder)?.tipo;

  // Calculate non-remainder rule amount.
  // 'percentuale_netto' non è calcolabile qui: la sua base dipende dal totale delle altre
  // voci, quindi è gestita nel secondo passaggio di buildSimulation().
  const calculateRuleAmount = (rule: ContractRule, gross: number, nights: number, guests: number): number => {
    if (rule.isRemainder) return 0; // calculated separately
    switch (rule.calcMode) {
      case 'fisso': return rule.valore;
      case 'percentuale':
      case 'percentuale_lordo': return Math.round(gross * rule.valore / 100 * 100) / 100;
      case 'fisso_per_notte': return rule.valore * nights;
      case 'fisso_per_persona': return rule.valore * guests;
      default: return rule.valore;
    }
  };

  // Build per-OTA simulation tables (client-side, from backend rules)
  const otaRulesInContract = rules.filter(r => r.tipo === 'commissione_ota');
  const nonOtaRules = rules.filter(r => r.tipo !== 'commissione_ota');
  const simulationChannels = otaRulesInContract.length > 0
    ? otaRulesInContract.map(r => r.canaleName || 'N/D')
    : ['Diretto'];

  const buildSimulation = (channelName: string) => {
    const otaRule = otaRulesInContract.find(r => (r.canaleName || 'N/D') === channelName);
    const activeRules = otaRule ? [...nonOtaRules, otaRule] : [...nonOtaRules];

    // Passaggio 1: tutte le voci tranne quelle in percentuale sul netto.
    const items = activeRules
      .filter(r => !r.isRemainder && r.calcMode !== 'percentuale_netto')
      .map(r => ({
        rule: r,
        amount: calculateRuleAmount(r, sampleGross, sampleNights, sampleGuests),
      }));

    // Passaggio 2: percentuale sul netto, con base = lordo meno le voci del passaggio 1.
    // Stessa logica di ContrattoCalcolatoreService: base unica, mai negativa.
    const totalePassaggio1 = items.reduce((s, i) => s + i.amount, 0);
    const baseNetto = Math.max(0, Math.round((sampleGross - totalePassaggio1) * 100) / 100);
    activeRules
      .filter(r => !r.isRemainder && r.calcMode === 'percentuale_netto')
      .forEach(r => items.push({
        rule: r,
        amount: Math.round(baseNetto * r.valore / 100 * 100) / 100,
      }));

    const totalNonRemainder = items.reduce((s, i) => s + i.amount, 0);
    const remainderRule = activeRules.find(r => r.isRemainder);
    const remainderAmount = remainderRule ? Math.round((sampleGross - totalNonRemainder) * 100) / 100 : 0;

    const allItems = items.map(i => ({ ...i, isRemainder: false }));
    if (remainderRule) {
      allItems.push({ rule: remainderRule, amount: remainderAmount, isRemainder: true });
    }

    const total = totalNonRemainder + remainderAmount;
    return { channelName, items: allItems, total, remainderAmount };
  };

  const simulations = simulationChannels.map(ch => buildSimulation(ch));

  const resetForm = () => {
    setNewType('pulizie');
    setNewCalcMode('fisso');
    setNewValue('');
    setNewOtaChannelId('');
    setEditingRule(null);
  };

  const handleSaveRule = async () => {
    const isRemainder = newCalcMode === 'rimanenza';
    const val = isRemainder ? 0 : parseFloat(newValue);
    if (!isRemainder && (isNaN(val) || val <= 0)) {
      toast({ title: 'Errore', description: 'Inserire un valore numerico valido.', variant: 'destructive' });
      return;
    }
    if (newType === 'commissione_ota' && !newOtaChannelId) {
      toast({ title: 'Errore', description: 'Seleziona un canale OTA.', variant: 'destructive' });
      return;
    }

    const payload: ContractRuleCreate = {
      fkPropertyId: propertyId,
      fkCanaleOtaId: newType === 'commissione_ota' ? Number(newOtaChannelId) : undefined,
      tipo: newType,
      calcMode: newCalcMode,
      valore: isRemainder ? 0 : val,
      isRemainder,
      ordine: editingRule ? editingRule.ordine : rules.length,
    };

    // Avviso duplicati: stessa combinazione tipo voce + canale OTA.
    // Solo in inserimento — in modifica la regola stessa risulterebbe duplicata di sé.
    if (!editingRule) {
      const duplicato = rules.find(r =>
        r.tipo === payload.tipo &&
        (r.fkCanaleOtaId ?? null) === (payload.fkCanaleOtaId ?? null)
      );
      if (duplicato) {
        const labelTipo = ruleTypeLabels[newType];
        // Il canale si ricava dal payload: newOtaChannelId può contenere una selezione
        // precedente anche dopo il cambio di tipo, il payload invece è già normalizzato.
        const canaleNome = payload.fkCanaleOtaId
          ? otaChannels.find(o => o.id === payload.fkCanaleOtaId)?.nome
          : undefined;
        const conferma = window.confirm(
          `Esiste già una voce "${labelTipo}" ` +
          (canaleNome
            ? `per il canale ${canaleNome}. `
            : `senza canale specifico. `) +
          `Vuoi inserirne un'altra?`
        );
        if (!conferma) return;
      }
    }

    setIsSaving(true);
    try {
      if (editingRule) {
        await updateContractRule(propertyId, editingRule.id, payload);
        toast({ title: 'Regola aggiornata' });
      } else {
        await createContractRule(propertyId, payload);
        toast({ title: 'Regola aggiunta' });
      }
      await reloadRules();
      setShowAddRule(false);
      resetForm();
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeleteRule = async (ruleId: number) => {
    try {
      await deleteContractRule(propertyId, ruleId);
      await reloadRules();
      toast({ title: 'Regola rimossa' });
    } catch (err) {
      toast({ title: 'Errore', description: (err as Error).message, variant: 'destructive' });
    }
  };

  const openEditDialog = (rule: ContractRule) => {
    setEditingRule(rule);
    setNewType(rule.tipo as CostRuleType);
    setNewCalcMode(rule.calcMode as CalcMode);
    setNewValue(rule.isRemainder ? '' : rule.valore.toString());
    setNewOtaChannelId(rule.fkCanaleOtaId ? rule.fkCanaleOtaId.toString() : '');
    setShowAddRule(true);
  };

  const openAddDialog = () => {
    resetForm();
    setShowAddRule(true);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => navigate(`/properties/${id}`)}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div className="flex-1">
          <h1 className="text-2xl font-bold">Contratto — {property.displayName}</h1>
          <p className="text-sm text-muted-foreground">{property.internalCode} · Regole di imputazione costi</p>
        </div>
        <Button size="sm" className="gap-2" onClick={openAddDialog}>
          <Plus className="h-4 w-4" /> Aggiungi Regola
        </Button>
      </div>

      {/* Remainder info */}
      <Card className="border-primary/30 bg-primary/5">
        <CardContent className="p-4">
          <div className="flex items-start gap-3">
            <CheckCircle2 className="h-5 w-5 text-primary mt-0.5" />
            <div>
              <p className="text-sm font-medium">
                {hasRemainder
                  ? <>La voce <span className="font-bold">{remainderRuleType === 'commissione_pm' ? 'Commissione PM' : 'Provvigione Proprietario'}</span> è impostata come <span className="font-bold">rimanenza</span>: assorbe automaticamente la differenza per garantire copertura 100%.</>
                  : <>⚠️ Nessuna voce è impostata come "rimanenza". Imposta la Commissione PM o la Provvigione Proprietario come rimanenza per assorbire automaticamente il residuo.</>
                }
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Rules table */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Regole di Imputazione Costi</CardTitle>
          <CardDescription>Definisci come il prezzo lordo di ogni prenotazione viene ripartito. Una voce tra PM e Proprietario deve essere la "rimanenza".</CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Voce di Costo</TableHead>
                <TableHead>Canale OTA</TableHead>
                <TableHead>Modalità Calcolo</TableHead>
                <TableHead className="text-right">Valore</TableHead>
                <TableHead className="w-20"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rules.map(rule => (
                <TableRow key={rule.id} className={rule.isRemainder ? 'bg-primary/5' : ''}>
                  <TableCell>
                    <span className="font-medium text-sm">{rule.tipoLabel}</span>
                    {rule.isRemainder && <Badge variant="default" className="ml-2 text-[10px]">RIMANENZA</Badge>}
                  </TableCell>
                  <TableCell>
                    {rule.canaleName ? <Badge variant="outline" className="text-xs">{rule.canaleName}</Badge> : <span className="text-muted-foreground text-xs">—</span>}
                  </TableCell>
                  <TableCell>
                    <Badge variant="secondary" className="text-xs">{rule.calcModeLabel}</Badge>
                  </TableCell>
                  <TableCell className="text-right font-mono">
                    {rule.isRemainder ? <span className="text-muted-foreground italic text-xs">auto</span> : rule.calcMode.includes('percentuale') ? `${rule.valore}%` : `€${rule.valore.toFixed(2)}`}
                  </TableCell>
                  <TableCell>
                    <div className="flex gap-1">
                      <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => openEditDialog(rule)}>
                        <Pencil className="h-3.5 w-3.5" />
                      </Button>
                      <Button variant="ghost" size="icon" className="h-7 w-7 text-destructive" onClick={() => handleDeleteRule(rule.id)}>
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
              {rules.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} className="text-center text-muted-foreground py-8">
                    Nessuna regola configurata.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* Simulation section */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base"><Calculator className="h-4 w-4" /> Simulazione Prenotazione</CardTitle>
          <CardDescription>Verifica la ripartizione dei costi per una prenotazione tipo, con breakdown per ogni OTA configurata.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Simulation inputs */}
          <div className="grid grid-cols-3 gap-4">
            <div className="space-y-1">
              <Label className="text-xs">Prezzo Lordo (€)</Label>
              <Input type="number" value={simGross} onChange={e => setSimGross(e.target.value)} min="1" />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Notti</Label>
              <Input type="number" value={simNights} onChange={e => setSimNights(e.target.value)} min="1" />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Ospiti</Label>
              <Input type="number" value={simGuests} onChange={e => setSimGuests(e.target.value)} min="1" />
            </div>
          </div>

          <Separator />

          {/* Per-OTA simulation tables */}
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {simulations.map(sim => {
              const isExact = Math.abs(sim.total - sampleGross) < 0.02;
              return (
                <Card key={sim.channelName} className={`border ${isExact ? 'border-green-500/50' : 'border-destructive/50'}`}>
                  <CardHeader className="py-3 px-4">
                    <CardTitle className="text-sm flex items-center justify-between">
                      <Badge variant="outline">{sim.channelName}</Badge>
                      <span className={`font-mono text-xs ${isExact ? 'text-green-600' : 'text-destructive'}`}>
                        {isExact ? '✓ 100%' : `${Math.round(sim.total / sampleGross * 100)}%`}
                      </span>
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="px-4 pb-3 space-y-1.5">
                    {sim.items.map((item, idx) => (
                      <div key={idx} className={`flex justify-between text-xs ${item.isRemainder ? 'font-semibold text-primary' : ''}`}>
                        <span className="text-muted-foreground">
                          {item.rule.tipoLabel}
                          {item.isRemainder && ' ⇐'}
                        </span>
                        <span className="font-mono">€{item.amount.toFixed(2)}</span>
                      </div>
                    ))}
                    <Separator className="my-1" />
                    <div className="flex justify-between text-xs font-bold">
                      <span>Totale</span>
                      <span className="font-mono">€{sim.total.toFixed(2)}</span>
                    </div>
                    {sim.remainderAmount < 0 && (
                      <p className="text-[10px] text-destructive mt-1">⚠️ La rimanenza è negativa: i costi superano il lordo!</p>
                    )}
                  </CardContent>
                </Card>
              );
            })}
          </div>
        </CardContent>
      </Card>

      {/* Legenda */}
      <Card>
        <CardHeader><CardTitle className="text-base">Guida alle Regole</CardTitle></CardHeader>
        <CardContent className="space-y-3 text-sm text-muted-foreground">
          <div><span className="font-medium text-foreground">Pulizie Abitazione</span> — Importo fisso addebitato per ogni prenotazione.</div>
          <Separator />
          <div><span className="font-medium text-foreground">Commissione OTA</span> — Percentuale sul lordo variabile per canale. Solo una OTA si applica per prenotazione.</div>
          <Separator />
          <div><span className="font-medium text-foreground">Cambio Biancheria</span> — Importo fisso per persona (× ospiti) oppure importo totale fisso.</div>
          <Separator />
          <div><span className="font-medium text-foreground">Commissione PM</span> — Compenso del PM: fisso per notte, % sul lordo, <strong>% sul netto</strong>, oppure <strong>rimanenza</strong>.</div>
          <div className="text-xs pl-4">
            <span className="font-medium text-foreground">Percentuale sul Netto</span> — Percentuale calcolata sul lordo
            residuo dopo aver sottratto tutte le altre voci di costo (OTA, pulizie, ecc.).
            Disponibile solo per la Commissione PM.
          </div>
          <Separator />
          <div><span className="font-medium text-foreground">Provvigione Proprietario</span> — Quota proprietario: % sul lordo, fisso per notte, oppure <strong>rimanenza</strong>.</div>
          <Separator />
          <div className="text-xs italic">⚠️ Tra PM e Proprietario, uno dei due deve essere impostato come "rimanenza" per assorbire il residuo e garantire che il 100% del prezzo sia sempre coperto.</div>
        </CardContent>
      </Card>

      {/* Dialog add/edit rule */}
      <Dialog open={showAddRule} onOpenChange={(open) => { if (!open) { resetForm(); } setShowAddRule(open); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingRule ? 'Modifica Regola' : 'Nuova Regola di Costo'}</DialogTitle>
            <DialogDescription>Configura la voce di costo e la modalità di calcolo.</DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="space-y-2">
              <Label>Tipo di Costo</Label>
              <Select value={newType} onValueChange={(v: CostRuleType) => {
                setNewType(v);
                // Mantiene la modalità già scelta se resta ammessa: cambia solo quando
                // era 'rimanenza' e il nuovo tipo non la consente.
                const modes = calcModesForType(v);
                if (!modes.includes(newCalcMode)) setNewCalcMode(modes[0]);
              }}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {Object.entries(ruleTypeLabels).map(([k, v]) => (
                    <SelectItem key={k} value={k}>{v}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {newType === 'commissione_ota' && (
              <div className="space-y-2">
                <Label>Canale OTA</Label>
                <Select value={newOtaChannelId} onValueChange={(chId) => {
                  setNewOtaChannelId(chId);
                  // Auto-fill default commission from OTA channel
                  const otaReg = otaChannels.find(o => o.id.toString() === chId);
                  if (otaReg && !editingRule) {
                    setNewValue(otaReg.commissioneDefaultPct.toString());
                  }
                }}>
                  <SelectTrigger><SelectValue placeholder="Seleziona canale..." /></SelectTrigger>
                  <SelectContent>
                    {otaChannels.map(ch => (
                      <SelectItem key={ch.id} value={ch.id.toString()}>
                        {ch.nome} (default: {ch.commissioneDefaultPct}%)
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {newOtaChannelId && (() => {
                  const reg = otaChannels.find(o => o.id.toString() === newOtaChannelId);
                  if (!reg) return null;
                  return (
                    <div className="rounded-md bg-muted p-2 text-xs text-muted-foreground space-y-1">
                      <p>Tassa soggiorno: <strong>{reg.touristTaxIncluded ? 'Inclusa nel totale' : 'Esclusa (si somma al lordo)'}</strong></p>
                      <p>Riscossione: <strong className="capitalize">{reg.touristTaxCollection === 'payment_link' ? 'Payment Link' : reg.touristTaxCollection}</strong></p>
                    </div>
                  );
                })()}
              </div>
            )}

            <div className="space-y-2">
              <Label>Modalità di Calcolo</Label>
              <Select value={newCalcMode} onValueChange={(v: CalcMode) => setNewCalcMode(v)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {calcModesForType(newType).map(m => (
                    <SelectItem key={m} value={m}>{calcModeLabels[m]}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {newCalcMode === 'percentuale_netto' && (
                <p className="text-xs text-muted-foreground">
                  Percentuale calcolata sul lordo residuo dopo aver sottratto tutte le altre
                  voci di costo (OTA, pulizie, ecc.).
                </p>
              )}
            </div>

            {newCalcMode !== 'rimanenza' && (
              <div className="space-y-2">
                <Label>
                  {newCalcMode.includes('percentuale') ? 'Percentuale (%)' : 'Importo (€)'}
                </Label>
                <Input
                  type="number"
                  step="0.01"
                  min="0"
                  placeholder={newCalcMode.includes('percentuale') ? 'es. 15' : 'es. 60.00'}
                  value={newValue}
                  onChange={e => setNewValue(e.target.value)}
                />
              </div>
            )}

            {newCalcMode === 'rimanenza' && (
              <div className="rounded-md bg-primary/10 p-3 text-sm text-primary">
                Questa voce assorbirà automaticamente la differenza tra il prezzo lordo e tutte le altre voci, garantendo la copertura al 100%.
              </div>
            )}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => { setShowAddRule(false); resetForm(); }}>Annulla</Button>
            <Button onClick={handleSaveRule} disabled={isSaving || (newCalcMode !== 'rimanenza' && !newValue) || (newType === 'commissione_ota' && !newOtaChannelId)}>
              {isSaving && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
              {editingRule ? 'Salva Modifiche' : 'Aggiungi'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default PropertyContracts;
