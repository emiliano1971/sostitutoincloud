Leggi il file CLAUDE.md e i file esistenti:
- dto/settlement/SettlementBookingDTO.java
- service/SettlementService.java
- frontend/src/pages/tenant/SettlementDetail.tsx
- frontend/src/api/settlementApi.ts
  prima di procedere.

Aggiungi le voci di costo mancanti alla
pagina dettaglio liquidazione in modo che
il calcolo dal lordo al netto sia
completamente trasparente.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — SettlementBookingDTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dto/settlement/SettlementBookingDTO.java:

BigDecimal otaCommissionAmount   ← booking.otaCommissionAmount
BigDecimal cleaningAmount        ← booking.cleaningAmount
BigDecimal pmFeeAmount           ← booking.pmFeeAmount
BigDecimal ivaAmount             ← booking.pmFeeAmount * 0.22 / 1.22
(IVA scorporata dalla provvigione PM)
arrotonda a 2 decimali

Tutti nullable — usa BigDecimal.ZERO
come fallback se null.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — SettlementService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica service/SettlementService.java
nel metodo findById():

Nel blocco che costruisce SettlementBookingDTO
per ogni booking, aggiungi la popolazione
dei nuovi campi dal booking già caricato:

.otaCommissionAmount(nullSafe(
booking.getOtaCommissionAmount()))
.cleaningAmount(nullSafe(
booking.getCleaningAmount()))
.pmFeeAmount(nullSafe(
booking.getPmFeeAmount()))
.ivaAmount(calcolaIva(
booking.getPmFeeAmount()))

Aggiungi metodo privato:
private BigDecimal nullSafe(BigDecimal val) {
return val != null ? val : BigDecimal.ZERO;
}

private BigDecimal calcolaIva(BigDecimal pmFee) {
if (pmFee == null ||
pmFee.compareTo(BigDecimal.ZERO) == 0)
return BigDecimal.ZERO;
return pmFee
.multiply(new BigDecimal("0.22"))
.divide(new BigDecimal("1.22"), 2,
RoundingMode.HALF_UP);
}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. FRONTEND — settlementApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a SettlementBookingItem:
otaCommissionAmount: number
cleaningAmount: number
pmFeeAmount: number
ivaAmount: number

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND — SettlementDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Ridisegna la tabella con tutte le voci
in ordine logico dal lordo al netto:

Colonne:
# | ID Prenotazione | Immobile
| Check-in | Check-out | Notti
| Lordo €
| Comm. OTA €   (in rosso se > 0)
| Pulizie €     (in rosso se > 0)
| Provv. PM €   (in rosso se > 0)
| di cui IVA €  (grigio, informativo)
| Canone €      (= ownerNetAmount, bold)
| Bollo €       (in rosso se > 0)
| Ritenuta €    (in rosso se > 0)
| Netto €       (verde bold)

Separatore visivo tra le colonne:
- Raggruppa con un sottotitolo
  nell'header a due livelli se il
  componente UI lo supporta,
  altrimenti usa colori di sfondo
  alternati per gruppo:
    * Lordo: sfondo bianco
    * Voci costo: sfondo rosso
      chiarissimo (#fff5f5)
    * Canone: sfondo bianco
    * Deduzioni fiscali: sfondo
      arancione chiarissimo (#fffaf0)
    * Netto: sfondo verde
      chiarissimo (#f0fff4)

Riga TOTALE in fondo:
TOTALE | | | | | {lordo} | {ota}
| {pulizie} | {pm} | {iva}
| {canone} | {bollo} | {ritenuta}
| {netto}
Tutti i totali in bold.
Le voci costo in rosso bold.
Netto in verde bold.

Card riepilogo sotto la tabella
(aggiorna le 4 card esistenti + aggiungi):

Lordo totale    Comm. OTA
€890,30         -€xx,xx

Pulizie         Provv. PM (+ IVA)
-€xx,xx         -€xx,xx (di cui IVA €xx)

Bollo totale    Ritenute totali
-€4,00          -€106,62

──────────────────────────────
Netto da pagare
€401,10  (grande, verde bold)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/settlements/3" \
| python3 -m json.tool \
| grep -E '"otaCommission|cleaning|pmFee\
|ivaAmount|ownerNet|grossAmount"'

Verifica per ogni booking che:
lordo − commOTA − pulizie − provvPM
= ownerNetAmount (canone)

canone − bollo − ritenuta = netto

Riporta output build e verifica.