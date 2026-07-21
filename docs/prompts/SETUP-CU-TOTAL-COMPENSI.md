Leggi il file CLAUDE.md prima di procedere.

Correggi la query aggregate_by_owner_year.sql
per la CU: total_compensi deve essere il
lordo (canone + ritenuta), non solo il canone.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. SQL
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica src/main/resources/sql/cu_record/
aggregate_by_owner_year.sql:

DA:
SELECT
SUM(wl.canone_locazione) AS total_compensi,
SUM(wl.canone_locazione) AS total_imponibile,
SUM(wl.ritenuta_amount)  AS total_ritenute
FROM withholding_ledger wl
WHERE wl.fk_tenant_id = ?
AND wl.fk_owner_id  = ?
AND EXTRACT(YEAR FROM wl.payment_date) = ?

A:
SELECT
SUM(wl.canone_locazione +
wl.ritenuta_amount)  AS total_compensi,
SUM(wl.canone_locazione) AS total_imponibile,
SUM(wl.ritenuta_amount)  AS total_ritenute
FROM withholding_ledger wl
WHERE wl.fk_tenant_id = ?
AND wl.fk_owner_id  = ?
AND EXTRACT(YEAR FROM wl.payment_date) = ?

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. AGGIORNA CU ESISTENTI NEL DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Esegui sul DB per ricalcolare le CU
già generate con il valore errato:

psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -c "
UPDATE cu_record cr
SET total_compensi = sq.new_compensi,
updated_at = NOW()
FROM (
SELECT
wl.fk_tenant_id,
wl.fk_owner_id,
EXTRACT(YEAR FROM wl.payment_date)
AS tax_year,
SUM(wl.canone_locazione +
wl.ritenuta_amount)
AS new_compensi
FROM withholding_ledger wl
GROUP BY wl.fk_tenant_id,
wl.fk_owner_id,
EXTRACT(YEAR FROM wl.payment_date)
) sq
WHERE cr.fk_tenant_id = sq.fk_tenant_id
AND cr.fk_owner_id  = sq.fk_owner_id
AND cr.tax_year     = sq.tax_year::integer;
"

Verifica:
psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -c "
SELECT cr.id, cr.tax_year,
o.first_name || ' ' || o.last_name
AS owner,
cr.total_compensi,
cr.total_imponibile,
cr.total_ritenute,
cr.total_compensi - cr.total_imponibile
AS differenza_attesa_ritenuta
FROM cu_record cr
JOIN owner_profile o ON o.id = cr.fk_owner_id
ORDER BY cr.id;
"

Verifica che:
total_compensi - total_imponibile
= total_ritenute (per ogni riga)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. TEST
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

# Rigenera CU per verificare nuova formula
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"taxYear":2026}' \
http://localhost:8081/sostitutoincloud/\
api/cu/genera \
| python3 -m json.tool

curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/cu?taxYear=2026" \
| python3 -m json.tool \
| grep -E '"totalCompensi"|"totalImponibile"\
|"totalRitenute"'

Verifica che per Emiliano Zerbinati:
- totalCompensi  = 614,34
  (507,72 + 106,62)
- totalImponibile = 507,72
- totalRitenute   = 106,62

Riporta output build, SQL update e curl.