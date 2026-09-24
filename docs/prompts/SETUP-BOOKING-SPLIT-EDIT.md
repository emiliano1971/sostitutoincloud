Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- frontend/src/pages/tenant/BookingDetail.tsx
- frontend/src/api/bookingApi.ts
- controller/BookingController.java
- service/BookingService.java
- dao/BookingDAO.java
  prima di procedere.

Aggiungi nella sezione Split Economico
di BookingDetail.tsx:
1. Switch tourist_tax_included_in_gross
2. Campo editabile commissione OTA
   con percentuale calcolata in tempo reale
   Entrambi ricalcolano lo split e sono
   bloccati se esistono documenti fiscali.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/booking/BookingUpdateSplitDTO.java:
- Boolean touristTaxIncludedInGross
  ← nullable, se null non cambia
- BigDecimal otaCommissionOverride
  ← nullable, se null usa regole contratto

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — BookingDAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/BookingDAO.java:

void updateSplit(
Integer id,
Integer tenantId,
Boolean touristTaxIncludedInGross,
BigDecimal touristTaxAmount,
BigDecimal otaCommission,
BigDecimal cleaning,
BigDecimal pmFee,
BigDecimal ownerNet,
BigDecimal withholding)

UPDATE booking SET
tourist_tax_included_in_gross = ?,
tourist_tax_amount = ?,
ota_commission_amount = ?,
cleaning_amount = ?,
pm_fee_amount = ?,
owner_net_amount = ?,
withholding_amount = ?,
updated_at = NOW()
WHERE id = ?
AND fk_tenant_id = ?

