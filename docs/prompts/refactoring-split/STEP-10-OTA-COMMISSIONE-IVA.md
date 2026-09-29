Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- model/CanaleOta.java
- dao/CanaleOtaDAO.java
- dao/mapper/CanaleOtaRowMapper.java
- service/BookingImportService.java
- service/BookingService.java
  metodo popolaSplitEconomico()
- model/BookingSplitEconomico.java
- dao/BookingSplitEconomicoDAO.java
- frontend/src/pages/tenant/
  OtaManagement.tsx o equivalente
  (pagina gestione canali OTA)
  prima di procedere.

Aggiungi la configurazione
commissione_ivata sul canale OTA
e salva il valore originale dal file
in booking_split_economico.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MIGRATION DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica l'ultimo numero migration
e crea il file successivo:
021_ota_commissione_iva.sql

-- Campo sul canale OTA
ALTER TABLE canale_ota
ADD COLUMN IF NOT EXISTS
commissione_ivata BOOLEAN
NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN
canale_ota.commissione_ivata IS
'true = commissione nel file di import
è lorda (IVA inclusa, es. Booking.com).
false = commissione nel file è netta,
il sistema aggiunge IVA 22%
(es. Airbnb).
Default true per retrocompatibilità.';

-- Campo su booking_split_economico
ALTER TABLE booking_split_economico
ADD COLUMN IF NOT EXISTS
importo_originale_file DECIMAL(10,2)
DEFAULT NULL;

COMMENT ON COLUMN
booking_split_economico
.importo_originale_file IS
'Valore grezzo della commissione OTA
dal file di import, prima di qualsiasi
trasformazione IVA. NULL per righe non
da import o per voci non OTA.
Utile per riconciliazione e debug.';

-- Imposta Airbnb come commissione netta
-- Verifica prima l'id di Airbnb:
SELECT id, nome FROM canale_ota
WHERE nome ILIKE '%airbnb%';

-- Poi aggiorna (sostituisci {ID_AIRBNB}
-- con l'id trovato):
UPDATE canale_ota
SET commissione_ivata = FALSE
WHERE nome ILIKE '%airbnb%';

Aggiorna schema-target.sql.
Esegui sul DB locale.
Riporta i canali OTA esistenti
e i loro valori di commissione_ivata.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — model e DAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a model/CanaleOta.java:
Boolean commissioneIvata

Aggiorna dao/mapper/
CanaleOtaRowMapper.java:
.commissioneIvata(
rs.getBoolean("commissione_ivata"))

Aggiorna dao/CanaleOtaDAO.java:
- Aggiungi commissione_ivata in SELECT
- Aggiungi commissione_ivata in INSERT
  con default true
- Aggiungi commissione_ivata in UPDATE

Aggiungi a model/
BookingSplitEconomico.java:
BigDecimal importoOriginaleFile

Aggiorna dao/mapper/
BookingSplitEconomicoRowMapper.java:
.importoOriginaleFile(
rs.getBigDecimal(
"importo_originale_file"))

Aggiorna dao/
BookingSplitEconomicoDAO.java:
- Aggiungi importo_originale_file
  in SELECT_ALL
- Aggiungi importo_originale_file
  in INSERT (nullable)
- NON aggiungere in UPDATE
  ← valore storico, non modificabile

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — BookingImportService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In BookingImportService.confirm()
dove viene determinato overridesDaImport:

DA:
boolean overridesDaImport = true
← sempre tratta commissione come lorda

A:
// Leggi configurazione dal canale
boolean commissioneIvata =
canale == null
|| Boolean.TRUE.equals(
canale.getCommissioneIvata())
// true = lorda (default retrocompat)
// false = netta (es. Airbnb)

boolean overridesDaImport =
commissioneIvata
// true → commissione lorda →
//   il calcolatore scorpora IVA
// false → commissione netta →
//   il calcolatore aggiunge IVA

Salva il valore originale dal file:
BigDecimal commissioneOriginale =
row.getOtaCommissionAmount()
← il valore grezzo prima di
qualsiasi trasformazione

Questo verrà passato a
popolaSplitEconomico() come
importoOriginaleFile per la riga OTA.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BACKEND — BookingService
   popolaSplitEconomico()
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi parametro a
popolaSplitEconomico():

DA:
void popolaSplitEconomico(
Integer bookingId,
Integer tenantId,
ContrattoCalcoloResult calcolo,
BigDecimal touristTaxAmount,
Boolean touristTaxIncludedInGross,
String canaleName,
Integer utenteId,
String source)

A:
void popolaSplitEconomico(
Integer bookingId,
Integer tenantId,
ContrattoCalcoloResult calcolo,
BigDecimal touristTaxAmount,
Boolean touristTaxIncludedInGross,
String canaleName,
Integer utenteId,
String source,
BigDecimal importoOriginaleFile)
← null se non da import

Nella riga OTA:
.importoOriginaleFile(
importoOriginaleFile)

Aggiorna tutti i chiamanti di
popolaSplitEconomico() passando null
tranne BookingImportService.confirm()
che passa commissioneOriginale.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BACKEND — DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dto/booking/
BookingSplitEconomicoDTO.java:
BigDecimal importoOriginaleFile

Aggiungi a bookingApi.ts
interfaccia BookingSplitRiga:
importoOriginaleFile?: number

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. FRONTEND — gestione canali OTA
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Trova la pagina o il dialog di
creazione/modifica canale OTA
(probabilmente OtaManagement.tsx
o simile).

Aggiungi toggle per commissioneIvata:

<div className="flex items-center
  justify-between">
  <div>
    <Label>Commissione nel file
      include IVA</Label>
    <p className="text-xs
      text-muted-foreground">
      Attivo: commissione è lorda
        (es. Booking.com)
      Spento: commissione è netta,
        il sistema aggiunge IVA 22%
        (es. Airbnb)
    </p>
  </div>
  <Switch
    checked={form.commissioneIvata
      ?? true}
    onCheckedChange={v =>
      setForm({...form,
        commissioneIvata: v})}
  />
</div>

Default: true (attivo = lorda)

Aggiorna otaApi.ts o canaleOtaApi.ts:
Aggiungi commissioneIvata?: boolean
all'interfaccia CanaleOta e
alle funzioni create/update.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. FRONTEND — BookingDetail
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Nella riga OTA dello Split Economico
se importoOriginaleFile è valorizzato
mostra un hint sotto la descrizione:

{r.importoOriginaleFile != null
&& r.importoOriginaleFile !==
r.imponibile && (
<span className="text-xs
text-muted-foreground/60">
file: €{formatAmount(
r.importoOriginaleFile)}
</span>
)}

Così il PM può confrontare il valore
originale dal file con quello
trasformato dal sistema.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. BUILD E TEST
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

# Verifica canali OTA e commissione_ivata
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/canali-ota" \
| python3 -c "
import sys,json
for c in json.load(sys.stdin):
print(f\"{c['id']:3} {c['nome']:20} \
ivata={c.get('commissioneIvata')}\")
"

# Test import con Airbnb
# (commissione netta → sistema aggiunge IVA)
# Prepara CSV con commissione 15.00
# su canale Airbnb (commissione_ivata=false)
# Atteso: imponibile=15.00, importo=18.30

# Verifica booking importato
# importoOriginaleFile=15.00 (dal file)
# imponibile=15.00 (netto)
# importo=18.30 (lordo con IVA)

Riporta output build, canali OTA
con commissione_ivata e verifica
che Airbnb abbia commissioneIvata=false.