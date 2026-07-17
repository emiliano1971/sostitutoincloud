Leggi il file CLAUDE.md e docs/db/schema-target.sql
prima di procedere.

Rimuovi la colonna fk_booking_id dalla tabella
settlement: è un campo residuo mai utilizzato.
La relazione booking↔settlement è già gestita
dalla tabella settlement_booking (N:N).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MIGRATION DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea docs/db/migrations/
003_drop_settlement_fk_booking_id.sql:

ALTER TABLE settlement
DROP COLUMN IF EXISTS fk_booking_id;

Esegui sul DB:
psql -U sostitutoincloud -d sostitutoincloud \
-h localhost \
-f docs/db/migrations/003_drop_settlement_fk_booking_id.sql

Verifica:
psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -c "\d settlement"

La colonna fk_booking_id NON deve comparire.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. AGGIORNA SCHEMA SQL
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica docs/db/schema-target.sql:
- Rimuovi la riga fk_booking_id da
  CREATE TABLE settlement
- Verifica che non esistano indici dedicati
  a settlement.fk_booking_id (se presenti
  rimuovili — NON toccare settlement_booking)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. VERIFICA CODICE BACKEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Cerca in tutto il backend ogni riferimento a:
- fkBookingId nel contesto di Settlement.java
- fk_booking_id nelle query SQL di settlement
  (NON settlement_booking)

Se trovi riferimenti rimuovili.
Se non ne esistono confermalo esplicitamente.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. VERIFICA FRONTEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Cerca in frontend/src ogni riferimento a
settlement.bookingId o campo analogo
collegato direttamente a un singolo booking
sul settlement (NON sull'array bookings).

Se non ne esistono confermalo.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BUILD
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

Riporta:
- output \d settlement (senza fk_booking_id)
- esito ricerche codice backend e frontend
- output build