Leggi il file CLAUDE.md e questi file:
- src/main/java/it/gavia/sostitutoincloud/
  service/PropertyContractService.java
- src/main/java/it/gavia/sostitutoincloud/
  service/TenantSettingsService.java
- src/main/java/it/gavia/sostitutoincloud/
  dao/PropertyContractRuleDAO.java
- docs/db/schema-target.sql (tabella
  property_contract_rule)

Implementa il servizio di calcolo dello
split economico basato sulle regole
del contratto immobile.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. CREA ContrattoCalcoloResult
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/booking/ContrattoCalcoloResult.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor

Campi:
- BigDecimal grossAmount
- BigDecimal otaCommissionAmount
- BigDecimal cleaningAmount
- BigDecimal pmFeeAmount
- BigDecimal imponibilePm
  (ota + cleaning + pmFee)
- BigDecimal ivaPm
  (imponibilePm * aliquotaIva)
- BigDecimal fatturaPmTotale
  (imponibilePm + ivaPm)
- BigDecimal ownerNetAmount
  (gross - fatturaPmTotale)
- BigDecimal withholdingAmount
  (ownerNet * aliquotaRitenuta)
- BigDecimal liquidazioneOwner
  (ownerNet - withholding)
- String regimeFiscalePm
  (RF01 o RF19)
- Boolean calcoloCompleto
  (true se trovate tutte le regole)
- List<String> warnings
  (messaggi se regole mancanti)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. CREA ContrattoCalcolatoreService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/ContrattoCalcolatoreService.java:
- @Service @Log4j2
- Costruttore con PropertyContractRuleDAO,
  TenantSettingsService

Metodo principale:

ContrattoCalcoloResult calcola(
Integer tenantId,
Integer propertyId,
Integer fkCanaleOtaId,
BigDecimal gross,
BigDecimal otaCommissionOverride,
Integer nights,
Integer guests)

Parametri:
- otaCommissionOverride: se != null usa
  questo valore invece di calcolare
  la commissione OTA dalle regole
  (viene dal CSV campo Commissione)

Logica:
1. Carica settings =
   tenantSettingsService.getSettings(tenantId)
2. Determina aliquotaIvaPm:
   RF19 → 0, RF01 → 0.22
3. Carica regole:
   contractRuleDAO.findByPropertyId(propertyId)
4. Separa regole per canale:
    - regole con fkCanaleOtaId = canale corrente
      O fkCanaleOtaId = null (tutte le OTA)
    - per commissione_ota prendi solo la regola
      del canale corrente se esiste,
      altrimenti quella generica
5. Calcola ogni voce:

   Per ogni regola NON rimanenza:

   "pulizie":
   fisso → valore
   fisso_per_notte → valore * nights
   fisso_per_persona → valore * guests

   "commissione_ota":
   SE otaCommissionOverride != null:
   → usa otaCommissionOverride (dal CSV)
   ALTRIMENTI:
   percentuale → gross * valore / 100
   fisso → valore

   "cambio_biancheria":
   fisso_per_persona → valore * guests
   fisso_per_notte → valore * nights
   fisso → valore

   "commissione_pm":
   percentuale_lordo → gross * valore / 100
   percentuale → gross * valore / 100
   fisso → valore
   (NON rimanenza qui)

   Tutti arrotondati a 2 decimali HALF_UP

6. Calcola rimanenza:
    - trova regola is_remainder = true
    - remainderAmount = gross
        - sum(tutte le voci non rimanenza)
    - se remainderAmount < 0 →
      aggiungi warning "I costi superano
      il lordo della prenotazione"

7. Assegna i valori:
    - otaCommissionAmount = valore calcolato
      per commissione_ota
    - cleaningAmount = valore calcolato
      per pulizie + cambio_biancheria
    - pmFeeAmount = valore calcolato
      per commissione_pm
    - ownerProvvigione = remainderAmount
      (provvigione_proprietario)

8. Calcola split fiscale:
   imponibilePm = ota + cleaning + pmFee
   ivaPm = imponibilePm * aliquotaIvaPm
   fatturaPmTotale = imponibilePm + ivaPm
   ownerNet = gross - fatturaPmTotale
   withholding = ownerNet *
   (aliquotaRitenuta / 100)
   liquidazione = ownerNet - withholding

9. Se nessuna regola trovata:
    - calcoloCompleto = false
    - aggiungi warning
    - usa valori di fallback:
      ownerNet = gross (nessuna detrazione)
      withholding = gross * 0.21

