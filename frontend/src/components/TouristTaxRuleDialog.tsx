import { useEffect, useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Separator } from '@/components/ui/separator';
import { Loader2, AlertCircle, Plus, X } from 'lucide-react';
import ComuneAutocomplete from '@/components/ComuneAutocomplete';
import { toast } from '@/hooks/use-toast';
import {
  createTouristTaxRule, updateTouristTaxRule,
  type TouristTaxRuleDetail, type TouristTaxRuleCreateRequest,
  type TouristTaxAgeBand, type TouristTaxSeason, type TouristTaxZone,
} from '@/api/touristTaxApi';

interface TouristTaxRuleDialogProps {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
  regola?: TouristTaxRuleDetail | null;
}

const numOrNull = (s: string): number | null => (s.trim() === '' ? null : Number(s));

const TouristTaxRuleDialog = ({ open, onClose, onSaved, regola }: TouristTaxRuleDialogProps) => {
  const isEdit = !!regola;

  const [comune, setComune] = useState('');
  const [provincia, setProvincia] = useState('');
  const [regione, setRegione] = useState('');
  const [importoPerNotte, setImportoPerNotte] = useState('');
  const [maxNotti, setMaxNotti] = useState('');
  const [maxAmountPerPerson, setMaxAmountPerPerson] = useState('');
  const [validaDal, setValidaDal] = useState('');
  const [validaAl, setValidaAl] = useState('');
  const [exemptions, setExemptions] = useState('');
  const [notes, setNotes] = useState('');
  const [fascieEta, setFascieEta] = useState<TouristTaxAgeBand[]>([]);
  const [stagioni, setStagioni] = useState<TouristTaxSeason[]>([]);
  const [zone, setZone] = useState<TouristTaxZone[]>([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Prefill all'apertura (o reset per la creazione)
  useEffect(() => {
    if (!open) return;
    setError(null);
    if (regola) {
      setComune(regola.comune ?? '');
      setProvincia(regola.provincia ?? '');
      setRegione(regola.region ?? '');
      setImportoPerNotte(regola.importoPerNotte != null ? String(regola.importoPerNotte) : '');
      setMaxNotti(regola.maxNotti != null ? String(regola.maxNotti) : '');
      setMaxAmountPerPerson(regola.maxAmountPerPerson != null ? String(regola.maxAmountPerPerson) : '');
      setValidaDal(regola.validaDal ?? '');
      setValidaAl(regola.validaAl ?? '');
      setExemptions(regola.exemptions ?? '');
      setNotes(regola.notes ?? '');
      setFascieEta(regola.fascieEta ?? []);
      setStagioni(regola.stagioni ?? []);
      setZone(regola.zone ?? []);
    } else {
      setComune(''); setProvincia(''); setRegione('');
      setImportoPerNotte(''); setMaxNotti(''); setMaxAmountPerPerson('');
      setValidaDal(''); setValidaAl(''); setExemptions(''); setNotes('');
      setFascieEta([]); setStagioni([]); setZone([]);
    }
  }, [open, regola]);

  // ── helpers liste dinamiche ──
  const addFascia = () => setFascieEta(p => [...p, { label: '', minAge: 0, maxAge: 999, reductionPct: 0 }]);
  const updFascia = (i: number, patch: Partial<TouristTaxAgeBand>) =>
    setFascieEta(p => p.map((f, j) => (j === i ? { ...f, ...patch } : f)));
  const delFascia = (i: number) => setFascieEta(p => p.filter((_, j) => j !== i));

  const addStagione = () => setStagioni(p => [...p, { label: '', startDay: 1, startMonth: 1, endDay: 31, endMonth: 12, reductionPct: 0 }]);
  const updStagione = (i: number, patch: Partial<TouristTaxSeason>) =>
    setStagioni(p => p.map((s, j) => (j === i ? { ...s, ...patch } : s)));
  const delStagione = (i: number) => setStagioni(p => p.filter((_, j) => j !== i));

  const addZona = () => setZone(p => [...p, { label: '', reductionPct: 0 }]);
  const updZona = (i: number, patch: Partial<TouristTaxZone>) =>
    setZone(p => p.map((z, j) => (j === i ? { ...z, ...patch } : z)));
  const delZona = (i: number) => setZone(p => p.filter((_, j) => j !== i));

  const handleSave = async () => {
    setError(null);
    if (!comune.trim()) { setError('Il comune è obbligatorio'); return; }
    if (importoPerNotte.trim() === '' || isNaN(Number(importoPerNotte))) { setError('Importo per notte non valido'); return; }
    if (!validaDal) { setError('La data "Valida dal" è obbligatoria'); return; }

    const payload: TouristTaxRuleCreateRequest = {
      comune: comune.trim(),
      provincia: provincia.trim(),
      region: regione.trim() || null,
      importoPerNotte: Number(importoPerNotte),
      maxNotti: numOrNull(maxNotti),
      maxAmountPerPerson: numOrNull(maxAmountPerPerson),
      validaDal,
      validaAl: validaAl || null,
      exemptions: exemptions.trim() || null,
      notes: notes.trim() || null,
      fascieEta,
      stagioni,
      zone,
    };

    setSaving(true);
    try {
      if (regola) {
        await updateTouristTaxRule(regola.id, payload);
      } else {
        await createTouristTaxRule(payload);
      }
      toast({ title: isEdit ? 'Regola aggiornata' : 'Regola creata' });
      onSaved();
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Errore durante il salvataggio');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onClose(); }}>
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{isEdit ? 'Modifica regola tassa di soggiorno' : 'Nuova regola tassa di soggiorno'}</DialogTitle>
          <DialogDescription>Definisci tariffa, validità e riduzioni per comune.</DialogDescription>
        </DialogHeader>

        <div className="space-y-6">
          {/* ── Dati principali ── */}
          <div className="space-y-3">
            <h3 className="text-sm font-semibold">Dati principali</h3>
            <div className="space-y-1">
              <Label className="text-xs">Comune *</Label>
              <ComuneAutocomplete
                value={comune}
                requireValidComune
                // Comune svuotato (testo non valido): azzera anche provincia e regione derivate.
                onChange={(nome) => { setComune(nome); if (!nome) { setProvincia(''); setRegione(''); } }}
                onSelect={(c) => { setComune(c.nome); setProvincia(c.siglaProvincia); setRegione(c.regione); }}
                placeholder="Cerca comune…"
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label className="text-xs">Provincia</Label>
                <Input value={provincia} readOnly className="bg-muted/40" placeholder="—" />
              </div>
              <div className="space-y-1">
                <Label className="text-xs">Regione</Label>
                <Input value={regione} readOnly className="bg-muted/40" placeholder="—" />
              </div>
            </div>
            <div className="grid grid-cols-3 gap-3">
              <div className="space-y-1">
                <Label className="text-xs">Importo per notte (€) *</Label>
                <Input type="number" step="0.01" value={importoPerNotte} onChange={e => setImportoPerNotte(e.target.value)} />
              </div>
              <div className="space-y-1">
                <Label className="text-xs">Max notti</Label>
                <Input type="number" value={maxNotti} onChange={e => setMaxNotti(e.target.value)} placeholder="∞" />
              </div>
              <div className="space-y-1">
                <Label className="text-xs">Max importo/persona (€)</Label>
                <Input type="number" step="0.01" value={maxAmountPerPerson} onChange={e => setMaxAmountPerPerson(e.target.value)} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label className="text-xs">Valida dal *</Label>
                <Input type="date" value={validaDal} onChange={e => setValidaDal(e.target.value)} />
              </div>
              <div className="space-y-1">
                <Label className="text-xs">Valida al</Label>
                <Input type="date" value={validaAl} onChange={e => setValidaAl(e.target.value)} />
              </div>
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Esenzioni (una per riga)</Label>
              <Textarea rows={3} value={exemptions} onChange={e => setExemptions(e.target.value)} placeholder="Es. Minori di 12 anni&#10;Disabili e accompagnatori" />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">Note</Label>
              <Textarea rows={2} value={notes} onChange={e => setNotes(e.target.value)} />
            </div>
          </div>

          <Separator />

          {/* ── Fasce età ── */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold">Fasce età</h3>
              <Button type="button" variant="outline" size="sm" className="h-7 gap-1" onClick={addFascia}>
                <Plus className="h-3.5 w-3.5" /> Aggiungi fascia
              </Button>
            </div>
            {fascieEta.map((f, i) => (
              <div key={i} className="flex items-end gap-2">
                <div className="flex-1 space-y-1"><Label className="text-[10px]">Label</Label>
                  <Input className="h-8" value={f.label} onChange={e => updFascia(i, { label: e.target.value })} /></div>
                <div className="w-20 space-y-1"><Label className="text-[10px]">Età min</Label>
                  <Input className="h-8" type="number" value={f.minAge} onChange={e => updFascia(i, { minAge: Number(e.target.value) })} /></div>
                <div className="w-20 space-y-1"><Label className="text-[10px]">Età max</Label>
                  <Input className="h-8" type="number" value={f.maxAge} onChange={e => updFascia(i, { maxAge: Number(e.target.value) })} /></div>
                <div className="w-24 space-y-1"><Label className="text-[10px]">Riduz. %</Label>
                  <Input className="h-8" type="number" value={f.reductionPct} onChange={e => updFascia(i, { reductionPct: Number(e.target.value) })} /></div>
                <Button type="button" variant="ghost" size="icon" className="h-8 w-8 text-destructive shrink-0" onClick={() => delFascia(i)}><X className="h-4 w-4" /></Button>
              </div>
            ))}
          </div>

          <Separator />

          {/* ── Stagioni ── */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold">Stagioni</h3>
              <Button type="button" variant="outline" size="sm" className="h-7 gap-1" onClick={addStagione}>
                <Plus className="h-3.5 w-3.5" /> Aggiungi stagione
              </Button>
            </div>
            {stagioni.map((s, i) => (
              <div key={i} className="flex items-end gap-2">
                <div className="flex-1 space-y-1"><Label className="text-[10px]">Label</Label>
                  <Input className="h-8" value={s.label} onChange={e => updStagione(i, { label: e.target.value })} /></div>
                <div className="w-16 space-y-1"><Label className="text-[10px]">Dal gg</Label>
                  <Input className="h-8" type="number" value={s.startDay} onChange={e => updStagione(i, { startDay: Number(e.target.value) })} /></div>
                <div className="w-16 space-y-1"><Label className="text-[10px]">mm</Label>
                  <Input className="h-8" type="number" value={s.startMonth} onChange={e => updStagione(i, { startMonth: Number(e.target.value) })} /></div>
                <div className="w-16 space-y-1"><Label className="text-[10px]">Al gg</Label>
                  <Input className="h-8" type="number" value={s.endDay} onChange={e => updStagione(i, { endDay: Number(e.target.value) })} /></div>
                <div className="w-16 space-y-1"><Label className="text-[10px]">mm</Label>
                  <Input className="h-8" type="number" value={s.endMonth} onChange={e => updStagione(i, { endMonth: Number(e.target.value) })} /></div>
                <div className="w-20 space-y-1"><Label className="text-[10px]">Riduz. %</Label>
                  <Input className="h-8" type="number" value={s.reductionPct} onChange={e => updStagione(i, { reductionPct: Number(e.target.value) })} /></div>
                <Button type="button" variant="ghost" size="icon" className="h-8 w-8 text-destructive shrink-0" onClick={() => delStagione(i)}><X className="h-4 w-4" /></Button>
              </div>
            ))}
          </div>

          <Separator />

          {/* ── Zone ── */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold">Zone</h3>
              <Button type="button" variant="outline" size="sm" className="h-7 gap-1" onClick={addZona}>
                <Plus className="h-3.5 w-3.5" /> Aggiungi zona
              </Button>
            </div>
            {zone.map((z, i) => (
              <div key={i} className="flex items-end gap-2">
                <div className="flex-1 space-y-1"><Label className="text-[10px]">Label</Label>
                  <Input className="h-8" value={z.label} onChange={e => updZona(i, { label: e.target.value })} /></div>
                <div className="w-24 space-y-1"><Label className="text-[10px]">Riduz. %</Label>
                  <Input className="h-8" type="number" value={z.reductionPct} onChange={e => updZona(i, { reductionPct: Number(e.target.value) })} /></div>
                <Button type="button" variant="ghost" size="icon" className="h-8 w-8 text-destructive shrink-0" onClick={() => delZona(i)}><X className="h-4 w-4" /></Button>
              </div>
            ))}
          </div>

          {error && (
            <div className="flex items-center gap-2 text-destructive text-sm">
              <AlertCircle className="h-4 w-4" /> {error}
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={saving}>Annulla</Button>
          <Button onClick={handleSave} disabled={saving}>
            {saving && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
            Salva
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default TouristTaxRuleDialog;
