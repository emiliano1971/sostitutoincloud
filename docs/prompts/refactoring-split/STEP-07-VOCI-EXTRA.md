Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- controller/BookingController.java
- service/BookingService.java
- dao/BookingSplitEconomicoDAO.java
- frontend/src/pages/tenant/BookingDetail.tsx
- frontend/src/api/bookingApi.ts
  prima di procedere.

Implementa il CRUD delle voci extra
in booking_split_economico dal
BookingDetail.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/booking/
BookingVoceExtraDTO.java:
- @Data @NoArgsConstructor
  @AllArgsConstructor
- String descrizione  ← obbligatorio
- BigDecimal importo  ← obbligatorio, > 0
- Boolean includeInFatturaPm
  ← default true

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — BookingService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a service/BookingService.java:

BookingDetailDTO aggiungiVoceExtra(
Integer tenantId,
Integer bookingId,
BookingVoceExtraDTO dto)

1. Verifica appartenenza al tenant
2. Verifica assenza documenti:
   SE esistono documenti emessi →
   throw IllegalStateException(
   "Impossibile aggiungere voci:
   documenti fiscali già emessi")
3. Validazioni:
    - descrizione non blank
    - importo > 0
4. Calcola ordinamento:
   int ordine = splitEconomicoDAO
   .findByBookingId(bookingId)
   .stream()
   .mapToInt(r -> r.getOrdinamento())
   .max()
   .orElse(0) + 10
   ← si aggiunge in fondo
5. Inserisci riga:
   BookingSplitEconomico.builder()
   .fkBookingId(bookingId)
   .fkTenantId(tenantId)
   .fkPropertyContractRuleId(null)
   .tipoVoce("extra")
   .descrizione(dto.getDescrizione()
   .trim())
   .importo(dto.getImporto())
   .aliquotaIva(new BigDecimal("22.00"))
   .includeInFatturaPm(
   dto.getIncludeInFatturaPm()
   != null
   ? dto.getIncludeInFatturaPm()
   : true)
   .ordinamento(ordine)
   .source("manuale")
   .createdBy(
   SecurityUtils.getCurrentUtenteId())
   .updatedBy(
   SecurityUtils.getCurrentUtenteId())
   .build()
6. Aggiorna total_costi_pm:
   bookingDAO.updateTotalCostiPm(
   bookingId, tenantId,
   splitEconomicoDAO
   .sumImportoByBookingId(bookingId))
7. Log INFO "BookingService
   .aggiungiVoceExtra() -
   bookingId={} descrizione={}"
8. Return findById(tenantId, bookingId)

BookingDetailDTO aggiornaVoceExtra(
Integer tenantId,
Integer bookingId,
Integer rigaId,
BookingVoceExtraDTO dto)

1. Verifica appartenenza al tenant
2. Verifica assenza documenti
3. Carica riga:
   BookingSplitEconomico riga =
   splitEconomicoDAO.findById(rigaId)
   .orElseThrow(...)
   Verifica che fk_booking_id == bookingId
   Verifica che tipo_voce == 'extra'
   ← solo le voci extra sono modificabili
   manualmente; le voci calcolate
   si aggiornano con Ricalcola
4. Aggiorna riga:
   riga.setDescrizione(
   dto.getDescrizione().trim())
   riga.setImporto(dto.getImporto())
   if (dto.getIncludeInFatturaPm()
   != null)
   riga.setIncludeInFatturaPm(
   dto.getIncludeInFatturaPm())
   riga.setUpdatedBy(
   SecurityUtils.getCurrentUtenteId())
   splitEconomicoDAO.update(riga)
5. Aggiorna total_costi_pm
6. Log INFO
7. Return findById(tenantId, bookingId)

BookingDetailDTO eliminaVoceExtra(
Integer tenantId,
Integer bookingId,
Integer rigaId)

1. Verifica appartenenza al tenant
2. Verifica assenza documenti
3. Carica riga e verifica
   tipo_voce == 'extra'
4. Soft delete:
   splitEconomicoDAO.softDelete(
   rigaId, tenantId,
   SecurityUtils.getCurrentUtenteId())
5. Aggiorna total_costi_pm
6. Log INFO
7. Return findById(tenantId, bookingId)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — Controller
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/BookingController.java:

POST /api/bookings/{id}/split/extra
- @RequestBody BookingVoceExtraDTO
- tenantId da SecurityUtils
- chiama bookingService
  .aggiungiVoceExtra()
- 201 BookingDetailDTO
- 400 se validazione fallisce
- 400 se documenti emessi
- Log INFO

PATCH /api/bookings/{id}/split/{rigaId}
- @RequestBody BookingVoceExtraDTO
- chiama bookingService
  .aggiornaVoceExtra()
- 200 BookingDetailDTO
- 400 se non è voce extra
- 404 se riga non trovata
- Log INFO

DELETE /api/bookings/{id}/split/{rigaId}
- chiama bookingService
  .eliminaVoceExtra()
- 200 BookingDetailDTO
- 400 se non è voce extra
- 404 se riga non trovata
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND — bookingApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi:

export interface VoceExtraRequest {
descrizione: string
importo: number
includeInFatturaPm?: boolean
}

export async function aggiungiVoceExtra(
bookingId: number,
data: VoceExtraRequest
): Promise<BookingDetail>
// POST /api/bookings/{id}/split/extra

export async function aggiornaVoceExtra(
bookingId: number,
rigaId: number,
data: VoceExtraRequest
): Promise<BookingDetail>
// PATCH /api/bookings/{id}/split/{rigaId}

export async function eliminaVoceExtra(
bookingId: number,
rigaId: number
): Promise<BookingDetail>
// DELETE /api/bookings/{id}/split/{rigaId}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. FRONTEND — BookingDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi stati:
const [showAggiungiVoce, setShowAggiungiVoce]
= useState(false)
const [editingRigaId, setEditingRigaId]
= useState<number | null>(null)
const [voceForm, setVoceForm] = useState({
descrizione: '',
importo: '',
includeInFatturaPm: true
})

── A. Pulsante "+ Aggiungi voce" ────────
Dopo le righe split esistenti
(solo se !hasDocuments):

<button
onClick={() => {
setShowAggiungiVoce(true)
setVoceForm({
descrizione: '',
importo: '',
includeInFatturaPm: true
})
}}
className="flex items-center gap-1
text-xs text-primary
hover:text-primary/80 mt-2"
>
  <Plus className="h-3 w-3" />
  Aggiungi voce
</button>

── B. Form inline aggiunta/modifica ─────
Mostra quando showAggiungiVoce = true
o editingRigaId != null:

<div className="border rounded-md
  p-3 mt-2 space-y-2 bg-muted/30">
  <div className="flex gap-2">
    <Input
      placeholder="Descrizione voce
        (es. Parcheggio)"
      value={voceForm.descrizione}
      onChange={e => setVoceForm({
        ...voceForm,
        descrizione: e.target.value
      })}
      className="flex-1 h-7 text-xs"
    />
    <Input
      type="number"
      placeholder="€"
      value={voceForm.importo}
      onChange={e => setVoceForm({
        ...voceForm,
        importo: e.target.value
      })}
      className="w-24 h-7 text-xs"
      min="0.01"
      step="0.01"
    />
  </div>
  <div className="flex items-center
    justify-between">
    <label className="flex items-center
      gap-2 text-xs
      text-muted-foreground cursor-pointer">
      <Switch
        checked={voceForm
          .includeInFatturaPm}
        onCheckedChange={v =>
          setVoceForm({
            ...voceForm,
            includeInFatturaPm: v
          })}
        className="scale-75"
      />
      Includi in fattura PM
    </label>
    <div className="flex gap-1">
      <button
        onClick={handleSalvaVoce}
        disabled={isUpdatingSplit
          || !voceForm.descrizione.trim()
          || !voceForm.importo}
        className="text-xs px-2 py-1
          bg-primary text-primary-foreground
          rounded disabled:opacity-50"
      >
        Salva
      </button>
      <button
        onClick={() => {
          setShowAggiungiVoce(false)
          setEditingRigaId(null)
        }}
        className="text-xs px-2 py-1
          border rounded"
      >
        Annulla
      </button>
    </div>
  </div>
</div>

── C. Matita e cestino su voci extra ────
Per ogni riga con tipoVoce === 'extra'
aggiungi accanto all'importo:

<button onClick={() => {
setEditingRigaId(r.id)
setShowAggiungiVoce(false)
setVoceForm({
descrizione: r.descrizione,
importo: String(r.importo),
includeInFatturaPm:
r.includeInFatturaPm
})
}}>
<Pencil className="h-3 w-3
text-muted-foreground" />
</button>
<button onClick={() =>
handleEliminaVoce(r.id)}>
<Trash2 className="h-3 w-3
text-destructive" />
</button>

── D. Handler ────────────────────────────

const handleSalvaVoce = async () => {
setIsUpdatingSplit(true)
try {
const data = {
descrizione:
voceForm.descrizione.trim(),
importo: parseFloat(
voceForm.importo),
includeInFatturaPm:
voceForm.includeInFatturaPm
}
const updated = editingRigaId
? await aggiornaVoceExtra(
booking.id,
editingRigaId, data)
: await aggiungiVoceExtra(
booking.id, data)
setBooking(updated)
setShowAggiungiVoce(false)
setEditingRigaId(null)
toast({ title: editingRigaId
? "Voce aggiornata"
: "Voce aggiunta" })
} catch (e: any) {
toast({
title: "Errore",
description: e.message,
variant: "destructive"
})
} finally {
setIsUpdatingSplit(false)
}
}

const handleEliminaVoce = async (
rigaId: number) => {
if (!confirm(
"Eliminare questa voce?")) return
setIsUpdatingSplit(true)
try {
const updated = await
eliminaVoceExtra(
booking.id, rigaId)
setBooking(updated)
toast({ title: "Voce eliminata" })
} catch (e: any) {
toast({
title: "Errore",
description: e.message,
variant: "destructive"
})
} finally {
setIsUpdatingSplit(false)
}
}

Importa Plus, Trash2 da lucide-react
se non già importati.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package
cd frontend && npm run build
npm run typecheck

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena2026"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Trova booking senza documenti
BOOKING_ID=$(curl -s \
-H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings" \
| python3 -c "
import sys,json
bs=[b for b in json.load(sys.stdin)
if b.get('statoPrenotazione')
not in ['doc_issued','settled']]
print(bs[0]['id'] if bs else 'NESSUNO')")
echo "Booking di test: $BOOKING_ID"

# Aggiungi voce extra
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"descrizione": "Parcheggio",
"importo": 20.00,
"includeInFatturaPm": true
}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings/$BOOKING_ID/split/extra" \
| python3 -m json.tool \
| grep -A 5 '"tipoVoce": "extra"'

# Modifica voce extra
RIGA_ID=$(curl -s \
-H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings/$BOOKING_ID" \
| python3 -c "
import sys,json
b=json.load(sys.stdin)
extra=[r for r in
b.get('righeSplit',[])
if r['tipoVoce']=='extra']
print(extra[0]['id'] if extra else 0)")
echo "Riga extra ID: $RIGA_ID"

curl -s -X PATCH \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"descrizione": "Parcheggio coperto",
"importo": 25.00,
"includeInFatturaPm": true
}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings/$BOOKING_ID/split/$RIGA_ID" \
| python3 -m json.tool \
| grep -A 3 '"tipoVoce": "extra"'

# Elimina voce extra
curl -s -X DELETE \
-H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings/$BOOKING_ID/split/$RIGA_ID" \
| python3 -m json.tool \
| grep '"righeSplit"'

Verifica che:
- POST aggiunge la voce con
  tipoVoce='extra' e source='manuale'
- total_costi_pm si aggiorna
- PATCH modifica descrizione e importo
- DELETE rimuove la voce (soft delete)
- Booking con documenti → 400

Riporta output build e curl.