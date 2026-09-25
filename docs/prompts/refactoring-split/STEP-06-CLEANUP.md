Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- docs/db/schema-target.sql
- docs/db/migrations/ (ultimo numero)
- frontend/tests/e2e/fase04-documenti-fiscali.spec.ts
- frontend/tests/e2e/verifica-importi-booking.spec.ts
  prima di procedere.

Cleanup finale del refactoring split
economico: ALTER TABLE, dead code
e aggiornamento test.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MIGRATION — ALTER TABLE booking
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea docs/db/migrations/
019_booking_reorder_columns.sql

Obiettivo: spostare i campi dello split
economico (ora storici) in fondo alla
tabella booking, dopo tutti gli altri
campi operativi.

In PostgreSQL l'ordine delle colonne
non si cambia con ALTER TABLE diretto.
L'unico modo è ricreare la tabella.

Verifica prima l'ordine attuale:
psql -U sostitutoincloud \
-d sostitutoincloud -h localhost -c "
SELECT column_name, ordinal_position
FROM information_schema.columns
WHERE table_name = 'booking'
ORDER BY ordinal_position;"

Nella migration 019, i campi da spostare
in fondo a booking sono TUTTI gli importi:

- gross_amount
- ota_commission_amount
- cleaning_amount
- pm_fee_amount
- owner_net_amount
- withholding_amount
- aliquota_ritenuta
- tourist_tax_amount
- tourist_tax_included_in_gross
- tourist_tax_collection
- total_costi_pm

La migration deve:
1. Rinominare booking → booking_old
2. CREATE TABLE booking con colonne
   nell'ordine desiderato
3. INSERT INTO booking SELECT ...
   FROM booking_old
   (tutti i campi nell'ordine nuovo)
4. Ricrea FK, indici, trigger,
   sequenze dalla tabella originale
5. DROP TABLE booking_old

ATTENZIONE: prima di eseguire
verifica tutte le FK che puntano
a booking:
psql -U sostitutoincloud \
-d sostitutoincloud -h localhost -c "
SELECT tc.table_name,
kcu.column_name,
rc.delete_rule
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
ON tc.constraint_name =
kcu.constraint_name
JOIN information_schema.referential_constraints rc
ON tc.constraint_name =
rc.constraint_name
JOIN information_schema.table_constraints tc2
ON rc.unique_constraint_name =
tc2.constraint_name
WHERE tc2.table_name = 'booking'
AND tc.constraint_type =
'FOREIGN KEY';"

Tutte le FK verso booking vanno
ricreate dopo il DROP/CREATE.

Esegui in una transazione:
BEGIN;
-- tutto il DDL
COMMIT;
-- oppure ROLLBACK per test a vuoto

Aggiorna schema-target.sql
con il nuovo ordine colonne.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. DEAD CODE — backend
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Cerca e rimuovi o segna come deprecated
il codice non più usato dopo il
refactoring split.

Verifica se questi elementi sono
ancora usati da qualcuno:

a) ScenarioFiscaleDAO.java
ScenarioFiscaleRowMapper.java
model/ScenarioFiscale.java
→ erano già dead code prima
del refactoring, ora ancora
meno usati

b) LookupService.scenariFiscali()
→ verificare se ancora esposto
e se qualcuno lo chiama

c) Metodi in ContrattoCalcolatoreService
che non vengono più chiamati
dopo l'introduzione di
ContrattoCalcoloResult con FK regole

d) VociFatturaPmService
→ verificare che sia usato
da tutti e tre i punti
(FiscalDocumentService,
DocumentPdfService,
SdiXmlService)

NON rimuovere senza verificare —
riporta prima cosa è ancora usato
e cosa è dead code, poi aspetta
conferma prima di eliminare.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. DEAD CODE — frontend
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica se questi elementi sono
ancora usati:

a) Righe hardcodate OTA/pulizie/PM
in BookingDetail.tsx
→ rimaste come fallback per
booking pre-migrazione:
tenerle o rimuoverle?

b) Campi nel tipo BookingDetail
in bookingApi.ts che sono ora
ridondanti con righeSplit:
otaCommissionAmount,
cleaningAmount, pmFeeAmount
→ tenerli: usati in BookingsList
e in altri componenti

c) F24PreviewDialog.tsx
→ già identificato come dead code
in sessioni precedenti

d) mock-data.ts
→ ancora usato da qualcuno?

NON rimuovere — riporta solo
l'analisi e aspetta conferma.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. TEST E2E — aggiornamento
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna i test E2E che verificano
gli importi dello split per usare
le righe split quando disponibili.

In verifica-importi-booking.spec.ts:

V.1 — coerenza split booking:
Aggiungi verifica che righeSplit
sia presente e coerente con i
campi flat:

if (booking.righeSplit &&
booking.righeSplit.length > 0) {
const sommaRigheSplit =
booking.righeSplit
.filter(r =>
r.includeInFatturaPm)
.reduce((s, r) =>
s + r.importo, 0)

// total_costi_pm deve coincidere
// con la somma delle righe
const totalCostiPm =
(booking as any).totalCostiPm ?? 0

expect(approxEqual(
totalCostiPm,
sommaRigheSplit)).toBeTruthy()

console.log(
`V.1b Split righe: ` +
`${booking.righeSplit.length} righe, ` +
`somma=${sommaRigheSplit.toFixed(2)}, ` +
`totalCostiPm=${totalCostiPm}`)
}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BUILD E TEST FINALE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package
cd frontend && npm run build
npm run typecheck

# Lancia suite completa E2E
cd frontend
npx playwright test

# Lancia verifica importi
# sul primo booking con documenti
npx playwright test \
tests/e2e/verifica-importi-booking.spec.ts

Verifica che:
- Tutti gli 88 test E2E passino
- V.1b verifica coerenza righe split
- Nessuna regressione

Riporta output con ✅/❌.