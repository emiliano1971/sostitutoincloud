-- Migration 003: rimuove la colonna residua settlement.fk_booking_id.
-- Campo mai utilizzato: la relazione booking <-> settlement e' gestita dalla
-- tabella di join settlement_booking (N:N).
-- NB: idempotente (IF EXISTS). Il drop era gia' stato applicato dalla
-- migration 001; questa 003 lo ripete in modo sicuro per allineare gli ambienti.
ALTER TABLE settlement DROP COLUMN IF EXISTS fk_booking_id;
