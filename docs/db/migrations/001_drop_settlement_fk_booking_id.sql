-- Migration 001: rimuove la colonna residua settlement.fk_booking_id.
-- La relazione booking <-> settlement e' gestita dalla tabella di join
-- settlement_booking (N:N). Il campo non e' mai stato usato da Model/DAO/
-- Service/frontend.
ALTER TABLE settlement DROP COLUMN IF EXISTS fk_booking_id;
