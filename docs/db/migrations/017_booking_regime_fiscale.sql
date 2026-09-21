-- 017_booking_regime_fiscale.sql
-- booking.fk_scenario_fiscale_id diventa booking.fk_regime_fiscale_id, con FK verso
-- regime_fiscale invece di scenario_fiscale. La prenotazione fotografa il regime del
-- proprietario al momento dell'inserimento: l'owner può cambiarlo in seguito e i
-- documenti già emessi devono restare coerenti con il regime di allora.
--
-- ATTENZIONE — la FK NON vincola il metadata. regime_fiscale è una lookup multi-uso
-- (REGIME_FISCALE, REGIME_FISCALE_PM, NATURA_IVA, ALIQUOTA_IVA) e una FOREIGN KEY
-- semplice accetta qualunque id della tabella, non solo i tre con
-- metadata='REGIME_FISCALE'. PostgreSQL non ammette subquery nei CHECK, quindi il
-- filtro resta responsabilità del codice, che assegna sempre
-- owner_profile.fk_regime_fiscale_id. Per imporlo a DB servirebbe un trigger.
--
-- scenario_fiscale resta in piedi ma non è più referenziata da nessuna FK.

-- Rimuovi FK e indice vecchi
ALTER TABLE booking
  DROP CONSTRAINT IF EXISTS booking_fk_scenario_fiscale_id_fkey;

DROP INDEX IF EXISTS idx_booking_fk_scenario_fiscale_id;

-- Rinomina colonna
ALTER TABLE booking
  RENAME COLUMN fk_scenario_fiscale_id TO fk_regime_fiscale_id;

-- Nuova FK verso regime_fiscale
ALTER TABLE booking
  ADD CONSTRAINT booking_fk_regime_fiscale_id_fkey
  FOREIGN KEY (fk_regime_fiscale_id)
  REFERENCES regime_fiscale(id)
  ON DELETE SET NULL;

-- Ricrea indice
CREATE INDEX IF NOT EXISTS idx_booking_fk_regime_fiscale_id
  ON booking(fk_regime_fiscale_id);

-- Backfill: regime del proprietario della prenotazione.
-- owner_profile.fk_regime_fiscale_id è NOT NULL, quindi ogni booking con owner
-- valorizzato riceve un regime; quelli senza owner restano NULL.
UPDATE booking b
SET fk_regime_fiscale_id = (
  SELECT op.fk_regime_fiscale_id
  FROM owner_profile op
  WHERE op.id = b.fk_owner_id)
WHERE b.fk_owner_id IS NOT NULL;