Log INFO "BookingDAO.updateSplit()
- id={} tenantId={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — BookingService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a service/BookingService.java:

BookingDetailDTO updateSplit(
Integer tenantId,
Integer bookingId,
BookingUpdateSplitDTO dto)

1. Carica booking verificando
   appartenenza al tenant

2. Verifica assenza documenti fiscali:
   SE fiscalDocumentDAO
   .findByBookingId(bookingId)
   non vuota:
   throw IllegalStateException(
   "Impossibile modificare: esistono
   documenti fiscali emessi per
   questa prenotazione")

3. Determina valori da usare:
   boolean taxIncluded =
   dto.getTouristTaxIncludedInGross()
   != null
   ? dto.getTouristTaxIncludedInGross()
   : booking.getTouristTaxIncludedInGross()

   BigDecimal otaOverride =
   dto.getOtaCommissionOverride()
   ← null = usa regole contratto

4. Ricalcola tassa:
   BigDecimal nuovaTassa =
   touristTaxService.calcolaPerBooking(
   tenantId,
   property.getCity(),
   booking.getCheckinDate(),
   booking.getNights(),
   booking.getGuests(),
   false) ← calcola sempre

5. Ricalcola split:
   BigDecimal grossPerCalcolo =
   taxIncluded
   ? booking.getGrossAmount()
   .subtract(nuovaTassa)
   : booking.getGrossAmount()

   ContrattoCalcoloResult calcolo =
   contrattoCalcolatore.calcola(
   tenantId,
   booking.getFkPropertyId(),
   booking.getFkCanaleOtaId(),
   grossPerCalcolo,
   otaOverride,
   booking.getNights(),
   booking.getGuests())

6. Aggiorna DB:
   bookingDAO.updateSplit(
   bookingId, tenantId,
   taxIncluded,
   nuovaTassa,
   calcolo.getOtaCommissionAmount(),
   calcolo.getCleaningAmount(),
   calcolo.getPmFeeAmount(),
   calcolo.getOwnerNetAmount(),
   calcolo.getWithholdingAmount())

7. aggiornaStato(bookingId)

8. Log INFO "BookingService.updateSplit()
    - id={} taxIncluded={} otaOverride={}"

9. Return findById(tenantId, bookingId)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BACKEND — Controller
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/BookingController.java:

PATCH /api/bookings/{id}/split
- @RequestBody BookingUpdateSplitDTO
- tenantId da SecurityUtils
- chiama bookingService.updateSplit()
- 200 BookingDetailDTO
- 400 se documenti emessi
  (catch IllegalStateException)
- 404 se non trovato
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. FRONTEND — bookingApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/bookingApi.ts:

export interface BookingUpdateSplitRequest {
touristTaxIncludedInGross?: boolean
otaCommissionOverride?: number
}

export async function updateBookingSplit(
id: number,
data: BookingUpdateSplitRequest
): Promise<BookingDetail>
// PATCH /api/bookings/{id}/split

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. FRONTEND — BookingDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi stati:
const [isUpdatingSplit, setIsUpdatingSplit]
= useState(false)
const [editingOta, setEditingOta]
= useState(false)
const [otaValue, setOtaValue]
= useState('')

Verifica documenti emessi:
const hasDocuments =
booking.statoPrenotazione === 'doc_issued'

Helper per chiamare updateSplit:
const handleUpdateSplit = async (
patch: BookingUpdateSplitRequest) => {
setIsUpdatingSplit(true)
try {
const updated = await
updateBookingSplit(booking.id, patch)
setBooking(updated)
toast({ title: "Split ricalcolato" })
} catch (e: any) {
toast({
title: "Errore",
description: e.message,
variant: "destructive"
})
} finally {
setIsUpdatingSplit(false)
setEditingOta(false)
}
}

── A. Switch tassa inclusa ──────────────
Posiziona PRIMA delle voci di costo
nella sezione Split Economico:

<div className="flex items-center
  justify-between py-2 border-b mb-2">
  <div>
    <span className="text-sm font-medium">
      Tassa soggiorno inclusa nel lordo
    </span>
    {hasDocuments && (
      <p className="text-xs
        text-muted-foreground">
        Non modificabile: documenti
        fiscali emessi
      </p>
    )}
  </div>
  <Switch
    checked={booking
      .touristTaxIncludedInGross ?? false}
    disabled={hasDocuments
      || isUpdatingSplit}
    onCheckedChange={(val) =>
      handleUpdateSplit({
        touristTaxIncludedInGross: val
      })}
  />
</div>

── B. Commissione OTA editabile ─────────
Nella riga "Commissione OTA" dello split:

Calcola percentuale visualizzata:
const otaPct = booking.grossAmount > 0
? ((booking.otaCommissionAmount ?? 0)
/ booking.grossAmount * 100)
.toFixed(1)
: '0.0'

SE !editingOta:
  <div className="flex items-center gap-2">
    <span className="text-destructive">
      - €{formatAmount(
        booking.otaCommissionAmount)}
    </span>
    <span className="text-xs
      text-muted-foreground">
      ({otaPct}%)
    </span>
    {!hasDocuments && (
      <button
        onClick={() => {
          setEditingOta(true)
          setOtaValue(String(
            booking.otaCommissionAmount
            ?? 0))
        }}
        className="text-muted-foreground
          hover:text-foreground"
      >
        <Pencil className="h-3 w-3" />
      </button>
    )}
  </div>

SE editingOta:
Calcola percentuale in tempo reale:
const otaEditPct =
booking.grossAmount > 0
? (parseFloat(otaValue || '0')
/ booking.grossAmount * 100)
.toFixed(1)
: '0.0'

  <div className="flex items-center gap-1">
    <span>- € </span>
    <Input
      type="number"
      value={otaValue}
      onChange={e =>
        setOtaValue(e.target.value)}
      className="w-24 h-6 text-xs"
      min="0"
      step="0.01"
      autoFocus
    />
    <span className="text-xs
      text-muted-foreground">
      ({otaEditPct}%)
    </span>
    <button
      onClick={() =>
        handleUpdateSplit({
          otaCommissionOverride:
            parseFloat(otaValue)
        })}
      disabled={isUpdatingSplit}
    >
      <Check className="h-3 w-3
        text-green-600" />
    </button>
    <button
      onClick={() => setEditingOta(false)}>
      <X className="h-3 w-3
        text-destructive" />
    </button>
  </div>

Importa Check, X, Pencil da lucide-react
se non già importati.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package
cd frontend && npm run build &&
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
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings" \
| python3 -m json.tool \
| grep -E '"id"|"statoPrenotazione"'

# Test switch tassa inclusa
curl -s -X PATCH \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"touristTaxIncludedInGross": true}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings/{ID}/split" \
| python3 -m json.tool \
| grep -E '"tourist|owner|withholding"'

# Ripristina
curl -s -X PATCH \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"touristTaxIncludedInGross": false}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings/{ID}/split" \
| python3 -m json.tool \
| grep -E '"tourist|owner|withholding"'

# Test override commissione OTA
curl -s -X PATCH \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"otaCommissionOverride": 50.00}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings/{ID}/split" \
| python3 -m json.tool \
| grep -E '"ota|owner|withholding"'

# Test blocco con documenti emessi
curl -s -X PATCH \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"touristTaxIncludedInGross": true}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings/{ID_CON_DOC}/split" \
| python3 -m json.tool

# Ripristina OTA originale
curl -s -X PATCH \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"otaCommissionOverride": null}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings/{ID}/split" \
| python3 -m json.tool \
| grep '"otaCommissionAmount"'

Verifica che:
- Switch tassa → split ricalcolato
  con/senza scorporo
- Override OTA → split ricalcolato
  con nuova commissione e percentuale
- null come otaCommissionOverride →
  usa regole contratto
- Booking con documenti → 400
- Switch disabilitato in UI per
  booking con documenti

Riporta output build e curl.