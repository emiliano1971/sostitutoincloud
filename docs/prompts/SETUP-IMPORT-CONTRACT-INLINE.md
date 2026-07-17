Leggi il file CLAUDE.md e i file esistenti:
- frontend/src/pages/tenant/ImportBookings.tsx
- frontend/src/api/propertyApi.ts
- frontend/src/pages/tenant/PropertyContracts.tsx
  (o nome equivalente per la gestione contratti)
  prima di procedere.

Aggiungi un dialog inline nella preview
dell'import per configurare rapidamente
la regola di rimanenza di un immobile
quando compare il warning
"Nessuna voce impostata come rimanenza
per l'immobile".

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — verifica endpoint contratti
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che esistano già:
GET  /api/properties/{id}/contracts
POST /api/properties/{id}/contracts

Se non esistono creali in
PropertyContractController (o equivalente):

GET /api/properties/{id}/contracts
- Restituisce List<PropertyContractRuleDTO>
  per l'immobile
- tenantId da SecurityUtils per sicurezza

POST /api/properties/{id}/contracts
- @RequestBody PropertyContractRuleDTO
- Inserisce una nuova regola
- Verifica che non esista già
  is_remainder=true se si sta inserendo
  un'altra rimanenza →
  IllegalArgumentException 400
- ResponseEntity.ok(savedRule)

PropertyContractRuleDTO deve avere:
- Integer id
- Integer fkPropertyId
- String tipo         ← commissione_pm,
  provvigione_proprietario,
  pulizie, commissione_ota,
  cambio_biancheria
- String calcMode     ← fisso, percentuale,
  fisso_per_notte,
  percentuale_lordo,
  rimanenza
- BigDecimal valore
- Boolean isRemainder
- Integer ordine
- Boolean attivo
- String fkCanaleOtaId (nullable)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. FRONTEND — propertyApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/propertyApi.ts:

export interface PropertyContractRule {
id?: number
fkPropertyId?: number
tipo: string
calcMode: string
valore: number
isRemainder: boolean
ordine: number
attivo: boolean
fkCanaleOtaId?: number
}

export async function getPropertyContracts(
propertyId: number
): Promise<PropertyContractRule[]>
// GET /api/properties/{id}/contracts

export async function addPropertyContract(
propertyId: number,
rule: PropertyContractRule
): Promise<PropertyContractRule>
// POST /api/properties/{id}/contracts

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. FRONTEND — QuickContractDialog.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/components/
QuickContractDialog.tsx:

Props:
propertyId: number
propertyName: string
open: boolean
onClose: () => void
onSaved: () => void  ← callback dopo salvataggio

Comportamento:
1. Al mount (quando open=true):
    - carica le regole esistenti con
      getPropertyContracts(propertyId)
    - mostra lista regole esistenti
      (tipo, calcMode, valore, isRemainder)
      in una tabella compatta read-only

2. Sezione "Aggiungi regola rimanenza":
    - Titolo: "Configura regola di rimanenza"
    - Spiegazione breve:
      "La regola di rimanenza assorbe il
      residuo del lordo dopo le altre voci.
      Necessaria per il calcolo dello split."

   Form con campi:
    - Tipo: Select
        * commissione_pm →
          "Commissione PM"
        * provvigione_proprietario →
          "Provvigione Proprietario"
          (solo questi due ammessi come rimanenza)
    - Valore %: Input numerico (0-100)
      visibile solo se calcMode != rimanenza
    - calcMode: hidden, impostato
      automaticamente a 'percentuale'
    - isRemainder: hidden, sempre true
    - ordine: hidden, impostato a 99

   Pulsante "Salva regola":
    - chiama addPropertyContract()
    - successo:
        * toast "Regola salvata"
        * ricarica lista regole
        * chiama onSaved()
    - errore 400 → messaggio inline
      (es. "Esiste già una regola di
      rimanenza per questo immobile")

3. Pulsante "Vai ai contratti completi":
    - apre /properties/{propertyId}/contracts
      in nuova tab (target="_blank")
    - non chiude il dialog

4. Pulsante "Chiudi" → onClose()

UI: usa shadcn/ui Dialog già presente
nel progetto. Larghezza dialog: max-w-2xl.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND — ImportBookings.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica lo Step 2 (Anteprima) di
ImportBookings.tsx:

Aggiungi stati:
const [contractDialogOpen,
setContractDialogOpen] = useState(false)
const [contractDialogProperty,
setContractDialogProperty] = useState<{
id: number, name: string
} | null>(null)

Nel backend il warning
"Nessuna voce impostata come rimanenza
per l'immobile" è già presente in
splitWarnings di ogni riga.

Per ogni warning che contiene
"rimanenza" nella colonna Warnings:
- Rendi il testo del warning cliccabile
  (stile link sottolineato, cursore pointer)
- onClick → apri QuickContractDialog
  impostando contractDialogProperty con
  il propertyId e propertyName della riga

NOTA: il propertyId deve essere disponibile
nel BookingImportRowDTO — verifica che
sia già presente come campo. Se non lo è
aggiungilo al DTO e popolalo nel service
durante il preview (è già noto perché
l'immobile è stato trovato).

Aggiungi QuickContractDialog in fondo
al render dello Step 2:
<QuickContractDialog
propertyId={contractDialogProperty?.id}
propertyName={contractDialogProperty?.name}
open={contractDialogOpen}
onClose={() => setContractDialogOpen(false)}
onSaved={() => {
setContractDialogOpen(false)
// mostra toast suggerendo di
// rigenerare l'anteprima
toast.info("Regola salvata —
torna allo step 1 e rigenera
l'anteprima per aggiornare
i warnings")
}}
/>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

Verifica con Reservations_1782985997.xlsx
(headerRow=6, template Airbnb se salvato):
- Step 2: il warning "Nessuna voce
  impostata come rimanenza" deve essere
  cliccabile sulla riga 307/2026
  (Ferrara Vittoria)
- Click → si apre QuickContractDialog
  con nome immobile nel titolo
- Form permette di salvare una regola
  di rimanenza
- Dopo salvataggio compare toast
  con suggerimento di rigenerare

Verifica anche:
curl autenticato
GET /api/properties/{id}/contracts
→ deve restituire le regole esistenti

Riporta output build e verifica curl.