10. Log INFO: "ContrattoCalcolatore -
    tenant={} property={} canale={}
    gross={} ownerNet={} withholding={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. AGGIORNA BookingImportService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In service/BookingImportService.java:
- Inietta ContrattoCalcolatoreService
- Nel metodo confirm() per ogni riga:

    * Recupera fkPropertyId dalla riga
      (già presente dopo il match immobile)
    * Recupera fkCanaleOtaId dalla riga
    * Chiama:
      ContrattoCalcoloResult result =
      contrattoCalcolatore.calcola(
      tenantId,
      row.getFkPropertyId(),
      row.getFkCanaleOtaId(),
      row.getGrossAmount(),
      row.getOtaCommissionAmount(),
      ← questo è l'override dal CSV
      row.getNights(),
      row.getGuests())

    * Usa result per popolare il Booking:
      .otaCommissionAmount(
      result.getOtaCommissionAmount())
      .cleaningAmount(result.getCleaningAmount())
      .pmFeeAmount(result.getPmFeeAmount())
      .ownerNetAmount(result.getOwnerNetAmount())
      .withholdingAmount(
      result.getWithholdingAmount())

    * Se result.getWarnings() non vuoto:
      aggiungi i warning ai log dell'import

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. AGGIORNA BookingService — SplitEconomicoDTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In service/BookingService.java:
- Inietta ContrattoCalcolatoreService
- Nel metodo toDetailDTO() che costruisce
  SplitEconomicoDTO:

    * Chiama contrattoCalcolatore.calcola(
      b.getFkTenantId(),
      b.getFkPropertyId(),
      b.getFkCanaleOtaId(),
      b.getGrossAmount(),
      b.getOtaCommissionAmount(),
      ← usa il valore già nel DB come override
      b.getNights(),
      b.getGuests())

    * Usa result per popolare SplitEconomicoDTO:
      .grossAmount(b.getGrossAmount())
      .otaCommissionAmount(
      result.getOtaCommissionAmount())
      .cleaningAmount(result.getCleaningAmount())
      .pmFeeAmount(result.getPmFeeAmount())
      .ownerNetAmount(result.getOwnerNetAmount())
      .withholdingAmount(b.getWithholdingAmount())
      ← mantieni quello del DB
      .liquidazioneOwner(
      result.getLiquidazioneOwner())
      .ivaPm(result.getIvaPm())
      ← nuovo campo

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. AGGIORNA SplitEconomicoDTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna dto/booking/SplitEconomicoDTO.java:
- Aggiungi BigDecimal ivaPm
- Aggiungi BigDecimal fatturaPmTotale
- Aggiungi List<String> warnings
- Aggiungi Boolean calcoloCompleto

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. AGGIORNA FRONTEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna frontend/src/api/bookingApi.ts:
- Aggiungi a SplitEconomico:
  ivaPm?: number
  fatturaPmTotale?: number
  warnings?: string[]
  calcoloCompleto?: boolean

Aggiorna frontend/src/pages/tenant/
BookingDetail.tsx:
- Aggiungi riga nello split dopo
  "Provvigione PM":
  "IVA 22% servizi PM" → split.ivaPm
  (mostra solo se ivaPm > 0, segno −)
- Se split.warnings?.length > 0:
  mostra un alert giallo con i warnings
- La riga "Netto proprietario" mostra
  split.ownerNetAmount

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

Dopo il build verifica con curl
il booking 1 (che ha le regole contratto):

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena"}' \
| python3 -c \
"import sys,json; \
print(json.load(sys.stdin)['token'])")

curl -s -H "Authorization: Bearer $TOKEN" \
http://localhost:8081/sostitutoincloud/\
api/bookings/1 \
| python3 -m json.tool \
| grep -A 30 "splitEconomico"

Verifica che:
- otaCommissionAmount = valore dal CSV
  (override, non dalle regole)
- ivaPm = (ota+cleaning+pmFee) * 0.22
- ownerNetAmount = gross - fatturaPmTotale
- calcoloCompleto = true se regole presenti

Riporta output di entrambi i build
e del curl.

NOTA: backend Tomcat e frontend Vite sono
già in esecuzione — non fermarli.
Dopo mvn -Plocal clean package le nuove
classi vengono copiate in WEB-INF/classes/
dall'Ant automaticamente, ma Tomcat deve
essere riavviato per caricarle.

Esegui solo il build, poi avvisa che
serve riavvio Tomcat prima del curl di test.