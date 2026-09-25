Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- service/BookingService.java
  nel metodo toDetailDTO()
- service/DocumentGenerationService.java
- dto/booking/BookingDetailDTO.java
- dto/booking/SplitEconomicoDTO.java
  (o dove è definito lo split nel DTO)
- dao/BookingSplitEconomicoDAO.java
  prima di procedere.

Aggiorna la lettura dello split economico
per usare booking_split_economico
invece dei campi flat del booking.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
STRATEGIA
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

- BookingsList → continua a usare
  i campi flat del booking
  (gross_amount, owner_net_amount ecc.)
  nessuna modifica alle query di lista
- BookingDetail → legge le righe
  da booking_split_economico
- DocumentGenerationService → legge
  le righe per calcolare i totali
  della fattura PM

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BookingDetailDTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dto/booking/BookingDetailDTO.java:

List<BookingSplitEconomicoDTO> righeSpliit
← lista righe da booking_split_economico
ordinate per ordinamento
← nome campo: righeSplit

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BookingService.toDetailDTO()
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In BookingService.toDetailDTO()
aggiungi il caricamento delle righe split:

List<BookingSplitEconomico> righeSplit =
splitEconomicoDAO
.findByBookingId(b.getId())

Mappa le righe in DTO:
List<BookingSplitEconomicoDTO> righeSplitDTO =
righeSplit.stream()
.map(r -> BookingSplitEconomicoDTO
.builder()
.id(r.getId())
.fkBookingId(r.getFkBookingId())
.fkPropertyContractRuleId(
r.getFkPropertyContractRuleId())
.tipoVoce(r.getTipoVoce())
.descrizione(r.getDescrizione())
.importo(r.getImporto())
.aliquotaIva(r.getAliquotaIva())
.includeInFatturaPm(
r.getIncludeInFatturaPm())
.ordinamento(r.getOrdinamento())
.source(r.getSource())
.createdAt(r.getCreatedAt())
.updatedAt(r.getUpdatedAt())
.build())
.collect(Collectors.toList())

Aggiungi al builder del DTO:
.righeSplit(righeSplitDTO)

Nota: questo va aggiunto in ENTRAMBI
i rami di toDetailDTO():
- ramo con documenti emessi
  (usa valori storici)
- ramo senza documenti
  (ricalcola lo split)
  Le righe split vengono sempre lette
  da DB indipendentemente dal ramo.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. SplitEconomicoDTO — aggiorna
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica la struttura attuale di
SplitEconomicoDTO e dove viene usato.

Se SplitEconomicoDTO ha campi flat
(otaCommissionAmount, cleaningAmount ecc.)
mantienili per retrocompatibilità
con il frontend esistente.

Le righeSpliit sono in aggiunta,
non in sostituzione — il frontend
le userà gradualmente.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. DocumentGenerationService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In DocumentGenerationService.java
inietta BookingSplitEconomicoDAO.

Nel metodo che genera la fattura_pm:

DA:
BigDecimal lordoServizi =
nz(ota) + nz(cleaning) + nz(pmFee)

A:
// Leggi le voci dalla tabella split
List<BookingSplitEconomico> righe =
splitEconomicoDAO
.findByBookingId(bookingId)

// Somma solo le voci in fattura PM
BigDecimal lordoServizi = righe
.stream()
.filter(r -> Boolean.TRUE.equals(
r.getIncludeInFatturaPm()))
.map(BookingSplitEconomico::getImporto)
.reduce(BigDecimal.ZERO,
BigDecimal::add)

// Se nessuna riga split trovata
// fallback ai campi flat del booking
// (per booking pre-migrazione)
if (lordoServizi.compareTo(
BigDecimal.ZERO) == 0
&& righe.isEmpty()) {
lordoServizi = nz(ota)
.add(nz(cleaning))
.add(nz(pmFee))
log.warn("DocumentGenerationService" +
" - bookingId={} nessuna riga" +
" split trovata, uso campi flat",
bookingId)
}

Aggiorna anche le righe del documento
(buildRigaConIva) per usare
le descrizioni dalle righe split
invece delle etichette hardcodate:

Per ogni riga split con
include_in_fattura_pm = true:
crea una riga documento con
descrizione = r.getDescrizione()
importo = r.getImporto()
aliquota_iva = r.getAliquotaIva()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BUILD E TEST
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

# Verifica che BookingDetail
# esponga le righe split
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings/275" \
| python3 -m json.tool \
| grep -A 30 '"righeSplit"'

# Verifica booking senza righe split
# (booking pre-migrazione)
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings/245" \
| python3 -m json.tool \
| grep -A 5 '"righeSplit"'

Verifica che:
- booking 275 → righeSplit con
  righe OTA, pulizie, PM, tassa
- booking 245 → righeSplit vuota
  o lista vuota []
- DocumentGenerationService usa
  le righe split per lordoServizi
  con fallback ai campi flat

Riporta output build e curl.