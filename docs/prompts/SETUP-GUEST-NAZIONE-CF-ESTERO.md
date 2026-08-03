Leggi il file CLAUDE.md e i file esistenti:
- frontend/src/components/GuestEditDialog.tsx
- service/BookingService.java
- dao/BookingDAO.java
  prima di procedere.

Modifica la gestione anagrafica ospite:
1. Campo nazione diventa Select Italia/Straniero
2. Se Straniero: CF fittizio generato automaticamente
3. Blocco emissione fattura PM se CF mancante
4. Rimozione blocco emissione ricevuta owner

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — generazione CF estero
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a service/CodiceFiscaleService.java:

String generaCfEstero(Integer tenantId,
Integer anno)
- Formato: EST + {anno:4} + {progressivo:09d}
  es. "EST202600000001" (16 chars)
- Il progressivo è il COUNT(*) + 1 dei
  booking del tenant che hanno
  guest_tax_code LIKE 'EST%'
  nell'anno specificato
- Implementa con query su BookingDAO:

  Aggiungi a dao/BookingDAO.java:
  Integer countCfEsteroByTenantAndAnno(
  Integer tenantId, Integer anno)
    - SELECT COUNT(*) FROM booking
      WHERE fk_tenant_id = ?
      AND EXTRACT(YEAR FROM created_at) = ?
      AND guest_tax_code LIKE 'EST%'
    - Log DEBUG

- Costruisci il CF:
  String progressivo = String.format(
  "%09d", count + 1);
  return "EST" + anno + progressivo;
  ← verifica che sia esattamente 16 chars:
  EST(3) + anno(4) + progressivo(9) = 16 ✓

- Log INFO "CodiceFiscaleService
  .generaCfEstero() - tenantId={} cf={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — endpoint CF estero
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/CfController.java:

GET /api/cf/estero
- tenantId da SecurityUtils
- anno = anno corrente
- chiama codiceFiscaleService
  .generaCfEstero(tenantId, anno)
- ResponseEntity.ok(Map.of("cf", cf))
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — blocco fattura PM
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica service/DocumentGenerationService.java
nel metodo che genera la fattura PM:

Prima di procedere con la generazione,
verifica che booking.getGuestTaxCode()
sia valorizzato:

if (booking.getGuestTaxCode() == null
|| booking.getGuestTaxCode().isBlank()) {
throw new IllegalStateException(
"CF ospite mancante — inserire il "
+ "codice fiscale prima di emettere "
+ "la fattura PM");
}

Per la ricevuta owner NON aggiungere
nessun blocco — è già emettibile
senza CF.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND — GuestEditDialog.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/components/
GuestEditDialog.tsx:

Sostituisci il campo "Nazione" (text input)
con una Select a due opzioni:
"Italia"    → value="Italia"
"Straniero" → value="Straniero"
default: "Italia"

Aggiungi stato:
const [nazione, setNazione] =
useState<"Italia" | "Straniero">(
guest.guestCountry === "Straniero"
? "Straniero" : "Italia")

Comportamento in base alla nazione:

SE nazione = "Italia":
- Mostra tutti i campi come ora:
  data nascita, sesso, comune (ComuneAutocomplete)
- Pulsante "Calcola CF" attivo se
  dati completi
- CF è campo readonly con calcolo manuale

SE nazione = "Straniero":
- Nascondi: data nascita, sesso,
  comune di nascita
- Mostra solo: nome, tipo documento,
  numero documento
- Campo CF: readonly con placeholder
  "Verrà generato automaticamente"
- Pulsante "Genera CF Straniero"
  al posto di "Calcola CF":
    * onClick → chiama GET /api/cf/estero
    * popola il campo CF con il risultato
    * toast "CF estero generato: {cf}"

Al salvataggio passa:
guestCountry: nazione
(= "Italia" o "Straniero")

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. FRONTEND — BookingDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica la costante datiFatturazioneMancanti:

DA:
const datiFatturazioneMancanti =
!booking.guestTaxCode

A:
// Il badge "incompleto" serve solo
// per la fattura PM — la ricevuta
// owner non richiede il CF
const datiFatturazioneMancanti =
!booking.guestTaxCode

// Ma il blocco emissione è solo
// lato backend per fattura PM —
// il badge frontend resta informativo
// non bloccante

Quindi il badge rimane ma non blocca
l'emissione della ricevuta owner
(il blocco è solo backend per fattura PM).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. TEST
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

# Genera CF estero
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/cf/estero" \
| python3 -m json.tool

# Verifica che sia 16 chars
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/cf/estero" \
| python3 -c "import sys,json; \
cf=json.load(sys.stdin)['cf']; \
print(f'CF: {cf} len={len(cf)}')"

Verifica che:
- CF estero = "EST" + anno(4) + 9 cifre
  = esattamente 16 chars
- Due chiamate consecutive restituiscono
  CF diversi (progressivo incrementa)
- Emissione ricevuta owner senza CF → OK
- Emissione fattura PM senza CF → 422
  con messaggio "CF ospite mancante"

Riporta output build e curl.