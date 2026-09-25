Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- service/ContrattoCalcolatoreService.java
- service/BookingImportService.java
- service/BookingService.java
- dto/ContrattoCalcoloResult.java
  (o dove è definito il risultato
  del calcolatore)
- dao/BookingSplitEconomicoDAO.java
  prima di procedere.

Implementa il popolamento di
booking_split_economico dopo ogni
creazione di booking.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
STRATEGIA
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Il calcolatore rimane stateless.
Chi chiama calcola() è responsabile
di scrivere le righe split.

Le righe da creare per ogni booking:

1. Commissione OTA (se > 0)
   tipo_voce: 'commissione_ota'
   descrizione: nome canale OTA
   es. "Commissione Booking.com"
   importo: otaCommissionAmount
   aliquota_iva: 22.00
   include_in_fattura_pm: true
   ordinamento: 10
   source: 'calcolato' o 'import'
   se viene dal file

2. Pulizie (se > 0)
   tipo_voce: 'pulizie'
   descrizione: "Pulizie"
   o descrizione dalla regola
   contratto se disponibile
   importo: cleaningAmount
   aliquota_iva: 22.00
   include_in_fattura_pm: true
   ordinamento: 20
   source: 'calcolato'

3. Commissione PM (se > 0)
   tipo_voce: 'commissione_pm'
   descrizione: "Commissione PM"
   o descrizione dalla regola
   importo: pmFeeAmount
   aliquota_iva: 22.00
   include_in_fattura_pm: true
   ordinamento: 30
   source: 'calcolato'

4. Tassa soggiorno (se > 0)
   tipo_voce: 'tassa_soggiorno'
   descrizione: "Tassa di soggiorno"
   importo: touristTaxAmount
   aliquota_iva: 0.00
   include_in_fattura_pm: false
   ← non va in fattura PM
   ordinamento: 40
   source: 'calcolato'

NON creare riga per netto proprietario
e ritenuta — non sono voci di costo
della fattura PM.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. ContrattoCalcoloResult
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica se ContrattoCalcoloResult
espone già fkPropertyContractRuleId
per le singole voci (OTA, pulizie, PM).

Se non espone le FK delle regole
usate, aggiungile:
Integer fkRegolaPmId
← id della regola commissione_pm
Integer fkRegolaOtaId
← id della regola commissione_ota
Integer fkRegolaCleaningId
← id della regola pulizie/biancheria

Queste FK servono per popolare
fk_property_contract_rule_id
nelle righe split.

Se il risultato ha già un modo per
risalire alle regole usate,
usare quello invece.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. Helper di scrittura split
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea un metodo privato condiviso
in BookingService.java:

private void popolaSplitEconomico(
Integer bookingId,
Integer tenantId,
ContrattoCalcoloResult calcolo,
BigDecimal touristTaxAmount,
Boolean touristTaxIncludedInGross,
String canaleName,
Integer utenteId,
String source)

Logica:
1. Elimina righe esistenti con
   splitEconomicoDAO.deleteByBookingId(
   bookingId)
   ← reset completo prima di reinserire

2. Inserisci riga OTA se
   calcolo.getOtaCommissionAmount()
   != null && > 0:
   BookingSplitEconomico.builder()
   .fkBookingId(bookingId)
   .fkTenantId(tenantId)
   .fkPropertyContractRuleId(
   calcolo.getFkRegolaOtaId())
   .tipoVoce("commissione_ota")
   .descrizione("Commissione "
   + (canaleName ?? "OTA"))
   .importo(calcolo
   .getOtaCommissionAmount())
   .aliquotaIva(new BigDecimal("22.00"))
   .includeInFatturaPm(true)
   .ordinamento(10)
   .source(source)
   .createdBy(utenteId)
   .updatedBy(utenteId)
   .build()

3. Inserisci riga Pulizie se > 0:
   stesso pattern, ordinamento 20
   descrizione: "Pulizie"
   fkPropertyContractRuleId:
   calcolo.getFkRegolaCleaningId()

