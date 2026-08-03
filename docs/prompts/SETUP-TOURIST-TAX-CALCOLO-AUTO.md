Leggi il file CLAUDE.md e i file esistenti:
- service/ContrattoCalcolatoreService.java
- service/TouristTaxCalculatorService.java
- dao/RegolaTassaSoggiornoDAO.java
- dao/PropertyDAO.java
prima di procedere.

Implementa il calcolo automatico della tassa
di soggiorno nello split economico del booking,
cercando la regola per comune della property.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. RegolaTassaSoggiornoDAO — nuovo metodo
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/RegolaTassaSoggiornoDAO.java:

Optional<RegolaTassaSoggiorno>
findAttivaByComune(Integer tenantId,
    String comune, LocalDate dataRiferimento)
- SELECT * FROM regola_tassa_soggiorno
  WHERE fk_tenant_id = ?
    AND LOWER(comune) = LOWER(?)
    AND attivo = true
    AND valida_dal <= ?
    AND (valida_al IS NULL OR valida_al >= ?)
  ORDER BY valida_dal DESC
  LIMIT 1
- parametri: tenantId, comune,
  dataRiferimento, dataRiferimento
- Log DEBUG "RegolaTassaSoggiornoDAO
  .findAttivaByComune() - comune={}
  data={} trovata={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. TouristTaxService — metodo calcola
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a service/TouristTaxService.java:

BigDecimal calcolaPerBooking(
    Integer tenantId,
    String comune,
    LocalDate checkinDate,
    Integer nights,
    Integer guests,
    Boolean touristTaxIncludedInGross)

Logica:
1. Se touristTaxIncludedInGross = true
   → return BigDecimal.ZERO
   (già inclusa nel lordo OTA)

2. Cerca regola:
   regolaDAO.findAttivaByComune(
     tenantId, comune, checkinDate)

3. Se regola non trovata
   → return BigDecimal.ZERO
   Log DEBUG "Nessuna regola tassa
   soggiorno per comune={}"

4. Carica dettaglio regola con fasce/
   stagioni/zone:
   findById(tenantId, regola.getId())

5. Costruisci lista età ospiti:
   List<Integer> guestAges con `guests`
   elementi tutti a 18 (età adulta default
   — non abbiamo l'età reale degli ospiti
   nei booking OTA)

6. Chiama TouristTaxCalculatorService
   .calcola(regola, nights, checkinDate,
   null, guestAges)

7. Ritorna result.getTotal()

Log INFO "TouristTaxService
  .calcolaPerBooking() - comune={}
  nights={} guests={} tassa={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. ContrattoCalcolatoreService
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica service/ContrattoCalcolatoreService.java:

Aggiungi TouristTaxService al costruttore.

Nel metodo che calcola lo split economico,
dopo aver calcolato ownerNetAmount
e withholdingAmount, aggiungi:

// Calcola tassa soggiorno se non inclusa
Property property = propertyDAO
  .findById(booking.getFkPropertyId())
  .orElseThrow();

BigDecimal touristTax = touristTaxService
  .calcolaPerBooking(
    tenantId,
    property.getCity(),
    booking.getCheckinDate(),
    booking.getNights(),
    booking.getGuests(),
    booking.getTouristTaxIncludedInGross()
  );

// Aggiorna booking se tassa cambiata
if (touristTax.compareTo(
    booking.getTouristTaxAmount()) != 0) {
  bookingDAO.updateTouristTax(
    booking.getId(), touristTax);
  booking.setTouristTaxAmount(touristTax);
}

Aggiungi a dao/BookingDAO.java:
void updateTouristTax(Integer id,
    BigDecimal touristTaxAmount)
- UPDATE booking
  SET tourist_tax_amount = ?,
      updated_at = NOW()
  WHERE id = ?
- Log INFO "BookingDAO.updateTouristTax()
  - id={} amount={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BookingService — ricalcola alla lettura
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica service/BookingService.java
nel metodo toDetailDTO():

Dopo aver popolato splitEconomico,
se booking.touristTaxAmount == 0
E booking.touristTaxIncludedInGross = false:
  chiama touristTaxService.calcolaPerBooking()
  e aggiorna il booking con updateTouristTax()
  se il risultato è > 0

Questo ricalcola la tassa sui booking
esistenti già importati senza tassa.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. TEST
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Verifica split economico booking
# con property in comune con regola
# tassa soggiorno configurata
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings/{id_booking_con_property_venezia}" \
| python3 -m json.tool \
| grep -E '"touristTax|"city|"comune"'

Verifica che:
- touristTaxAmount > 0 per booking
  in comuni con regola configurata
- touristTaxAmount = 0 per booking
  in comuni senza regola
- touristTaxIncludedInGross = true
  → touristTaxAmount = 0 sempre

Riporta output build e curl.