4. Inserisci riga PM se > 0:
   ordinamento 30
   descrizione: "Commissione PM"
   fkPropertyContractRuleId:
   calcolo.getFkRegolaPmId()

5. Inserisci riga tassa soggiorno
   se touristTaxAmount != null && > 0:
   tipo_voce: 'tassa_soggiorno'
   includeInFatturaPm: false
   ordinamento: 40
   aliquota_iva: 0

6. Aggiorna total_costi_pm sul booking:
   BigDecimal totale =
   splitEconomicoDAO
   .sumImportoByBookingId(bookingId)
   bookingDAO.updateTotalCostiPm(
   bookingId, tenantId, totale)

   Aggiungi a BookingDAO.java:
   void updateTotalCostiPm(
   Integer bookingId,
   Integer tenantId,
   BigDecimal totalCostiPm)
   UPDATE booking SET
   total_costi_pm = ?,
   updated_at = NOW()
   WHERE id = ?
   AND fk_tenant_id = ?

7. Log INFO "BookingService
   .popolaSplitEconomico() -
   bookingId={} righe={} totale={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BookingImportService.confirm()
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In BookingImportService.java
inietta BookingSplitEconomicoDAO
e BookingService nel costruttore
(o chiama il metodo helper di
BookingService se accessibile).

Dopo bookingDAO.insert(booking)
e agiornaStato():

popolaSplitEconomico(
saved.getId(),
tenantId,
calcolo,
touristTax,
tassaInclusa,
canale != null
? canale.getNome() : null,
null,  ← utenteId non disponibile
in import automatico
tassaInclusa != null
&& row.getTouristTaxIncluded()
!= null
? "import" : "calcolato")

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BookingService.createManuale()
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In BookingService.java
dopo aggiornaStato(saved.getId()):

String canaleName = dto
.getFkCanaleOtaId() != null
? canaleOtaDAO.findById(
dto.getFkCanaleOtaId())
.map(CanaleOta::getNome)
.orElse("OTA")
: null;

popolaSplitEconomico(
saved.getId(),
tenantId,
calcolo,
touristTax,
dto.isTouristTaxIncludedInGross(),
canaleName,
SecurityUtils.getCurrentUtenteId(),
"calcolato")

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BookingService.updateSplit()
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In BookingService.updateSplit()
dopo aver aggiornato il booking
con bookingDAO.updateSplit():

Chiama popolaSplitEconomico()
con i nuovi valori calcolati.

Questo garantisce che ogni volta
che lo split viene ricalcolato
(cambio flag tassa, override OTA)
le righe vengano aggiornate.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena2026"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Inserimento manuale di test
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"fkPropertyId": 6,
"fkCanaleOtaId": 2,
"checkinDate": "2026-12-10",
"checkoutDate": "2026-12-14",
"guests": 2,
"grossAmount": 380.00,
"guestName": "Test Split"
}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings" \
| python3 -m json.tool \
| grep '"id"'

# Verifica righe split create
psql -U sostitutoincloud \
-d sostitutoincloud \
-h localhost -c "
SELECT tipo_voce, descrizione,
importo, aliquota_iva,
include_in_fattura_pm,
ordinamento, source
FROM booking_split_economico
WHERE fk_booking_id = {ID}
AND deleted_at IS NULL
ORDER BY ordinamento;"

# Verifica total_costi_pm sul booking
psql -U sostitutoincloud \
-d sostitutoincloud \
-h localhost -c "
SELECT id, gross_amount,
total_costi_pm,
owner_net_amount
FROM booking WHERE id = {ID};"

# Cleanup
psql -U sostitutoincloud \
-d sostitutoincloud \
-h localhost -c "
DELETE FROM booking
WHERE id = {ID}
AND fk_tenant_id = 1;"

Verifica che:
- Righe split create correttamente
  per OTA, pulizie, PM
- total_costi_pm = OTA + pulizie + PM
- Cascade DELETE funziona
  (righe split eliminate con booking)

Riporta output build e query.